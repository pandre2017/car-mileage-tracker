import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
if (
  !process.env.FIRESTORE_EMULATOR_HOST ||
  !process.env.FIREBASE_AUTH_EMULATOR_HOST
)
  throw new Error("Seed only runs against emulators.");
initializeApp({ projectId: "demo-car-mileage" });
for (const uid of ["driver-one", "driver-two"]) {
  const email = `${uid}@example.test`;
  await getAuth()
    .getUser(uid)
    .catch(() =>
      getAuth().createUser({ uid, email, password: "local-demo-only" }),
    );
  await getFirestore()
    .doc(`users/${uid}`)
    .set({ email, displayName: uid, enabled: true });
}
await getFirestore()
  .doc("cars/family-car")
  .set({
    displayName: "Family car",
    estimatedFullTankRangeKm: 650,
    memberUids: ["driver-one", "driver-two"],
    revision: 0,
  });
console.log("Emulator users and shared car provisioned.");
