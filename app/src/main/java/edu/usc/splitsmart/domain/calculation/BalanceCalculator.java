package edu.usc.splitsmart.domain.calculation;

import java.math.BigInteger;
import java.util.*;
import edu.usc.splitsmart.domain.contract.ContractVersion;
import edu.usc.splitsmart.domain.model.*;

/** Exact pairwise projection of one coherent ledger. Positive means the other member owes the viewer. */
public final class BalanceCalculator {
    private static final BigInteger MAX = BigInteger.valueOf(ContractVersion.MAX_ABSOLUTE_CENTS);
    private BalanceCalculator() { }

    public static BalanceSummary calculate(LedgerSnapshot ledger, String viewerId) {
        Set<String> members = new HashSet<>(ledger.memberIds());
        require(members.size() == ledger.memberIds().size() && !members.contains("") && members.contains(viewerId));
        require(ledger.revision() >= 1 && ledger.revision() <= ContractVersion.MAX_REVISION);
        Map<String, BigInteger> pairs = new TreeMap<>();
        for (String member : members) if (!member.equals(viewerId)) pairs.put(member, BigInteger.ZERO);
        Set<String> expenseIds = new HashSet<>();
        for (Expense expense : ledger.expenses()) {
            require(expenseIds.add(expense.expenseId()));
            require(ledger.groupId().equals(expense.groupId()) && "USD".equals(expense.currency()));
            require(members.contains(expense.payerId()) && members.contains(expense.creatorId()));
            amount(expense.amountCents(), false);
            Set<String> participants = new HashSet<>(expense.participantIds());
            require(participants.size() >= 2 && participants.size() == expense.participantIds().size()
                && participants.contains(expense.payerId()) && members.containsAll(participants));
            Set<String> shared = new HashSet<>();
            BigInteger total = BigInteger.ZERO;
            for (ExpenseShare share : expense.shares()) {
                require(participants.contains(share.participantId()) && shared.add(share.participantId()));
                amount(share.amountCents(), true);
                BigInteger cents = BigInteger.valueOf(share.amountCents());
                total = total.add(cents);
                transfer(pairs, viewerId, share.participantId(), expense.payerId(), cents);
            }
            require(shared.equals(participants) && total.equals(BigInteger.valueOf(expense.amountCents())));
        }
        Set<String> settlementIds = new HashSet<>();
        for (Settlement settlement : ledger.settlements()) {
            require(settlementIds.add(settlement.settlementId()));
            require(ledger.groupId().equals(settlement.groupId()) && "USD".equals(settlement.currency()));
            require(members.contains(settlement.payerId()) && members.contains(settlement.payeeId())
                && members.contains(settlement.creatorId()) && !settlement.payerId().equals(settlement.payeeId()));
            amount(settlement.amountCents(), false);
            // A payment offsets the payer's debt. Historical payments may become credits after edits.
            transfer(pairs, viewerId, settlement.payeeId(), settlement.payerId(), BigInteger.valueOf(settlement.amountCents()));
        }
        List<PairBalance> result = new ArrayList<>();
        BigInteger pay = BigInteger.ZERO;
        BigInteger receive = BigInteger.ZERO;
        for (var pair : pairs.entrySet()) {
            BigInteger value = pair.getValue();
            result.add(new PairBalance(pair.getKey(), bounded(value)));
            if (value.signum() < 0) pay = pay.subtract(value); else receive = receive.add(value);
        }
        return new BalanceSummary(ledger.groupId(), viewerId, ledger.revision(), result, bounded(pay), bounded(receive));
    }
    private static void transfer(Map<String, BigInteger> pairs, String viewer, String debtor, String creditor, BigInteger cents) {
        if (debtor.equals(creditor)) return;
        if (viewer.equals(creditor)) pairs.compute(debtor, (id, prior) -> prior.add(cents));
        else if (viewer.equals(debtor)) pairs.compute(creditor, (id, prior) -> prior.subtract(cents));
    }
    private static long bounded(BigInteger cents) {
        if (cents.abs().compareTo(MAX) > 0) throw new ArithmeticException("Balance exceeds the supported money range.");
        // The explicit contract bound above is stricter than the long range (API 26 safe).
        return cents.longValue();
    }
    private static void amount(long cents, boolean zeroAllowed) {
        require(cents >= (zeroAllowed ? 0 : 1) && cents <= ContractVersion.MAX_ABSOLUTE_CENTS);
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid group ledger.");
    }
}
