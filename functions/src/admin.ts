import { getAuth, UserRecord } from "firebase-admin/auth";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall, CallableRequest } from "firebase-functions/v2/https";
import { ADMIN_EMAILS, REGION } from "./config";
import { addMember, claimGroupCode, deleteExpiredPairings, removeMember } from "./groups";
import { cleanGroupName, MAX_GROUP_SIZE, SWIPE_COLLECTIONS, SwipeKind } from "./logic";

/**
 * Admin tools: a maintenance screen (view, create, edit, delete users, groups and their data) and
 * impersonation. Every call checks the caller's *verified* Google email against ADMIN_EMAILS on
 * the server, and is refused while impersonating. Every action is written to /adminAudit, which
 * clients can't read or write.
 */

const db = () => getFirestore();
const opts = { region: REGION };
const KINDS = Object.keys(SWIPE_COLLECTIONS) as SwipeKind[];

export function isAdminToken(token: Record<string, unknown>): boolean {
  const t = token as { email?: string; email_verified?: boolean; impersonatedBy?: string };
  return !!t.email && t.email_verified === true && !t.impersonatedBy && ADMIN_EMAILS.includes(t.email.toLowerCase());
}

export function requireAdmin(req: CallableRequest): string {
  if (!req.auth || !isAdminToken(req.auth.token)) throw new HttpsError("permission-denied", "Admins only.");
  return req.auth.uid;
}

export async function audit(adminUid: string, action: string, target: Record<string, unknown>) {
  await db().collection("adminAudit").add({ adminUid, action, ...target, at: FieldValue.serverTimestamp() });
}

function str(v: unknown, name: string): string {
  if (typeof v !== "string" || !v.trim()) throw new HttpsError("invalid-argument", `Missing ${name}.`);
  return v.trim();
}

const millis = (t: unknown) => (t instanceof Timestamp ? t.toMillis() : null);
const authTime = (s: string | undefined) => (s ? Date.parse(s) : null);

// ---------------------------------------------------------------- who am I

export const adminWhoAmI = onCall(opts, async (req) => ({ admin: !!req.auth && isAdminToken(req.auth.token) }));

// ---------------------------------------------------------------- users

async function allAuthUsers(): Promise<UserRecord[]> {
  const out: UserRecord[] = [];
  let token: string | undefined;
  do {
    const page = await getAuth().listUsers(1000, token);
    out.push(...page.users);
    token = page.pageToken;
  } while (token && out.length < 5000);
  return out;
}

export const adminListUsers = onCall(opts, async (req) => {
  requireAdmin(req);
  const q = typeof req.data?.query === "string" ? req.data.query.trim().toLowerCase() : "";
  const users = await allAuthUsers();
  const docs = users.length ? await db().getAll(...users.map((u) => db().doc(`users/${u.uid}`))) : [];
  const rows = users.map((u, i) => {
    const d = docs[i];
    return {
      uid: u.uid,
      email: u.email ?? d.get("email") ?? null,
      displayName: d.get("displayName") ?? u.displayName ?? "",
      photoUrl: d.get("photoUrl") ?? u.photoURL ?? null,
      groupCount: ((d.get("groupIds") as string[] | undefined) ?? []).length,
      createdAt: authTime(u.metadata.creationTime),
      lastSignInAt: authTime(u.metadata.lastRefreshTime ?? u.metadata.lastSignInTime),
      disabled: u.disabled,
      providers: u.providerData.map((p) => p.providerId),
      isAdmin: !!u.email && ADMIN_EMAILS.includes(u.email.toLowerCase()),
    };
  }).filter((r) => !q || [r.displayName, r.email ?? "", r.uid].some((s) => s.toLowerCase().includes(q)));
  rows.sort((a, b) => (b.lastSignInAt ?? 0) - (a.lastSignInAt ?? 0));
  return { users: rows };
});

async function swipeCounts(gid: string, uid: string) {
  const counts: Record<string, { interested: number; passed: number }> = {};
  for (const kind of KINDS) {
    const snap = await db().collection(`groups/${gid}/${SWIPE_COLLECTIONS[kind].swipes}`).where("userId", "==", uid).get();
    counts[kind] = {
      interested: snap.docs.filter((d) => d.get("decision") === "interested").length,
      passed: snap.docs.filter((d) => d.get("decision") === "passed").length,
    };
  }
  return counts;
}

export const adminGetUser = onCall(opts, async (req) => {
  requireAdmin(req);
  const uid = str(req.data?.uid, "user");
  const [authUser, doc, devices] = await Promise.all([
    getAuth().getUser(uid).catch(() => null),
    db().doc(`users/${uid}`).get(),
    db().collection(`users/${uid}/devices`).get(),
  ]);
  if (!authUser && !doc.exists) throw new HttpsError("not-found", "No such user.");
  const groupIds = (doc.get("groupIds") as string[] | undefined) ?? [];
  const groups = await Promise.all(groupIds.map(async (gid) => {
    const g = await db().doc(`groups/${gid}`).get();
    return {
      id: gid,
      type: g.get("type") ?? "couple",
      name: g.get("name") ?? null,
      status: g.get("status") ?? "missing",
      memberCount: ((g.get("memberIds") as string[] | undefined) ?? []).length,
      swipes: await swipeCounts(gid, uid),
    };
  }));
  return {
    uid,
    email: authUser?.email ?? doc.get("email") ?? null,
    displayName: doc.get("displayName") ?? authUser?.displayName ?? "",
    photoUrl: doc.get("photoUrl") ?? authUser?.photoURL ?? null,
    onboarded: doc.get("onboarded") ?? false,
    notifications: doc.get("notifications") ?? {},
    disabled: authUser?.disabled ?? false,
    providers: authUser?.providerData.map((p) => p.providerId) ?? [],
    createdAt: authTime(authUser?.metadata.creationTime) ?? millis(doc.get("createdAt")),
    lastSignInAt: authTime(authUser?.metadata.lastRefreshTime ?? authUser?.metadata.lastSignInTime),
    deviceCount: devices.size,
    groups,
  };
});

export const adminCreateUser = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const email = str(req.data?.email, "email").toLowerCase();
  const displayName = str(req.data?.displayName, "name").slice(0, 40);
  const password = str(req.data?.password, "password");
  if (password.length < 6) throw new HttpsError("invalid-argument", "Password needs at least 6 characters.");
  const user = await getAuth().createUser({ email, password, displayName }).catch((e) => {
    throw new HttpsError("already-exists", (e as Error).message);
  });
  await db().doc(`users/${user.uid}`).set({
    displayName, email, photoUrl: null, onboarded: true, groupIds: [], createdAt: FieldValue.serverTimestamp(),
    notifications: { enabled: true, matches: true, partnerActivity: true, dateNightReminders: true, swipeReminders: true },
  });
  await audit(admin, "createUser", { uid: user.uid, email });
  return { uid: user.uid };
});

export const adminUpdateUser = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const uid = str(req.data?.uid, "user");
  const d = req.data ?? {};
  const docUpdate: Record<string, unknown> = {};
  const authUpdate: { displayName?: string; disabled?: boolean } = {};
  if (typeof d.displayName === "string" && d.displayName.trim()) {
    docUpdate.displayName = authUpdate.displayName = d.displayName.trim().slice(0, 40);
  }
  if (typeof d.onboarded === "boolean") docUpdate.onboarded = d.onboarded;
  if (d.notifications && typeof d.notifications === "object") docUpdate.notifications = d.notifications;
  if (typeof d.disabled === "boolean") {
    if (uid === admin && d.disabled) throw new HttpsError("failed-precondition", "You can't disable your own account.");
    authUpdate.disabled = d.disabled;
  }
  if (Object.keys(authUpdate).length) await getAuth().updateUser(uid, authUpdate);
  if (Object.keys(docUpdate).length) await db().doc(`users/${uid}`).set(docUpdate, { merge: true });
  // Keep the name members see in sync across all their groups.
  if (docUpdate.displayName) {
    const groupIds = ((await db().doc(`users/${uid}`).get()).get("groupIds") as string[] | undefined) ?? [];
    await Promise.all(groupIds.map((gid) => db().doc(`groups/${gid}/members/${uid}`).set({ displayName: docUpdate.displayName }, { merge: true })));
  }
  if (authUpdate.disabled) await getAuth().revokeRefreshTokens(uid);
  await audit(admin, "updateUser", { uid, fields: [...Object.keys(docUpdate), ...Object.keys(authUpdate)] });
  return { ok: true };
});

/**
 * Removes a user everywhere: their swipes (matches recount), group memberships, user doc with
 * devices, and the sign-in account. Used by the admin and by "Delete my account".
 */
export async function deleteUserData(uid: string): Promise<string[]> {
  const userRef = db().doc(`users/${uid}`);
  const groupIds = ((await userRef.get()).get("groupIds") as string[] | undefined) ?? [];
  for (const gid of groupIds) {
    await deleteSwipesOf(gid, uid);
    await removeMember(gid, uid, false);
  }
  await db().recursiveDelete(userRef); // user doc + devices
  await getAuth().deleteUser(uid).catch(() => undefined);
  return groupIds;
}

/** Deletes a user's own swipes in a group (the triggers then recount any matches). */
async function deleteSwipesOf(gid: string, uid: string) {
  for (const kind of KINDS) {
    const snap = await db().collection(`groups/${gid}/${SWIPE_COLLECTIONS[kind].swipes}`).where("userId", "==", uid).get();
    for (let i = 0; i < snap.docs.length; i += 400) {
      const batch = db().batch();
      snap.docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
      await batch.commit();
    }
  }
}

export const adminDeleteUser = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const uid = str(req.data?.uid, "user");
  if (uid === admin) throw new HttpsError("failed-precondition", "You can't delete your own admin account here.");
  const groupIds = await deleteUserData(uid);
  await audit(admin, "deleteUser", { uid, groupIds });
  return { ok: true };
});

// ---------------------------------------------------------------- groups

export const adminListGroups = onCall(opts, async (req) => {
  requireAdmin(req);
  await deleteExpiredPairings().catch(() => 0); // never list invites that have run out
  const q = typeof req.data?.query === "string" ? req.data.query.trim().toLowerCase() : "";
  const snap = await db().collection("groups").get();
  const allMemberIds = [...new Set(snap.docs.flatMap((d) => (d.get("memberIds") as string[] | undefined) ?? []))];
  const users = allMemberIds.length ? await db().getAll(...allMemberIds.map((u) => db().doc(`users/${u}`))) : [];
  const nameOf = new Map(users.map((u) => [u.id, (u.get("displayName") as string) || u.id]));
  const rows = snap.docs.map((g) => {
    const ids = (g.get("memberIds") as string[] | undefined) ?? [];
    return {
      id: g.id,
      type: g.get("type") ?? "couple",
      name: g.get("name") ?? null,
      status: g.get("status") ?? "ended",
      code: g.get("code") ?? null,
      memberCount: ids.length,
      memberNames: ids.map((u) => nameOf.get(u) ?? u),
      createdAt: millis(g.get("createdAt")),
    };
  }).filter((r) => !q || [r.name ?? "", r.id, r.code ?? "", ...r.memberNames].some((s) => s.toLowerCase().includes(q)));
  const order: Record<string, number> = { active: 0, pending: 1, ended: 2 };
  rows.sort((a, b) => (order[a.status] ?? 3) - (order[b.status] ?? 3) || (b.createdAt ?? 0) - (a.createdAt ?? 0));
  return { groups: rows };
});

export const adminGetGroup = onCall(opts, async (req) => {
  requireAdmin(req);
  const gid = str(req.data?.groupId, "group");
  const g = await db().doc(`groups/${gid}`).get();
  if (!g.exists) throw new HttpsError("not-found", "No such group.");
  const ids = (g.get("memberIds") as string[] | undefined) ?? [];
  const users = ids.length ? await db().getAll(...ids.map((u) => db().doc(`users/${u}`))) : [];
  const matches: unknown[] = [];
  for (const kind of KINDS) {
    const snap = await db().collection(`groups/${gid}/${SWIPE_COLLECTIONS[kind].matches}`).get();
    snap.docs.forEach((m) => matches.push({
      kind, id: m.id, title: m.get("snapshot")?.title ?? m.id,
      interestedCount: m.get("interestedCount") ?? 0, memberCount: m.get("memberCount") ?? ids.length,
      matchedAt: millis(m.get("matchedAt")),
    }));
  }
  const plansSnap = await db().collection(`groups/${gid}/plans`).get();
  const plans = plansSnap.docs.map((p) => {
    const rsvps = (p.get("rsvps") ?? {}) as Record<string, string>;
    return {
      id: p.id,
      title: [p.get("restaurant")?.name, p.get("activity")?.name, p.get("watch")?.title].filter(Boolean).join(" + ") || "Plan",
      startAt: millis(p.get("startAt")),
      going: Object.values(rsvps).filter((v) => v === "going").length,
    };
  }).sort((a, b) => (b.startAt ?? 0) - (a.startAt ?? 0));
  return {
    id: gid,
    type: g.get("type") ?? "couple",
    name: g.get("name") ?? null,
    status: g.get("status") ?? "ended",
    code: g.get("code") ?? null,
    ownerId: g.get("ownerId") ?? null,
    members: users.map((u) => ({ uid: u.id, displayName: u.get("displayName") ?? u.id, email: u.get("email") ?? null, photoUrl: u.get("photoUrl") ?? null })),
    matches,
    plans,
  };
});

export const adminUpdateGroup = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const gid = str(req.data?.groupId, "group");
  const name = cleanGroupName(req.data?.name);
  if (!name) throw new HttpsError("invalid-argument", "Group names are 1–40 characters.");
  await db().doc(`groups/${gid}`).update({ name });
  await audit(admin, "updateGroup", { groupId: gid, name });
  return { ok: true };
});

export const adminAddMember = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const gid = str(req.data?.groupId, "group");
  const uid = str(req.data?.uid, "user");
  await db().runTransaction(async (tx) => {
    const group = await tx.get(db().doc(`groups/${gid}`));
    const user = await tx.get(db().doc(`users/${uid}`));
    if (!group.exists || group.get("status") !== "active") throw new HttpsError("failed-precondition", "Group isn't active.");
    if (!user.exists) throw new HttpsError("not-found", "No such user.");
    const members = group.get("memberIds") as string[];
    if (members.includes(uid)) return;
    const max = group.get("type") === "couple" ? 2 : MAX_GROUP_SIZE;
    if (members.length >= max) throw new HttpsError("resource-exhausted", group.get("type") === "couple" ? "A couple already has two people." : "This group is full.");
    tx.update(group.ref, { memberIds: FieldValue.arrayUnion(uid) });
    addMember(tx, gid, user);
  });
  await audit(admin, "addMember", { groupId: gid, uid });
  return { ok: true };
});

export const adminRemoveMember = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const gid = str(req.data?.groupId, "group");
  const uid = str(req.data?.uid, "user");
  await removeMember(gid, uid, false);
  await audit(admin, "removeMember", { groupId: gid, uid });
  return { ok: true };
});

export const adminResetGroupCode = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const gid = str(req.data?.groupId, "group");
  const ref = db().doc(`groups/${gid}`);
  const g = await ref.get();
  if (g.get("type") !== "friends") throw new HttpsError("failed-precondition", "Only friend groups have codes.");
  const code = await claimGroupCode(gid);
  const old = g.get("code") as string | null;
  const batch = db().batch();
  batch.update(ref, { code });
  if (old) batch.delete(db().doc(`groupCodes/${old}`));
  await batch.commit();
  await audit(admin, "resetGroupCode", { groupId: gid });
  return { code };
});

export const adminDeleteGroup = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const gid = str(req.data?.groupId, "group");
  const ref = db().doc(`groups/${gid}`);
  const g = await ref.get();
  const members = (g.get("memberIds") as string[] | undefined) ?? [];
  const code = g.get("code") as string | null;
  await Promise.all(members.map((u) => db().doc(`users/${u}`).set({ groupIds: FieldValue.arrayRemove(gid) }, { merge: true })));
  if (code) await db().doc(`groupCodes/${code}`).delete();
  await db().recursiveDelete(ref); // group + members, swipes, matches, plans, notifications
  await audit(admin, "deleteGroup", { groupId: gid, members });
  return { ok: true };
});

/** Deletes one match, plan or swipe. Only these paths are allowed. */
const DELETABLE = /^groups\/[^/]+\/(watchSwipes|restaurantSwipes|activitySwipes|watchMatches|restaurantMatches|activityMatches|plans)\/[^/]+$/;

export const adminDeleteRecord = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const path = str(req.data?.path, "path");
  if (!DELETABLE.test(path)) throw new HttpsError("invalid-argument", "That record can't be deleted here.");
  await db().doc(path).delete();
  await audit(admin, "deleteRecord", { path });
  return { ok: true };
});

// ---------------------------------------------------------------- impersonation

/**
 * Returns a one-time sign-in token for [uid]. The app signs in with it, so the admin sees and
 * uses the app exactly as that person. The session carries `impersonatedBy`, which blocks admin
 * tools and lets `stopImpersonating` hand the admin their own account back.
 */
export const adminImpersonate = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const uid = str(req.data?.uid, "user");
  if (uid === admin) throw new HttpsError("invalid-argument", "That's you.");
  const target = await getAuth().getUser(uid).catch(() => null);
  if (!target) throw new HttpsError("not-found", "No such user.");
  if (target.email && ADMIN_EMAILS.includes(target.email.toLowerCase())) throw new HttpsError("permission-denied", "Admins can't be impersonated.");
  if (target.disabled) throw new HttpsError("failed-precondition", "That account is disabled.");
  const token = await getAuth().createCustomToken(uid, { impersonatedBy: admin }).catch((e) => {
    throw new HttpsError("failed-precondition", "Impersonation isn't set up yet: the functions' service account needs the " +
      "'Service Account Token Creator' role (see README → Admin). " + (e as Error).message);
  });
  await audit(admin, "impersonate", { uid });
  return { token, displayName: target.displayName ?? target.email ?? uid };
});

export const stopImpersonating = onCall(opts, async (req) => {
  const adminUid = req.auth?.token?.impersonatedBy as string | undefined;
  if (!req.auth || !adminUid) throw new HttpsError("failed-precondition", "You're not impersonating anyone.");
  const admin = await getAuth().getUser(adminUid);
  if (!admin.email || !ADMIN_EMAILS.includes(admin.email.toLowerCase())) throw new HttpsError("permission-denied", "Admins only.");
  await audit(adminUid, "stopImpersonating", { uid: req.auth.uid });
  return { token: await getAuth().createCustomToken(adminUid) };
});

// ---------------------------------------------------------------- maintenance

/**
 * "Start fresh": deletes every couple/group (with all swipes, matches, plans and notifications)
 * and every pairing/group code, and empties everyone's group list. User accounts, profiles,
 * devices and the admin audit log are kept. Requires the exact confirmation phrase.
 */
export const WIPE_PHRASE = "DELETE EVERYTHING";

/** Deletes every group (with everything in it) and every pairing/group code. Returns the group count. */
async function wipeGroupsAndCodes(): Promise<number> {
  const groups = await db().collection("groups").listDocuments();
  for (const g of groups) await db().recursiveDelete(g);
  await db().recursiveDelete(db().collection("pairingCodes"));
  await db().recursiveDelete(db().collection("groupCodes"));
  return groups.length;
}

export const adminWipeData = onCall({ ...opts, timeoutSeconds: 540 }, async (req) => {
  const admin = requireAdmin(req);
  if (req.data?.confirm !== WIPE_PHRASE) throw new HttpsError("invalid-argument", `Type ${WIPE_PHRASE} to confirm.`);
  const groupCount = await wipeGroupsAndCodes();
  const users = await db().collection("users").get();
  for (let i = 0; i < users.docs.length; i += 400) {
    const batch = db().batch();
    users.docs.slice(i, i + 400).forEach((u) => batch.update(u.ref, { groupIds: [] }));
    await batch.commit();
  }
  await audit(admin, "wipeData", { groups: groupCount, users: users.size });
  return { groups: groupCount, users: users.size };
});

/**
 * "Delete everything": Start fresh PLUS every user account (sign-in, profile, devices), so
 * everyone has to register again. The calling admin's own account is kept (with an empty group
 * list) so they aren't locked out; the audit log and app config are kept too.
 */
export const NUKE_PHRASE = "DELETE ALL USERS";

export const adminDeleteEverything = onCall({ ...opts, timeoutSeconds: 540 }, async (req) => {
  const admin = requireAdmin(req);
  if (req.data?.confirm !== NUKE_PHRASE) throw new HttpsError("invalid-argument", `Type ${NUKE_PHRASE} to confirm.`);
  const groupCount = await wipeGroupsAndCodes();
  // Profiles and devices.
  const userDocs = await db().collection("users").listDocuments();
  for (const u of userDocs) if (u.id !== admin) await db().recursiveDelete(u);
  await db().doc(`users/${admin}`).set({ groupIds: [] }, { merge: true });
  // Sign-in accounts, 1000 at a time.
  let deleted = 0;
  let token: string | undefined;
  const toDelete: string[] = [];
  do {
    const page = await getAuth().listUsers(1000, token);
    toDelete.push(...page.users.map((u) => u.uid).filter((uid) => uid !== admin));
    token = page.pageToken;
  } while (token);
  for (let i = 0; i < toDelete.length; i += 1000) {
    deleted += (await getAuth().deleteUsers(toDelete.slice(i, i + 1000))).successCount;
  }
  await audit(admin, "deleteEverything", { groups: groupCount, users: deleted });
  return { groups: groupCount, users: deleted };
});

/**
 * App-wide settings read by every app on start (/config/app). [minVersionCode] > 0 blocks older
 * builds with [message] until they install the new APK.
 */
export const adminSetAppConfig = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const min = Number(req.data?.minVersionCode ?? 0);
  if (!Number.isInteger(min) || min < 0) throw new HttpsError("invalid-argument", "minVersionCode must be a whole number.");
  const message = typeof req.data?.message === "string" ? req.data.message.trim().slice(0, 300) : "";
  await db().doc("config/app").set({ minVersionCode: min, message, updatedAt: FieldValue.serverTimestamp(), updatedBy: admin }, { merge: true });
  await audit(admin, "setAppConfig", { minVersionCode: min });
  return { ok: true };
});
