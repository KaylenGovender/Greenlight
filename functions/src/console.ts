import { getAuth, UserRecord } from "firebase-admin/auth";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { REGION } from "./config";
import { audit, requireAdmin } from "./admin";
import { deleteExpiredPairings } from "./groups";
import { cleanSettings, readSettings, SettingsError, SWIPE_COLLECTIONS, SwipeKind } from "./logic";
import { notifyAll, safeNotify } from "./notify";
import { accessMode, isAccessStatus } from "./access";

/**
 * Functions used only by the Greenlight Admin web console (admin/): dashboard stats, the audit
 * log, remote app settings and broadcast notifications. Same admin check and audit trail as
 * admin.ts.
 */

const db = () => getFirestore();
const opts = { region: REGION };
const KINDS = Object.keys(SWIPE_COLLECTIONS) as SwipeKind[];
const DAY = 24 * 3600_000;

const millis = (t: unknown) => (t instanceof Timestamp ? t.toMillis() : null);
const dayKey = (ms: number) => new Date(ms).toISOString().slice(0, 10);

async function allAuthUsers(): Promise<UserRecord[]> {
  const out: UserRecord[] = [];
  let token: string | undefined;
  do {
    const page = await getAuth().listUsers(1000, token);
    out.push(...page.users);
    token = page.pageToken;
  } while (token);
  return out;
}

/** Last 30 days as YYYY-MM-DD keys, oldest first, each starting at 0. */
function emptySeries(now: number): Record<string, number> {
  const out: Record<string, number> = {};
  for (let i = 29; i >= 0; i--) out[dayKey(now - i * DAY)] = 0;
  return out;
}

// ---------------------------------------------------------------- dashboard

export const adminStats = onCall({ ...opts, timeoutSeconds: 120 }, async (req) => {
  requireAdmin(req);
  await deleteExpiredPairings().catch(() => 0);
  const now = Date.now();
  const users = await allAuthUsers();
  const lastActive = (u: UserRecord) => Date.parse(u.metadata.lastRefreshTime ?? u.metadata.lastSignInTime ?? "") || 0;
  const newUsers = emptySeries(now);
  for (const u of users) {
    const k = dayKey(Date.parse(u.metadata.creationTime));
    if (k in newUsers) newUsers[k]++;
  }

  const groups = await db().collection("groups").get();
  const active = groups.docs.filter((g) => g.get("status") === "active");
  const matchesByKind: Record<string, number> = { watch: 0, restaurant: 0, activity: 0 };
  const matchSeries: Record<string, Record<string, number>> = { watch: emptySeries(now), restaurant: emptySeries(now), activity: emptySeries(now) };
  let swipes7 = 0, plansUpcoming = 0, plansPast = 0;
  const since7 = Timestamp.fromMillis(now - 7 * DAY);

  // Per group (a personal app has few groups, so this avoids collection-group indexes).
  for (const g of groups.docs) {
    for (const kind of KINDS) {
      const m = await g.ref.collection(SWIPE_COLLECTIONS[kind].matches).get();
      matchesByKind[kind] += m.size;
      for (const d of m.docs) {
        const at = millis(d.get("matchedAt"));
        if (at) { const k = dayKey(at); if (k in matchSeries[kind]) matchSeries[kind][k]++; }
      }
      const s = await g.ref.collection(SWIPE_COLLECTIONS[kind].swipes).where("timestamp", ">=", since7).count().get();
      swipes7 += s.data().count;
    }
    const plans = await g.ref.collection("plans").get();
    for (const p of plans.docs) {
      const end = millis(p.get("endAt")) ?? ((millis(p.get("startAt")) ?? 0) + 3 * 3600_000);
      if (end >= now) plansUpcoming++; else plansPast++;
    }
  }

  return {
    generatedAt: now,
    users: {
      total: users.length,
      active7: users.filter((u) => lastActive(u) > now - 7 * DAY).length,
      active30: users.filter((u) => lastActive(u) > now - 30 * DAY).length,
      disabled: users.filter((u) => u.disabled).length,
    },
    groups: {
      couples: active.filter((g) => g.get("type") === "couple").length,
      friends: active.filter((g) => g.get("type") !== "couple").length,
      pendingOrEnded: groups.size - active.length,
    },
    matches: { ...matchesByKind, total: matchesByKind.watch + matchesByKind.restaurant + matchesByKind.activity },
    plans: { upcoming: plansUpcoming, past: plansPast },
    swipesLast7Days: swipes7,
    series: {
      days: Object.keys(newUsers),
      newUsers: Object.values(newUsers),
      matchesWatch: Object.values(matchSeries.watch),
      matchesEat: Object.values(matchSeries.restaurant),
      matchesDo: Object.values(matchSeries.activity),
    },
  };
});

// ---------------------------------------------------------------- audit log

export const adminListAudit = onCall(opts, async (req) => {
  requireAdmin(req);
  const limit = Math.min(Math.max(Number(req.data?.limit ?? 100), 1), 500);
  const action = typeof req.data?.action === "string" ? req.data.action.trim() : "";
  const snap = await db().collection("adminAudit").orderBy("at", "desc").limit(action ? 500 : limit).get();
  const adminIds = [...new Set(snap.docs.map((d) => d.get("adminUid") as string).filter(Boolean))];
  const admins = adminIds.length ? await getAuth().getUsers(adminIds.map((uid) => ({ uid }))) : { users: [] };
  const emailOf = new Map(admins.users.map((u) => [u.uid, u.email ?? u.uid]));
  const entries = snap.docs
    .map((d) => {
      const { adminUid, action: a, at, ...rest } = d.data();
      return { id: d.id, adminUid, adminEmail: emailOf.get(adminUid) ?? adminUid, action: a, at: millis(at), details: rest };
    })
    .filter((e) => !action || e.action === action)
    .slice(0, limit);
  return { entries, actions: [...new Set(snap.docs.map((d) => d.get("action") as string))].sort() };
});

// ---------------------------------------------------------------- remote app settings

/** The full settings (with defaults filled in) plus the version gate fields. */
export const adminGetAppConfig = onCall(opts, async (req) => {
  requireAdmin(req);
  const doc = await db().doc("config/app").get();
  return {
    settings: readSettings(doc.get("settings")),
    minVersionCode: Number(doc.get("minVersionCode") ?? 0),
    message: (doc.get("message") as string | undefined) ?? "",
    updatedAt: millis(doc.get("updatedAt")),
    updatedBy: (doc.get("updatedBy") as string | undefined) ?? null,
  };
});

/** Validates and saves settings; the app picks them up live (it listens to /config/app). */
export const adminSaveAppSettings = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const ref = db().doc("config/app");
  const current = readSettings((await ref.get()).get("settings"));
  let settings;
  try {
    settings = cleanSettings(req.data?.settings, current);
  } catch (e) {
    if (e instanceof SettingsError) throw new HttpsError("invalid-argument", e.message);
    throw e;
  }
  await ref.set({ settings, updatedAt: FieldValue.serverTimestamp(), updatedBy: admin }, { merge: true });
  const changed = Object.keys(settings).filter((k) => JSON.stringify((settings as unknown as Record<string, unknown>)[k]) !== JSON.stringify((current as unknown as Record<string, unknown>)[k]));
  await audit(admin, "saveAppSettings", { changed });
  return { settings, changed };
});

// ---------------------------------------------------------------- broadcast notifications

async function recipients(audience: Record<string, unknown>): Promise<string[]> {
  const type = audience?.type;
  const id = typeof audience?.id === "string" ? audience.id : "";
  if (type === "user") {
    if (!id) throw new HttpsError("invalid-argument", "Pick a user.");
    return [id];
  }
  if (type === "group") {
    if (!id) throw new HttpsError("invalid-argument", "Pick a couple or group.");
    const g = await db().doc(`groups/${id}`).get();
    if (!g.exists) throw new HttpsError("not-found", "That group doesn't exist.");
    return (g.get("memberIds") as string[] | undefined) ?? [];
  }
  if (type === "all") return (await db().collection("users").listDocuments()).map((d) => d.id);
  throw new HttpsError("invalid-argument", "Audience must be all, group or user.");
}

/**
 * Push notification from the admin to everyone, one couple/group, or one person. With
 * dryRun, only counts who would get it (people and registered phones). Respects each person's
 * "notifications off" setting.
 */
export const adminBroadcast = onCall({ ...opts, timeoutSeconds: 300 }, async (req) => {
  const admin = requireAdmin(req);
  const title = typeof req.data?.title === "string" ? req.data.title.trim().slice(0, 80) : "";
  const body = typeof req.data?.body === "string" ? req.data.body.trim().slice(0, 240) : "";
  const link = typeof req.data?.link === "string" && req.data.link.startsWith("greenlight://") ? req.data.link : "greenlight://home";
  const dryRun = req.data?.dryRun === true;
  if (!dryRun && (!title || !body)) throw new HttpsError("invalid-argument", "Write a title and a message.");
  const uids = [...new Set(await recipients(req.data?.audience ?? {}))];
  let devices = 0, optedOut = 0;
  for (const uid of uids) {
    const user = await db().doc(`users/${uid}`).get();
    if ((user.get("notifications") as { enabled?: boolean } | undefined)?.enabled === false) optedOut++;
    devices += (await db().collection(`users/${uid}/devices`).count().get()).data().count;
  }
  if (!dryRun) {
    await notifyAll(uids, { title, body, link, category: "announcements", tag: `broadcast-${Date.now()}` });
    await audit(admin, "broadcast", { audience: req.data?.audience ?? null, title, recipients: uids.length });
  }
  return { dryRun, recipients: uids.length, devices, optedOut };
});

// ---------------------------------------------------------------- beta access

/** The "Approval required" switch and everyone waiting for (or refused) access. */
export const adminGetAccess = onCall(opts, async (req) => {
  requireAdmin(req);
  const mode = await accessMode();
  const snap = await db().collection("users").where("access", "in", ["pending", "denied"]).get();
  const requests = snap.docs.map((d) => ({
    uid: d.id,
    displayName: (d.get("displayName") as string | undefined) || "",
    email: (d.get("email") as string | undefined) ?? null,
    photoUrl: (d.get("photoUrl") as string | undefined) ?? null,
    status: d.get("access") as string,
    requestedAt: millis(d.get("accessRequestedAt")) ?? millis(d.get("createdAt")),
  }));
  requests.sort((a, b) => (a.status === b.status ? 0 : a.status === "pending" ? -1 : 1) || (b.requestedAt ?? 0) - (a.requestedAt ?? 0));
  return { requireApproval: mode.requireApproval, enabledAt: mode.enabledAt || null, requests };
});

/**
 * Turns "Approval required" on or off. Turning it on keeps access for everyone who has already
 * signed up; only people who sign up afterwards wait for approval.
 */
export const adminSetAccessMode = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  if (typeof req.data?.requireApproval !== "boolean") throw new HttpsError("invalid-argument", "Missing requireApproval.");
  const on = req.data.requireApproval as boolean;
  const current = await accessMode();
  const enabledAt = on ? (current.requireApproval ? current.enabledAt : Date.now()) : current.enabledAt;
  await db().doc("config/app").set({ access: { requireApproval: on, enabledAt }, updatedAt: FieldValue.serverTimestamp(), updatedBy: admin }, { merge: true });
  await audit(admin, "setAccessMode", { requireApproval: on });
  return { requireApproval: on, enabledAt };
});

/** Approves, denies or resets one person's request. Approved people get a notification. */
export const adminSetAccess = onCall(opts, async (req) => {
  const admin = requireAdmin(req);
  const uid = typeof req.data?.uid === "string" ? req.data.uid.trim() : "";
  const status = req.data?.status;
  if (!uid) throw new HttpsError("invalid-argument", "Missing uid.");
  if (!isAccessStatus(status)) throw new HttpsError("invalid-argument", "status must be approved, pending or denied.");
  const ref = db().doc(`users/${uid}`);
  const user = await ref.get();
  if (!user.exists) throw new HttpsError("not-found", "No such user.");
  await ref.update({ access: status, accessDecidedAt: FieldValue.serverTimestamp(), accessDecidedBy: admin });
  if (status === "approved" && user.get("access") !== "approved") {
    await safeNotify(uid, { title: "🎉 You're in!", body: "Your Greenlight beta access has been approved. Open the app to get started.", link: "greenlight://home", category: "announcements" });
  }
  await audit(admin, "setAccess", { uid, status, name: user.get("displayName") ?? null });
  return { uid, status };
});
