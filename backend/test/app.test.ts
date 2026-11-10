import { test } from "node:test";
import assert from "node:assert/strict";
import request from "supertest";
import { createApp } from "../src/app.js";
import { ApiError, type Store } from "../src/domain.js";
const store: Store = {
  profile: async (uid) => {
    if (uid !== "allowed") throw new ApiError(403, "NOT_PROVISIONED", "Denied");
    return { uid, displayName: "Driver", email: "driver@example.test" };
  },
  cars: async () => [],
  history: async () => [],
  create: async () => {
    throw new Error("not used");
  },
};
const app = createApp(store, async (token) => {
  if (token === "invalid") throw new Error();
  return token;
});
test("health is public; data requires valid identity and provisioning", async () => {
  assert.equal((await request(app).get("/health")).status, 200);
  assert.equal((await request(app).get("/v1/me")).status, 401);
  assert.equal(
    (await request(app).get("/v1/me").auth("invalid", { type: "bearer" }))
      .status,
    401,
  );
  assert.equal(
    (await request(app).get("/v1/me").auth("stranger", { type: "bearer" }))
      .status,
    403,
  );
  assert.equal(
    (await request(app).get("/v1/me").auth("allowed", { type: "bearer" }))
      .status,
    200,
  );
});
