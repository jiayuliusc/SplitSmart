import {Firestore, Transaction, DocumentSnapshot, FieldPath, Timestamp} from "firebase-admin/firestore";
import {Expense, Settlement, MAX_ABSOLUTE_CENTS, LedgerSnapshot} from "../contracts/v1";
import {DomainError, requireThat} from "../infrastructure/errors";
import * as validate from "../infrastructure/validation";
import {authorizedGroup, time} from "../infrastructure/records";
import {computeShares} from "../expenses/shares";

function storedCents(value: unknown, zero = false): `${bigint}` {
  const text = typeof value === "number" && Number.isSafeInteger(value) ? String(value) : value;
  requireThat(typeof text === "string" && /^(0|[1-9][0-9]*)$/.test(text), "INTERNAL", "Invalid stored money.");
  requireThat(BigInt(text) >= (zero ? 0n : 1n) && BigInt(text) <= MAX_ABSOLUTE_CENTS, "INTERNAL", "Invalid stored money range.");
  return text as `${bigint}`;
}
function timestamp(value: unknown) {
  requireThat(value instanceof Timestamp && value.seconds >= -62135596800 && value.seconds <= 253402300799 && value.nanoseconds % 1000 === 0, "INTERNAL", "Invalid stored timestamp.");
  return time(value);
}
function stored<T>(read: () => T): T {
  try { return read(); } catch (error) {
    if (error instanceof DomainError && error.code === "INTERNAL") throw error;
    throw new DomainError("INTERNAL", "Invalid stored financial record.");
  }
}
const common = ["schemaVersion", "creatorId", "payerId", "amountCents", "currency", "localDate", "createdAt", "updatedAt"];
function metadata(data: FirebaseFirestore.DocumentData) {
  requireThat(data.schemaVersion === 1 && data.currency === "USD", "INTERNAL", "Unsupported financial schema or currency.");
  return {creatorId: validate.id(data.creatorId), payerId: validate.id(data.payerId), amountCents: storedCents(data.amountCents),
    currency: "USD" as const, localDate: validate.localDate(data.localDate), createdAt: timestamp(data.createdAt), updatedAt: timestamp(data.updatedAt),
    ...(data.note === undefined ? {} : {note: validate.string(data.note, "note", 1000, true)})};
}
export function expenseRecord(expenseId: string, groupId: string, data: FirebaseFirestore.DocumentData): Expense {
  return stored(() => {
    validate.object(data, [...common, "title", "participantIds", "splitMethod", "category", "shares"], ["note"]);
    const meta = metadata(data);
    requireThat(Array.isArray(data.participantIds) && new Set(data.participantIds).size === data.participantIds.length && data.participantIds.length >= 2, "INTERNAL", "Invalid stored participants.");
    const participantIds = data.participantIds.map((p: unknown) => validate.id(p)).sort();
    requireThat(participantIds.includes(meta.payerId), "INTERNAL", "Invalid stored payer.");
    requireThat(Array.isArray(data.shares) && data.shares.length === participantIds.length, "INTERNAL", "Invalid stored shares.");
    const method = validate.splitMethod(data.splitMethod);
    const shares = data.shares.map((raw: unknown) => {
      const share = validate.object(raw, ["participantId", "amountCents"], method === "PERCENTAGE" ? ["basisPoints"] : []);
      if (method === "PERCENTAGE") requireThat(Number.isInteger(share.basisPoints) && Number(share.basisPoints) >= 0 && Number(share.basisPoints) <= 10000, "INTERNAL", "Invalid percentage.");
      return {participantId: validate.id(share.participantId), amountCents: storedCents(share.amountCents, true), ...(share.basisPoints === undefined ? {} : {basisPoints: share.basisPoints as number})};
    }).sort((a, b) => a.participantId < b.participantId ? -1 : a.participantId > b.participantId ? 1 : 0);
    requireThat(shares.every((s, i) => s.participantId === participantIds[i]) && shares.reduce((sum, s) => sum + BigInt(s.amountCents), 0n) === BigInt(meta.amountCents), "INTERNAL", "Invalid share totals or references.");
    const calculated = computeShares(meta.amountCents, participantIds, method, method === "EQUAL" ? [] : shares);
    requireThat(calculated.every((s, i) => s.amountCents === shares[i].amountCents), "INTERNAL", "Stored shares do not match the split.");
    return {expenseId, groupId, ...meta, title: validate.string(data.title, "title"), participantIds, splitMethod: method, category: validate.category(data.category), shares};
  });
}
export function settlementRecord(settlementId: string, groupId: string, data: FirebaseFirestore.DocumentData): Settlement {
  return stored(() => {
    validate.object(data, [...common, "payeeId"], ["note"]);
    const meta = metadata(data); const payeeId = validate.id(data.payeeId);
    requireThat(meta.creatorId === meta.payerId && meta.payerId !== payeeId, "INTERNAL", "Invalid stored settlement actor.");
    return {settlementId, groupId, ...meta, payeeId};
  });
}
/** Exact signed balances. Expense shares create debt; recorded payments offset it even after corrections. */
export function balances(ledger: Pick<LedgerSnapshot, "memberIds" | "expenses" | "settlements">, checkBounds = true): Map<string, Map<string, bigint>> {
  const pairs = new Map(ledger.memberIds.map(uid => [uid, new Map(ledger.memberIds.filter(p => p !== uid).map(p => [p, 0n]))]));
  const transfer = (debtor: string, creditor: string, amount: bigint) => {
    requireThat(pairs.has(debtor) && pairs.has(creditor), "INTERNAL", "Financial reference is not a group member.");
    if (debtor === creditor) return;
    pairs.get(creditor)!.set(debtor, pairs.get(creditor)!.get(debtor)! + amount);
    pairs.get(debtor)!.set(creditor, pairs.get(debtor)!.get(creditor)! - amount);
  };
  for (const expense of ledger.expenses) {
    requireThat(pairs.has(expense.creatorId), "INTERNAL", "Expense creator is not a member.");
    for (const share of expense.shares) transfer(share.participantId, expense.payerId, BigInt(share.amountCents));
  }
  for (const payment of ledger.settlements) {
    requireThat(pairs.has(payment.creatorId), "INTERNAL", "Payment creator is not a member.");
    transfer(payment.payeeId, payment.payerId, BigInt(payment.amountCents));
  }
  if (!checkBounds) return pairs;
  for (const row of pairs.values()) {
    let pay = 0n, receive = 0n;
    for (const value of row.values()) {
      requireThat(value >= -MAX_ABSOLUTE_CENTS && value <= MAX_ABSOLUTE_CENTS, "OUT_OF_RANGE", "Pair balance exceeds the supported range.");
      if (value < 0n) pay -= value; else receive += value;
    }
    requireThat(pay <= MAX_ABSOLUTE_CENTS && receive <= MAX_ABSOLUTE_CENTS, "OUT_OF_RANGE", "Group balance exceeds the supported range.");
  }
  return pairs;
}
export async function readFinancialLedger(db: Firestore, tx: Transaction, actor: string, groupId: string, expected?: number): Promise<{group: DocumentSnapshot; ledger: LedgerSnapshot}> {
  const group = await authorizedGroup(db, tx, actor, groupId, false, expected);
  const memberIds = group.get("memberIds");
  requireThat(Array.isArray(memberIds) && memberIds.length > 0 && new Set(memberIds).size === memberIds.length && memberIds.includes(group.get("ownerId")) && Number.isSafeInteger(group.get("revision")) && group.get("revision") >= 1, "INTERNAL", "Invalid stored group.");
  stored(() => memberIds.forEach(uid => validate.id(uid)));
  const expenses = await tx.get(db.collection(`groups/${groupId}/expenses`).orderBy(FieldPath.documentId()));
  const settlements = await tx.get(db.collection(`groups/${groupId}/settlements`).orderBy(FieldPath.documentId()));
  const compare = (a: Expense | Settlement, b: Expense | Settlement) => a.createdAt.seconds - b.createdAt.seconds || a.createdAt.nanoseconds - b.createdAt.nanoseconds;
  const byId = (a: string, b: string) => a < b ? -1 : a > b ? 1 : 0;
  const ledger: LedgerSnapshot = {groupId, revision: String(group.get("revision")) as `${bigint}`, memberIds: [...memberIds].sort(),
    expenses: expenses.docs.map(d => expenseRecord(d.id, groupId, d.data())).sort((a, b) => compare(a, b) || byId(a.expenseId, b.expenseId)),
    settlements: settlements.docs.map(d => settlementRecord(d.id, groupId, d.data())).sort((a, b) => compare(a, b) || byId(a.settlementId, b.settlementId))};
  balances(ledger);
  return {group, ledger};
}
