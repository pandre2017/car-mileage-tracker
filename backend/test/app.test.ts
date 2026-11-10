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

test("server errors correlate logs without exposing internal details", async () => {
  const logs: string[] = [];
  const original = console.error;
  console.error = (line: string) => {
    logs.push(line);
  };
  try {
    const failing = createApp(
      {
        ...store,
        profile: async () => {
          throw Object.assign(new Error("Firestore index missing"), {
            code: 9,
          });
        },
      },
      async () => "allowed",
    );
    const response = await request(failing)
      .get("/v1/me")
      .set("X-Request-ID", "test-correlation-123")
      .auth("private-test-token", { type: "bearer" });
    assert.equal(response.status, 500);
    assert.equal(response.headers["x-request-id"], "test-correlation-123");
    assert.equal(response.body.code, "INTERNAL_ERROR");
    assert.equal(
      JSON.stringify(response.body).includes("Firestore index"),
      false,
    );
    const log = JSON.parse(logs[0]!);
    assert.equal(log.requestId, "test-correlation-123");
    assert.equal(log.errorCode, "9");
    assert.equal(log.errorMessage, "Firestore index missing");
    assert.equal(logs.join("").includes("private-test-token"), false);
  } finally {
    console.error = original;
  }
});
