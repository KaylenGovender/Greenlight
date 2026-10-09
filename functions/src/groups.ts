import { getFirestore, FieldValue, Timestamp, Transaction, DocumentSnapshot } from "firebase-admin/firestore";
import { HttpsError, onCall, CallableRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { PAIRING_TTL_MINUTES, REGION, SCHEDULER_REGION, TIME_ZONE } from "./config";
import {
  cleanGroupName, firstName, generateGroupCode, generatePairingCode, MAX_GROUP_SIZE,
  normalizeGroupCode, normalizePairingCode,
} from "./logic";
import { notifyAll, safeNotify } from "./notify";
import { requireAccess } from "./access";

/**
 * Groups: couples (2 people, joined with an expiring code + ACCEPT) and friend groups (up to 20,
 * joined with a permanent code). All membership changes go through here so no client can add
 * itself to a group it wasn't invited to.
 *
 *   /groups/{gid}         type, name, memberIds, ownerId, code, status
 *   /groupCodes/{CODE}    gid (friend groups, permanent)
 *   /pairingCodes/{CODE}  couple codes (15 min)
 *   /users/{uid}.groupIds server-maintained list of the user's active groups
 */

const db = () => getFirestore();
const opts = { region: REGION };
const DEFAULT_SERVICES = ["netflix", "prime", "disney"];

function requireUid(req: CallableRequest): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  return req.auth.uid;
}

function memberDoc(user: DocumentSnapshot) {
  return {
    displayName: user.get("displayName") ?? "",
    photoUrl: user.get("photoUrl") ?? null,
    streamingServices: DEFAULT_SERVICES,
    foodPrefs: [],
    activityPrefs: [],
    shareInterests: true,
    activity: null,
    joinedAt: FieldValue.serverTimestamp(),
  };
}

/** Adds [user] to the group inside [tx]. Caller has already done all reads. */
export function addMember(tx: Transaction, gid: string, user: DocumentSnapshot) {
  tx.set(db().doc(`groups/${gid}/members/${user.id}`), memberDoc(user));
  tx.set(user.ref, { groupIds: FieldValue.arrayUnion(gid) }, { merge: true });
}

async function activeCoupleOf(uid: string, tx?: Transaction): Promise<string | null> {
  const q = db().collection("groups")
    .where("memberIds", "array-contains", uid)
    .where("type", "==", "couple")
    .where("status", "==", "active")
    .limit(1);
  const snap = tx ? await tx.get(q) : await q.get();
  return snap.empty ? null : snap.docs[0].id;
}

// ---------------------------------------------------------------- couples

function requirePairingCode(raw: unknown): string {
  const code = normalizePairingCode(raw);
  if (!code) throw new HttpsError("invalid-argument", "Couple codes look like ABCD-1234.");
  return code;
}

/**
 * Deletes a couple invite: the code and its not-yet-paired couple. A pending couple has only the
 * creator in it and no data, so nothing is lost; the app only ever shows active groups.
 */
async function deletePairing(code: DocumentSnapshot) {
  const batch = db().batch();
  batch.delete(code.ref);
  const gid = code.get("groupId") as string | undefined;
  if (gid) {
    const group = await db().doc(`groups/${gid}`).get();
    if (group.exists && group.get("status") === "pending") batch.delete(group.ref);
  }
  await batch.commit();
}

async function endPendingCouplesOf(uid: string) {
  const pending = await db().collection("pairingCodes").where("creatorId", "==", uid).get();
  await Promise.all(pending.docs.map(deletePairing));
}

/** Deletes couple invites older than their 15 minutes, plus pending couples left without a code. Returns how many. */
export async function deleteExpiredPairings(): Promise<number> {
  const expired = await db().collection("pairingCodes").where("expiresAt", "<", Timestamp.now()).get();
  await Promise.all(expired.docs.map(deletePairing));
  // Pending couples whose code is already gone (older app versions only marked them ended).
  const cutoff = Timestamp.fromMillis(Date.now() - PAIRING_TTL_MINUTES * 60_000);
  const orphans = await db().collection("groups").where("status", "==", "pending").get();
  const stale = orphans.docs.filter((g) => {
    const created = g.get("createdAt") as Timestamp | undefined;
    return !created || created.toMillis() < cutoff.toMillis();
  });
  const live = stale.length ? await db().collection("pairingCodes").where("groupId", "in", stale.slice(0, 30).map((g) => g.id)).get() : null;
  const withCode = new Set(live?.docs.map((d) => d.get("groupId") as string) ?? []);
  const gone = stale.filter((g) => !withCode.has(g.id));
  await Promise.all(gone.map((g) => g.ref.delete()));
  return expired.size + gone.length;
}

/** Every 15 minutes: remove couple invites nobody accepted in time. */
export const cleanupExpiredPairings = onSchedule(
  { schedule: "every 15 minutes", region: SCHEDULER_REGION, timeZone: TIME_ZONE },
  async () => { await deleteExpiredPairings(); },
);

export const createCouple = onCall(opts, async (req) => {
  const uid = requireUid(req);
  await requireAccess(req); // beta: approved people only while approval is required
  if (await activeCoupleOf(uid)) throw new HttpsError("failed-precondition", "You're already in a couple. Leave it first.");
  await endPendingCouplesOf(uid);
  const user = await db().doc(`users/${uid}`).get();
  const groupRef = db().collection("groups").doc();
  const expiresAt = Timestamp.fromMillis(Date.now() + PAIRING_TTL_MINUTES * 60_000);

  for (let attempt = 0; attempt < 5; attempt++) {
    const code = generatePairingCode();
    try {
      await db().runTransaction(async (tx) => {
        const codeRef = db().doc(`pairingCodes/${code}`);
        if ((await tx.get(codeRef)).exists) throw new Error("collision");
        tx.create(groupRef, {
          type: "couple", name: null, memberIds: [uid], ownerId: uid, code: null,
          status: "pending", createdAt: FieldValue.serverTimestamp(),
        });
        tx.create(codeRef, {
          groupId: groupRef.id, creatorId: uid,
          creatorName: user.get("displayName") ?? "", creatorPhoto: user.get("photoUrl") ?? null,
          createdAt: FieldValue.serverTimestamp(), expiresAt,
        });
      });
      return { code, groupId: groupRef.id, expiresAt: expiresAt.toMillis() };
    } catch (e) {
      if ((e as Error).message !== "collision") throw e;
    }
  }
  throw new HttpsError("resource-exhausted", "Couldn't create a code. Try again.");
});

async function loadValidPairingCode(code: string, callerUid: string) {
  const snap = await db().doc(`pairingCodes/${code}`).get();
  if (!snap.exists) throw new HttpsError("not-found", "That code doesn't exist.");
  if ((snap.get("expiresAt") as Timestamp).toMillis() < Date.now()) {
    throw new HttpsError("deadline-exceeded", "That code has expired. Ask your partner for a new one.");
  }
  if (snap.get("creatorId") === callerUid) throw new HttpsError("invalid-argument", "That's your own code. Give it to your partner.");
  return snap;
}

export const previewCouple = onCall(opts, async (req) => {
  const uid = requireUid(req);
  await requireAccess(req); // beta: approved people only while approval is required
  const code = requirePairingCode(req.data?.code);
  if (await activeCoupleOf(uid)) throw new HttpsError("failed-precondition", "You're already in a couple. Leave it first.");
  const snap = await loadValidPairingCode(code, uid);
  return {
    code,
    creatorName: snap.get("creatorName") || "Your partner",
    creatorPhoto: snap.get("creatorPhoto") ?? null,
    expiresAt: (snap.get("expiresAt") as Timestamp).toMillis(),
  };
});

export const acceptCouple = onCall(opts, async (req) => {
  const uid = requireUid(req);
  await requireAccess(req); // beta: approved people only while approval is required
  const code = requirePairingCode(req.data?.code);

  const result = await db().runTransaction(async (tx) => {
    const codeRef = db().doc(`pairingCodes/${code}`);
    const codeSnap = await tx.get(codeRef);
    if (!codeSnap.exists) throw new HttpsError("not-found", "That code doesn't exist.");
    if ((codeSnap.get("expiresAt") as Timestamp).toMillis() < Date.now()) {
      throw new HttpsError("deadline-exceeded", "That code has expired. Ask your partner for a new one.");
    }
    const creatorId = codeSnap.get("creatorId") as string;
    const gid = codeSnap.get("groupId") as string;
    if (creatorId === uid) throw new HttpsError("invalid-argument", "That's your own code.");

    const groupRef = db().doc(`groups/${gid}`);
    const [group, me, creator] = await Promise.all([
      tx.get(groupRef), tx.get(db().doc(`users/${uid}`)), tx.get(db().doc(`users/${creatorId}`)),
    ]);
    if (!group.exists || group.get("status") !== "pending") throw new HttpsError("failed-precondition", "This pairing is no longer available.");
    if (await activeCoupleOf(uid, tx)) throw new HttpsError("failed-precondition", "You're already in a couple. Leave it first.");
    if (await activeCoupleOf(creatorId, tx)) throw new HttpsError("failed-precondition", "Your partner is already in a couple.");

    tx.update(groupRef, { memberIds: [creatorId, uid], status: "active", activatedAt: FieldValue.serverTimestamp() });
    addMember(tx, gid, creator);
    addMember(tx, gid, me);
    tx.delete(codeRef);
    return { gid, creatorId, myName: firstName(me.get("displayName")), creatorName: firstName(creator.get("displayName")) };
  });

  await Promise.all([
    safeNotify(result.creatorId, {
      title: "🤝 You're paired!", body: `${result.myName} accepted your pairing. Start swiping!`,
      link: "greenlight://home", category: "partnerActivity",
    }),
    safeNotify(uid, {
      title: "🤝 You're paired!", body: `You and ${result.creatorName} are now paired.`,
      link: "greenlight://home", category: "partnerActivity",
    }),
  ]);
  return { groupId: result.gid };
});

export const declineCouple = onCall(opts, async (req) => {
  const uid = requireUid(req);
  const code = requirePairingCode(req.data?.code);
  const snap = await loadValidPairingCode(code, uid);
  const me = await db().doc(`users/${uid}`).get();
  await deletePairing(snap);
  await safeNotify(snap.get("creatorId"), {
    title: "Pairing declined", body: `${firstName(me.get("displayName"))} declined the pairing request.`,
    link: "greenlight://home", category: "partnerActivity",
  });
  return { ok: true };
});

export const cancelCouple = onCall(opts, async (req) => {
  const uid = requireUid(req);
  const code = requirePairingCode(req.data?.code);
  const snap = await db().doc(`pairingCodes/${code}`).get();
  if (!snap.exists) return { ok: true };
  if (snap.get("creatorId") !== uid) throw new HttpsError("permission-denied", "Not your code.");
  await deletePairing(snap);
  return { ok: true };
});

// ---------------------------------------------------------------- friend groups

function requireGroupCode(raw: unknown): string {
  const code = normalizeGroupCode(raw);
  if (!code) throw new HttpsError("invalid-argument", "Group codes look like K7M-2QX.");
  return code;
}

/** Creates a unique permanent code doc inside [tx]; retries on the (rare) collision. */
export async function claimGroupCode(gid: string): Promise<string> {
  for (let attempt = 0; attempt < 8; attempt++) {
    const code = generateGroupCode();
    try {
      await db().doc(`groupCodes/${code}`).create({ groupId: gid, createdAt: FieldValue.serverTimestamp() });
      return code;
    } catch (e) {
      if ((e as { code?: number }).code !== 6) throw e; // 6 = ALREADY_EXISTS
    }
  }
  throw new HttpsError("resource-exhausted", "Couldn't create a code. Try again.");
}

export const createGroup = onCall(opts, async (req) => {
  const uid = requireUid(req);
  await requireAccess(req); // beta: approved people only while approval is required
  const name = cleanGroupName(req.data?.name);
  if (!name) throw new HttpsError("invalid-argument", "Give your group a name (up to 40 characters).");
  const groupRef = db().collection("groups").doc();
  const code = await claimGroupCode(groupRef.id);
  await db().runTransaction(async (tx) => {
    const user = await tx.get(db().doc(`users/${uid}`));
    tx.create(groupRef, {
      type: "friends", name, memberIds: [uid], ownerId: uid, code,
      status: "active", createdAt: FieldValue.serverTimestamp(),
    });
    addMember(tx, groupRef.id, user);
  });
  return { groupId: groupRef.id, code };
});

async function groupForCode(code: string) {
  const codeSnap = await db().doc(`groupCodes/${code}`).get();
  if (!codeSnap.exists) throw new HttpsError("not-found", "That group code doesn't exist.");
  const group = await db().doc(`groups/${codeSnap.get("groupId")}`).get();
  if (!group.exists || group.get("status") !== "active") throw new HttpsError("not-found", "That group no longer exists.");
  return group;
}

export const previewGroup = onCall(opts, async (req) => {
  const uid = requireUid(req);
  await requireAccess(req); // beta: approved people only while approval is required
  const code = requireGroupCode(req.data?.code);
  const group = await groupForCode(code);
  const members = group.get("memberIds") as string[];
  const owner = await db().doc(`groups/${group.id}/members/${group.get("ownerId")}`).get();
  return {
    code,
    groupId: group.id,
    name: group.get("name"),
    memberCount: members.length,
    ownerName: owner.get("displayName") ?? null,
    alreadyMember: members.includes(uid),
    full: members.length >= MAX_GROUP_SIZE,
  };
});

export const joinGroup = onCall(opts, async (req) => {
  const uid = requireUid(req);
  await requireAccess(req); // beta: approved people only while approval is required
  const code = requireGroupCode(req.data?.code);
  const found = await groupForCode(code);
  const joined = await db().runTransaction(async (tx) => {
    const group = await tx.get(found.ref);
    const user = await tx.get(db().doc(`users/${uid}`));
    const members = group.get("memberIds") as string[];
    if (group.get("status") !== "active") throw new HttpsError("not-found", "That group no longer exists.");
    if (members.includes(uid)) return null;
    if (members.length >= MAX_GROUP_SIZE) throw new HttpsError("resource-exhausted", `This group is full (${MAX_GROUP_SIZE} people).`);
    tx.update(group.ref, { memberIds: FieldValue.arrayUnion(uid) });
    addMember(tx, group.id, user);
    return { others: members, name: firstName(user.get("displayName")), groupName: group.get("name") as string };
  });
  if (joined) {
    await notifyAll(joined.others, {
      title: `👋 ${joined.name} joined ${joined.groupName}`,
      body: "Swipe to see what you all agree on.",
      link: "greenlight://home", category: "partnerActivity", groupId: found.id,
    });
  }
  return { groupId: found.id };
});

/**
 * Removes [uid] from a group. Leaving a couple (or the last person leaving) ends it for everyone.
 * Shared by "leave group" and the admin tools. [notify] = false for silent admin changes.
 */
export async function removeMember(gid: string, uid: string, notify = true) {
  const outcome = await db().runTransaction(async (tx) => {
    const ref = db().doc(`groups/${gid}`);
    const group = await tx.get(ref);
    const members = (group.get("memberIds") as string[] | undefined) ?? [];
    if (!group.exists || !members.includes(uid)) return null;
    const me = await tx.get(db().doc(`users/${uid}`));
    const others = members.filter((m) => m !== uid);
    const type = group.get("type") as string;

    if (type === "couple" || others.length === 0) {
      tx.update(ref, { status: "ended", endedAt: FieldValue.serverTimestamp(), endedBy: uid });
      for (const m of members) tx.set(db().doc(`users/${m}`), { groupIds: FieldValue.arrayRemove(gid) }, { merge: true });
      const code = group.get("code") as string | null;
      if (code) tx.delete(db().doc(`groupCodes/${code}`));
    } else {
      const update: Record<string, unknown> = { memberIds: FieldValue.arrayRemove(uid) };
      if (group.get("ownerId") === uid) update.ownerId = others[0];
      tx.update(ref, update);
      tx.delete(db().doc(`groups/${gid}/members/${uid}`));
      tx.set(me.ref, { groupIds: FieldValue.arrayRemove(gid) }, { merge: true });
    }
    return { others, type, myName: firstName(me.get("displayName")), groupName: group.get("name") as string | null };
  });
  if (notify && outcome && outcome.others.length) {
    await notifyAll(outcome.others, outcome.type === "couple"
      ? { title: "You've been unpaired", body: `${outcome.myName} ended your pairing.`, link: "greenlight://home", category: "partnerActivity" }
      : { title: `${outcome.myName} left ${outcome.groupName}`, body: "Matches now need a majority of the remaining members.", link: "greenlight://home", category: "partnerActivity", groupId: gid });
  }
}

export const leaveGroup = onCall(opts, async (req) => {
  const uid = requireUid(req);
  const gid = req.data?.groupId;
  if (typeof gid !== "string" || !gid) throw new HttpsError("invalid-argument", "Missing group.");
  await removeMember(gid, uid);
  return { ok: true };
});

export const resetGroupCode = onCall(opts, async (req) => {
  const uid = requireUid(req);
  const gid = req.data?.groupId;
  if (typeof gid !== "string" || !gid) throw new HttpsError("invalid-argument", "Missing group.");
  const ref = db().doc(`groups/${gid}`);
  const group = await ref.get();
  if (group.get("type") !== "friends" || group.get("status") !== "active") throw new HttpsError("failed-precondition", "Only friend groups have codes.");
  if (group.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Only the group owner can reset the code.");
  const code = await claimGroupCode(gid);
  const old = group.get("code") as string | null;
  const batch = db().batch();
  batch.update(ref, { code });
  if (old) batch.delete(db().doc(`groupCodes/${old}`));
  await batch.commit();
  return { code };
});
