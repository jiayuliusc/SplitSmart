import {createHash} from "node:crypto";
import {Firestore, Transaction, Timestamp, DocumentData} from "firebase-admin/firestore";
import {DomainError, requireThat} from "./errors";

export function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value !== null && typeof value === "object") {
    const data = value as Record<string, unknown>;
    return `{${Object.keys(data).sort().map(k => `${JSON.stringify(k)}:${canonical(data[k])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

export interface Plan<T> {
  response: T;
  write: (tx: Transaction) => void;
}

function identity(operation: string, request: Record<string, unknown>) {
  const {requestId, ...payload} = request;
  const canonicalPayload = canonical({operation, request: payload});
  return {requestId, canonicalPayload, payloadHash: createHash("sha256").update(canonicalPayload, "utf8").digest("hex")};
}

function checkSaved(data: DocumentData, operation: string, canonicalPayload: string, payloadHash: string) {
  requireThat(data.operation === operation && data.canonicalPayload === canonicalPayload && data.payloadHash === payloadHash,
    "IDEMPOTENCY_MISMATCH", "This request ID was already used for different input.");
}

function response<T>(outcome: DocumentData): T {
  if (outcome.outcome === "ERROR") throw new DomainError(outcome.error.code, outcome.error.message, outcome.error.field);
  return outcome.response as T;
}

/** A completed request must replay even if an optional external lookup is currently unavailable. */
export async function replay(db: Firestore, actor: string, operation: string, request: Record<string, unknown>) {
  const {requestId, canonicalPayload, payloadHash} = identity(operation, request);
  const saved = await db.doc(`mutationReceipts/${actor}/requests/${requestId}`).get();
  if (!saved.exists) return null;
  checkSaved(saved.data()!, operation, canonicalPayload, payloadHash);
  return {value: response<unknown>(saved.data()!)};
}

/** All business writes are staged until validation finishes. A rejected plan writes only its receipt. */
export async function mutate<T>(db: Firestore, actor: string, operation: string,
  request: Record<string, unknown>, build: (tx: Transaction) => Promise<Plan<T>>): Promise<T> {
  const {requestId, canonicalPayload, payloadHash} = identity(operation, request);
  const ref = db.doc(`mutationReceipts/${actor}/requests/${requestId}`);
  const outcome: DocumentData = await db.runTransaction(async tx => {
    const saved = await tx.get(ref);
    if (saved.exists) {
      const data = saved.data()!;
      checkSaved(data, operation, canonicalPayload, payloadHash);
      return data;
    }
    const common = {schemaVersion: 1, operation, canonicalPayload, payloadHash, createdAt: Timestamp.now()};
    let plan: Plan<T>;
    try { plan = await build(tx); }
    catch (error) {
      if (!(error instanceof DomainError)) throw error;
      const result = {...common, outcome: "ERROR", error: {
        code: error.code, message: error.message, ...(error.field === undefined ? {} : {field: error.field}),
      }};
      tx.create(ref, result);
      return result;
    }
    plan.write(tx);
    const result = {...common, outcome: "SUCCESS", response: plan.response};
    tx.create(ref, result);
    return result;
  }, {maxAttempts: 5});
  // Throw only AFTER runTransaction commits; otherwise deterministic error receipts roll back.
  return response<T>(outcome);
}
