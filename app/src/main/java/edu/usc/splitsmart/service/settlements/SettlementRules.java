package edu.usc.splitsmart.service.settlements;

import java.math.BigDecimal;
import edu.usc.splitsmart.domain.calculation.BalanceCalculator;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;

/** Pure exact-money validation and edit preview using one coherent ledger. */
public final class SettlementRules {
    private SettlementRules() { }
    public static long cents(String input) {
        String value = input == null ? "" : input.trim();
        if (!value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Invalid amount");
        try {
            long cents = new BigDecimal(value).movePointRight(2).longValueExact();
            if (cents <= 0 || cents > ContractVersion.MAX_ABSOLUTE_CENTS) throw new IllegalArgumentException("Invalid amount");
            return cents;
        } catch (ArithmeticException invalid) { throw new IllegalArgumentException("Invalid amount", invalid); }
    }
    public static AppError validate(SettlementDraft draft) {
        if (draft == null) return invalid("draft");
        if (draft.payeeId().trim().isEmpty()) return invalid("payeeId");
        if (draft.payerId() != null && draft.payerId().trim().isEmpty()) return invalid("payerId");
        if (draft.amountCents() <= 0 || draft.amountCents() > ContractVersion.MAX_ABSOLUTE_CENTS) return invalid("amountCents");
        if (draft.note() != null && draft.note().length() > 1000) return invalid("note");
        return null;
    }
    private static AppError invalid(String field) { return new AppError(ErrorCode.VALIDATION, "Invalid settlement details.", field, false); }
    public static long remaining(LedgerSnapshot ledger, String payerId, String payeeId, String removingId) {
        // Validate/project the recorded ledger first. Removing a payment can temporarily
        // exceed the contract bound, even when both the recorded and replacement ledgers
        // are valid. A bounded pair plus one bounded payment fits exactly in a long.
        long debt = 0;
        for (PairBalance balance : BalanceCalculator.calculate(ledger, payerId).pairs()) {
            if (balance.otherUserId().equals(payeeId)) debt = -balance.netCents();
        }
        for (Settlement payment : ledger.settlements()) {
            if (!payment.settlementId().equals(removingId)) continue;
            if (payment.payerId().equals(payerId) && payment.payeeId().equals(payeeId))
                debt = Math.addExact(debt, payment.amountCents());
            else if (payment.payeeId().equals(payerId) && payment.payerId().equals(payeeId))
                debt = Math.subtractExact(debt, payment.amountCents());
        }
        return Math.max(0, debt);
    }
}
