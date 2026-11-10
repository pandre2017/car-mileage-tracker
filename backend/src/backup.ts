import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { readFile, writeFile } from "node:fs/promises";
import { z } from "zod";
import { inputSchema } from "./domain.js";

const [operation, filename] = process.argv.slice(2);
if (
  !["export", "restore"].includes(operation ?? "") ||
  !filename ||
  !process.env.GCLOUD_PROJECT
) {
  throw new Error(
    "Usage: GCLOUD_PROJECT=project npm run backup -- export|restore /secure/path/file.json",
  );
}
initializeApp({ projectId: process.env.GCLOUD_PROJECT });
const db = getFirestore();
const id = z.string().regex(/^[A-Za-z0-9_-]{1,128}$/);
const schema = z.object({
  version: z.literal(1),
  sourceProject: z.string(),
  users: z.array(
    z.object({
      id,
      displayName: z.string(),
      email: z.string(),
      enabled: z.boolean(),
    }),
  ),
  cars: z.array(
    z.object({
      id,
      displayName: z.string(),
      memberUids: z.array(z.string()),
      revision: z.number().int().nonnegative(),
      entries: z.array(
        inputSchema.extend({
          id: z.uuid(),
          createdByUid: z.string(),
          createdAt: z.iso.datetime(),
        }),
      ),
    }),
  ),
});
try {
  if (operation === "export") {
    const users = await db.collection("users").get();
    const cars = await db.collection("cars").get();
    const backup = schema.parse({
      version: 1,
      sourceProject: process.env.GCLOUD_PROJECT,
      users: users.docs.map((doc) => ({ ...doc.data(), id: doc.id })),
      cars: await Promise.all(
        cars.docs.map(async (car) => ({
          ...car.data(),
          id: car.id,
          revision: car.data().revision ?? 0,
          entries: (await car.ref.collection("fuelEntries").get()).docs.map(
            (entry) => ({ ...entry.data(), id: entry.id }),
          ),
        })),
      ),
    });
    await writeFile(filename, JSON.stringify(backup, null, 2), {
      flag: "wx",
      mode: 0o600,
    });
    console.log("Backup written. Treat it as private personal data.");
  } else {
    const backup = schema.parse(JSON.parse(await readFile(filename, "utf8")));
    const users = await db.collection("users").limit(1).get();
    const cars = await db.collection("cars").limit(1).get();
    if (!users.empty || !cars.empty)
      throw new Error(
        "Restore requires an empty target database; no data was changed.",
      );
    // Restore is an administrative maintenance operation; keep the API stopped during restoration.
    for (const user of backup.users) {
      const { id: uid, ...data } = user;
      await db.doc(`users/${uid}`).create(data);
    }
    for (const car of backup.cars) {
      const { id: carId, entries, ...data } = car;
      await db.doc(`cars/${carId}`).create(data);
      for (const entry of entries) {
        const { id: entryId, ...fields } = entry;
        await db.doc(`cars/${carId}/fuelEntries/${entryId}`).create(fields);
      }
    }
    console.log(
      "Restore complete. Firebase Authentication accounts must exist with the backed-up UIDs.",
    );
  }
} finally {
  await db.terminate();
}
