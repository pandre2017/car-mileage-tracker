import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { createApp } from "./app.js";
import { FirestoreStore } from "./firestore.js";

if (
  process.env.K_SERVICE &&
  (process.env.FIREBASE_AUTH_EMULATOR_HOST ||
    process.env.FIRESTORE_EMULATOR_HOST)
) {
  throw new Error("Emulator configuration must not be used on Cloud Run.");
}
initializeApp({
  projectId: process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT,
});
const app = createApp(
  new FirestoreStore(),
  async (token) => (await getAuth().verifyIdToken(token)).uid,
);
const server = app.listen(Number(process.env.PORT ?? 8080), "0.0.0.0", () =>
  console.log("API listening"),
);
process.on("SIGTERM", () => {
  server.close();
});
