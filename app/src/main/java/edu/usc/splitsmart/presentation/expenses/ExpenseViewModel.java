package edu.usc.splitsmart.presentation.expenses;

import androidx.lifecycle.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.*;

public final class ExpenseViewModel extends FeatureViewModel {
    private final ExpenseService expenses;
    private final GroupService groups;
    private final String groupId;
    private String selectedExpenseId;
    public final MutableLiveData<Group> group = new MutableLiveData<>();
    public final MutableLiveData<List<MemberProfile>> members = new MutableLiveData<>(List.of());
    public final MutableLiveData<LedgerSnapshot> ledger = new MutableLiveData<>();
    public final MutableLiveData<Expense> expense = new MutableLiveData<>();
    public final MutableLiveData<MutationResult<Expense>> created = new MutableLiveData<>();
    public final MutableLiveData<MutationResult<DeletedRecord>> deleted = new MutableLiveData<>();
    public final MutableLiveData<List<ExpenseShare>> splitPreview = new MutableLiveData<>(List.of());
    public final MutableLiveData<String> splitValidation = new MutableLiveData<>("");
    public final MutableLiveData<Boolean> splitValid = new MutableLiveData<>(false);
    private int previewGeneration;

    public ExpenseViewModel(SavedStateHandle state, ExpenseService expenses, GroupService groups, String groupId) {
        super(state);
        this.expenses = expenses;
        this.groups = groups;
        this.groupId = groupId;
    }

    public void loadGroup() {
        if (isBusy()) return;
        start();
        track(expenses.getLedger(groupId, ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<LedgerSnapshot> result) {
                ledger.setValue(result.value());
                track(groups.getMemberProfiles(groupId, ReadPolicy.FRESH, new Callback<>() {
                    @Override public void onSuccess(ReadResult<List<MemberProfile>> profiles) {
                        members.setValue(profiles.value());
                        if (selectedExpenseId != null) {
                            Expense found = null;
                            for (Expense candidate : result.value().expenses()) {
                                if (candidate.expenseId().equals(selectedExpenseId)) { found = candidate; break; }
                            }
                            expense.setValue(found);
                            if (found == null) {
                                failed(new AppError(ErrorCode.NOT_FOUND, "This expense no longer exists.", null, false));
                                return;
                            }
                        }
                        busy.setValue(false);
                    }
                    @Override public void onError(AppError error) { readFailed(error); }
                }));
            }
            @Override public void onError(AppError error) { readFailed(error); }
        }));
    }
    private void readFailed(AppError error) {
        ledger.setValue(null); members.setValue(List.of()); expense.setValue(null);
        // A read cannot establish the outcome of an earlier ambiguous write.
        if (pending()) { message.setValue(""); this.error.setValue(error); busy.setValue(false); }
        else failed(error);
    }
    public void refreshLedger() { loadGroup(); }
    public void loadExpense(String expenseId) {
        selectedExpenseId = expenseId;
        loadGroup();
    }
    public static long parseDollarsToCents(String input) {
        if (input == null || input.trim().isEmpty()) return 0L;
        BigDecimal value = new BigDecimal(input.trim());
        if (value.scale() > 2) throw new IllegalArgumentException("Maximum 2 decimal places.");
        return value.movePointRight(2).longValueExact();
    }
    public static int parsePercentageToBasisPoints(String input) {
        if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException("Enter a percentage.");
        BigDecimal value = new BigDecimal(input.trim());
        if (value.scale() > 2) throw new IllegalArgumentException("Maximum 2 decimal places.");
        return value.movePointRight(2).intValueExact();
    }
    public void clearSplitPreview(String message) {
        ++previewGeneration; splitPreview.setValue(List.of());
        splitValidation.setValue(message == null ? "" : message); splitValid.setValue(false);
    }
    public void previewSplit(String title, String amount, String payerId, List<String> participantIds,
                             SplitMethod method, List<SplitInput> inputs, Category category, String note) {
        int request = ++previewGeneration;
        ExpenseDraft draft;
        try { draft = draft(title, amount, payerId, participantIds, method, inputs, category, note); }
        catch (RuntimeException error) { clearSplitPreview(error.getMessage()); return; }
        splitValid.setValue(false); splitValidation.setValue("");
        track(expenses.previewSplit(draft, new Callback<>() {
            @Override public void onSuccess(List<ExpenseShare> shares) {
                if (request != previewGeneration) return;
                splitPreview.setValue(shares); splitValidation.setValue(""); splitValid.setValue(true);
            }
            @Override public void onError(AppError error) {
                if (request != previewGeneration) return;
                splitPreview.setValue(List.of()); splitValidation.setValue(error.message()); splitValid.setValue(false);
            }
        }));
    }
    public void create(String title, String amount, String payerId, List<String> participantIds, Category category, String note) {
        submit(null, title, amount, payerId, participantIds, category, note);
    }
    public void create(String title, String amount, String payerId, List<String> participantIds,
                       SplitMethod method, List<SplitInput> inputs, Category category, String note) {
        if (isBusy() || pending() || ledger.getValue() == null) return;
        ExpenseDraft draft;
        try { draft = draft(title, amount, payerId, participantIds, method, inputs, category, note); }
        catch (RuntimeException invalid) {
            failed(new AppError(ErrorCode.VALIDATION, invalid.getMessage(), "splitInputs", false)); return;
        }
        ExpenseIntent mutation = new ExpenseIntent("create", ledger.getValue().revision(), null, LocalDate.now(), draft);
        String key = intent(mutation.operation(), mutation.save().toArray(new String[0]));
        replay(mutation, key);
    }
    public void update(String expenseId, String title, String amount, String payerId, List<String> participantIds, Category category, String note) {
        submit(expenseId, title, amount, payerId, participantIds, category, note);
    }
    private void submit(String expenseId, String title, String amount, String payerId, List<String> participantIds, Category category, String note) {
        if (isBusy() || pending() || ledger.getValue() == null) return;
        long cents;
        try { cents = parseDollarsToCents(amount); }
        catch (RuntimeException invalid) {
            failed(new AppError(ErrorCode.VALIDATION, "Enter a valid dollar amount (e.g. 25.50).", "amountCents", false)); return;
        }
        ExpenseDraft original = originalDraft();
        SplitMethod method = expenseId != null && original != null ? original.splitMethod() : SplitMethod.EQUAL;
        if (method != SplitMethod.EQUAL && (!participantIds.equals(original.participantIds())
            || (method == SplitMethod.EXACT && cents != original.amountCents()))) {
            failed(new AppError(ErrorCode.VALIDATION, "Keep the original exact amounts and split participants for this expense.", null, false)); return;
        }
        ExpenseDraft draft = new ExpenseDraft(title.trim(), cents, payerId, participantIds, method, category,
            note == null || note.trim().isEmpty() ? null : note.trim(), method == SplitMethod.EQUAL ? List.of() : original.splitInputs());
        ExpenseIntent mutation = new ExpenseIntent(expenseId == null ? "create" : "update", ledger.getValue().revision(),
            expenseId, expenseId == null ? LocalDate.now() : null, draft);
        String key = intent(mutation.operation(), mutation.save().toArray(new String[0]));
        replay(mutation, key);
    }
    private static ExpenseDraft draft(String title, String amount, String payerId,
                                      List<String> participants, SplitMethod method, List<SplitInput> inputs,
                                      Category category, String note) {
        if (payerId == null || participants == null || method == null || inputs == null || category == null) {
            throw new IllegalArgumentException("Complete the payer, participants, split method, and category.");
        }
        long cents;
        try { cents = parseDollarsToCents(amount); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Enter a valid dollar amount (e.g. 25.50)."); }
        return new ExpenseDraft(title == null ? "" : title.trim(), cents, payerId, participants, method, category,
            note == null || note.trim().isEmpty() ? null : note.trim(), inputs);
    }
    /** Initial values are saved only once; fresh reads after a conflict never overwrite the draft. */
    public void initializeEditor(Expense expense) {
        if (saved.contains("draft:initialized")) return;
        ExpenseDraft draft = ExpenseIntent.draftFrom(expense);
        saved.set("draft:original", new ExpenseIntent("update", 0, expense.expenseId(), null, draft).save());
        editDraft("title", draft.title());
        editDraft("amount", BigDecimal.valueOf(draft.amountCents(), 2).toPlainString());
        editDraft("payer", draft.payerId());
        editDraft("participants", String.join(",", draft.participantIds()));
        editDraft("category", draft.category().name());
        editDraft("note", draft.note() == null ? "" : draft.note());
        editDraft("splitMethod", draft.splitMethod().name());
        for (SplitInput input : draft.splitInputs()) {
            if (input.amountCents() != null) editDraft("split:EXACT:" + input.participantId(),
                BigDecimal.valueOf(input.amountCents(), 2).toPlainString());
            if (input.basisPoints() != null) editDraft("split:PERCENTAGE:" + input.participantId(),
                BigDecimal.valueOf(input.basisPoints(), 2).stripTrailingZeros().toPlainString());
        }
        editDraft("initialized", "true");
    }
    public ExpenseDraft originalDraft() {
        ArrayList<String> fields = saved.get("draft:original");
        return fields == null ? null : ExpenseIntent.restore(fields).draft();
    }
    public void delete(String expenseId) {
        if (isBusy() || pending() || ledger.getValue() == null) return;
        ExpenseIntent mutation = new ExpenseIntent("delete", ledger.getValue().revision(), expenseId, null, null);
        String key = intent("delete", mutation.save().toArray(new String[0]));
        replay(mutation, key);
    }
    private void replay(ExpenseIntent mutation, String requestId) {
        start();
        if ("delete".equals(mutation.operation())) {
            track(expenses.deleteExpense(groupId, mutation.revision(), mutation.expenseId(), requestId, new Callback<>() {
                @Override public void onSuccess(MutationResult<DeletedRecord> result) { done("Expense deleted."); deleted.setValue(result); }
                @Override public void onError(AppError error) { mutationFailed(error); }
            }));
            return;
        }
        Callback<MutationResult<Expense>> callback = new Callback<>() {
            @Override public void onSuccess(MutationResult<Expense> result) { done("Expense saved."); created.setValue(result); }
            @Override public void onError(AppError error) { mutationFailed(error); }
        };
        // Validate and preview the exact same split payload that will be written.
        track(expenses.previewSplit(mutation.draft(), new Callback<>() {
            @Override public void onSuccess(List<ExpenseShare> shares) {
                track("create".equals(mutation.operation())
                    ? expenses.createExpense(groupId, mutation.revision(), mutation.draft(), mutation.date(), requestId, callback)
                    : expenses.updateExpense(groupId, mutation.revision(), mutation.expenseId(), mutation.draft(), requestId, callback));
            }
            @Override public void onError(AppError error) { mutationFailed(error); }
        }));
    }
    private void mutationFailed(AppError error) {
        failed(error);
        if (error.code() == ErrorCode.CONFLICT) {
            resolved();
            loadGroup();
            message.setValue("The group changed. Your draft is preserved. Review it and submit again.");
        }
    }
    public void retry() {
        if (isBusy()) return;
        if (pending()) replay(ExpenseIntent.restore(values()), requestId());
        else loadGroup();
    }
    @Override public void cancelOperations() {
        ++previewGeneration; super.cancelOperations(); ledger.setValue(null); expense.setValue(null); members.setValue(List.of());
    }
}
