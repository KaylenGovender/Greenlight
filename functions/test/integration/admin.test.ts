/** Admin tools + impersonation against the Emulator Suite (npm run test:functions). */
import * as assert from "node:assert/strict";
import { initializeApp as initClient, FirebaseApp, deleteApp } from "firebase/app";
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth, signInWithCustomToken, Auth } from "firebase/auth";
import { connectFirestoreEmulator, doc, getDoc, getFirestore, setDoc, serverTimestamp, Firestore, terminate } from "firebase/firestore";
import { connectFunctionsEmulator, getFunctions, httpsCallable, Functions } from "firebase/functions";
import { initializeApp as initAdmin, App as AdminApp, deleteApp as deleteAdmin } from "firebase-admin/app";
import { getAuth as adminAuth } from "firebase-admin/auth";
import { getFirestore as adminFirestore } from "firebase-admin/firestore";
import { REGION } from "../../src/config";

const PROJECT = "demo-greenlight";
interface Client { app: FirebaseApp; auth: Auth; db: Firestore; fns: Functions; uid: string }

async function client(name: string, email: string): Promise<Client> {
  const app = initClient({ projectId: PROJECT, apiKey: "fake-api-key", authDomain: "localhost" }, `${name}-${Date.now()}`);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const db = getFirestore(app);
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  const fns = getFunctions(app, REGION);
  connectFunctionsEmulator(fns, "127.0.0.1", 5001);
  const cred = await createUserWithEmailAndPassword(auth, email, "password123");
  await setDoc(doc(db, `users/${cred.user.uid}`), {
    displayName: name, photoUrl: null, email, createdAt: serverTimestamp(), onboarded: true, groupIds: [],
    notifications: { enabled: true, matches: true, partnerActivity: true, dateNightReminders: true, swipeReminders: true },
  });
  return { app, auth, db, fns, uid: cred.user.uid };
}

const call = (c: Client, name: string, data: object = {}) => httpsCallable(c.fns, name)(data).then((r) => r.data as any);
async function expectCallError(p: Promise<unknown>, code: string) {
  await assert.rejects(p, (e: any) => { assert.equal(e.code, `functions/${code}`); return true; });
}

describe("admin tools", function () {
  let admin: AdminApp;
  let boss: Client, sam: Client, lee: Client;
  let groupId: string;

  before(async () => {
    admin = initAdmin({ projectId: PROJECT }, "admin-tools-test");
    const stamp = Date.now();
    boss = await client("Kaylen", "kaylengovender@gmail.com");
    sam = await client("Sam", `sam.${stamp}@test.local`);
    lee = await client("Lee", `lee.${stamp}@test.local`);
    // Google accounts have verified emails; emulate that for the admin, then refresh the token.
    await adminAuth(admin).updateUser(boss.uid, { emailVerified: true });
    await boss.auth.currentUser!.getIdToken(true);
    groupId = (await call(sam, "createGroup", { name: "Crew" })).groupId;
    await call(lee, "joinGroup", { code: (await getDoc(doc(sam.db, `groups/${groupId}`))).get("code") });
  });
  after(async () => {
    for (const c of [boss, sam, lee]) { await terminate(c.db); await deleteApp(c.app); }
    await deleteAdmin(admin);
  });

  it("refuses non-admins", async () => {
    await expectCallError(call(sam, "adminListUsers"), "permission-denied");
    await expectCallError(call(sam, "adminImpersonate", { uid: lee.uid }), "permission-denied");
    assert.equal((await call(sam, "adminWhoAmI")).admin, false);
    assert.equal((await call(boss, "adminWhoAmI")).admin, true);
  });

  it("lists, searches and reads users with their groups", async () => {
    const all = (await call(boss, "adminListUsers")).users;
    assert.ok(all.length >= 3);
    const found = (await call(boss, "adminListUsers", { query: "sam" })).users;
    assert.ok(found.some((u: any) => u.uid === sam.uid));
    const detail = await call(boss, "adminGetUser", { uid: sam.uid });
    assert.equal(detail.groups[0].name, "Crew");
    assert.equal(detail.groups[0].memberCount, 2);
  });

  it("edits a user and keeps their member name in sync", async () => {
    await call(boss, "adminUpdateUser", { uid: sam.uid, displayName: "Samantha" });
    assert.equal((await getDoc(doc(lee.db, `groups/${groupId}/members/${sam.uid}`))).get("displayName"), "Samantha");
    assert.equal((await adminAuth(admin).getUser(sam.uid)).displayName, "Samantha");
  });

  it("manages group members and records, and audits everything", async () => {
    const created = await call(boss, "adminCreateUser", { email: `new.${Date.now()}@test.local`, password: "secret12", displayName: "Newbie" });
    await call(boss, "adminAddMember", { groupId, uid: created.uid });
    assert.equal((await call(boss, "adminGetGroup", { groupId })).members.length, 3);
    await call(boss, "adminRemoveMember", { groupId, uid: created.uid });
    assert.equal((await call(boss, "adminGetGroup", { groupId })).members.length, 2);
    await expectCallError(call(boss, "adminDeleteRecord", { path: `users/${sam.uid}` }), "invalid-argument");
    await call(boss, "adminDeleteUser", { uid: created.uid });
    await assert.rejects(adminAuth(admin).getUser(created.uid));
    const audit = await adminFirestore(admin).collection("adminAudit").where("adminUid", "==", boss.uid).get();
    assert.ok(audit.size >= 5);
  });

  it("impersonates a user and switches back", async () => {
    const { token } = await call(boss, "adminImpersonate", { uid: sam.uid });
    await signInWithCustomToken(boss.auth, token);
    assert.equal(boss.auth.currentUser!.uid, sam.uid);
    const claims = (await boss.auth.currentUser!.getIdTokenResult()).claims;
    assert.equal(claims.impersonatedBy, boss.uid);
    // While impersonating, the session is the user's: their data is readable, admin tools are not.
    assert.equal((await getDoc(doc(boss.db, `groups/${groupId}`))).get("name"), "Crew");
    await expectCallError(call(boss, "adminListUsers"), "permission-denied");

    const back = await call(boss, "stopImpersonating");
    await signInWithCustomToken(boss.auth, back.token);
    assert.equal(boss.auth.currentUser!.uid, boss.uid);
    await expectCallError(call(sam, "stopImpersonating"), "failed-precondition");
  });

  it("never impersonates the admin or yourself", async () => {
    await boss.auth.currentUser!.getIdToken(true);
    await expectCallError(call(boss, "adminImpersonate", { uid: boss.uid }), "invalid-argument");
  });

  it("publishes the minimum app version for every app to read", async () => {
    await expectCallError(call(sam, "adminSetAppConfig", { minVersionCode: 5 }), "permission-denied");
    await call(boss, "adminSetAppConfig", { minVersionCode: 42, message: "Get the new version from Kaylen" });
    const cfg = await getDoc(doc(sam.db, "config/app"));
    assert.equal(cfg.get("minVersionCode"), 42);
    await call(boss, "adminSetAppConfig", { minVersionCode: 0 });
  });

  it("serves dashboard stats and the audit log to admins only", async () => {
    await expectCallError(call(sam, "adminStats"), "permission-denied");
    const stats = await call(boss, "adminStats");
    assert.ok(stats.users.total >= 3);
    assert.equal(stats.series.days.length, 30);
    assert.equal(stats.series.newUsers.length, 30);
    const log = await call(boss, "adminListAudit", { limit: 50 });
    assert.ok(log.entries.length > 0);
    assert.ok(log.actions.includes("setAppConfig"));
    const only = await call(boss, "adminListAudit", { action: "setAppConfig" });
    assert.ok(only.entries.every((e: any) => e.action === "setAppConfig"));
  });

  it("validates and saves remote app settings", async () => {
    await expectCallError(call(boss, "adminSaveAppSettings", { settings: { defaults: { radiusKm: 7 } } }), "invalid-argument");
    const saved = await call(boss, "adminSaveAppSettings", { settings: { announcement: { enabled: true, text: "New version out", level: "success" } } });
    assert.deepEqual(saved.changed, ["announcement"]);
    const cfg = await getDoc(doc(sam.db, "config/app"));
    assert.equal(cfg.get("settings").announcement.text, "New version out");
    const got = await call(boss, "adminGetAppConfig");
    assert.equal(got.settings.defaults.radiusKm, 5);
    await call(boss, "adminSaveAppSettings", { settings: { announcement: { enabled: false } } });
  });

  it("counts broadcast recipients on a dry run", async () => {
    const all = await call(boss, "adminBroadcast", { dryRun: true, audience: { type: "all" } });
    assert.ok(all.recipients >= 3);
    const one = await call(boss, "adminBroadcast", { dryRun: true, audience: { type: "group", id: groupId } });
    assert.equal(one.recipients, 2);
    await expectCallError(call(boss, "adminBroadcast", { audience: { type: "all" } }), "invalid-argument");
  });

  it("holds new sign-ups for approval while it's required, and lets earlier ones in", async () => {
    await expectCallError(call(sam, "adminSetAccessMode", { requireApproval: true }), "permission-denied");
    await call(boss, "adminSetAccessMode", { requireApproval: true });
    await new Promise((r) => setTimeout(r, 1100)); // sign-up times are to the second
    const newbie = await client("Newbie", `newbie.${Date.now()}@test.local`);
    try {
      assert.equal((await call(sam, "checkAccess")).status, "approved"); // signed up before it was on
      assert.equal((await call(newbie, "checkAccess")).status, "pending");
      await expectCallError(call(newbie, "createGroup", { name: "Sneaky" }), "permission-denied");
      await expectCallError(call(newbie, "createCouple"), "permission-denied");
      const waiting = (await call(boss, "adminGetAccess")).requests;
      assert.ok(waiting.some((r: any) => r.uid === newbie.uid && r.status === "pending"));

      await call(boss, "adminSetAccess", { uid: newbie.uid, status: "approved" });
      assert.equal((await getDoc(doc(newbie.db, `users/${newbie.uid}`))).get("access"), "approved");
      assert.ok((await call(newbie, "createGroup", { name: "Welcome" })).groupId);

      await call(boss, "adminSetAccess", { uid: newbie.uid, status: "denied" });
      await expectCallError(call(newbie, "createCouple"), "permission-denied");
      // Turning it off lets everyone in again, whatever is stored.
      await call(boss, "adminSetAccessMode", { requireApproval: false });
      assert.equal((await call(newbie, "checkAccess")).status, "approved");
      await call(newbie, "cancelCouple", { code: (await call(newbie, "createCouple")).code });
    } finally {
      await call(boss, "adminSetAccessMode", { requireApproval: false });
      await terminate(newbie.db); await deleteApp(newbie.app);
    }
  });

  it("deletes couple invites that ran out, instead of listing them as pending", async () => {
    const { code, groupId: pendingId } = await call(lee, "createCouple");
    await adminFirestore(admin).doc(`pairingCodes/${code}`).update({ expiresAt: new Date(Date.now() - 1000) });
    const groups = (await call(boss, "adminListGroups")).groups;
    assert.ok(!groups.some((g: any) => g.id === pendingId));
    assert.equal((await adminFirestore(admin).doc(`groups/${pendingId}`).get()).exists, false);
    assert.equal((await adminFirestore(admin).doc(`pairingCodes/${code}`).get()).exists, false);
    // Cancelling deletes the invite's couple too, rather than leaving an "ended" one behind.
    const again = await call(lee, "createCouple");
    await call(lee, "cancelCouple", { code: again.code });
    assert.equal((await adminFirestore(admin).doc(`groups/${again.groupId}`).get()).exists, false);
  });

  it("deletes a group with all its data", async () => {
    await call(boss, "adminDeleteGroup", { groupId });
    assert.equal((await adminFirestore(admin).doc(`groups/${groupId}`).get()).exists, false);
    assert.deepEqual((await getDoc(doc(lee.db, `users/${lee.uid}`))).get("groupIds"), []);
  });

  it("starts fresh: wipes every group but keeps user accounts", async () => {
    const g = await call(sam, "createGroup", { name: "Again" });
    await expectCallError(call(boss, "adminWipeData", { confirm: "yes" }), "invalid-argument");
    await expectCallError(call(sam, "adminWipeData", { confirm: "DELETE EVERYTHING" }), "permission-denied");
    await call(boss, "adminWipeData", { confirm: "DELETE EVERYTHING" });
    assert.equal((await adminFirestore(admin).collection("groups").get()).size, 0);
    assert.equal((await adminFirestore(admin).doc(`groupCodes/${g.code}`).get()).exists, false);
    assert.deepEqual((await getDoc(doc(sam.db, `users/${sam.uid}`))).get("groupIds"), []);
    assert.ok(await adminAuth(admin).getUser(sam.uid));
  });

  it("deletes everything including user accounts, but keeps the admin", async () => {
    await call(sam, "createGroup", { name: "Last" });
    await expectCallError(call(boss, "adminDeleteEverything", { confirm: "DELETE EVERYTHING" }), "invalid-argument");
    await expectCallError(call(sam, "adminDeleteEverything", { confirm: "DELETE ALL USERS" }), "permission-denied");
    await call(boss, "adminDeleteEverything", { confirm: "DELETE ALL USERS" });
    const db = adminFirestore(admin);
    assert.equal((await db.collection("groups").get()).size, 0);
    assert.equal((await db.doc(`users/${sam.uid}`).get()).exists, false);
    await assert.rejects(adminAuth(admin).getUser(sam.uid));
    await assert.rejects(adminAuth(admin).getUser(lee.uid));
    assert.ok(await adminAuth(admin).getUser(boss.uid));
    assert.deepEqual((await db.doc(`users/${boss.uid}`).get()).get("groupIds"), []);
  });
});
