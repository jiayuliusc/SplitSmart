import {MAX_REVISION} from "../contracts/v1";
import {requireThat} from "./errors";

export function object(value: unknown, required: string[], optional: string[] = []): Record<string, unknown> {
  requireThat(value !== null && typeof value === "object" && !Array.isArray(value), "VALIDATION", "Expected an object.");
  const data = value as Record<string, unknown>;
  requireThat(required.every(k => Object.hasOwn(data, k)) &&
    Object.keys(data).every(k => required.includes(k) || optional.includes(k)), "VALIDATION", "Unexpected or missing fields.");
  requireThat(Object.values(data).every(v => v !== null && v !== undefined), "VALIDATION", "Omit optional values instead of null.");
  return data;
}

export function string(value: unknown, field: string, max = 100, blank = false): string {
  requireThat(typeof value === "string" && value.length <= max && (blank || value.trim().length > 0),
    "VALIDATION", `Check ${field}.`, field);
  return value;
}

export function email(value: unknown): string {
  const result = string(value, "email", 254).trim().toLowerCase();
  requireThat(/^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@usc\.edu$/.test(result) &&
    !result.startsWith(".") && !result.includes("..") && !result.includes(".@"),
  "VALIDATION", "Use an email address with the exact usc.edu domain.", "email");
  return result;
}

export function id(value: unknown, field = "groupId"): string {
  requireThat(typeof value === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(value), "VALIDATION", "Invalid ID.", field);
  return value;
}

export function revision(value: unknown): number {
  requireThat(typeof value === "string" && /^(0|[1-9][0-9]*)$/.test(value), "VALIDATION", "Invalid revision.", "expectedRevision");
  requireThat(BigInt(value) <= MAX_REVISION, "OUT_OF_RANGE", "Revision is outside the supported range.");
  return Number(value);
}

export function envelope(value: unknown, fields: string[], mutation = true): Record<string, unknown> {
  const data = object(value, ["schemaVersion", ...(mutation ? ["requestId"] : []), ...fields]);
  requireThat(data.schemaVersion === 1, "VALIDATION", "Unsupported schema version.");
  if (mutation) requireThat(typeof data.requestId === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(data.requestId),
  "VALIDATION", "Invalid request ID.", "requestId");
  return data;
}

export function profile(value: unknown): {name: string; uscId: string} {
  const data = object(value, ["name", "uscId"]);
  const uscId = string(data.uscId, "uscId", 10);
  requireThat(/^[0-9]{10}$/.test(uscId), "VALIDATION", "USC ID must contain exactly 10 digits.", "uscId");
  return {name: string(data.name, "name").trim(), uscId};
}

export function groupDraft(value: unknown): {name: string; description?: string} {
  const data = object(value, ["name"], ["description"]);
  return {name: string(data.name, "name").trim(),
    ...(data.description === undefined ? {} : {description: string(data.description, "description", 1000, true)})};
}
