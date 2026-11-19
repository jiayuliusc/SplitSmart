package edu.usc.splitsmart.expense;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;

import edu.usc.splitsmart.data.repository.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.domain.validation.ExpenseValidation;
import edu.usc.splitsmart.presentation.expenses.ExpenseViewModel;
import edu.usc.splitsmart.service.ExpenseService;
import edu.usc.splitsmart.service.expenses.DefaultExpenseService;
import edu.usc.splitsmart.util.PendingOperation;

public final class ExpenseFeatureWhiteBoxTest {
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private final Executor executor = queue::add;

    private void drain() {
        while (!queue.isEmpty()) {
            queue.remove().run();
        }
    }

    private static final class Result<T> implements Callback<T> {
        T value;
        AppError error;
        int calls;

        @Override
        public void onSuccess(T value) {
            this.value = value;
            calls++;
        }

        @Override
        public void onError(AppError error) {
            this.error = error;
            calls++;
        }
    }

    private static final class FakeExpenseRepository implements ExpenseRepository, LedgerRepository {
        final Map<String, Expense> expenses = new HashMap<>();
        final List<String> memberIds = List.of("bob", "carol", "david");
        long revision = 1;
        int writeCount = 0;
        String currentActorUid = "bob";

        @Override
        public AsyncOperation getExpense(String groupId, String expenseId, ReadPolicy policy, Callback<ReadResult<Expense>> callback) {
            Expense expense = expenses.get(expenseId);
            if (expense == null || !expense.groupId().equals(groupId)) {
                callback.onError(new AppError(ErrorCode.NOT_FOUND, "Expense not found", null, false));
            } else {
                callback.onSuccess(new ReadResult<>(expense, ReadSource.SERVER));
            }
            return () -> {};
        }

        @Override
        public AsyncOperation getLedger(String groupId, ReadPolicy policy, Callback<ReadResult<LedgerSnapshot>> callback) {
            List<Expense> list = new ArrayList<>(expenses.values());
            callback.onSuccess(new ReadResult<>(new LedgerSnapshot(groupId, revision, memberIds, list, List.of()), ReadSource.SERVER));
            return () -> {};
        }

        @Override
        public AsyncOperation createExpense(String groupId, long expectedRevision, ExpenseDraft draft, LocalDate localDate, String requestId, Callback<MutationResult<Expense>> callback) {
            if (expectedRevision != revision) {
                callback.onError(new AppError(ErrorCode.CONFLICT, "Revision conflict", null, false));
                return () -> {};
            }
            writeCount++;
            String expenseId = "exp-" + writeCount;
            Instant now = Instant.now();
            Expense expense = new Expense(
                expenseId, groupId, draft.title(), draft.amountCents(), "USD",
                currentActorUid, draft.payerId(), draft.participantIds(),
                draft.splitMethod(), draft.category(), draft.note(), localDate, now, now,
                List.of(
                    new ExpenseShare("bob", draft.amountCents() / 2, null),
                    new ExpenseShare("carol", draft.amountCents() / 2, null)
                )
            );
            expenses.put(expenseId, expense);
            revision++;
            callback.onSuccess(new MutationResult<>(requestId, revision, expense));
            return () -> {};
        }

        @Override
        public AsyncOperation updateExpense(String groupId, long expectedRevision, String expenseId, ExpenseDraft draft, String requestId, Callback<MutationResult<Expense>> callback) {
            Expense existing = expenses.get(expenseId);
            if (existing == null) {
                callback.onError(new AppError(ErrorCode.NOT_FOUND, "Expense not found", null, false));
                return () -> {};
            }
            if (!existing.creatorId().equals(currentActorUid)) {
                callback.onError(new AppError(ErrorCode.PERMISSION_DENIED, "Only creator can edit", null, false));
                return () -> {};
            }
            if (expectedRevision != revision) {
                callback.onError(new AppError(ErrorCode.CONFLICT, "Revision conflict", null, false));
                return () -> {};
            }
            writeCount++;
            Instant now = Instant.now();
            Expense updated = new Expense(
                existing.expenseId(), existing.groupId(), draft.title(), draft.amountCents(), existing.currency(),
                existing.creatorId(), draft.payerId(), draft.participantIds(),
                draft.splitMethod(), draft.category(), draft.note(), existing.localDate(), existing.createdAt(), now,
                List.of(
                    new ExpenseShare("bob", draft.amountCents() / 2, null),
                    new ExpenseShare("david", draft.amountCents() / 2, null)
                )
            );
            expenses.put(expenseId, updated);
            revision++;
            callback.onSuccess(new MutationResult<>(requestId, revision, updated));
            return () -> {};
        }

        @Override
        public AsyncOperation deleteExpense(String groupId, long expectedRevision, String expenseId, String requestId, Callback<MutationResult<DeletedRecord>> callback) {
            Expense existing = expenses.get(expenseId);
            if (existing == null) {
                callback.onError(new AppError(ErrorCode.NOT_FOUND, "Expense not found", null, false));
                return () -> {};
            }
            if (!existing.creatorId().equals(currentActorUid)) {
                callback.onError(new AppError(ErrorCode.PERMISSION_DENIED, "Only creator can delete", null, false));
                return () -> {};
            }
            if (expectedRevision != revision) {
                callback.onError(new AppError(ErrorCode.CONFLICT, "Revision conflict", null, false));
                return () -> {};
            }
            writeCount++;
            expenses.remove(expenseId);
            revision++;
            callback.onSuccess(new MutationResult<>(requestId, revision, new DeletedRecord(expenseId)));
            return () -> {};
        }
    }

    private FakeExpenseRepository repo;
    private DefaultExpenseService service;

    @Before
    public void setup() {
        repo = new FakeExpenseRepository();
        service = new DefaultExpenseService(repo, repo, executor);
    }

    /** W6 - Expense fields and automatic date */
    @Test
    public void expenseFieldsAndDateTest() {
        LocalDate fixedDate = LocalDate.of(2026, 11, 18);
        ExpenseDraft validDraft = new ExpenseDraft(
            "Movie", 4500, "carol", List.of("bob", "carol"),
            SplitMethod.EQUAL, Category.TRANSPORTATION, "tickets for Godzilla", List.of()
        );

        // Valid expense creation with fixed local date
        Result<MutationResult<Expense>> result = new Result<>();
        service.createExpense("g1", 1, validDraft, fixedDate, "req-1", result);
        drain();

        assertNull(result.error);
        assertEquals(1, result.calls);
        Expense created = result.value.value();
        assertEquals("Movie", created.title());
        assertEquals(4500, created.amountCents());
        assertEquals("carol", created.payerId());
        assertEquals(Category.TRANSPORTATION, created.category());
        assertEquals("tickets for Godzilla", created.note());
        assertEquals(fixedDate, created.localDate());
        assertEquals(1, repo.writeCount);

        // Empty and whitespace-only titles fail validation without a write
        int writesBefore = repo.writeCount;
        for (String invalidTitle : List.of("", "   ")) {
            ExpenseDraft badTitleDraft = new ExpenseDraft(
                invalidTitle, 4500, "carol", List.of("bob", "carol"),
                SplitMethod.EQUAL, Category.TRANSPORTATION, "note", List.of()
            );
            Result<MutationResult<Expense>> badResult = new Result<>();
            service.createExpense("g1", repo.revision, badTitleDraft, fixedDate, "req-bad", badResult);
            drain();
            assertNotNull(badResult.error);
            assertEquals(ErrorCode.VALIDATION, badResult.error.code());
            assertEquals(writesBefore, repo.writeCount);
        }

        // Editing preserves the original creation date
        ExpenseDraft updateDraft = new ExpenseDraft(
            "Movie Night", 5000, "carol", List.of("bob", "carol"),
            SplitMethod.EQUAL, Category.ENTERTAINMENT, "updated note", List.of()
        );
        Result<MutationResult<Expense>> updateResult = new Result<>();
        service.updateExpense("g1", repo.revision, created.expenseId(), updateDraft, "req-2", updateResult);
        drain();

        assertNull(updateResult.error);
        Expense updated = updateResult.value.value();
        assertEquals("Movie Night", updated.title());
        assertEquals(5000, updated.amountCents());
        assertEquals(fixedDate, updated.localDate()); // Preserves original creation date!
    }

    /** W7 - Default expense fields and categories */
    @Test
    public void defaultExpenseCategoryTest() {
        // Test production default initializations: Other category, current user payer, Equal split, empty note
        Category defaultCategory = Category.OTHER;
        String defaultPayer = repo.currentActorUid; // "bob"
        SplitMethod defaultSplit = SplitMethod.EQUAL;
        String defaultNote = null;

        ExpenseDraft defaultDraft = new ExpenseDraft(
            "Default Expense", 2000, defaultPayer, List.of("bob", "david"),
            defaultSplit, defaultCategory, defaultNote, List.of()
        );

        AppError err = ExpenseValidation.expense(defaultDraft);
        assertNull("Defaults must form a valid expense", err);

        Result<MutationResult<Expense>> result = new Result<>();
        service.createExpense("g1", 1, defaultDraft, LocalDate.now(), "req-def", result);
        drain();
        assertNull(result.error);
        assertEquals(Category.OTHER, result.value.value().category());

        // Test each supported explicit category is preserved
        for (Category cat : Category.values()) {
            ExpenseDraft catDraft = new ExpenseDraft(
                "Category " + cat.name(), 1000, "bob", List.of("bob", "david"),
                SplitMethod.EQUAL, cat, "note", List.of()
            );
            Result<MutationResult<Expense>> catResult = new Result<>();
            service.createExpense("g1", repo.revision, catDraft, LocalDate.now(), "req-cat-" + cat.name(), catResult);
            drain();
            assertNull(catResult.error);
            assertEquals(cat, catResult.value.value().category());
        }
    }

    /** W8 - Payer and participant validation */
    @Test
    public void payerAndParticipantTest() {
        List<String> groupMembers = List.of("bob", "carol", "david");

        // Reject absent payer
        ExpenseDraft noPayer = new ExpenseDraft("Title", 1000, "", List.of("bob", "carol"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        assertNotNull(ExpenseValidation.expense(noPayer, groupMembers));

        // Reject payer outside group
        ExpenseDraft payerOutside = new ExpenseDraft("Title", 1000, "eve", List.of("eve", "bob"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        AppError errPayerOutside = ExpenseValidation.expense(payerOutside, groupMembers);
        assertNotNull(errPayerOutside);
        assertEquals("payerId", errPayerOutside.field());

        // Reject participant outside group
        ExpenseDraft partOutside = new ExpenseDraft("Title", 1000, "bob", List.of("bob", "eve"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        AppError errPartOutside = ExpenseValidation.expense(partOutside, groupMembers);
        assertNotNull(errPartOutside);
        assertEquals("participantIds", errPartOutside.field());

        // Reject fewer than two distinct participants (1 participant)
        ExpenseDraft onePart = new ExpenseDraft("Title", 1000, "bob", List.of("bob"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        assertNotNull(ExpenseValidation.expense(onePart, groupMembers));

        // Reject duplicate-only participant list
        ExpenseDraft dupOnly = new ExpenseDraft("Title", 1000, "bob", List.of("bob", "bob"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        assertNotNull(ExpenseValidation.expense(dupOnly, groupMembers));

        // Reject payer absent from participants
        ExpenseDraft payerAbsent = new ExpenseDraft("Title", 1000, "bob", List.of("carol", "david"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        assertNotNull(ExpenseValidation.expense(payerAbsent, groupMembers));

        // Valid payer with two distinct group participants succeeds
        ExpenseDraft valid = new ExpenseDraft("Title", 1000, "bob", List.of("bob", "carol"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        assertNull(ExpenseValidation.expense(valid, groupMembers));

        // Service reject returns VALIDATION error and never writes
        int writesBefore = repo.writeCount;
        Result<MutationResult<Expense>> serviceResult = new Result<>();
        service.createExpense("g1", repo.revision, dupOnly, LocalDate.now(), "req-dup", serviceResult);
        drain();
        assertNotNull(serviceResult.error);
        assertEquals(ErrorCode.VALIDATION, serviceResult.error.code());
        assertEquals(writesBefore, repo.writeCount);
    }

    /** W9 - Expense amount validation */
    @Test
    public void expenseAmountTest() {
        int writesBefore = repo.writeCount;

        // Reject blank amount ""
        try {
            long cents = ExpenseViewModel.parseDollarsToCents("");
            assertNotNull(ExpenseValidation.amount(cents));
        } catch (RuntimeException expected) { }

        // Reject 0 amount
        long zeroCents = ExpenseViewModel.parseDollarsToCents("0");
        assertNotNull(ExpenseValidation.amount(zeroCents));

        // Reject -12 amount
        long negCents = ExpenseViewModel.parseDollarsToCents("-12");
        assertNotNull(ExpenseValidation.amount(negCents));

        // Reject 12.345 (more than 2 decimal places)
        try {
            ExpenseViewModel.parseDollarsToCents("12.345");
            fail("12.345 must throw invalid scale exception");
        } catch (RuntimeException expected) { }

        assertEquals("Invalid amount inputs must cause zero writes", writesBefore, repo.writeCount);

        // Verify 0.01 becomes 1 cent
        assertEquals(1L, ExpenseViewModel.parseDollarsToCents("0.01"));
        assertNull(ExpenseValidation.amount(1L));

        // Verify 12.34 becomes 1234 cents
        assertEquals(1234L, ExpenseViewModel.parseDollarsToCents("12.34"));
        assertNull(ExpenseValidation.amount(1234L));

        // Verify 12345 becomes 1234500 cents
        assertEquals(1234500L, ExpenseViewModel.parseDollarsToCents("12345"));
        assertNull(ExpenseValidation.amount(1234500L));

        // Overflow fails safely without crash or write
        try {
            ExpenseViewModel.parseDollarsToCents("99999999999999999999999999999999");
            fail("Excessive amount must throw overflow exception");
        } catch (RuntimeException expected) { }
        assertEquals(writesBefore, repo.writeCount);
    }

    /** W10 - Creator authorization and edit validation */
    @Test
    public void expenseCreatorPermissionTest() {
        // Seed expense created by Bob and paid by Carol
        repo.currentActorUid = "bob";
        ExpenseDraft draft = new ExpenseDraft("Dinner", 4000, "carol", List.of("bob", "carol"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        Result<MutationResult<Expense>> seedResult = new Result<>();
        service.createExpense("g1", repo.revision, draft, LocalDate.now(), "seed-1", seedResult);
        drain();
        String expId = seedResult.value.value().expenseId();
        long currentRev = repo.revision;

        ExpenseDraft editDraft = new ExpenseDraft("Dinner Edit", 5000, "carol", List.of("bob", "carol"), SplitMethod.EQUAL, Category.FOOD, null, List.of());

        // Edit as Carol must fail with PERMISSION_DENIED without changes/writes
        repo.currentActorUid = "carol";
        Result<MutationResult<Expense>> carolEdit = new Result<>();
        service.updateExpense("g1", currentRev, expId, editDraft, "req-carol-edit", carolEdit);
        drain();
        assertNotNull(carolEdit.error);
        assertEquals(ErrorCode.PERMISSION_DENIED, carolEdit.error.code());

        // Delete as Carol must fail with PERMISSION_DENIED
        Result<MutationResult<DeletedRecord>> carolDelete = new Result<>();
        service.deleteExpense("g1", currentRev, expId, "req-carol-del", carolDelete);
        drain();
        assertNotNull(carolDelete.error);
        assertEquals(ErrorCode.PERMISSION_DENIED, carolDelete.error.code());

        // Edit/Delete as David must fail with PERMISSION_DENIED
        repo.currentActorUid = "david";
        Result<MutationResult<Expense>> davidEdit = new Result<>();
        service.updateExpense("g1", currentRev, expId, editDraft, "req-david-edit", davidEdit);
        drain();
        assertNotNull(davidEdit.error);
        assertEquals(ErrorCode.PERMISSION_DENIED, davidEdit.error.code());

        // Bob's edit to amount zero must fail validation without an update
        repo.currentActorUid = "bob";
        ExpenseDraft zeroAmountDraft = new ExpenseDraft("Dinner", 0, "carol", List.of("bob", "carol"), SplitMethod.EQUAL, Category.FOOD, null, List.of());
        Result<MutationResult<Expense>> zeroEdit = new Result<>();
        service.updateExpense("g1", currentRev, expId, zeroAmountDraft, "req-zero-edit", zeroEdit);
        drain();
        assertNotNull(zeroEdit.error);
        assertEquals(ErrorCode.VALIDATION, zeroEdit.error.code());

        // Bob's valid edit must succeed
        Result<MutationResult<Expense>> bobEdit = new Result<>();
        service.updateExpense("g1", currentRev, expId, editDraft, "req-bob-edit", bobEdit);
        drain();
        assertNull(bobEdit.error);
        assertEquals("Dinner Edit", bobEdit.value.value().title());

        // Bob's valid deletion must succeed
        Result<MutationResult<DeletedRecord>> bobDelete = new Result<>();
        service.deleteExpense("g1", repo.revision, expId, "req-bob-del", bobDelete);
        drain();
        assertNull(bobDelete.error);
        assertEquals(expId, bobDelete.value.value().recordId());
    }

    /** W11 - Latest expense state and persistence interactions */
    @Test
    public void repoExpensePersistenceTest() {
        repo.currentActorUid = "bob";
        ExpenseDraft initialDraft = new ExpenseDraft("Initial", 3000, "bob", List.of("bob", "carol"), SplitMethod.EQUAL, Category.FOOD, "note 1", List.of());

        // 1. Create
        Result<MutationResult<Expense>> createRes = new Result<>();
        service.createExpense("g1", repo.revision, initialDraft, LocalDate.of(2026, 11, 1), "req-create", createRes);
        drain();
        assertNull(createRes.error);
        Expense created = createRes.value.value();
        String id = created.expenseId();

        // 2. Read
        Result<ReadResult<Expense>> readRes1 = new Result<>();
        service.getExpense("g1", id, ReadPolicy.FRESH, readRes1);
        drain();
        assertNull(readRes1.error);
        assertEquals("Initial", readRes1.value.value().title());
        assertEquals("bob", readRes1.value.value().creatorId());

        // 3. Update
        ExpenseDraft updatedDraft = new ExpenseDraft("Updated Title", 4500, "carol", List.of("bob", "carol"), SplitMethod.EQUAL, Category.HOUSING, "note 2", List.of());
        Result<MutationResult<Expense>> updateRes = new Result<>();
        service.updateExpense("g1", repo.revision, id, updatedDraft, "req-update", updateRes);
        drain();
        assertNull(updateRes.error);

        // 4. Read using fresh service with same repository reads latest data
        DefaultExpenseService freshService = new DefaultExpenseService(repo, repo, executor);
        Result<ReadResult<Expense>> readRes2 = new Result<>();
        freshService.getExpense("g1", id, ReadPolicy.FRESH, readRes2);
        drain();
        assertNull(readRes2.error);
        Expense readExpense = readRes2.value.value();
        assertEquals(id, readExpense.expenseId());
        assertEquals("bob", readExpense.creatorId()); // Preserves creator!
        assertEquals("Updated Title", readExpense.title());
        assertEquals(4500, readExpense.amountCents());
        assertEquals("carol", readExpense.payerId());

        // 5. Delete
        Result<MutationResult<DeletedRecord>> deleteRes = new Result<>();
        service.deleteExpense("g1", repo.revision, id, "req-delete", deleteRes);
        drain();
        assertNull(deleteRes.error);

        // 6. Read after deletion returns absent (NOT_FOUND)
        Result<ReadResult<Expense>> readRes3 = new Result<>();
        freshService.getExpense("g1", id, ReadPolicy.FRESH, readRes3);
        drain();
        assertNotNull(readRes3.error);
        assertEquals(ErrorCode.NOT_FOUND, readRes3.error.code());
    }
}
