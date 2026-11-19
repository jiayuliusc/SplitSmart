package edu.usc.splitsmart.balances;

import static org.junit.Assert.assertThrows;

import edu.usc.splitsmart.domain.calculation.BalanceCalculator;
import edu.usc.splitsmart.domain.contract.ContractVersion;
import edu.usc.splitsmart.domain.model.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Feature 4 white-box tests: each row independently exercises a ledger validation branch. */
@RunWith(Parameterized.class)
public final class BalanceValidationTest {
    private static final long MAX = ContractVersion.MAX_ABSOLUTE_CENTS;
    private static final List<String> MEMBERS = List.of("A", "B", "C");
    private static final List<String> AB = List.of("A", "B");
    private static final List<ExpenseShare> SHARES = List.of(share("A", 100), share("B", 100));
    private final Supplier<LedgerSnapshot> input;

    public BalanceValidationTest(String name, Supplier<LedgerSnapshot> input) { this.input = input; }

    private static ExpenseShare share(String uid, long cents) { return new ExpenseShare(uid, cents, null); }
    private static Expense expense(String group, String currency, String payer, String creator,
            long cents, List<String> participants, List<ExpenseShare> shares) {
        return new Expense("e", group, "Lunch", cents, currency, creator, payer, participants,
                SplitMethod.EXACT, Category.FOOD, null, LocalDate.of(2026, 11, 18),
                Instant.EPOCH, Instant.EPOCH, shares);
    }
    private static LedgerSnapshot expenses(Expense... entries) {
        return new LedgerSnapshot("g", 1, MEMBERS, List.of(entries), List.of());
    }
    private static LedgerSnapshot payment(String group, String currency, String payer, String payee,
            String creator, long cents, boolean duplicate) {
        Settlement s = new Settlement("s", group, creator, payer, payee, cents, currency, null,
                LocalDate.of(2026, 11, 18), Instant.EPOCH, Instant.EPOCH);
        return new LedgerSnapshot("g", 1, MEMBERS, List.of(), duplicate ? List.of(s, s) : List.of(s));
    }
    private static Object[] row(String name, Supplier<LedgerSnapshot> input) { return new Object[]{name, input}; }

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> invalidLedgers() {
        return List.of(
            row("WB01_duplicateMembers", () -> new LedgerSnapshot("g", 1, List.of("A", "A"), List.of(), List.of())),
            row("WB02_emptyMemberId", () -> new LedgerSnapshot("g", 1, List.of("A", ""), List.of(), List.of())),
            row("WB03_viewerNotMember", () -> new LedgerSnapshot("g", 1, List.of("B", "C"), List.of(), List.of())),
            row("WB04_revisionBelowMinimum", () -> new LedgerSnapshot("g", 0, MEMBERS, List.of(), List.of())),
            row("WB05_revisionAboveMaximum", () -> new LedgerSnapshot("g", ContractVersion.MAX_REVISION + 1, MEMBERS, List.of(), List.of())),
            row("WB06_duplicateExpense", () -> { Expense e = expense("g", "USD", "A", "A", 200, AB, SHARES); return expenses(e, e); }),
            row("WB07_foreignGroupExpense", () -> expenses(expense("other", "USD", "A", "A", 200, AB, SHARES))),
            row("WB08_foreignCurrencyExpense", () -> expenses(expense("g", "EUR", "A", "A", 200, AB, SHARES))),
            row("WB09_outsiderPayer", () -> expenses(expense("g", "USD", "X", "A", 200, AB, SHARES))),
            row("WB10_outsiderCreator", () -> expenses(expense("g", "USD", "A", "X", 200, AB, SHARES))),
            row("WB11_zeroExpense", () -> expenses(expense("g", "USD", "A", "A", 0, AB, SHARES))),
            row("WB12_expenseAboveMaximum", () -> expenses(expense("g", "USD", "A", "A", MAX + 1, AB, SHARES))),
            row("WB13_singleParticipant", () -> expenses(expense("g", "USD", "A", "A", 100, List.of("A"), List.of(share("A", 100))))),
            row("WB14_duplicateParticipant", () -> expenses(expense("g", "USD", "A", "A", 200, List.of("A", "B", "B"), SHARES))),
            row("WB15_payerNotParticipant", () -> expenses(expense("g", "USD", "C", "A", 200, AB, SHARES))),
            row("WB16_outsiderParticipant", () -> expenses(expense("g", "USD", "A", "A", 200, List.of("A", "X"), List.of(share("A", 100), share("X", 100))))),
            row("WB17_shareOutsideParticipants", () -> expenses(expense("g", "USD", "A", "A", 200, AB, List.of(share("A", 100), share("C", 100))))),
            row("WB18_duplicateShare", () -> expenses(expense("g", "USD", "A", "A", 200, AB, List.of(share("A", 100), share("A", 100))))),
            row("WB19_negativeShare", () -> expenses(expense("g", "USD", "A", "A", 200, AB, List.of(share("A", -1), share("B", 201))))),
            row("WB20_shareAboveMaximum", () -> expenses(expense("g", "USD", "A", "A", 200, AB, List.of(share("A", MAX + 1), share("B", 0))))),
            row("WB21_missingShare", () -> expenses(expense("g", "USD", "A", "A", 200, AB, List.of(share("A", 200))))),
            row("WB22_mismatchedShareTotal", () -> expenses(expense("g", "USD", "A", "A", 200, AB, List.of(share("A", 100), share("B", 99))))),
            row("WB23_duplicateSettlement", () -> payment("g", "USD", "B", "A", "B", 100, true)),
            row("WB24_foreignGroupSettlement", () -> payment("other", "USD", "B", "A", "B", 100, false)),
            row("WB25_foreignCurrencySettlement", () -> payment("g", "EUR", "B", "A", "B", 100, false)),
            row("WB26_outsiderSettlementPayer", () -> payment("g", "USD", "X", "A", "B", 100, false)),
            row("WB27_outsiderSettlementPayee", () -> payment("g", "USD", "B", "X", "B", 100, false)),
            row("WB28_outsiderSettlementCreator", () -> payment("g", "USD", "B", "A", "X", 100, false)),
            row("WB29_selfSettlement", () -> payment("g", "USD", "A", "A", "A", 100, false)),
            row("WB30_zeroSettlement", () -> payment("g", "USD", "B", "A", "B", 0, false)),
            row("WB31_negativeSettlement", () -> payment("g", "USD", "B", "A", "B", -1, false)),
            row("WB32_settlementAboveMaximum", () -> payment("g", "USD", "B", "A", "B", MAX + 1, false))
        );
    }

    @Test public void rejectsMalformedLedger() {
        // Construct outside assertThrows: a model-constructor failure must not masquerade as calculator coverage.
        LedgerSnapshot ledger = input.get();
        assertThrows(IllegalArgumentException.class, () -> BalanceCalculator.calculate(ledger, "A"));
    }
}
