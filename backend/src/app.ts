import { randomUUID } from "node:crypto";
import express, { type ErrorRequestHandler } from "express";
import { z } from "zod";
import { ApiError, inputSchema, type Store } from "./domain.js";

export function createApp(
  store: Store,
  verify: (token: string) => Promise<string>,
) {
  const app = express();
  app.disable("x-powered-by");
  app.use((req, res, next) => {
    const supplied = req.header("x-request-id");
    const requestId =
      supplied && /^[a-zA-Z0-9-]{1,64}$/.test(supplied)
        ? supplied
        : randomUUID();
    res.locals.requestId = requestId;
    res.setHeader("X-Request-ID", requestId);
    const started = Date.now();
    res.on("finish", () =>
      console.log(
        JSON.stringify({
          severity: res.statusCode >= 500 ? "ERROR" : "INFO",
          message: "HTTP request completed",
          requestId,
          method: req.method,
          route: req.route?.path ?? "unmatched",
          status: res.statusCode,
          durationMs: Date.now() - started,
        }),
      ),
    );
    next();
  });
  app.use(express.json({ limit: "16kb" }));
  app.get("/health", (_req, res) => {
    res.json({ status: "ok" });
  });
  app.use("/v1", async (req, res, next) => {
    const header = req.header("authorization");
    if (!header?.startsWith("Bearer "))
      return next(new ApiError(401, "UNAUTHENTICATED", "Please sign in."));
    try {
      res.locals.uid = await verify(header.slice(7));
      next();
    } catch {
      next(
        new ApiError(
          401,
          "UNAUTHENTICATED",
          "Your session needs to be renewed.",
        ),
      );
    }
  });
  const pathId = z.string().regex(/^[A-Za-z0-9_-]{1,128}$/);
  app.get("/v1/me", async (_req, res) => {
    res.json(await store.profile(res.locals.uid));
  });
  app.get("/v1/cars", async (_req, res) => {
    res.json(await store.cars(res.locals.uid));
  });
  app.get("/v1/cars/:carId/fuel-entries", async (req, res) => {
    res.json(
      await store.history(res.locals.uid, pathId.parse(req.params.carId)),
    );
  });
  app.put("/v1/cars/:carId/fuel-entries/:entryId", async (req, res) => {
    const result = await store.create(
      res.locals.uid,
      pathId.parse(req.params.carId),
      z.uuid().parse(req.params.entryId),
      inputSchema.parse(req.body),
    );
    res.status(result.created ? 201 : 200).json(result.entry);
  });
  app.use((_req, _res, next) =>
    next(new ApiError(404, "NOT_FOUND", "Endpoint not found.")),
  );
  const errors: ErrorRequestHandler = (error, _req, res, _next) => {
    if (error instanceof z.ZodError) {
      res.status(400).json({
        code: "INVALID_INPUT",
        message: "Check the date and numeric values.",
        details: error.issues,
      });
      return;
    }
    if (error instanceof ApiError) {
      res
        .status(error.status)
        .json({ code: error.code, message: error.message });
      return;
    }
    if (error instanceof SyntaxError || error?.type === "entity.too.large") {
      res
        .status(400)
        .json({ code: "INVALID_INPUT", message: "Invalid request body." });
      return;
    }
    console.error(
      JSON.stringify({
        severity: "ERROR",
        message: "Request failed",
        requestId: res.locals.requestId,
        route: _req.route?.path ?? "unmatched",
        errorType: error?.constructor?.name,
        errorCode:
          typeof error?.code === "number" || typeof error?.code === "string"
            ? String(error.code).slice(0, 80)
            : undefined,
        // Server SDK errors explain failures such as missing indexes and IAM denial.
        errorMessage: String(error?.message ?? "Unknown error")
          .replace(/Bearer\s+\S+/gi, "Bearer [redacted]")
          .replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g, "[redacted token]")
          .slice(0, 1500),
      }),
    );
    res.status(500).json({
      code: "INTERNAL_ERROR",
      message: "Temporary server error. Please retry.",
    });
  };
  app.use(errors);
  return app;
}
