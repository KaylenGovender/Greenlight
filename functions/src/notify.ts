import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";

/** Maps to the user's notification settings toggles (Profile → Notification settings). */
export type NotificationCategory = "matches" | "partnerActivity" | "dateNightReminders" | "swipeReminders" | "announcements";

/** Android channel ids, matching NotificationChannels.kt. */
const CHANNEL: Record<NotificationCategory, string> = {
  matches: "matches",
  partnerActivity: "partner",
  dateNightReminders: "date_night",
  swipeReminders: "reminders",
  announcements: "announcements",
};

export interface Notice {
  title: string;
  body: string;
  link: string;
  category: NotificationCategory;
  /** Lets a later notification replace an earlier one (same tag) on the device. */
  tag?: string;
  /** When set, a copy is kept at /groups/{groupId}/notifications for in-app history. */
  groupId?: string;
}

/**
 * Sends a data-only, high-priority FCM message to every registered device of [uid], if the user
 * hasn't turned the category (or all notifications) off. Invalid tokens are cleaned up.
 */
export async function notifyUser(uid: string, notice: Notice): Promise<void> {
  const db = getFirestore();
  const user = await db.doc(`users/${uid}`).get();
  const prefs = (user.get("notifications") ?? {}) as Record<string, boolean | undefined>;
  if (prefs.enabled === false || prefs[notice.category] === false) return;

  if (notice.groupId) {
    await db.collection(`groups/${notice.groupId}/notifications`).add({
      toUid: uid,
      title: notice.title,
      body: notice.body,
      link: notice.link,
      category: notice.category,
      createdAt: FieldValue.serverTimestamp(),
    });
  }

  const devices = await db.collection(`users/${uid}/devices`).get();
  const targets = devices.docs
    .map((d) => ({ ref: d.ref, token: d.get("fcmToken") as string | undefined }))
    .filter((t): t is { ref: typeof t.ref; token: string } => !!t.token);
  if (targets.length === 0) return;

  const res = await getMessaging().sendEachForMulticast({
    tokens: targets.map((t) => t.token),
    data: {
      title: notice.title,
      body: notice.body,
      link: notice.link,
      channel: CHANNEL[notice.category],
      tag: notice.tag ?? notice.link,
    },
    android: { priority: "high" },
  });

  const stale: Promise<unknown>[] = [];
  res.responses.forEach((r, i) => {
    const code = r.error?.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      stale.push(targets[i].ref.delete());
    } else if (r.error) {
      logger.warn("FCM send failed", { uid, code, message: r.error.message });
    }
  });
  await Promise.all(stale);
}

/** Notifications are best-effort: they should never make the calling operation fail. */
export async function safeNotify(uid: string, notice: Notice) {
  try {
    await notifyUser(uid, notice);
  } catch (e) {
    logger.warn("notify failed", { uid, error: (e as Error).message });
  }
}

export async function notifyAll(uids: string[], notice: Notice) {
  await Promise.all(uids.map((u) => safeNotify(u, notice)));
}
