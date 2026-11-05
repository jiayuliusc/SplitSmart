import {ExpenseShare} from "../contracts/v1";
import {requireThat} from "../infrastructure/errors";

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
    const baseShares: Array<{pId: string; amt: bigint; bp: number; fraction: bigint}> = [];
    for (const pId of sorted) {
      const match = splitInputs.find(i => i.participantId === pId)!;
      const product = total * BigInt(match.basisPoints!);
      const amt = product / 10000n;
      sumAllocated += amt;
      baseShares.push({pId, amt, bp: match.basisPoints!, fraction: product % 10000n});
    }
    const remainder = Number(total - sumAllocated);
    // Largest fractional remainder wins each extra cent; UID breaks exact ties.
    const ranked = [...baseShares].sort((a, b) => a.fraction === b.fraction ?
      (a.pId < b.pId ? -1 : a.pId > b.pId ? 1 : 0) : a.fraction > b.fraction ? -1 : 1);
    const extraIds = new Set(ranked.slice(0, remainder).map(share => share.pId));
    const result: ExpenseShare[] = [];
    for (let i = 0; i < baseShares.length; i++) {
      const extra = extraIds.has(baseShares[i].pId) ? 1n : 0n;
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
