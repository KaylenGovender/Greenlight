import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall, CallableRequest } from "firebase-functions/v2/https";
import { ADMIN_EMAILS, REGION } from "./config";
import { safeNotify } from "./notify";

/**
 * Beta access. When the admin turns on "Approval required" (/config/app → access), people who
 * sign up afterwards can't create or join a couple or group until an admin approves them. People
 * who signed up before it was turned on keep their access. With it off, everyone gets in.
 *
 * The status lives on /users/{uid}.access ("approved" | "pending" | "denied"); only these
 * functions write it (the Firestore rules don't let users touch it).
 */

const db = () => getFirestore();
const opts = { region: REGION };

export type AccessStatus = "approved" | "pending" | "denied";
export interface AccessMode { requireApproval: boolean; enabledAt: number }

const STATUSES: AccessStatus[] = ["approved", "pending", "denied"];
export const isAccessStatus = (v: unknown): v is AccessStatus => STATUSES.includes(v as AccessStatus);

export async function accessMode(): Promise<AccessMode> {
  const a = (await db().doc("config/app").get()).get("access") as Partial<AccessMode> | undefined;
  return { requireApproval: a?.requireApproval === true, enabledAt: Number(a?.enabledAt ?? 0) };
}

function isAdminEmail(email: unknown, verified: unknown): boolean {
  return typeof email === "string" && verified === true && ADMIN_EMAILS.includes(email.toLowerCase());
}

/**
 * What a user's access is right now. A stored status wins; without one, people who signed up
 * before approval was turned on (or while it's off) count as approved.
 */
export async function resolveAccess(uid: string, mode: AccessMode): Promise<{ status: AccessStatus; stored: AccessStatus | null }> {
  const stored = (await db().doc(`users/${uid}`).get()).get("access");
  const known = isAccessStatus(stored) ? stored : null;
  if (known) return { status: known, stored: known };
  if (!mode.requireApproval) return { status: "approved", stored: null };
  const created = Date.parse((await getAuth().getUser(uid)).metadata.creationTime ?? "") || 0;
  return { status: created && created < mode.enabledAt ? "approved" : "pending", stored: null };
}

/** Throws unless the caller may use the app (always true while approval is off, and for admins). */
export async function requireAccess(req: CallableRequest): Promise<void> {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  if (isAdminEmail(req.auth.token.email, req.auth.token.email_verified)) return;
  const mode = await accessMode();
  if (!mode.requireApproval) return;
  const { status } = await resolveAccess(req.auth.uid, mode);
  if (status === "approved") return;
  throw new HttpsError("permission-denied", status === "denied"
    ? "Your request to join the Greenlight beta wasn't approved."
    : "Your request to join the Greenlight beta is waiting for approval.");
}

/** Push to every admin account that has the app installed. */
async function notifyAdmins(title: string, body: string) {
  const admins = await Promise.all(ADMIN_EMAILS.map((e) => getAuth().getUserByEmail(e).catch(() => null)));
  await Promise.all(admins.filter((a) => a).map((a) => safeNotify(a!.uid, { title, body, link: "greenlight://home", category: "announcements" })));
}

/**
 * Called by the app after sign-in. Records the user's status (so a later switch-on never locks
 * out people who already had access), files a request the first time someone new needs
 * approval, and tells the app what to show.
 */
export const checkAccess = onCall(opts, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const uid = req.auth.uid;
  const mode = await accessMode();
  if (isAdminEmail(req.auth.token.email, req.auth.token.email_verified)) {
    return { status: "approved", requireApproval: mode.requireApproval };
  }
  const { status, stored } = await resolveAccess(uid, mode);
  if (!stored) {
    await db().doc(`users/${uid}`).set({
      access: status,
      ...(status === "pending" ? { accessRequestedAt: FieldValue.serverTimestamp() } : {}),
    }, { merge: true });
    if (status === "pending") {
      const name = (req.auth.token.name as string | undefined) || (req.auth.token.email as string | undefined) || "Someone";
      await notifyAdmins("🙋 New beta request", `${name} is waiting for access. Approve them in Greenlight Admin.`);
    }
  }
  // While approval is off, everyone gets in regardless of what's stored.
  return { status: mode.requireApproval ? status : "approved", requireApproval: mode.requireApproval };
});

export const millisOf = (v: unknown): number | null => (v instanceof Timestamp ? v.toMillis() : null);
