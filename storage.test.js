const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { ref, uploadBytes, getBytes } = require("firebase/storage");
const { test, before, after } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_student_123";
const BOB_UID = "bob_student_456";
const ADMIN_UID = "admin_super_999";

const [storageHost, storagePortRaw] = (
  process.env.FIREBASE_STORAGE_EMULATOR_HOST || "127.0.0.1:9199"
).split(":");

before(async () => {
  const rules = fs.readFileSync("./storage.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    storage: {
      rules,
      host: storageHost,
      port: Number(storagePortRaw),
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

test("Community image upload is limited to the authenticated owner's UID path", async () => {
  const aliceStorage = testEnv.authenticatedContext(ALICE_UID).storage();
  const imageBytes = Buffer.from("test-image-bytes");

  await assertSucceeds(
    uploadBytes(
      ref(aliceStorage, `community_images/${ALICE_UID}/post-image.jpg`),
      imageBytes,
      { contentType: "image/jpeg" }
    )
  );

  await assertFails(
    uploadBytes(
      ref(aliceStorage, `community_images/${BOB_UID}/forged-owner.jpg`),
      imageBytes,
      { contentType: "image/jpeg" }
    )
  );
});

test("Community image path rejects PDF and unsupported MIME types", async () => {
  const aliceStorage = testEnv.authenticatedContext(ALICE_UID).storage();
  const bytes = Buffer.from("not-an-image");

  await assertFails(
    uploadBytes(
      ref(aliceStorage, `community_images/${ALICE_UID}/forbidden.pdf`),
      bytes,
      { contentType: "application/pdf" }
    )
  );

  await assertFails(
    uploadBytes(
      ref(aliceStorage, `community_images/${ALICE_UID}/forbidden.svg`),
      bytes,
      { contentType: "image/svg+xml" }
    )
  );
});

test("Community report evidence is private to reporter and Admin", async () => {
  const aliceStorage = testEnv.authenticatedContext(ALICE_UID).storage();
  const bobStorage = testEnv.authenticatedContext(BOB_UID).storage();
  const adminStorage = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).storage();
  const evidenceRef = ref(aliceStorage, `community_report_evidence/${ALICE_UID}/report-1`);

  await assertSucceeds(
    uploadBytes(evidenceRef, Buffer.from("private-evidence"), { contentType: "image/jpeg" })
  );

  await assertSucceeds(getBytes(evidenceRef));
  await assertFails(
    getBytes(ref(bobStorage, `community_report_evidence/${ALICE_UID}/report-1`))
  );
  await assertSucceeds(
    getBytes(ref(adminStorage, `community_report_evidence/${ALICE_UID}/report-1`))
  );
});
