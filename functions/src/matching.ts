import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { REGION } from "./config";
import {
  firstName, GroupType, groupMatchMessage, isMajority, matchLink, matchMessage, otherMember,
  SWIPE_COLLECTIONS, SwipeKind,
} from "./logic";
import { safeNotify } from "./notify";

/**
 * Match detection runs server-side, so a client never reads other members' PASS decisions.
 * Rule: an item is a match once MORE THAN HALF of the current members are interested (for a
 * couple that means both). The match id is the item key, so it's created exactly once; later
 * "me too" swipes only update the count. Matches are sticky if people leave or join.
 */
async function handleSwipe(kind: SwipeKind, gid: string, before: FirebaseFirestore.DocumentData | undefined, after: FirebaseFirestore.DocumentData | undefined) {
  const wasYes = before?.decision === "interested";
  const isYes = after?.decision === "interested";
  // A "yes" was removed (trash icon / changed to pass): recount the existing match, if any.
  if (wasYes && !isYes) {
    await recountAfterRemoval(kind, gid, before!.itemKey as string);
    return;
  }
  if (!after) return;
  const uid = after.userId as string;
  const itemKey = after.itemKey as string;
  if (!uid || !itemKey) return;

  const db = getFirestore();
  await db.doc(`groups/${gid}/members/${uid}`).set({ lastSwipeAt: FieldValue.serverTimestamp() }, { merge: true });
  if (!isYes || wasYes) return;

  const cols = SWIPE_COLLECTIONS[kind];
  const result = await db.runTransaction(async (tx) => {
    const group = await tx.get(db.doc(`groups/${gid}`));
    if (group.get("status") !== "active") return null;
    const members = group.get("memberIds") as string[];
    if (!members.includes(uid)) return null;

    const interestedSnap = await tx.get(
      db.collection(`groups/${gid}/${cols.swipes}`).where("itemKey", "==", itemKey).where("decision", "==", "interested"),
    );
    const interested = [...new Set(interestedSnap.docs.map((d) => d.get("userId") as string))].filter((m) => members.includes(m));
    const matchRef = db.doc(`groups/${gid}/${cols.matches}/${itemKey}`);
    const existing = await tx.get(matchRef);

    if (existing.exists) {
      tx.update(matchRef, { interestedUids: interested, interestedCount: interested.length, memberCount: members.length });
      return null;
    }
    if (!isMajority(interested.length, members.length)) return null;

    const snapshot = Object.assign({}, ...interestedSnap.docs.map((d) => d.get("snapshot") ?? {}), after.snapshot ?? {});
    tx.create(matchRef, {
      itemKey,
      matchType: kind,
      contentId: after.contentId ?? null,
      mediaType: after.mediaType ?? null,
      placeId: after.placeId ?? null,
      snapshot,
      interestedUids: interested,
      interestedCount: interested.length,
      memberCount: members.length,
      matchedAt: FieldValue.serverTimestamp(),
    });
    return {
      members,
      interested,
      type: (group.get("type") as GroupType) ?? "couple",
      groupName: (group.get("name") as string) || "your group",
      title: (snapshot.title as string) || "something",
    };
  });
  if (!result) return;

  const link = matchLink(kind, itemKey, gid);
  if (result.type === "couple") {
    const names = await Promise.all(result.members.map(async (m) =>
      firstName((await db.doc(`groups/${gid}/members/${m}`).get()).get("displayName"))));
    await Promise.all(result.members.map((m) => {
      const partner = otherMember(result.members, m);
      const partnerName = partner ? names[result.members.indexOf(partner)] : "your partner";
      return safeNotify(m, { ...matchMessage(kind, partnerName, result.title), link, category: "matches", groupId: gid });
    }));
  } else {
    const msg = groupMatchMessage(kind, result.groupName, result.title, result.interested.length, result.members.length);
    await Promise.all(result.members.map((m) => safeNotify(m, { ...msg, link, category: "matches", groupId: gid })));
  }
}

/**
 * Someone took back their "yes". Keep the match (with an updated count) while it's still a
 * majority; otherwise remove it for everyone. No notification is sent.
 */
async function recountAfterRemoval(kind: SwipeKind, gid: string, itemKey: string) {
  if (!itemKey) return;
  const db = getFirestore();
  const cols = SWIPE_COLLECTIONS[kind];
  await db.runTransaction(async (tx) => {
    const matchRef = db.doc(`groups/${gid}/${cols.matches}/${itemKey}`);
    const match = await tx.get(matchRef);
    if (!match.exists) return;
    const group = await tx.get(db.doc(`groups/${gid}`));
    const members = (group.get("memberIds") as string[] | undefined) ?? [];
    const snap = await tx.get(
      db.collection(`groups/${gid}/${cols.swipes}`).where("itemKey", "==", itemKey).where("decision", "==", "interested"),
    );
    const interested = [...new Set(snap.docs.map((d) => d.get("userId") as string))].filter((m) => members.includes(m));
    if (isMajority(interested.length, members.length)) {
      tx.update(matchRef, { interestedUids: interested, interestedCount: interested.length, memberCount: members.length });
    } else {
      tx.delete(matchRef);
    }
  });
}

function trigger(kind: SwipeKind) {
  return onDocumentWritten(
    { document: `groups/{groupId}/${SWIPE_COLLECTIONS[kind].swipes}/{swipeId}`, region: REGION },
    (event) => handleSwipe(kind, event.params.groupId, event.data?.before?.data(), event.data?.after?.data()),
  );
}

export const onGroupWatchSwipe = trigger("watch");
export const onGroupRestaurantSwipe = trigger("restaurant");
export const onGroupActivitySwipe = trigger("activity");
