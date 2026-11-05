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

    public final MutableLiveData<Group> group = new MutableLiveData<>();
    public final MutableLiveData<List<MemberProfile>> members = new MutableLiveData<>(List.of());
    public final MutableLiveData<LedgerSnapshot> ledger = new MutableLiveData<>();
    public final MutableLiveData<Expense> expense = new MutableLiveData<>();
    public final MutableLiveData<MutationResult<Expense>> created = new MutableLiveData<>();
    public final MutableLiveData<MutationResult<Expense>> updated = new MutableLiveData<>();
    public final MutableLiveData<MutationResult<DeletedRecord>> deleted = new MutableLiveData<>();

    public ExpenseViewModel(SavedStateHandle state, ExpenseService expenses, GroupService groups, String groupId) {
        super(state);
        this.expenses = expenses;
        this.groups = groups;
        this.groupId = groupId;
    }

    public void loadGroup() {
        if (isBusy()) return;
        start();
        track(groups.getGroup(groupId, ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<Group> val) {
                group.setValue(val.value());
                track(groups.getMemberProfiles(groupId, ReadPolicy.FRESH, new Callback<>() {
                    @Override public void onSuccess(ReadResult<List<MemberProfile>> result) {
                        members.setValue(result.value());
                        track(expenses.getLedger(groupId, ReadPolicy.FRESH, new Callback<>() {
                            @Override public void onSuccess(ReadResult<LedgerSnapshot> ledgerRes) {
                                ledger.setValue(ledgerRes.value());
                                busy.setValue(false);
                            }
                            @Override public void onError(AppError e) { failed(e); }
                        }));
                    }
                    @Override public void onError(AppError e) { failed(e); }
                }));
            }
            @Override public void onError(AppError e) { group.setValue(null); members.setValue(List.of()); failed(e); }
        }));
    }

    public void refreshLedger() {
        loadGroup();
    }

    public void loadExpense(String expenseId) {
        if (isBusy()) return;
        start();
        track(expenses.getExpense(groupId, expenseId, ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<Expense> result) {
                expense.setValue(result.value());
                busy.setValue(false);
            }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }

    private void updateGroupRevision(Long rev) {
        if (rev != null && group.getValue() != null) {
            Group g = group.getValue();
            group.setValue(new Group(g.groupId(), g.name(), g.description(), g.ownerId(), g.memberIds(), rev, g.status(), g.createdAt(), g.updatedAt()));
        }
    }

    public static long parseDollarsToCents(String input) throws IllegalArgumentException {
        if (input == null || input.trim().isEmpty()) return 0L;
        BigDecimal bd = new BigDecimal(input.trim());
        if (bd.scale() > 2) throw new IllegalArgumentException("Maximum 2 decimal places.");
        BigDecimal centsBd = bd.movePointRight(2);
        return centsBd.longValueExact();
    }

    public void create(String title, String amountStr, String payerId, List<String> participantIds, Category category, String note) {
        if (isBusy() || pending() || group.getValue() == null) return;
        long amountCents;
        try {
            amountCents = parseDollarsToCents(amountStr);
        } catch (Exception e) {
            failed(new AppError(ErrorCode.VALIDATION, "Enter a valid dollar amount (e.g. 25.50).", "amountCents", false));
            return;
        }
        Category catVal = category != null ? category : Category.OTHER;
        String noteVal = note != null && !note.trim().isEmpty() ? note.trim() : null;
        List<String> participants = new ArrayList<>(participantIds != null ? participantIds : List.of());
        if (payerId != null && !payerId.trim().isEmpty() && !participants.contains(payerId)) {
            participants.add(payerId);
        }
        ExpenseDraft draft = new ExpenseDraft(title, amountCents, payerId, participants, SplitMethod.EQUAL, catVal, noteVal, List.of());
        String key = intent("create", Long.toString(group.getValue().revision()), title);
        retryCreate(draft, key);
    }

    private void retryCreate(ExpenseDraft draft, String key) {
        start();
        LocalDate now = LocalDate.now();
        track(expenses.createExpense(groupId, group.getValue().revision(), draft, now, key, new Callback<>() {
            @Override public void onSuccess(MutationResult<Expense> result) {
                updateGroupRevision(result.groupRevision());
                done("Expense recorded successfully.");
                created.setValue(result);
            }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }

    public void update(String expenseId, String title, String amountStr, String payerId, List<String> participantIds, Category category, String note) {
        if (isBusy() || pending() || group.getValue() == null || expenseId == null) return;
        long amountCents;
        try {
            amountCents = parseDollarsToCents(amountStr);
        } catch (Exception e) {
            failed(new AppError(ErrorCode.VALIDATION, "Enter a valid dollar amount (e.g. 25.50).", "amountCents", false));
            return;
        }
        Category catVal = category != null ? category : Category.OTHER;
        String noteVal = note != null && !note.trim().isEmpty() ? note.trim() : null;
        List<String> participants = new ArrayList<>(participantIds != null ? participantIds : List.of());
        if (payerId != null && !payerId.trim().isEmpty() && !participants.contains(payerId)) {
            participants.add(payerId);
        }
        ExpenseDraft draft = new ExpenseDraft(title, amountCents, payerId, participants, SplitMethod.EQUAL, catVal, noteVal, List.of());
        String key = intent("update", Long.toString(group.getValue().revision()), expenseId);
        retryUpdate(expenseId, draft, key);
    }

    private void retryUpdate(String expenseId, ExpenseDraft draft, String key) {
        start();
        track(expenses.updateExpense(groupId, group.getValue().revision(), expenseId, draft, key, new Callback<>() {
            @Override public void onSuccess(MutationResult<Expense> result) {
                updateGroupRevision(result.groupRevision());
                done("Expense updated successfully.");
                updated.setValue(result);
            }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }

    public void delete(String expenseId) {
        if (isBusy() || pending() || group.getValue() == null) return;
        String key = intent("delete", Long.toString(group.getValue().revision()), expenseId);
        retryDelete(expenseId, key);
    }

    private void retryDelete(String expenseId, String key) {
        start();
        long rev = Long.parseLong(values().get(0));
        track(expenses.deleteExpense(groupId, rev, expenseId, key, new Callback<>() {
            @Override public void onSuccess(MutationResult<DeletedRecord> result) {
                updateGroupRevision(result.groupRevision());
                done("Expense deleted.");
                deleted.setValue(result);
            }
            @Override public void onError(AppError e) { failed(e); }
        }));
    }

    public void retry() {
        if (!pending() || isBusy()) return;
        if ("delete".equals(operation())) {
            retryDelete(values().get(1), requestId());
        }
    }
}
