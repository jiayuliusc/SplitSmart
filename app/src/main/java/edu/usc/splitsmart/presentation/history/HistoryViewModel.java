package edu.usc.splitsmart.presentation.history;

import androidx.lifecycle.*;
import java.util.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.HistoryService;

/** Group history with search, filters and sort. The filter survives rotation through SavedStateHandle. */
public final class HistoryViewModel extends FeatureViewModel {
    private static final String KEYWORD = "history:keyword";
    private static final String TYPE = "history:type";
    private static final String CATEGORY = "history:category";
    private static final String MEMBER = "history:member";
    private static final String SORT = "history:sort";

    /** null while loading or after an error; an empty list means "no matching transactions". */
    public final MutableLiveData<List<HistoryItem>> items = new MutableLiveData<>();

    private final HistoryService history;
    private final String groupId;
    private String viewer;
    private boolean active;
    private int generation;

    public HistoryViewModel(SavedStateHandle state, HistoryService history, String groupId) {
        super(state);
        this.history = history; this.groupId = groupId;
    }

    // ---------------------------------------------------------------- filter state

    public HistoryFilter filter() {
        String keyword = saved.get(KEYWORD);
        return new HistoryFilter(keyword == null ? "" : keyword,
            enumOrNull(TransactionType.class, saved.get(TYPE)),
            enumOrNull(Category.class, saved.get(CATEGORY)),
            saved.get(MEMBER),
            sort());
    }

    public HistorySort sort() {
        String value = saved.get(SORT);
        return value == null ? HistorySort.NEWEST_FIRST : HistorySort.valueOf(value);   // newest first by default
    }

    public void setKeyword(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (put(KEYWORD, trimmed.isEmpty() ? null : trimmed)) refresh();
    }

    public void setType(TransactionType type) {
        boolean changed = put(TYPE, type == null ? null : type.name());
        // Categories only apply to expenses, so choosing "Settlements" clears the category filter.
        if (type == TransactionType.SETTLEMENT) changed |= put(CATEGORY, null);
        if (changed) refresh();
    }

    public void setCategory(Category category) {
        if (put(CATEGORY, category == null ? null : category.name())) refresh();
    }

    public void setMember(String memberId) {
        if (put(MEMBER, memberId)) refresh();
    }

    public void toggleSort() {
        put(SORT, sort() == HistorySort.NEWEST_FIRST ? HistorySort.OLDEST_FIRST.name() : HistorySort.NEWEST_FIRST.name());
        refresh();
    }

    /** Stores a value (null removes it) and reports whether it actually changed. */
    private boolean put(String key, String value) {
        if (Objects.equals(saved.get(key), value)) return false;
        if (value == null) saved.remove(key); else saved.set(key, value);
        return true;
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
        return name == null ? null : Enum.valueOf(type, name);
    }

    // ---------------------------------------------------------------- loading

    /** Called from onAuthorized: on first open and on every resume, so changes made elsewhere appear. */
    public void activate(String uid) {
        if (active && Objects.equals(viewer, uid)) return;
        if (!Objects.equals(viewer, uid)) items.setValue(null);
        bindUser(uid); viewer = uid; active = true; refresh();
    }

    /** Always a FRESH read; a newer request supersedes an older one. */
    public void refresh() {
        if (!active || viewer == null) return;
        cancelOperations();
        int read = ++generation;
        start();
        track(history.getHistory(groupId, filter(), ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<List<HistoryItem>> result) {
                if (!active || read != generation) return;
                items.setValue(result.value());
                done("");
            }
            @Override public void onError(AppError error) {
                if (!active || read != generation) return;
                items.setValue(null);
                failed(error);
            }
        }));
    }

    public void pause() { active = false; ++generation; cancelOperations(); }
    public void clear() { pause(); viewer = null; items.setValue(null); }
    @Override protected void onCleared() { pause(); super.onCleared(); }
}
