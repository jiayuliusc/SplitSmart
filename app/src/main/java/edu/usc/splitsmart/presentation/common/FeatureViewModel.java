package edu.usc.splitsmart.presentation.common;

import androidx.lifecycle.*;
import java.util.*;
import edu.usc.splitsmart.domain.contract.*;

/** One visible action at a time, with a saved immutable mutation intent for ambiguous outcomes. */
public abstract class FeatureViewModel extends ViewModel {
    protected final SavedStateHandle saved;
    public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    public final MutableLiveData<String> message = new MutableLiveData<>("");
    public final MutableLiveData<AppError> error = new MutableLiveData<>();
    private final List<AsyncOperation> operations = new ArrayList<>();
    protected FeatureViewModel(SavedStateHandle saved) { this.saved = saved; }
    protected void track(AsyncOperation operation) { operations.add(operation); }
    protected void start() { busy.setValue(true); message.setValue(""); error.setValue(null); }
    public boolean isBusy() { return Boolean.TRUE.equals(busy.getValue()); }
    public boolean pending() { return saved.contains("mutationId"); }
    protected String intent(String operation, String... values) {
        if (pending()) return saved.get("mutationId");
        String key = UUID.randomUUID().toString(); saved.set("mutationId", key);
        saved.set("mutationOperation", operation); saved.set("mutationValues", new ArrayList<>(Arrays.asList(values)));
        return key;
    }
    protected String operation() { return saved.get("mutationOperation"); }
    protected List<String> values() { return saved.get("mutationValues"); }
    protected String requestId() { return saved.get("mutationId"); }
    protected void resolved() { saved.remove("mutationId"); saved.remove("mutationOperation"); saved.remove("mutationValues"); }
    protected void failed(AppError failure) {
        if (!failure.retryable() && failure.code() != ErrorCode.INTERNAL) resolved();
        error.setValue(failure); message.setValue(failure.message()); busy.setValue(false);
    }
    protected void done(String text) { resolved(); message.setValue(text); busy.setValue(false); }
    public void bindUser(String uid) {
        String prior = saved.get("scopeUid");
        if (prior != null && !prior.equals(uid)) { resolved(); cancelOperations(); }
        saved.set("scopeUid", uid);
    }
    public void cancelOperations() { operations.forEach(AsyncOperation::cancel); operations.clear(); busy.setValue(false); }
    @Override protected void onCleared() { operations.forEach(AsyncOperation::cancel); operations.clear(); }
}
