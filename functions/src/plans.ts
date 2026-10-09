import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { onDocumentUpdated, onDocumentWrittenWithAuthContext } from "firebase-functions/v2/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { REGION, SCHEDULER_REGION, TIME_ZONE } from "./config";
import { firstName, GroupType, planNoun, SWIPE_COLLECTIONS, SwipeKind } from "./logic";
import { notifyAll, safeNotify } from "./notify";

// "done*" is set by completePlan, which sends its own notification.
const QUIET_FIELDS = new Set(["reminderDaySent", "reminderHourSent", "updatedAt", "done", "doneAt", "doneBy"]);
const RSVP_LABEL: Record<string, string> = { going: "is going", maybe: "might come", not_going: "can't make it" };

export function formatWhen(ms: number): string {
  return new Intl.DateTimeFormat("en-ZA", {
    timeZone: TIME_ZONE, weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", hour12: false,
  }).format(new Date(ms));
}

function dayKey(ms: number) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TIME_ZONE }).format(new Date(ms));
}

/**
 * What's planned, per kind. Plans hold lists (restaurants / activities / watches); older ones
 * only the single restaurant / activity / watch field.
 */
export function planPicks(d: FirebaseFirestore.DocumentData): Record<SwipeKind, { key: string; name: string }[]> {
  const list = (many: unknown, one: unknown) => (Array.isArray(many) && many.length ? many : one ? [one] : []) as Record<string, unknown>[];
  const place = (p: Record<string, unknown>) => ({ key: String(p.placeId ?? ""), name: String(p.name ?? "") });
  return {
    restaurant: list(d.restaurants, d.restaurant).map(place),
    activity: list(d.activities, d.activity).map(place),
    watch: list(d.watches, d.watch).map((w) => ({ key: String(w.itemKey ?? ""), name: String(w.title ?? "") })),
  };
}

export function describePlan(d: FirebaseFirestore.DocumentData): string {
  const p = planPicks(d);
  const parts = [...p.restaurant, ...p.activity, ...p.watch].map((x) => x.name).filter(Boolean);
  if (!parts.length) return "your plan";
  return parts.length > 3 ? `${parts.slice(0, 3).join(" + ")} + ${parts.length - 3} more` : parts.join(" + ");
}

function capital(s: string) { return s.charAt(0).toUpperCase() + s.slice(1); }

/** Tells other members about new/changed/cancelled plans, and the creator about RSVPs. */
export const onGroupPlanWritten = onDocumentWrittenWithAuthContext(
  { document: "groups/{groupId}/plans/{id}", region: REGION },
  async (event) => {
    const { groupId, id } = event.params;
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    const db = getFirestore();
    const group = await db.doc(`groups/${groupId}`).get();
    if (group.get("status") !== "active") return;
    const type = (group.get("type") as GroupType) ?? "couple";
    const noun = planNoun(type);
    const members = group.get("memberIds") as string[];
    const link = after ? `greenlight://datenight/${encodeURIComponent(id)}?g=${groupId}` : "greenlight://home";
    const nameOf = async (uid: string) => firstName((await db.doc(`groups/${groupId}/members/${uid}`).get()).get("displayName"));

    if (before && after) {
      const changed = Object.keys({ ...before, ...after }).filter((k) => JSON.stringify(before[k]) !== JSON.stringify(after[k]));
      if (changed.every((k) => QUIET_FIELDS.has(k))) return;
      if (changed.includes("startAt")) {
        await event.data!.after.ref.update({ reminderDaySent: false, reminderHourSent: false });
      }
      // RSVP-only change: tell everyone else in the group (push + in-app history). RSVPs are never
      // put on share images.
      if (changed.every((k) => k === "rsvps" || QUIET_FIELDS.has(k))) {
        const prev = (before.rsvps ?? {}) as Record<string, string>;
        const next = (after.rsvps ?? {}) as Record<string, string>;
        const who = Object.keys(next).find((u) => next[u] !== prev[u]);
        if (!who || !RSVP_LABEL[next[who]]) return;
        const going = Object.values(next).filter((v) => v === "going").length;
        await notifyAll(members.filter((m) => m !== who), {
          title: `${await nameOf(who)} ${RSVP_LABEL[next[who]]}`,
          body: `${capital(noun)}: ${describePlan(after)}, ${formatWhen((after.startAt as Timestamp).toMillis())}. ${going} going so far.`,
          link, category: "dateNightReminders", groupId, tag: `rsvp-${id}`,
        });
        return;
      }
    }

    const actor = (after?.updatedBy ?? event.authId ?? before?.updatedBy) as string | undefined;
    const others = members.filter((m) => m !== actor);
    if (!actor || others.length === 0) return;
    const actorName = await nameOf(actor);
    const data = after ?? before!;
    const when = data.startAt ? formatWhen((data.startAt as Timestamp).toMillis()) : "";
    const emoji = type === "couple" ? "📅" : "🗓️";
    let title: string;
    let body: string;
    if (!before) {
      title = `${emoji} ${capital(noun)} planned!`;
      body = `${actorName} planned ${describePlan(data)}${when ? ` for ${when}` : ""}.` + (type === "friends" ? " Are you in?" : "");
    } else if (!after) {
      title = `${capital(noun)} cancelled`;
      body = `${actorName} cancelled ${describePlan(data)}.`;
    } else {
      title = `${emoji} ${capital(noun)} updated`;
      body = `${actorName} changed ${describePlan(data)}${when ? `, now ${when}` : ""}.`;
    }
    await notifyAll(others, { title, body, link, category: "dateNightReminders", groupId });
  },
);

/** Every 30 minutes: "tomorrow/tonight" (~24h before) and "in 1 hour" reminders. */
export const planReminders = onSchedule(
  { schedule: "every 30 minutes", region: SCHEDULER_REGION, timeZone: TIME_ZONE },
  async () => {
    const db = getFirestore();
    const now = Date.now();
    const upcoming = await db.collectionGroup("plans")
      .where("startAt", ">=", Timestamp.fromMillis(now))
      .where("startAt", "<=", Timestamp.fromMillis(now + 24 * 3600_000))
      .get();

    for (const doc of upcoming.docs) {
      const d = doc.data();
      if (d.done === true) continue;
      const group = await doc.ref.parent.parent!.get();
      if (group.get("status") !== "active") continue;
      const noun = planNoun((group.get("type") as GroupType) ?? "couple");
      const start = (d.startAt as Timestamp).toMillis();
      const minutesLeft = (start - now) / 60_000;
      const link = `greenlight://datenight/${encodeURIComponent(doc.id)}?g=${group.id}`;

      let notice: { title: string; body: string } | null = null;
      const update: Record<string, boolean> = {};
      if (!d.reminderHourSent && minutesLeft <= 60) {
        notice = { title: `⏰ ${capital(noun)} in 1 hour`, body: `${describePlan(d)} at ${formatWhen(start).split(" ").pop()}.` };
        update.reminderHourSent = true;
        update.reminderDaySent = true;
      } else if (!d.reminderDaySent && minutesLeft > 120) {
        const tonight = dayKey(start) === dayKey(now);
        notice = { title: `📅 ${capital(noun)} ${tonight ? "tonight" : "tomorrow"}!`, body: `${describePlan(d)}, ${formatWhen(start)}.` };
        update.reminderDaySent = true;
      }
      if (!notice) continue;
      await doc.ref.update(update);
      const rsvps = (d.rsvps ?? {}) as Record<string, string>;
      const recipients = (group.get("memberIds") as string[]).filter((u) => rsvps[u] !== "not_going");
      await notifyAll(recipients, { ...notice, link, category: "dateNightReminders", tag: `reminder-${doc.id}` });
    }
  },
);

/** Daily at 17:30: nudge members who haven't swiped for 3+ days (at most every 3 days). */
export const groupSwipeReminders = onSchedule(
  { schedule: "30 17 * * *", region: SCHEDULER_REGION, timeZone: TIME_ZONE },
  async () => {
    const db = getFirestore();
    const cutoff = Timestamp.fromMillis(Date.now() - 3 * 24 * 3600_000);
    const idle = await db.collectionGroup("members").where("lastSwipeAt", "<", cutoff).get();
    const nudged = new Set<string>();
    for (const m of idle.docs) {
      if (nudged.has(m.id)) continue; // one nudge per person even if they're in several groups
      const last = m.get("lastSwipeReminderAt") as Timestamp | undefined;
      if (last && last.toMillis() > cutoff.toMillis()) continue;
      const group = await m.ref.parent.parent!.get();
      if (group.get("status") !== "active" || !(group.get("memberIds") as string[]).includes(m.id)) continue;
      nudged.add(m.id);
      await m.ref.update({ lastSwipeReminderAt: FieldValue.serverTimestamp() });
      await safeNotify(m.id, {
        title: "🍿 Got a minute to swipe?",
        body: "A few swipes now could turn into tonight's plan.",
        link: "greenlight://home", category: "swipeReminders",
      });
    }
  },
);

const THROTTLE_MS = 2 * 3600_000;

/** "Sam finished a round in Movies" to the rest of the group, at most every 2 hours. */
export const onGroupMemberActivity = onDocumentUpdated(
  { document: "groups/{groupId}/members/{uid}", region: REGION },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    const activity = after?.activity as string | undefined;
    if (!activity?.startsWith("finished:") || before?.activity === activity) return;
    const last = after?.lastFinishNotifiedAt as Timestamp | undefined;
    if (last && Date.now() - last.toMillis() < THROTTLE_MS) return;

    const { groupId, uid } = event.params;
    const group = await getFirestore().doc(`groups/${groupId}`).get();
    if (group.get("status") !== "active") return;
    const others = (group.get("memberIds") as string[]).filter((m) => m !== uid);
    if (!others.length) return;

    await event.data!.after.ref.update({ lastFinishNotifiedAt: FieldValue.serverTimestamp() });
    const section = activity.slice("finished:".length);
    const kind = section === "Restaurants" ? "restaurant" : section === "Activities" ? "activity" : "watch";
    await notifyAll(others, {
      title: `${firstName(after?.displayName)} finished a round in ${section}`,
      body: group.get("type") === "couple" ? "Your turn: swipe to see if you match." : "Your turn: see what the group agrees on.",
      link: `greenlight://partner/${kind}?g=${groupId}`, category: "partnerActivity", groupId,
    });
  },
);

/**
 * "Mark as done": the plan is marked done, and what was on it (every movie/series, place to eat
 * and activity) leaves the matches: the match and everyone's swipes on it are deleted, so it can
 * come back in the decks and be matched again. Any member can do it.
 */
export const completePlan = onCall({ region: REGION }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const uid = req.auth.uid;
  const groupId = typeof req.data?.groupId === "string" ? req.data.groupId : "";
  const planId = typeof req.data?.planId === "string" ? req.data.planId : "";
  if (!groupId || !planId) throw new HttpsError("invalid-argument", "Missing groupId or planId.");
  const db = getFirestore();
  const group = await db.doc(`groups/${groupId}`).get();
  const members = (group.get("memberIds") as string[] | undefined) ?? [];
  if (!group.exists || group.get("status") !== "active" || !members.includes(uid)) throw new HttpsError("permission-denied", "You're not in this group.");
  const planRef = db.doc(`groups/${groupId}/plans/${planId}`);
  const plan = await planRef.get();
  if (!plan.exists) throw new HttpsError("not-found", "That plan no longer exists.");
  if (plan.get("done") === true) return { ok: true, cleared: 0 };

  await planRef.update({ done: true, doneAt: FieldValue.serverTimestamp(), doneBy: uid });
  const picks = planPicks(plan.data()!);
  let cleared = 0;
  for (const kind of Object.keys(picks) as SwipeKind[]) {
    const cols = SWIPE_COLLECTIONS[kind];
    for (const { key } of picks[kind]) {
      if (!key) continue;
      // Swipes first, so the match trigger's recount finds nothing to recreate.
      const swipes = await db.collection(`groups/${groupId}/${cols.swipes}`).where("itemKey", "==", key).get();
      const batch = db.batch();
      swipes.docs.forEach((d) => batch.delete(d.ref));
      batch.delete(db.doc(`groups/${groupId}/${cols.matches}/${key}`));
      await batch.commit();
      cleared++;
    }
  }
  const noun = planNoun((group.get("type") as GroupType) ?? "couple");
  const name = firstName((await db.doc(`groups/${groupId}/members/${uid}`).get()).get("displayName"));
  await notifyAll(members.filter((m) => m !== uid), {
    title: `✅ ${capital(noun)} done`,
    body: `${name} marked ${describePlan(plan.data()!)} as done. Those picks are back in the decks.`,
    link: "greenlight://home", category: "dateNightReminders", groupId,
  });
  return { ok: true, cleared };
});
