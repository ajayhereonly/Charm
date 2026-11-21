const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_dev_123";
const BOB_UID = "bob_dev_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validScriptPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    filename: "orbital_3d.py",
    folder: "models_3d",
    content: "import py3d\nscene = py3d.Scene()\nscene.add_torus()",
    category: "MODEL_3D",
    description: "Parametric 3D torus model",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validAutomatedAppPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Orbital Simulator",
    summary: "Real-time physics and 3D orbit visualizer",
    iconKey: "rocket",
    accentHex: "#00E5FF",
    pythonCode: "import math, py3d\nprint('Running simulation')",
    inputsSpec: "slider|speed|Orbit Speed|1.0|0.1|5.0",
    triggerMode: "ON_CHANGE",
    intervalSeconds: 0,
    has3dViewport: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated users cannot read or write scripts or automated apps", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scripts").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("scripts").doc("s1").set(validScriptPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("automated_apps").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("automated_apps").doc("a1").set(validAutomatedAppPayload(ALICE_UID))
  );
});

test("Authenticated owner can list, create, partial update, and delete Python scripts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const scriptsCol = aliceDb.collection("users").doc(ALICE_UID).collection("scripts");

  await assertSucceeds(scriptsCol.get());
  await assertSucceeds(scriptsCol.doc("script_01").set(validScriptPayload(ALICE_UID)));
  await assertSucceeds(scriptsCol.doc("script_01").get());

  await assertSucceeds(
    scriptsCol.doc("script_01").update({
      content: "print('updated')",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(scriptsCol.doc("script_01").delete());
});

test("Script validation blocks shadow fields, @DocumentId id field, future timestamps, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const scriptsCol = aliceDb.collection("users").doc(ALICE_UID).collection("scripts");

  // Shadow/extra field rejected
  await assertFails(
    scriptsCol.doc("s_shadow").set(validScriptPayload(ALICE_UID, { isAdmin: true }))
  );

  // @DocumentId `id` key rejected
  await assertFails(
    scriptsCol.doc("s_id_leak").set(validScriptPayload(ALICE_UID, { id: "s_id_leak" }))
  );

  // Future timestamp rejected
  await assertFails(
    scriptsCol.doc("s_future").set(validScriptPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Invalid category rejected
  await assertFails(
    scriptsCol.doc("s_cat").set(validScriptPayload(ALICE_UID, { category: "UNKNOWN" }))
  );

  // Valid create, then attempt immutable createdAt change
  await assertSucceeds(scriptsCol.doc("s_imm").set(validScriptPayload(ALICE_UID)));
  await assertFails(
    scriptsCol.doc("s_imm").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Cross-user access to scripts and automated apps is strictly denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("scripts").doc("bob_s1").set(validScriptPayload(BOB_UID));
    await context.firestore().collection("users").doc(BOB_UID).collection("automated_apps").doc("bob_a1").set(validAutomatedAppPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("scripts").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("scripts").doc("bob_s1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("scripts").doc("bob_s1").update({ content: "hacked" }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("automated_apps").doc("bob_a1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("automated_apps").doc("bob_a1").delete());
});

test("Authenticated owner can create, update, and delete automated apps with strict bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const appsCol = aliceDb.collection("users").doc(ALICE_UID).collection("automated_apps");

  await assertSucceeds(appsCol.get());
  await assertSucceeds(appsCol.doc("app_01").set(validAutomatedAppPayload(ALICE_UID)));
  await assertSucceeds(
    appsCol.doc("app_01").update({
      triggerMode: "INTERVAL",
      intervalSeconds: 10,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Out-of-bounds intervalSeconds rejected
  await assertFails(
    appsCol.doc("app_01").update({
      intervalSeconds: 99999,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Invalid triggerMode rejected
  await assertFails(
    appsCol.doc("app_02").set(validAutomatedAppPayload(ALICE_UID, { triggerMode: "CRON" }))
  );

  await assertSucceeds(appsCol.doc("app_01").delete());
});
