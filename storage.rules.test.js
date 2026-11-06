const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after } = require("node:test");
const fs = require("node:fs");
let testEnv;
const PROJECT_ID = "demo-storage-rules";
const [host, portText] = (process.env.FIREBASE_STORAGE_EMULATOR_HOST || "127.0.0.1:9199").split(":");
before(async () => {
  testEnv = await initializeTestEnvironment({ projectId: PROJECT_ID, storage: { rules: fs.readFileSync("./storage.rules", "utf8"), host, port: Number(portText) } });
});
after(async () => { if (testEnv) await testEnv.cleanup(); });
test("unauthenticated users cannot upload chart images", async () => {
  const storage = testEnv.unauthenticatedContext().storage();
  await assertFails(storage.ref("chart_images/alice/a.webp").put(Buffer.from("img"), { contentType: "image/webp" }));
});
test("users can upload only to their own UID-scoped chart path", async () => {
  const alice = testEnv.authenticatedContext("alice").storage();
  await assertSucceeds(alice.ref("chart_images/alice/a.webp").put(Buffer.from("img"), { contentType: "image/webp" }));
  await assertFails(alice.ref("chart_images/bob/b.webp").put(Buffer.from("img"), { contentType: "image/webp" }));
  await assertFails(alice.ref("chart_images/flat.webp").put(Buffer.from("img"), { contentType: "image/webp" }));
});
test("students cannot publish official test diagrams or PDFs", async () => {
  const alice = testEnv.authenticatedContext("alice").storage();
  await assertFails(alice.ref("tests/official/questions/q.webp").put(Buffer.from("img"), { contentType: "image/webp" }));
  await assertFails(alice.ref("pdfs/paper.pdf").put(Buffer.from("%PDF"), { contentType: "application/pdf" }));
  await assertFails(alice.ref("LiveTests/paper.pdf").put(Buffer.from("%PDF"), { contentType: "application/pdf" }));
});
test("admin can publish official diagrams and PDFs", async () => {
  const admin = testEnv.authenticatedContext("admin", { admin: true }).storage();
  await assertSucceeds(admin.ref("tests/official/questions/q.webp").put(Buffer.from("img"), { contentType: "image/webp" }));
  await assertSucceeds(admin.ref("pdfs/paper.pdf").put(Buffer.from("%PDF"), { contentType: "application/pdf" }));
});
test("uploads reject unsupported MIME types and empty objects", async () => {
  const alice = testEnv.authenticatedContext("alice").storage();
  await assertFails(alice.ref("chart_images/alice/bad.webp").put(Buffer.from("x"), { contentType: "application/octet-stream" }));
  await assertFails(alice.ref("chart_images/alice/empty.webp").put(Buffer.alloc(0), { contentType: "image/webp" }));
});
test("question diagram uploads are isolated by UID", async () => {
  const alice = testEnv.authenticatedContext("alice").storage();
  await assertSucceeds(alice.ref("question_images/alice/q1.webp").put(Buffer.from("img"), { contentType: "image/webp" }));
  await assertFails(alice.ref("question_images/bob/q2.webp").put(Buffer.from("img"), { contentType: "image/webp" }));
});