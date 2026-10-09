import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where, Timestamp } from "firebase/firestore";

// Run with: npm run test:rules  (starts the Firestore emulator)
let env: RulesTestEnvironment;

const C = "couple1";   // alice + bob
const F = "friends1";  // alice + carol + dave
const O = "other1";    // eve + mallory

async function seed(opts: { bobShares?: boolean; daveShares?: boolean } = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const u of ["alice", "bob", "carol", "dave", "eve"]) await setDoc(doc(db, `users/${u}`), { displayName: u, onboarded: true, groupIds: [] });
    await setDoc(doc(db, `groups/${C}`), { type: "couple", memberIds: ["alice", "bob"], status: "active" });
    await setDoc(doc(db, `groups/${F}`), { type: "friends", name: "Crew", memberIds: ["alice", "carol", "dave"], status: "active", code: "K7M-2QX" });
    await setDoc(doc(db, `groups/${O}`), { type: "friends", name: "Other", memberIds: ["eve", "mallory"], status: "active" });
    await setDoc(doc(db, `groups/${C}/members/alice`), { displayName: "Alice", shareInterests: true });
    await setDoc(doc(db, `groups/${C}/members/bob`), { displayName: "Bob", shareInterests: opts.bobShares ?? true });
    await setDoc(doc(db, `groups/${F}/members/alice`), { displayName: "Alice", shareInterests: true });
    await setDoc(doc(db, `groups/${F}/members/carol`), { displayName: "Carol", shareInterests: true });
    await setDoc(doc(db, `groups/${F}/members/dave`), { displayName: "Dave", shareInterests: opts.daveShares ?? true });
    await setDoc(doc(db, `groups/${C}/watchSwipes/movie_1_bob`), { userId: "bob", itemKey: "movie_1", decision: "interested" });
    await setDoc(doc(db, `groups/${C}/watchSwipes/movie_2_bob`), { userId: "bob", itemKey: "movie_2", decision: "passed" });
    await setDoc(doc(db, `groups/${C}/watchSwipes/movie_3_alice`), { userId: "alice", itemKey: "movie_3", decision: "passed" });
    await setDoc(doc(db, `groups/${C}/watchMatches/movie_9`), { itemKey: "movie_9", matchType: "watch" });
    await setDoc(doc(db, `groups/${C}/restaurantSwipes/place1_bob`), { userId: "bob", itemKey: "place1", decision: "passed" });
    await setDoc(doc(db, `groups/${F}/activitySwipes/bowl_carol`), { userId: "carol", itemKey: "bowl", decision: "interested" });
    await setDoc(doc(db, `groups/${F}/activitySwipes/bowl_dave`), { userId: "dave", itemKey: "bowl", decision: "passed" });
    await setDoc(doc(db, `groups/${F}/activitySwipes/zoo_dave`), { userId: "dave", itemKey: "zoo", decision: "interested" });
    await setDoc(doc(db, `groupCodes/K7M-2QX`), { groupId: F });
    await setDoc(doc(db, `pairingCodes/ABCD-1234`), { groupId: "x", creatorId: "bob" });
  });
}

const as = (uid: string) => env.authenticatedContext(uid).firestore();
const interestsOf = (viewer: string, gid: string, col: string, uid: string) =>
  getDocs(query(collection(as(viewer), `groups/${gid}/${col}`), where("userId", "==", uid), where("decision", "==", "interested")));

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-greenlight",
    firestore: { rules: readFileSync(resolve(__dirname, "../../../../firestore.rules"), "utf8") },
  });
});
after(async () => env.cleanup());
beforeEach(async () => { await env.clearFirestore(); await seed(); });

describe("users", () => {
  it("keeps user docs private, even from partners and friends", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "users/alice")));
    await assertFails(getDoc(doc(as("alice"), "users/bob")));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "users/alice")));
  });

  it("does not let a client add itself to groups", async () => {
    await assertFails(updateDoc(doc(as("eve"), "users/eve"), { groupIds: [F] }));
    await assertFails(setDoc(doc(as("newbie"), "users/newbie"), { displayName: "N", groupIds: [F] }));
    await assertSucceeds(setDoc(doc(as("newbie"), "users/newbie"), { displayName: "N", groupIds: [], onboarded: false }));
    await assertFails(updateDoc(doc(as("eve"), `groups/${F}`), { memberIds: ["alice", "carol", "dave", "eve"] }));
  });

  it("keeps device tokens private", async () => {
    await assertSucceeds(setDoc(doc(as("alice"), "users/alice/devices/d1"), { fcmToken: "t" }));
    await assertFails(getDoc(doc(as("bob"), "users/alice/devices/d1")));
  });
});

describe("codes and groups", () => {
  it("lets anyone read app config but nobody write it", async () => {
    await assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "config/app")));
    await assertFails(setDoc(doc(as("alice"), "config/app"), { minVersionCode: 0 }));
  });

  it("keeps the admin audit log server-only", async () => {
    await assertFails(getDocs(collection(as("alice"), "adminAudit")));
    await assertFails(setDoc(doc(as("alice"), "adminAudit/x"), { action: "fake" }));
  });

  it("never exposes pairing or group codes directly", async () => {
    await assertFails(getDoc(doc(as("alice"), "pairingCodes/ABCD-1234")));
    await assertFails(getDoc(doc(as("eve"), "groupCodes/K7M-2QX")));
    await assertFails(getDocs(collection(as("eve"), "groupCodes")));
  });

  it("lets members read their group (including its code) but blocks everyone else", async () => {
    await assertSucceeds(getDoc(doc(as("carol"), `groups/${F}`)));
    await assertFails(getDoc(doc(as("eve"), `groups/${F}`)));
    await assertFails(getDocs(collection(as("eve"), `groups/${F}/members`)));
    await assertFails(getDocs(collection(as("eve"), `groups/${C}/watchMatches`)));
    await assertFails(getDocs(collection(as("carol"), `groups/${C}/watchMatches`))); // friend of alice, not in the couple
    await assertFails(getDocs(collection(as("eve"), `groups/${F}/plans`)));
  });

  it("lets members edit only their own preference fields", async () => {
    await assertSucceeds(updateDoc(doc(as("alice"), `groups/${F}/members/alice`), { activityPrefs: ["bowling"] }));
    await assertFails(updateDoc(doc(as("alice"), `groups/${F}/members/carol`), { activityPrefs: ["bowling"] }));
    await assertFails(updateDoc(doc(as("alice"), `groups/${F}/members/alice`), { lastSwipeAt: Timestamp.now() }));
  });

  it("locks everything once a group has ended", async () => {
    await env.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), `groups/${C}`), { status: "ended" }));
    await assertSucceeds(getDoc(doc(as("alice"), `groups/${C}`)));
    await assertFails(getDocs(collection(as("alice"), `groups/${C}/watchMatches`)));
    await assertFails(setDoc(doc(as("alice"), `groups/${C}/watchSwipes/movie_5_alice`), swipe("alice", "movie_5", "interested")));
  });
});

function swipe(uid: string, itemKey: string, decision: string) {
  return { userId: uid, itemKey, decision, contentId: 5, mediaType: "movie", timestamp: Timestamp.now(), snapshot: { title: "T" } };
}

describe("swipe privacy", () => {
  it("lets a member write only their own correctly keyed swipes", async () => {
    const db = as("alice");
    await assertSucceeds(setDoc(doc(db, `groups/${C}/watchSwipes/movie_5_alice`), swipe("alice", "movie_5", "interested")));
    await assertSucceeds(setDoc(doc(db, `groups/${F}/activitySwipes/bowl_alice`), { ...swipe("alice", "bowl", "interested"), contentId: null, mediaType: null, placeId: "bowl" }));
    await assertFails(setDoc(doc(db, `groups/${C}/watchSwipes/movie_5_bob`), swipe("bob", "movie_5", "interested")));
    await assertFails(setDoc(doc(db, `groups/${C}/watchSwipes/movie_6_alice`), swipe("alice", "movie_5", "interested")));
    await assertFails(setDoc(doc(db, `groups/${C}/watchSwipes/movie_5_alice`), swipe("alice", "movie_5", "maybe")));
    await assertFails(deleteDoc(doc(db, `groups/${C}/watchSwipes/movie_1_bob`))); // not mine
    await assertSucceeds(deleteDoc(doc(db, `groups/${C}/watchSwipes/movie_5_alice`))); // taking back my own yes
    await assertFails(setDoc(doc(as("eve"), `groups/${F}/activitySwipes/bowl_eve`), swipe("eve", "bowl", "interested")));
  });

  it("lets me read all my own swipes, including passes", async () => {
    await assertSucceeds(getDocs(query(collection(as("alice"), `groups/${C}/watchSwipes`), where("userId", "==", "alice"))));
  });

  it("NEVER reveals a partner's or friend's PASS", async () => {
    await assertFails(getDoc(doc(as("alice"), `groups/${C}/watchSwipes/movie_2_bob`)));
    await assertFails(getDoc(doc(as("alice"), `groups/${C}/restaurantSwipes/place1_bob`)));
    await assertFails(getDoc(doc(as("alice"), `groups/${F}/activitySwipes/bowl_dave`)));
    await assertFails(getDocs(query(collection(as("alice"), `groups/${C}/watchSwipes`), where("userId", "==", "bob"))));
    await assertFails(getDocs(collection(as("alice"), `groups/${F}/activitySwipes`)));
  });

  it("reveals INTERESTED items of members who share", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), `groups/${C}/watchSwipes/movie_1_bob`)));
    await assertSucceeds(interestsOf("alice", C, "watchSwipes", "bob"));
    await assertSucceeds(interestsOf("alice", F, "activitySwipes", "carol"));
    await assertSucceeds(interestsOf("alice", F, "activitySwipes", "dave"));
  });

  it("hides interests once a member turns sharing off", async () => {
    await env.clearFirestore();
    await seed({ bobShares: false, daveShares: false });
    await assertFails(getDoc(doc(as("alice"), `groups/${C}/watchSwipes/movie_1_bob`)));
    await assertFails(interestsOf("alice", C, "watchSwipes", "bob"));
    await assertFails(interestsOf("alice", F, "activitySwipes", "dave"));
    await assertSucceeds(interestsOf("alice", F, "activitySwipes", "carol"));
  });
});

describe("matches and plans", () => {
  it("shows matches to all members but only the server can create them", async () => {
    await assertSucceeds(getDocs(collection(as("bob"), `groups/${C}/watchMatches`)));
    await assertSucceeds(getDocs(collection(as("dave"), `groups/${F}/activityMatches`)));
    await assertFails(setDoc(doc(as("alice"), `groups/${C}/watchMatches/movie_7`), { itemKey: "movie_7" }));
  });

  it("shares plans and lets each member RSVP only for themselves", async () => {
    const plan = { startAt: Timestamp.now(), notes: "Meet at 18:30", createdBy: "alice", updatedBy: "alice", restaurant: null, watch: null, activity: null, rsvps: { alice: "going" } };
    await assertSucceeds(setDoc(doc(as("alice"), `groups/${F}/plans/p1`), plan));
    await assertSucceeds(getDoc(doc(as("dave"), `groups/${F}/plans/p1`)));
    await assertSucceeds(updateDoc(doc(as("carol"), `groups/${F}/plans/p1`), { "rsvps.carol": "going" }));
    await assertSucceeds(updateDoc(doc(as("dave"), `groups/${F}/plans/p1`), { "rsvps.dave": "not_going" }));
    await assertFails(updateDoc(doc(as("dave"), `groups/${F}/plans/p1`), { "rsvps.carol": "not_going" }));
    await assertFails(updateDoc(doc(as("dave"), `groups/${F}/plans/p1`), { "rsvps.dave": "definitely" }));
    await assertSucceeds(updateDoc(doc(as("carol"), `groups/${F}/plans/p1`), { notes: "Meet at 18:15", updatedBy: "carol" }));
    await assertFails(updateDoc(doc(as("carol"), `groups/${F}/plans/p1`), { notes: "x", updatedBy: "alice" }));
    await assertFails(getDoc(doc(as("eve"), `groups/${F}/plans/p1`)));
    await assertFails(setDoc(doc(as("alice"), `groups/${F}/plans/p2`), { ...plan, rsvps: { alice: "going", dave: "going" } }));
    // An end time must be after the start.
    await assertSucceeds(setDoc(doc(as("alice"), `groups/${F}/plans/p3`), { ...plan, endAt: Timestamp.fromMillis(plan.startAt.toMillis() + 3600_000) }));
    await assertFails(setDoc(doc(as("alice"), `groups/${F}/plans/p4`), { ...plan, endAt: plan.startAt }));
    // Several picks per kind; "done" is only set by the completePlan function.
    await assertSucceeds(setDoc(doc(as("alice"), `groups/${F}/plans/p5`), { ...plan, watches: [{ itemKey: "movie_1" }, { itemKey: "tv_2" }], restaurants: [], activities: [] }));
    await assertFails(setDoc(doc(as("alice"), `groups/${F}/plans/p6`), { ...plan, done: true }));
    await assertFails(updateDoc(doc(as("alice"), `groups/${F}/plans/p5`), { done: true, updatedBy: "alice" }));
    await assertSucceeds(updateDoc(doc(as("alice"), `groups/${F}/plans/p5`), { watches: [{ itemKey: "movie_1" }], updatedBy: "alice" }));
  });
});
