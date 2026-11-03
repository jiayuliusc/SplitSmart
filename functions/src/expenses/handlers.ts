import {Auth} from "firebase-admin/auth";
import {Firestore, Timestamp, FieldValue} from "firebase-admin/firestore";
import {GroupMutationResponse, LedgerResponse, Expense, ExpenseShare, DeletedRecord} from "../contracts/v1";
import {requireThat} from "../infrastructure/errors";
import * as validate from "../infrastructure/validation";
import {mutate} from "../infrastructure/receipts";
import {authorizedGroup, time} from "../infrastructure/records";

export function expenseRecord(expenseId: string, groupId: string, data: FirebaseFirestore.DocumentData): Expense {
  requireThat(data.schemaVersion === 1, "INTERNAL", "Unsupported expense version.");
  return {
    expenseId,
    groupId,
    title: data.title,
    amountCents: data.amountCents as `${bigint}`,
    currency: "USD",
    creatorId: data.creatorId,
    payerId: data.payerId,
    participantIds: [...data.participantIds].sort(),
    splitMethod: data.splitMethod,
    category: data.category,
    ...(data.note === undefined ? {} : {note: data.note}),
    localDate: data.localDate,
    createdAt: time(data.createdAt),
    updatedAt: time(data.updatedAt),
    shares: (data.shares as unknown[]).map(s => {
      const share = s as Record<string, unknown>;
      return {
        participantId: share.participantId as string,
        amountCents: share.amountCents as `${bigint}`,
        ...(share.basisPoints === undefined ? {} : {basisPoints: share.basisPoints as number}),
      };
    }),
  };
}

export function computeShares(
  totalAmountStr: string,
  participantIds: string[],
  splitMethod: "EQUAL" | "EXACT" | "PERCENTAGE",
  splitInputs: Array<{participantId: string; amountCents?: string; basisPoints?: number}>
): ExpenseShare[] {
  const sorted = [...participantIds].sort();
  const total = BigInt(totalAmountStr);
  if (splitMethod === "EXACT") {
    let sum = 0n;
    const result: ExpenseShare[] = [];
    for (const pId of sorted) {
      const match = splitInputs.find(i => i.participantId === pId);
      requireThat(match !== undefined && match.amountCents !== undefined, "VALIDATION", `Missing exact split amount for ${pId}.`, "splitInputs");
      const amt = BigInt(match!.amountCents!);
      sum += amt;
      result.push({participantId: pId, amountCents: match!.amountCents! as `${bigint}`});
    }
    requireThat(sum === total, "VALIDATION", "Exact split amounts must equal total amount.", "amountCents");
    return result;
  }
  if (splitMethod === "PERCENTAGE") {
    let bpSum = 0;
    for (const pId of sorted) {
      const match = splitInputs.find(i => i.participantId === pId);
      requireThat(match !== undefined && match.basisPoints !== undefined, "VALIDATION", `Missing percentage basis points for ${pId}.`, "splitInputs");
      bpSum += match!.basisPoints!;
    }
    requireThat(bpSum === 10000, "VALIDATION", "Percentage basis points must sum to 10000 (100%).", "splitInputs");
    let sumAllocated = 0n;
    const baseShares: Array<{pId: string; amt: bigint; bp: number}> = [];
    for (const pId of sorted) {
      const match = splitInputs.find(i => i.participantId === pId)!;
      const amt = (total * BigInt(match.basisPoints!)) / 10000n;
      sumAllocated += amt;
      baseShares.push({pId, amt, bp: match.basisPoints!});
    }
    const remainder = Number(total - sumAllocated);
    const result: ExpenseShare[] = [];
    for (let i = 0; i < baseShares.length; i++) {
      const extra = i < remainder ? 1n : 0n;
      result.push({
        participantId: baseShares[i].pId,
        amountCents: (baseShares[i].amt + extra).toString() as `${bigint}`,
        basisPoints: baseShares[i].bp,
      });
    }
    return result;
  }

  // EQUAL
  const count = BigInt(sorted.length);
  const base = total / count;
  const remainder = Number(total % count);
  const result: ExpenseShare[] = [];
  for (let i = 0; i < sorted.length; i++) {
    const amt = base + (i < remainder ? 1n : 0n);
    result.push({participantId: sorted[i], amountCents: amt.toString() as `${bigint}`});
  }
  return result;
}

export async function handleExpenseCall(
  db: Firestore,
  _auth: Auth,
  actor: {uid: string; email: string},
  operation: "createExpense" | "updateExpense" | "deleteExpense" | "getGroupLedger",
  raw: unknown
): Promise<unknown> {
  validate.id(actor.uid, "uid");
  if (operation === "createExpense") {
    const data = validate.envelope(raw, ["groupId", "expectedRevision", "localDate", "draft"]);
    const groupId = validate.id(data.groupId);
    const expected = validate.revision(data.expectedRevision);
    const dateStr = validate.localDate(data.localDate);
    const draft = validate.expenseDraft(data.draft);
    return mutate(db, actor.uid, operation, data, async tx => {
      const group = await authorizedGroup(db, tx, actor.uid, groupId, false, expected);
      const memberIds: string[] = group.get("memberIds");
      requireThat(draft.participantIds.every(p => memberIds.includes(p)), "VALIDATION", "All participants must be group members.", "participantIds");
      requireThat(memberIds.includes(draft.payerId), "VALIDATION", "Payer must be a group member.", "payerId");
      const shares = computeShares(draft.amountCents, draft.participantIds, draft.splitMethod, draft.splitInputs);
      const now = Timestamp.now();
      const revision = expected + 1;
      const ref = db.collection(`groups/${groupId}/expenses`).doc();
      const stored = {
        schemaVersion: 1,
        title: draft.title,
        amountCents: draft.amountCents,
        currency: "USD",
        creatorId: actor.uid,
        payerId: draft.payerId,
        participantIds: draft.participantIds,
        splitMethod: draft.splitMethod,
        category: draft.category,
        ...(draft.note !== undefined ? {note: draft.note} : {}),
        localDate: dateStr,
        createdAt: now,
        updatedAt: now,
        shares,
      };
      return {
        response: {
          schemaVersion: 1,
          requestId: data.requestId as string,
          groupRevision: String(revision) as `${bigint}`,
          value: expenseRecord(ref.id, groupId, stored),
        } satisfies GroupMutationResponse<Expense>,
        write: transaction => {
          transaction.create(ref, stored);
          transaction.update(group.ref, {revision, updatedAt: now});
        },
      };
    });
  }

  if (operation === "updateExpense") {
    const data = validate.envelope(raw, ["groupId", "expectedRevision", "expenseId", "draft"]);
    const groupId = validate.id(data.groupId);
    const expected = validate.revision(data.expectedRevision);
    const expenseId = validate.id(data.expenseId, "expenseId");
    const draft = validate.expenseDraft(data.draft);
    return mutate(db, actor.uid, operation, data, async tx => {
      const group = await authorizedGroup(db, tx, actor.uid, groupId, false, expected);
      const ref = db.doc(`groups/${groupId}/expenses/${expenseId}`);
      const expenseDoc = await tx.get(ref);
      requireThat(expenseDoc.exists, "NOT_FOUND", "This expense no longer exists.");
      requireThat(expenseDoc.get("creatorId") === actor.uid, "PERMISSION_DENIED", "Only the expense creator can edit or delete this expense.");
      const memberIds: string[] = group.get("memberIds");
      requireThat(draft.participantIds.every(p => memberIds.includes(p)), "VALIDATION", "All participants must be group members.", "participantIds");
      requireThat(memberIds.includes(draft.payerId), "VALIDATION", "Payer must be a group member.", "payerId");
      const shares = computeShares(draft.amountCents, draft.participantIds, draft.splitMethod, draft.splitInputs);
      const now = Timestamp.now();
      const revision = expected + 1;
      const patch = {
        title: draft.title,
        amountCents: draft.amountCents,
        payerId: draft.payerId,
        participantIds: draft.participantIds,
        splitMethod: draft.splitMethod,
        category: draft.category,
        note: draft.note !== undefined ? draft.note : FieldValue.delete(),
        shares,
        updatedAt: now,
      };
      const stored = {...expenseDoc.data()!, ...patch, updatedAt: now};
      if (draft.note === undefined) delete (stored as Record<string, unknown>).note;
      return {
        response: {
          schemaVersion: 1,
          requestId: data.requestId as string,
          groupRevision: String(revision) as `${bigint}`,
          value: expenseRecord(expenseId, groupId, stored),
        } satisfies GroupMutationResponse<Expense>,
        write: transaction => {
          transaction.update(ref, patch);
          transaction.update(group.ref, {revision, updatedAt: now});
        },
      };
    });
  }

  if (operation === "deleteExpense") {
    const data = validate.envelope(raw, ["groupId", "expectedRevision", "expenseId"]);
    const groupId = validate.id(data.groupId);
    const expected = validate.revision(data.expectedRevision);
    const expenseId = validate.id(data.expenseId, "expenseId");
    return mutate(db, actor.uid, operation, data, async tx => {
      const group = await authorizedGroup(db, tx, actor.uid, groupId, false, expected);
      const ref = db.doc(`groups/${groupId}/expenses/${expenseId}`);
      const expenseDoc = await tx.get(ref);
      requireThat(expenseDoc.exists, "NOT_FOUND", "This expense no longer exists.");
      requireThat(expenseDoc.get("creatorId") === actor.uid, "PERMISSION_DENIED", "Only the expense creator can edit or delete this expense.");
      const now = Timestamp.now();
      const revision = expected + 1;
      return {
        response: {
          schemaVersion: 1,
          requestId: data.requestId as string,
          groupRevision: String(revision) as `${bigint}`,
          value: {recordId: expenseId},
        } satisfies GroupMutationResponse<DeletedRecord>,
        write: transaction => {
          transaction.delete(ref);
          transaction.update(group.ref, {revision, updatedAt: now});
        },
      };
    });
  }

  if (operation === "getGroupLedger") {
    const data = validate.envelope(raw, ["groupId"], false);
    const groupId = validate.id(data.groupId);
    return db.runTransaction(async tx => {
      const group = await authorizedGroup(db, tx, actor.uid, groupId, false);
      const expensesSnap = await tx.get(db.collection(`groups/${groupId}/expenses`));
      const expensesList: Expense[] = [];
      expensesSnap.forEach(doc => {
        if (doc.get("schemaVersion") === 1) {
          expensesList.push(expenseRecord(doc.id, groupId, doc.data()));
        }
      });
      expensesList.sort((a, b) => {
        if (a.createdAt.seconds !== b.createdAt.seconds) return a.createdAt.seconds - b.createdAt.seconds;
        if (a.createdAt.nanoseconds !== b.createdAt.nanoseconds) return a.createdAt.nanoseconds - b.createdAt.nanoseconds;
        return a.expenseId.localeCompare(b.expenseId);
      });
      return {
        schemaVersion: 1,
        ledger: {
          groupId,
          revision: String(group.get("revision")) as `${bigint}`,
          memberIds: [...group.get("memberIds")].sort(),
          expenses: expensesList,
          settlements: [],
        },
      } satisfies LedgerResponse;
    }, {maxAttempts: 5});
  }

  throw new Error(`Unknown expense operation: ${operation}`);
}
