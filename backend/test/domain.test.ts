import { test } from "node:test";
import assert from "node:assert/strict";
import {
  inputSchema,
  validateHistory,
  sameInput,
  type Entry,
} from "../src/domain.js";
const input = {
  fuelDate: "2026-11-02",
  odometerKm: 1200,
  litersHundredths: 4237,
  totalPriceCents: 7520,
};
const earlier: Entry = {
  ...input,
  id: "earlier",
  fuelDate: "2026-11-01",
  odometerKm: 1000,
  createdByUid: "one",
  createdAt: "now",
};
test("rejects invalid calendar dates and fractional stored amounts", () => {
  assert.equal(
    inputSchema.safeParse({ ...input, fuelDate: "2026-02-30" }).success,
    false,
  );
  assert.equal(
    inputSchema.safeParse({ ...input, litersHundredths: 42.37 }).success,
    false,
  );
});
test("allows delayed insertion between chronological neighbors", () => {
  validateHistory(input, [
    earlier,
    { ...earlier, fuelDate: "2026-11-03", odometerKm: 1400 },
  ]);
});
test("rejects duplicate date and odometer conflicts", () => {
  assert.throws(() =>
    validateHistory(input, [{ ...earlier, fuelDate: input.fuelDate }]),
  );
  assert.throws(() =>
    validateHistory(input, [{ ...earlier, odometerKm: 1300 }]),
  );
});
test("idempotency includes caller and all input values", () => {
  const entry = { ...earlier, ...input };
  assert.equal(sameInput(entry, input, "one"), true);
  assert.equal(sameInput(entry, input, "two"), false);
  assert.equal(
    sameInput(entry, { ...input, totalPriceCents: 1 }, "one"),
    false,
  );
});
