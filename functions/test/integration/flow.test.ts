/**
 * End-to-end against the Emulator Suite (npm run test:functions):
 * couples (expiring code + accept), friend groups (permanent code), majority matching, plans, leaving.
 */
import * as assert from "node:assert/strict";
import { initializeApp as initClient, FirebaseApp, deleteApp } from "firebase/app";
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth } from "firebase/auth";
import {
  connectFirestoreEmulator, deleteDoc, doc, getDoc, getFirestore, setDoc, updateDoc, serverTimestamp, Firestore, terminate,
} from "firebase/firestore";
import { connectFunctionsEmulator, getFunctions, httpsCallable, Functions } from "firebase/functions";
import { initializeApp as initAdmin, App as AdminApp, deleteApp as deleteAdmin } from "firebase-admin/app";
import { getFirestore as adminFirestore, Timestamp } from "firebase-admin/firestore";
import { REGION } from "../../src/config";

const PROJECT = "demo-greenlight";

interface Client { app: FirebaseApp; db: Firestore; fns: Functions; uid: string }

async function client(name: string): Promise<Client> {
  const app = initClient({ projectId: PROJECT, apiKey: "fake-api-key", authDomain: "localhost" }, `${name}-${Date.now()}`);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const db = getFirestore(app);
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  const fns = getFunctions(app, REGION);
  connectFunctionsEmulator(fns, "127.0.0.1", 5001);
  const cred = await createUserWithEmailAndPassword(auth, `${name}.${Date.now()}@test.local`, "password123");
  const uid = cred.user.uid;
  await setDoc(doc(db, `users/${uid}`), {
    displayName: `${name[0].toUpperCase()}${name.slice(1)} Tester`, photoUrl: null, email: cred.user.email,
    createdAt: serverTimestamp(), onboarded: true, groupIds: [],
    notifications: { enabled: true, matches: true, partnerActivity: true, dateNightReminders: true, swipeReminders: true },
  });
  return { app, db, fns, uid };
}

const call = (c: Client, name: string, data: object = {}) => httpsCallable(c.fns, name)(data).then((r) => r.data as any);
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function waitFor<T>(fn: () => Promise<T | null | undefined | false>, timeoutMs = 15000): Promise<T> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const v = await fn();
    if (v) return v;
    await sleep(300);
  }
  throw new Error("timed out");
}

async function expectCallError(p: Promise<unknown>, code: string) {
  await assert.rejects(p, (e: any) => { assert.equal(e.code, `functions/${code}`); return true; });
}

function watchSwipe(uid: string, itemKey: string, decision: "interested" | "passed", title: string) {
  const [mediaType, id] = itemKey.split("_");
  return { userId: uid, itemKey, contentId: Number(id), mediaType, decision, timestamp: serverTimestamp(), snapshot: { title } };
}
function placeSwipe(uid: string, placeId: string, decision: "interested" | "passed", title: string) {
  return { userId: uid, itemKey: placeId, placeId, decision, timestamp: serverTimestamp(), snapshot: { title } };
}
const groupIdsOf = async (c: Client) => ((await getDoc(doc(c.db, `users/${c.uid}`))).get("groupIds") ?? []) as string[];

describe("couples, groups and matching", function () {
  let alice: Client, bob: Client, carol: Client, dave: Client, eve: Client;
  let admin: AdminApp;
  let coupleId: string;
  let groupId: string;
  let groupCode: string;

  before(async () => {
    admin = initAdmin({ projectId: PROJECT }, "admin-test");
    [alice, bob, carol, dave, eve] = await Promise.all(["alice", "bob", "carol", "dave", "eve"].map(client));
  });
  after(async () => {
    for (const c of [alice, bob, carol, dave, eve]) { await terminate(c.db); await deleteApp(c.app); }
    await deleteAdmin(admin);
  });

  describe("couple", () => {
    it("rejects malformed, unknown and expired couple codes", async () => {
      await expectCallError(call(bob, "previewCouple", { code: "nope" }), "invalid-argument");
      await expectCallError(call(bob, "previewCouple", { code: "ZZZZ-0000" }), "not-found");
      await adminFirestore(admin).doc("pairingCodes/XXXX-1111").set({
        groupId: "old", creatorId: alice.uid, creatorName: "Alice", expiresAt: Timestamp.fromMillis(Date.now() - 1000),
      });
      await expectCallError(call(bob, "acceptCouple", { code: "XXXX-1111" }), "deadline-exceeded");
    });

    it("pairs only after acceptance", async () => {
      const created = await call(alice, "createCouple");
      coupleId = created.groupId;
      const preview = await call(bob, "previewCouple", { code: created.code.replace("-", "").toLowerCase() });
      assert.equal(preview.creatorName, "Alice Tester");
      assert.deepEqual(await groupIdsOf(alice), []);
      await call(bob, "acceptCouple", { code: created.code });
      assert.deepEqual(await groupIdsOf(alice), [coupleId]);
      assert.deepEqual(await groupIdsOf(bob), [coupleId]);
      await expectCallError(call(eve, "acceptCouple", { code: created.code }), "not-found");
      await expectCallError(call(alice, "createCouple"), "failed-precondition"); // one couple at a time
    });

    it("needs BOTH partners for a match", async () => {
      await setDoc(doc(alice.db, `groups/${coupleId}/watchSwipes/movie_1_${alice.uid}`), watchSwipe(alice.uid, "movie_1", "interested", "One"));
      await setDoc(doc(bob.db, `groups/${coupleId}/watchSwipes/movie_1_${bob.uid}`), watchSwipe(bob.uid, "movie_1", "passed", "One"));
      await sleep(2500);
      assert.equal((await getDoc(doc(alice.db, `groups/${coupleId}/watchMatches/movie_1`))).exists(), false);

      await setDoc(doc(bob.db, `groups/${coupleId}/watchSwipes/tv_2_${bob.uid}`), watchSwipe(bob.uid, "tv_2", "interested", "Wednesday"));
      await setDoc(doc(alice.db, `groups/${coupleId}/watchSwipes/tv_2_${alice.uid}`), watchSwipe(alice.uid, "tv_2", "interested", "Wednesday"));
      const m = await waitFor(async () => { const s = await getDoc(doc(alice.db, `groups/${coupleId}/watchMatches/tv_2`)); return s.exists() && s; });
      assert.equal(m.get("interestedCount"), 2);
      assert.equal(m.get("snapshot").title, "Wednesday");
    });
  });

  describe("friend group", () => {
    it("creates a group with a permanent code and lets friends join by code", async () => {
      await expectCallError(call(carol, "createGroup", { name: "   " }), "invalid-argument");
      const created = await call(carol, "createGroup", { name: "Braai Crew" });
      groupId = created.groupId;
      groupCode = created.code;
      assert.match(groupCode, /^[A-Z2-9]{3}-[A-Z2-9]{3}$/);

      const preview = await call(dave, "previewGroup", { code: groupCode.toLowerCase().replace("-", "") });
      assert.equal(preview.name, "Braai Crew");
      assert.equal(preview.memberCount, 1);
      await call(dave, "joinGroup", { code: groupCode });
      // Alice is in a couple AND can be in a friend group.
      await call(alice, "joinGroup", { code: groupCode });
      await call(alice, "joinGroup", { code: groupCode }); // idempotent
      const group = await getDoc(doc(carol.db, `groups/${groupId}`));
      assert.deepEqual(group.get("memberIds"), [carol.uid, dave.uid, alice.uid]);
      assert.deepEqual((await groupIdsOf(alice)).sort(), [coupleId, groupId].sort());
      await expectCallError(call(eve, "previewGroup", { code: "ZZZ-ZZZ" }), "not-found");
    });

    it("the code never expires: it still works much later", async () => {
      // Codes carry no expiry; simulate age by back-dating the code doc.
      await adminFirestore(admin).doc(`groupCodes/${groupCode}`).update({ createdAt: Timestamp.fromMillis(Date.now() - 365 * 24 * 3600_000) });
      const preview = await call(eve, "previewGroup", { code: groupCode });
      assert.equal(preview.memberCount, 3);
    });

    it("matches on a MAJORITY (2 of 3) and keeps counting after", async () => {
      const col = `groups/${groupId}/activitySwipes`;
      await setDoc(doc(carol.db, `${col}/bowl_${carol.uid}`), placeSwipe(carol.uid, "bowl", "interested", "Bowling"));
      await sleep(2000);
      assert.equal((await getDoc(doc(carol.db, `groups/${groupId}/activityMatches/bowl`))).exists(), false);

      await setDoc(doc(dave.db, `${col}/bowl_${dave.uid}`), placeSwipe(dave.uid, "bowl", "interested", "Bowling"));
      const m = await waitFor(async () => { const s = await getDoc(doc(alice.db, `groups/${groupId}/activityMatches/bowl`)); return s.exists() && s; });
      assert.equal(m.get("interestedCount"), 2);
      assert.equal(m.get("memberCount"), 3);

      await setDoc(doc(alice.db, `${col}/bowl_${alice.uid}`), placeSwipe(alice.uid, "bowl", "interested", "Bowling"));
      await waitFor(async () => (await getDoc(doc(alice.db, `groups/${groupId}/activityMatches/bowl`))).get("interestedCount") === 3);
    });

    it("taking back a yes updates the count, and removes the match below a majority", async () => {
      const col = `groups/${groupId}/activitySwipes`;
      const matchDoc = () => getDoc(doc(carol.db, `groups/${groupId}/activityMatches/bowl`));
      // 3 of 3 → Alice removes her yes → 2 of 3, still a match.
      await deleteDoc(doc(alice.db, `${col}/bowl_${alice.uid}`));
      await waitFor(async () => (await matchDoc()).get("interestedCount") === 2);
      assert.deepEqual((await matchDoc()).get("interestedUids").sort(), [carol.uid, dave.uid].sort());
      // Dave changes his mind to pass → 1 of 3 → match removed for everyone.
      await setDoc(doc(dave.db, `${col}/bowl_${dave.uid}`), placeSwipe(dave.uid, "bowl", "passed", "Bowling"));
      await waitFor(async () => !(await matchDoc()).exists());
      // Saying yes again brings it back.
      await setDoc(doc(alice.db, `${col}/bowl_${alice.uid}`), placeSwipe(alice.uid, "bowl", "interested", "Bowling"));
      await waitFor(async () => (await matchDoc()).exists());
    });

    it("does not match with only 1 of 3", async () => {
      await setDoc(doc(dave.db, `groups/${groupId}/restaurantSwipes/sushi_${dave.uid}`), placeSwipe(dave.uid, "sushi", "interested", "Sushi Place"));
      await setDoc(doc(carol.db, `groups/${groupId}/restaurantSwipes/sushi_${carol.uid}`), placeSwipe(carol.uid, "sushi", "passed", "Sushi Place"));
      await sleep(2500);
      assert.equal((await getDoc(doc(dave.db, `groups/${groupId}/restaurantMatches/sushi`))).exists(), false);
    });

    it("shares a meet up and collects RSVPs", async () => {
      const ref = doc(carol.db, `groups/${groupId}/plans/p1`);
      const start = Date.now() + 26 * 3600_000;
      await setDoc(ref, {
        startAt: new Date(start), notes: "Meet at the lanes", location: null, watch: null, restaurant: null,
        activity: { placeId: "bowl", name: "Bowling", address: "1 Main Rd", lat: -29.7, lng: 31.0 },
        createdBy: carol.uid, updatedBy: carol.uid, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
        reminderDaySent: false, reminderHourSent: false, rsvps: { [carol.uid]: "going" },
      });
      await updateDoc(doc(dave.db, `groups/${groupId}/plans/p1`), { [`rsvps.${dave.uid}`]: "going" });
      await updateDoc(doc(alice.db, `groups/${groupId}/plans/p1`), { [`rsvps.${alice.uid}`]: "maybe" });
      const plan = await getDoc(doc(carol.db, `groups/${groupId}/plans/p1`));
      assert.deepEqual(plan.get("rsvps"), { [carol.uid]: "going", [dave.uid]: "going", [alice.uid]: "maybe" });

      // Changing the time re-arms reminders.
      await adminFirestore(admin).doc(`groups/${groupId}/plans/p1`).update({ reminderDaySent: true });
      await updateDoc(doc(dave.db, `groups/${groupId}/plans/p1`), { startAt: new Date(start + 3600_000), updatedBy: dave.uid, updatedAt: serverTimestamp() });
      await waitFor(async () => (await getDoc(doc(carol.db, `groups/${groupId}/plans/p1`))).get("reminderDaySent") === false);
    });

    it("marking a plan done clears its picks from the matches so they can be matched again", async () => {
      const col = `groups/${groupId}/activitySwipes`;
      await setDoc(doc(carol.db, `${col}/golf_${carol.uid}`), placeSwipe(carol.uid, "golf", "interested", "Mini golf"));
      await setDoc(doc(dave.db, `${col}/golf_${dave.uid}`), placeSwipe(dave.uid, "golf", "interested", "Mini golf"));
      const matchRef = adminFirestore(admin).doc(`groups/${groupId}/activityMatches/golf`);
      await waitFor(async () => (await matchRef.get()).exists);

      const golf = { placeId: "golf", name: "Mini golf", address: null, lat: null, lng: null };
      await setDoc(doc(carol.db, `groups/${groupId}/plans/p2`), {
        startAt: new Date(Date.now() - 3600_000), notes: "", location: null,
        activities: [golf, { placeId: "bowl", name: "Bowling", address: null, lat: null, lng: null }], activity: golf,
        createdBy: carol.uid, updatedBy: carol.uid, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
        reminderDaySent: false, reminderHourSent: false, rsvps: { [carol.uid]: "going" },
      });
      // Members can't mark it done themselves; only the function does.
      await assert.rejects(updateDoc(doc(carol.db, `groups/${groupId}/plans/p2`), { done: true, updatedBy: carol.uid }));
      await expectCallError(call(eve, "completePlan", { groupId, planId: "p2" }), "permission-denied");

      const r = await call(dave, "completePlan", { groupId, planId: "p2" });
      assert.equal(r.cleared, 2);
      const plan = await adminFirestore(admin).doc(`groups/${groupId}/plans/p2`).get();
      assert.equal(plan.get("done"), true);
      assert.equal(plan.get("doneBy"), dave.uid);
      assert.equal((await matchRef.get()).exists, false);
      assert.equal((await adminFirestore(admin).collection(col).where("itemKey", "in", ["golf", "bowl"]).get()).size, 0);
      assert.equal((await call(dave, "completePlan", { groupId, planId: "p2" })).cleared, 0); // only once
    });

    it("leaving removes access; the owner role moves on", async () => {
      await call(carol, "leaveGroup", { groupId });
      const group = await getDoc(doc(dave.db, `groups/${groupId}`));
      assert.deepEqual(group.get("memberIds"), [dave.uid, alice.uid]);
      assert.equal(group.get("ownerId"), dave.uid);
      assert.deepEqual(await groupIdsOf(carol), []);
      await assert.rejects(getDoc(doc(carol.db, `groups/${groupId}/activityMatches/bowl`)));

      const reset = await call(dave, "resetGroupCode", { groupId });
      assert.notEqual(reset.code, groupCode);
      await expectCallError(call(eve, "previewGroup", { code: groupCode }), "not-found");
      await expectCallError(call(alice, "resetGroupCode", { groupId }), "permission-denied");
    });

    it("leaving a couple ends it for both", async () => {
      await call(bob, "leaveGroup", { groupId: coupleId });
      assert.deepEqual(await groupIdsOf(bob), []);
      assert.deepEqual(await groupIdsOf(alice), [groupId]);
      await assert.rejects(getDoc(doc(alice.db, `groups/${coupleId}/watchMatches/tv_2`)));
    });
  });
});
