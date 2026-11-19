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

export function cents(value: unknown, field = "amountCents"): string {
  requireThat(typeof value === "string" && /^(0|[1-9][0-9]*)$/.test(value), "VALIDATION", "Invalid cents.", field);
  const amount = BigInt(value);
  requireThat(amount > 0n && amount <= 9007199254740991n, "OUT_OF_RANGE", "Amount is outside supported range.", field);
  return value;
}

export function localDate(value: unknown): string {
  requireThat(typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value), "VALIDATION", "Invalid local date.", "localDate");
  const year = Number(value.slice(0, 4));
  const month = Number(value.slice(5, 7));
  const day = Number(value.slice(8, 10));
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  requireThat(year >= 1 && month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1], "VALIDATION", "Invalid local date.", "localDate");
  return value;
}

export function settlementDraft(value: unknown): {payerId?: string; payeeId: string; amountCents: string; note?: string} {
  const data = object(value, ["payeeId", "amountCents"], ["payerId", "note"]);
  return {payeeId: id(data.payeeId, "payeeId"), amountCents: cents(data.amountCents),
    ...(data.payerId === undefined ? {} : {payerId: id(data.payerId, "payerId")}),
    ...(data.note === undefined ? {} : {note: string(data.note, "note", 1000, true)})};
}

const CATEGORIES = new Set(["FOOD", "HOUSING", "TRANSPORTATION", "ENTERTAINMENT", "OTHER"]);
const SPLIT_METHODS = new Set(["EQUAL", "EXACT", "PERCENTAGE"]);

export function category(value: unknown): "FOOD" | "HOUSING" | "TRANSPORTATION" | "ENTERTAINMENT" | "OTHER" {
  requireThat(typeof value === "string" && CATEGORIES.has(value), "VALIDATION", "Invalid category.", "category");
  return value as "FOOD" | "HOUSING" | "TRANSPORTATION" | "ENTERTAINMENT" | "OTHER";
}

export function splitMethod(value: unknown): "EQUAL" | "EXACT" | "PERCENTAGE" {
  requireThat(typeof value === "string" && SPLIT_METHODS.has(value), "VALIDATION", "Invalid split method.", "splitMethod");
  return value as "EQUAL" | "EXACT" | "PERCENTAGE";
}

export function expenseDraft(value: unknown): {
  title: string;
  amountCents: string;
  payerId: string;
  participantIds: string[];
  splitMethod: "EQUAL" | "EXACT" | "PERCENTAGE";
  category: "FOOD" | "HOUSING" | "TRANSPORTATION" | "ENTERTAINMENT" | "OTHER";
  note?: string;
  splitInputs: Array<{participantId: string; amountCents?: string; basisPoints?: number}>;
} {
  const data = object(value, ["title", "amountCents", "payerId", "participantIds", "splitMethod", "category", "splitInputs"], ["note"]);
  const titleStr = string(data.title, "title").trim();
  const amountStr = cents(data.amountCents, "amountCents");
  const payer = id(data.payerId, "payerId");
  requireThat(Array.isArray(data.participantIds), "VALIDATION", "Participant IDs must be an array.", "participantIds");
  const rawParts = data.participantIds as unknown[];
  const parts = [...new Set(rawParts.map(p => id(p, "participantId")))].sort();
  requireThat(parts.length === rawParts.length, "VALIDATION", "Participants must be unique.", "participantIds");
  requireThat(parts.length >= 2, "VALIDATION", "At least two participants are required.", "participantIds");
  requireThat(parts.includes(payer), "VALIDATION", "Payer must be included in participants.", "payerId");
  const cat = category(data.category);
  const method = splitMethod(data.splitMethod);
  const noteStr = data.note === undefined ? undefined : string(data.note, "note", 1000, true);

  requireThat(Array.isArray(data.splitInputs), "VALIDATION", "Split inputs must be an array.", "splitInputs");
  const rawInputs = data.splitInputs as unknown[];
  const inputs: Array<{participantId: string; amountCents?: string; basisPoints?: number}> = [];
  for (const rawInput of rawInputs) {
    const inputObj = object(rawInput, ["participantId"], ["amountCents", "basisPoints"]);
    const pId = id(inputObj.participantId, "participantId");
    let amt: string | undefined;
    let bp: number | undefined;
    if (inputObj.amountCents !== undefined) {
      requireThat(typeof inputObj.amountCents === "string" && /^(0|[1-9][0-9]*)$/.test(inputObj.amountCents), "VALIDATION", "Invalid input amount.", "amountCents");
      amt = inputObj.amountCents as string;
    }
    if (inputObj.basisPoints !== undefined) {
      requireThat(Number.isInteger(inputObj.basisPoints) && (inputObj.basisPoints as number) >= 0 && (inputObj.basisPoints as number) <= 10000, "VALIDATION", "Invalid basis points.", "basisPoints");
      bp = inputObj.basisPoints as number;
    }
    inputs.push({participantId: pId, ...(amt !== undefined ? {amountCents: amt} : {}), ...(bp !== undefined ? {basisPoints: bp} : {})});
  }
  if (method === "EQUAL") {
    requireThat(inputs.length === 0, "VALIDATION", "Equal splits have no inputs.", "splitInputs");
  } else {
    requireThat(inputs.length === parts.length && new Set(inputs.map(i => i.participantId)).size === parts.length &&
      inputs.every(i => parts.includes(i.participantId)), "VALIDATION", "Split inputs must match participants exactly.", "splitInputs");
    requireThat(inputs.every(i => method === "EXACT" ? i.amountCents !== undefined && i.basisPoints === undefined :
      i.basisPoints !== undefined && i.amountCents === undefined), "VALIDATION", "Split inputs must match the split method.", "splitInputs");
    if (method === "EXACT") requireThat(inputs.every(i => BigInt(i.amountCents!) <= 9007199254740991n), "OUT_OF_RANGE", "Split amount is outside supported range.", "splitInputs");
  }
  return {
    title: titleStr,
    amountCents: amountStr,
    payerId: payer,
    participantIds: parts,
    splitMethod: method,
    category: cat,
    ...(noteStr !== undefined ? {note: noteStr} : {}),
    splitInputs: inputs,
  };
}
