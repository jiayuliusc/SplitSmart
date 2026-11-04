package edu.usc.splitsmart.presentation.expenses;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.SavedStateHandle;
import org.junit.*;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.service.*;
import static org.junit.Assert.*;

public class ExpenseViewModelTest {
    private final List<Object[]> writes = new ArrayList<>();
    private Callback<MutationResult<Expense>> mutation;
    private Callback<MutationResult<DeletedRecord>> deletion;
    private AppError readFailure;
    private long revision = 11;
    private SavedStateHandle state;
    @Before public void setup() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable action) { action.run(); }
            @Override public void postToMainThread(Runnable action) { action.run(); }
            @Override public boolean isMainThread() { return true; }
        });
        state = new SavedStateHandle();
    }
    @After public void teardown() { ArchTaskExecutor.getInstance().setDelegate(null); }
    @SuppressWarnings("unchecked") private ExpenseViewModel model(SavedStateHandle saved) {
        ExpenseService expenses = (ExpenseService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ExpenseService.class}, (proxy, method, args) -> {
            if (method.getName().equals("getLedger")) {
                Callback<ReadResult<LedgerSnapshot>> callback = (Callback<ReadResult<LedgerSnapshot>>)args[2];
                if (readFailure != null) callback.onError(readFailure);
                else callback.onSuccess(new ReadResult<>(new LedgerSnapshot("g", revision, List.of("a", "b"), List.of(), List.of()), ReadSource.SERVER));
            }
            else if (method.getName().equals("previewSplit")) ((Callback<List<ExpenseShare>>)args[1]).onSuccess(List.of());
            else if (method.getName().equals("createExpense") || method.getName().equals("updateExpense")) {
                writes.add(args.clone()); mutation = (Callback<MutationResult<Expense>>)args[args.length - 1];
            }
            else if (method.getName().equals("deleteExpense")) {
                writes.add(args.clone()); deletion = (Callback<MutationResult<DeletedRecord>>)args[args.length - 1];
            }
            else throw new AssertionError("Unexpected call: " + method.getName());
            return (AsyncOperation) () -> { };
        });
        GroupService groups = (GroupService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{GroupService.class}, (proxy, method, args) -> {
            assertEquals("getMemberProfiles", method.getName());
            ((Callback<ReadResult<List<MemberProfile>>>)args[2]).onSuccess(new ReadResult<>(List.of(), ReadSource.SERVER));
            return (AsyncOperation) () -> { };
        });
        return new ExpenseViewModel(saved, expenses, groups, "g");
    }
    @Test public void ambiguousCreateReplaysCompletePayloadAfterRecreationAndNewLedgerRead() {
        ExpenseViewModel first = model(state); first.loadGroup();
        first.create("Dinner", "12.34", "a", List.of("a", "b"), Category.OTHER, "note");
        mutation.onError(new AppError(ErrorCode.UNAVAILABLE, "Retry", null, true));
        assertTrue(first.pending());
        Map<String, Object> snapshot = new HashMap<>();
        for (String key : state.keys()) snapshot.put(key, state.get(key));
        revision = 99;
        ExpenseViewModel recreated = model(new SavedStateHandle(snapshot)); recreated.loadGroup();
        recreated.retry();
        assertEquals(2, writes.size());
        assertArrayEquals(Arrays.copyOf(writes.get(0), 5), Arrays.copyOf(writes.get(1), 5));
        assertEquals(11L, writes.get(1)[1]);
        assertEquals(LocalDate.now(), writes.get(1)[3]);
    }
    @Test public void failedRefreshDoesNotResolveAnAmbiguousMutation() {
        ExpenseViewModel model = model(state); model.loadGroup();
        model.create("Dinner", "12.34", "a", List.of("a", "b"), Category.OTHER, null);
        mutation.onError(new AppError(ErrorCode.UNAVAILABLE, "Unknown outcome", null, true));
        readFailure = new AppError(ErrorCode.NOT_FOUND, "Read failed", null, false);
        model.loadGroup();
        assertTrue(model.pending());
        model.retry();
        assertEquals(2, writes.size());
        assertArrayEquals(Arrays.copyOf(writes.get(0), 5), Arrays.copyOf(writes.get(1), 5));
    }
    @Test public void ambiguousDeleteReplaysOriginalLedgerRevisionAndRequest() {
        ExpenseViewModel model = model(state); model.loadGroup(); model.delete("expense-a");
        deletion.onError(new AppError(ErrorCode.INTERNAL, "Unknown outcome", null, false));
        assertTrue(model.pending()); revision = 88; model.loadGroup(); model.retry();
        assertArrayEquals(Arrays.copyOf(writes.get(0), 4), Arrays.copyOf(writes.get(1), 4));
        assertEquals(11L, writes.get(1)[1]); assertEquals("expense-a", writes.get(1)[2]);
    }
    @Test public void metadataUpdateRetainsExactSharesWhenRetried() {
        ExpenseViewModel model = model(state); model.loadGroup();
        Expense original = new Expense("e", "g", "Dinner", 1234, "USD", "a", "a", List.of("a", "b"), SplitMethod.EXACT,
            Category.OTHER, null, LocalDate.of(2025, 1, 2), java.time.Instant.EPOCH, java.time.Instant.EPOCH,
            List.of(new ExpenseShare("a", 1000, null), new ExpenseShare("b", 234, null)));
        model.initializeEditor(original);
        model.update("e", "Updated title", "12.34", "b", List.of("a", "b"), Category.OTHER, "new note");
        ExpenseDraft submitted = (ExpenseDraft) writes.get(0)[3];
        assertEquals(SplitMethod.EXACT, submitted.splitMethod());
        assertEquals(ExpenseIntent.draftFrom(original).splitInputs(), submitted.splitInputs());
        mutation.onError(new AppError(ErrorCode.DEADLINE_EXCEEDED, "Unknown outcome", null, true));
        model.retry();
        assertArrayEquals(Arrays.copyOf(writes.get(0), 5), Arrays.copyOf(writes.get(1), 5));
    }
    @Test public void conflictRefreshPreservesDraftAndRequiresExplicitNewSubmit() {
        ExpenseViewModel model = model(state); model.loadGroup(); model.editDraft("title", "User input");
        model.create("Dinner", "12.34", "a", List.of("a", "b"), Category.OTHER, "note");
        String oldRequest = (String) writes.get(0)[4];
        revision = 12;
        mutation.onError(new AppError(ErrorCode.CONFLICT, "Changed", null, false));
        assertFalse(model.pending()); assertEquals(12, model.ledger.getValue().revision());
        assertEquals("User input", model.draft("title", ""));
        assertTrue(model.message.getValue().contains("draft is preserved"));
        assertEquals(1, writes.size());
        model.create("User input", "12.34", "a", List.of("a", "b"), Category.OTHER, "note");
        assertNotEquals(oldRequest, writes.get(1)[4]); assertEquals(12L, writes.get(1)[1]);
    }
}
