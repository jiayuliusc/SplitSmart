import {HttpsError, FunctionsErrorCode} from "firebase-functions/v2/https";
import {ErrorCode} from "../contracts/v1";

export class DomainError extends Error {
  constructor(readonly code: ErrorCode, message: string, readonly field?: string) {
    super(message);
  }
}

export function requireThat(condition: unknown, code: ErrorCode, message: string, field?: string): asserts condition {
  if (!condition) throw new DomainError(code, message, field);
}

export function httpsError(error: unknown): HttpsError {
  const codes: Partial<Record<ErrorCode, FunctionsErrorCode>> = {
    VALIDATION: "invalid-argument", UNAUTHENTICATED: "unauthenticated",
    PERMISSION_DENIED: "permission-denied", NOT_FOUND: "not-found",
    ALREADY_EXISTS: "already-exists", CONFLICT: "aborted",
    IDEMPOTENCY_MISMATCH: "failed-precondition", GROUP_DELETING: "failed-precondition",
    OUT_OF_RANGE: "out-of-range", RESOURCE_EXHAUSTED: "resource-exhausted",
    UNAVAILABLE: "unavailable", DEADLINE_EXCEEDED: "deadline-exceeded", INTERNAL: "internal",
  };
  if (error instanceof HttpsError) return error;
  let safe: DomainError;
  if (error instanceof DomainError) safe = error;
  else {
    const code = (error as {code?: unknown})?.code;
    safe = new DomainError(code === 10 || code === 14 ? "UNAVAILABLE" :
      code === 4 ? "DEADLINE_EXCEEDED" : code === 8 ? "RESOURCE_EXHAUSTED" : "INTERNAL",
    "The operation could not be completed. Please try again when available.");
  }
  return new HttpsError(codes[safe.code] ?? "internal", safe.message, {
    schemaVersion: 1, code: safe.code,
    ...(safe.field === undefined ? {} : {field: safe.field}),
    retryable: safe.code === "UNAVAILABLE" || safe.code === "DEADLINE_EXCEEDED",
  });
}
