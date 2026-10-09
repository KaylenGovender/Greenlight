/** Self-service account tools (POPIA) against the Emulator Suite (npm run test:functions). */
import * as assert from "node:assert/strict";
import { initializeApp as initClient, FirebaseApp, deleteApp } from "firebase/app";
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth, Auth } from "firebase/auth";
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
describe("my account", function () {
  let admin: AdminApp;
  let ann: Client, ben: Client;
  let groupId: string;

  before(async () => {
    admin = initAdmin({ projectId: PROJECT }, "account-test");
    const stamp = Date.now();
    ann = await client("Ann", `ann.${stamp}@test.local`);
    ben = await client("Ben", `ben.${stamp}@test.local`);
    groupId = (await call(ann, "createGroup", { name: "Pals" })).groupId;
    await call(ben, "joinGroup", { code: (await getDoc(doc(ann.db, `groups/${groupId}`))).get("code") });
    await setDoc(doc(ann.db, `groups/${groupId}/watchSwipes/movie_1_${ann.uid}`), {
      userId: ann.uid, itemKey: "movie_1", decision: "interested", timestamp: serverTimestamp(), snapshot: { title: "Up" },
    });
  });
  after(async () => {
    for (const c of [ann, ben]) { await terminate(c.db).catch(() => undefined); await deleteApp(c.app); }
    await deleteAdmin(admin);
  });

  it("exports my data", async () => {
    const data = await call(ann, "exportMyData");
    assert.equal(data.uid, ann.uid);
    assert.equal(data.profile.displayName, "Ann");
    assert.equal(data.groups[0].name, "Pals");
    assert.equal(data.groups[0].mySwipes.watch[0].title, "Up");
  });

  it("needs the confirmation word to delete", async () => {
    await expectCallError(call(ann, "deleteMyAccount", { confirm: "yes" }), "invalid-argument");
  });

  it("deletes my account, swipes and memberships but not the group", async () => {
    await call(ann, "deleteMyAccount", { confirm: "DELETE" });
    const db = adminFirestore(admin);
    assert.equal((await db.doc(`users/${ann.uid}`).get()).exists, false);
    assert.equal((await db.doc(`groups/${groupId}/watchSwipes/movie_1_${ann.uid}`).get()).exists, false);
    const g = await db.doc(`groups/${groupId}`).get();
    assert.deepEqual(g.get("memberIds"), [ben.uid]);
    await assert.rejects(adminAuth(admin).getUser(ann.uid));
  });
});
