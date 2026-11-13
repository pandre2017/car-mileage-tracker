import {
  getFirestore,
  type Firestore,
  type DocumentSnapshot,
} from "firebase-admin/firestore";
import {
  ApiError,
  estimatedRangeSchema,
  sameInput,
  validateHistory,
  type Store,
  type Entry,
  type FuelInput,
} from "./domain.js";

function member(
  user: DocumentSnapshot,
  car: DocumentSnapshot,
  uid: string,
): void {
  if (user.data()?.enabled !== true)
    throw new ApiError(
      403,
      "NOT_PROVISIONED",
      "Access has not been configured for this account.",
    );
  if (
    !car.exists ||
    !Array.isArray(car.data()?.memberUids) ||
    !car.data()?.memberUids.includes(uid)
  ) {
    throw new ApiError(
      403,
      "CAR_ACCESS_DENIED",
      "You do not have access to this car.",
    );
  }
}
export class FirestoreStore implements Store {
  constructor(private db: Firestore = getFirestore()) {}
  async profile(uid: string) {
    const user = await this.db.doc(`users/${uid}`).get();
    if (user.data()?.enabled !== true)
      throw new ApiError(
        403,
        "NOT_PROVISIONED",
        "Access has not been configured for this account.",
      );
    return {
      uid,
      displayName: String(user.data()?.displayName ?? ""),
      email: String(user.data()?.email ?? ""),
    };
  }
  async cars(uid: string) {
    await this.profile(uid);
    const cars = await this.db
      .collection("cars")
      .where("memberUids", "array-contains", uid)
      .get();
    return cars.docs.map((car) => ({
      id: car.id,
      displayName: String(car.data().displayName),
      estimatedFullTankRangeKm:
        estimatedRangeSchema.safeParse(car.data().estimatedFullTankRangeKm)
          .data ?? null,
    }));
  }
  async history(uid: string, carId: string) {
    return this.db.runTransaction(async (tx) => {
      const user = await tx.get(this.db.doc(`users/${uid}`));
      const car = await tx.get(this.db.doc(`cars/${carId}`));
      member(user, car, uid);
      const entries = await tx.get(
        this.db.collection(`cars/${carId}/fuelEntries`).orderBy("fuelDate"),
      );
      return entries.docs.map(
        (doc) => ({ ...doc.data(), id: doc.id }) as Entry,
      );
    });
  }
  async create(uid: string, carId: string, id: string, input: FuelInput) {
    return this.db.runTransaction(async (tx) => {
      const user = await tx.get(this.db.doc(`users/${uid}`));
      const car = await tx.get(this.db.doc(`cars/${carId}`));
      member(user, car, uid);
      const ref = this.db.doc(`cars/${carId}/fuelEntries/${id}`);
      const existing = await tx.get(ref);
      if (existing.exists) {
        const entry = { ...existing.data(), id } as Entry;
        if (!sameInput(entry, input, uid))
          throw new ApiError(
            409,
            "ENTRY_CONFLICT",
            "This entry ID already has different content.",
          );
        return { entry, created: false };
      }
      // Reading the car and updating its revision serializes all entry writes for this tiny dataset.
      const snapshot = await tx.get(
        this.db.collection(`cars/${carId}/fuelEntries`).orderBy("fuelDate"),
      );
      validateHistory(
        input,
        snapshot.docs.map((doc) => ({ ...doc.data(), id: doc.id }) as Entry),
      );
      const entry: Entry = {
        ...input,
        id,
        createdByUid: uid,
        createdAt: new Date().toISOString(),
      };
      tx.create(ref, entry);
      tx.update(car.ref, { revision: Number(car.data()?.revision ?? 0) + 1 });
      return { entry, created: true };
    });
  }
}
