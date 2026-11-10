import { test } from "node:test";
import assert from "node:assert/strict";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { FirestoreStore } from "../src/firestore.js";
import { randomUUID } from "node:crypto";

test(
  "Firestore enforces membership, idempotency, chronology and concurrent dates",
  { skip: !process.env.FIRESTORE_EMULATOR_HOST },
  async () => {
    initializeApp({ projectId: "demo-car-mileage" });
    const db = getFirestore();
    const store = new FirestoreStore(db);
    const uid = `test-${randomUUID()}`;
    const carId = `test-${randomUUID()}`;
    await db.doc(`users/${uid}`).set({ enabled: true });
    await db
      .doc(`cars/${carId}`)
      .set({ memberUids: [uid], displayName: "Test", revision: 0 });
    const input = {
      fuelDate: "2026-11-03",
      odometerKm: 1400,
      litersHundredths: 4237,
      totalPriceCents: 7520,
    };
    const id = randomUUID();
    try {
      await assert.rejects(store.history("stranger", carId));
      assert.equal((await store.create(uid, carId, id, input)).created, true);
      assert.equal((await store.create(uid, carId, id, input)).created, false);
      await assert.rejects(
        store.create(uid, carId, id, { ...input, totalPriceCents: 1 }),
      );
      await store.create(uid, carId, randomUUID(), {
        ...input,
        fuelDate: "2026-11-01",
        odometerKm: 1000,
      });
      const results = await Promise.allSettled(
        [1200, 1250].map((odometerKm) =>
          store.create(uid, carId, randomUUID(), {
            ...input,
            fuelDate: "2026-11-02",
            odometerKm,
          }),
        ),
      );
      assert.equal(
        results.filter((value) => value.status === "fulfilled").length,
        1,
      );
      assert.equal((await store.history(uid, carId)).length, 3);
      await db.doc(`users/${uid}`).update({ enabled: false });
      await assert.rejects(store.create(uid, carId, id, input));
    } finally {
      await db.recursiveDelete(db.doc(`cars/${carId}`));
      await db.doc(`users/${uid}`).delete();
      await db.terminate();
    }
  },
);
