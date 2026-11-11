import {Auth} from "firebase-admin/auth";
import {Firestore, Timestamp} from "firebase-admin/firestore";
import {Expense, Settlement, LedgerResponse, GroupMutationResponse, DeletedRecord} from "../contracts/v1";
import {requireThat} from "../infrastructure/errors";
import * as validate from "../infrastructure/validation";
import {mutate} from "../infrastructure/receipts";
import {readFinancialLedger, balances, expenseRecord, settlementRecord} from "../financial/ledger";
import {computeShares} from "./shares";
export {computeShares} from "./shares";
export {expenseRecord} from "../financial/ledger";
export type FinancialCall = "createExpense" | "updateExpense" | "deleteExpense" | "createSettlement" | "updateSettlement" | "deleteSettlement" | "getGroupLedger";

/** Every financial writer uses the same complete ledger read and group revision guard. */
export async function handleExpenseCall(db: Firestore, _auth: Auth, actor: {uid: string; email: string}, operation: FinancialCall, raw: unknown): Promise<unknown> {
  validate.id(actor.uid, "uid");
  if (operation === "getGroupLedger") {
    const data = validate.envelope(raw, ["groupId"], false);
    const groupId = validate.id(data.groupId);
    return db.runTransaction(async tx => {
      const {ledger} = await readFinancialLedger(db, tx, actor.uid, groupId);
      return {schemaVersion: 1, ledger} satisfies LedgerResponse;
    }, {maxAttempts: 5});
  }
  const expense = operation.endsWith("Expense");
  const create = operation.startsWith("create");
  const remove = operation.startsWith("delete");
  const idField = expense ? "expenseId" : "settlementId";
  const collection = expense ? "expenses" : "settlements";
  const data = validate.envelope(raw, ["groupId", "expectedRevision", ...(create ? ["localDate"] : [idField]), ...(remove ? [] : ["draft"])]);
  const groupId = validate.id(data.groupId);
  const expected = validate.revision(data.expectedRevision);
  const localDate = create ? validate.localDate(data.localDate) : undefined;
  const draft = remove ? undefined : expense ? validate.expenseDraft(data.draft) : validate.settlementDraft(data.draft);
  // Generate a create ID once per call, outside retries. The receipt remains authoritative on replay.
  const ref = create ? db.collection(`groups/${groupId}/${collection}`).doc() : db.doc(`groups/${groupId}/${collection}/${validate.id(data[idField], idField)}`);
  return mutate(db, actor.uid, operation, data, async tx => {
    const {group, ledger} = await readFinancialLedger(db, tx, actor.uid, groupId, expected);
    const existing = (expense ? ledger.expenses : ledger.settlements).find(record => ("expenseId" in record ? record.expenseId : record.settlementId) === ref.id);
    if (!create) {
      requireThat(existing !== undefined, "NOT_FOUND", "This record no longer exists.");
      requireThat(existing.creatorId === actor.uid, "PERMISSION_DENIED", "Only the creator can edit or delete this record.");
    }
    const expenses = ledger.expenses.filter(record => !expense || record.expenseId !== ref.id);
    const settlements = ledger.settlements.filter(record => expense || record.settlementId !== ref.id);
    const now = Timestamp.now();
    let stored: FirebaseFirestore.DocumentData | undefined;
    let value: Expense | Settlement | DeletedRecord = {recordId: ref.id};
    if (!remove) {
      const metadata = {schemaVersion: 1, currency: "USD", creatorId: existing?.creatorId ?? actor.uid,
        localDate: existing?.localDate ?? localDate, createdAt: existing ? new Timestamp(existing.createdAt.seconds, existing.createdAt.nanoseconds) : now, updatedAt: now};
      if (expense) {
        const input = draft as ReturnType<typeof validate.expenseDraft>;
        requireThat(input.participantIds.every(uid => ledger.memberIds.includes(uid)), "VALIDATION", "All participants must be group members.", "participantIds");
        requireThat(ledger.memberIds.includes(input.payerId), "VALIDATION", "Payer must be a group member.", "payerId");
        const shares = computeShares(input.amountCents, input.participantIds, input.splitMethod, input.splitInputs);
        stored = {...metadata, title: input.title, amountCents: Number(input.amountCents), payerId: input.payerId, participantIds: input.participantIds,
          splitMethod: input.splitMethod, category: input.category, ...(input.note === undefined ? {} : {note: input.note}),
          shares: shares.map(share => ({...share, amountCents: Number(share.amountCents)}))};
        value = expenseRecord(ref.id, groupId, stored);
        expenses.push(value);
      } else {
        const input = draft as ReturnType<typeof validate.settlementDraft>;
        const prior = existing as Settlement | undefined;   // this branch only handles settlements
        const payerId = prior ? prior.payerId : (input.payerId ?? actor.uid);
        if (prior) {
          // Payer and recipient are fixed once the settlement exists.
          requireThat(input.payeeId === prior.payeeId && (input.payerId === undefined || input.payerId === prior.payerId),
            "VALIDATION", "The payer and recipient of a settlement cannot be changed.", "payeeId");
        } else {
          // Either side may record the payment, but the creator must be one of the two parties.
          requireThat(actor.uid === payerId || actor.uid === input.payeeId,
            "VALIDATION", "You must be the payer or the recipient of this settlement.", "payerId");
        }
        requireThat(input.payeeId !== payerId && ledger.memberIds.includes(payerId) && ledger.memberIds.includes(input.payeeId),
          "VALIDATION", "Choose another group member.", "payeeId");
        const pairBalances = balances({...ledger, expenses, settlements}, false);
        const debt = -pairBalances.get(payerId)!.get(input.payeeId)!;
        requireThat(BigInt(input.amountCents) <= debt, "VALIDATION", "Payment exceeds the current debt to this member.", "amountCents");
        stored = {...metadata, payerId, payeeId: input.payeeId, amountCents: Number(input.amountCents), ...(input.note === undefined ? {} : {note: input.note})};
        value = settlementRecord(ref.id, groupId, stored);
        settlements.push(value);
      }
    }
    balances({...ledger, expenses, settlements});
    const revision = expected + 1;
    const response: GroupMutationResponse<Expense | Settlement | DeletedRecord> = {schemaVersion: 1, requestId: data.requestId as string, groupRevision: String(revision) as `${bigint}`, value};
    return {response, write: transaction => {
      if (remove) transaction.delete(ref); else if (create) transaction.create(ref, stored!); else transaction.set(ref, stored!);
      transaction.update(group.ref, {revision, updatedAt: now});
    }};
  });
}
