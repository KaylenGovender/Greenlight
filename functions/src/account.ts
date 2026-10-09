import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall, CallableRequest } from "firebase-functions/v2/https";
import { REGION } from "./config";
import { deleteUserData } from "./admin";
import { SWIPE_COLLECTIONS, SwipeKind } from "./logic";

/**
 * Self-service account tools for privacy law (POPIA): delete my account, and download a copy of
 * my data. Both refuse while an admin is impersonating, so only the real owner can use them.
 */

const db = () => getFirestore();
const opts = { region: REGION };
const KINDS = Object.keys(SWIPE_COLLECTIONS) as SwipeKind[];
export const DELETE_ACCOUNT_PHRASE = "DELETE";

function requireOwner(req: CallableRequest): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  if ((req.auth.token as { impersonatedBy?: string }).impersonatedBy) {
    throw new HttpsError("permission-denied", "Not available while impersonating.");
  }
  return req.auth.uid;
}

/** Deletes everything about the caller: swipes, memberships (couples end), profile, devices, sign-in. */
export const deleteMyAccount = onCall(opts, async (req) => {
  const uid = requireOwner(req);
  if (req.data?.confirm !== DELETE_ACCOUNT_PHRASE) throw new HttpsError("invalid-argument", `Type ${DELETE_ACCOUNT_PHRASE} to confirm.`);
  await deleteUserData(uid);
  return { ok: true };
});

const plain = (v: unknown): unknown => {
  if (v instanceof Timestamp) return v.toDate().toISOString();
  if (Array.isArray(v)) return v.map(plain);
  if (v && typeof v === "object") return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, plain(x)]));
  return v;
};

/** A JSON copy of what Greenlight stores about the caller. Device push tokens are counted, not shown. */
export const exportMyData = onCall(opts, async (req) => {
  const uid = requireOwner(req);
  const user = await db().doc(`users/${uid}`).get();
  const devices = await db().collection(`users/${uid}/devices`).count().get();
  const groupIds = (user.get("groupIds") as string[] | undefined) ?? [];
  const groups = [];
  for (const gid of groupIds) {
    const g = await db().doc(`groups/${gid}`).get();
    if (!g.exists) continue;
    const swipes: Record<string, unknown[]> = {};
    for (const kind of KINDS) {
      const snap = await db().collection(`groups/${gid}/${SWIPE_COLLECTIONS[kind].swipes}`).where("userId", "==", uid).get();
      swipes[kind] = snap.docs.map((d) => plain({ itemKey: d.get("itemKey"), decision: d.get("decision"), title: d.get("snapshot.title") ?? null, at: d.get("timestamp") ?? null }));
    }
    const member = await db().doc(`groups/${gid}/members/${uid}`).get();
    const plans = await db().collection(`groups/${gid}/plans`).where("createdBy", "==", uid).get();
    groups.push({
      id: gid,
      type: g.get("type"),
      name: g.get("name") ?? null,
      memberCount: ((g.get("memberIds") as string[] | undefined) ?? []).length,
      myMemberSettings: plain(member.data() ?? {}),
      mySwipes: swipes,
      plansICreated: plans.docs.map((p) => plain({ id: p.id, ...p.data() })),
    });
  }
  return {
    exportedAt: new Date().toISOString(),
    uid,
    profile: plain(user.data() ?? {}),
    registeredDevices: devices.data().count,
    groups,
  };
});
