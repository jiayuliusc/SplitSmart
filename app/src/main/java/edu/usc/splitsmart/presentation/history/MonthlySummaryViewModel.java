package edu.usc.splitsmart.presentation.history;

import androidx.lifecycle.*;
import java.time.YearMonth;
import java.util.Objects;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.FeatureViewModel;
import edu.usc.splitsmart.service.HistoryService;

/** Monthly expense summary for one group. The selected month survives rotation through SavedStateHandle. */
public final class MonthlySummaryViewModel extends FeatureViewModel {
    private static final String MONTH = "summary:month";

    /** null while loading or after an error. */
    public final MutableLiveData<MonthlySummary> summary = new MutableLiveData<>();
    /** The month currently selected; the screen observes it for the month label. */
    public final MutableLiveData<YearMonth> month = new MutableLiveData<>();

    private final HistoryService history;
    private final String groupId;
    private String viewer;
    private boolean active;
    private int generation;

    public MonthlySummaryViewModel(SavedStateHandle state, HistoryService history, String groupId) {
        super(state);
        this.history = history; this.groupId = groupId;
        String stored = saved.get(MONTH);
        month.setValue(stored == null ? YearMonth.now() : YearMonth.parse(stored));   // defaults to the current month
    }

    public YearMonth selected() { return month.getValue(); }

    public void previousMonth() { select(selected().minusMonths(1)); }
    public void nextMonth() { select(selected().plusMonths(1)); }

    private void select(YearMonth value) {
        saved.set(MONTH, value.toString());   // "2026-11"
        month.setValue(value);
        refresh();
    }

    /** Called from onAuthorized: on first open and on every resume. */
    public void activate(String uid) {
        if (active && Objects.equals(viewer, uid)) return;
        if (!Objects.equals(viewer, uid)) summary.setValue(null);
        bindUser(uid); viewer = uid; active = true; refresh();
    }

    /** Always a FRESH read; a newer request supersedes an older one. */
    public void refresh() {
        if (!active || viewer == null) return;
        cancelOperations();
        int read = ++generation;
        YearMonth requested = selected();
        start();
        track(history.getMonthlySummary(groupId, requested, ReadPolicy.FRESH, new Callback<>() {
            @Override public void onSuccess(ReadResult<MonthlySummary> result) {
                if (!active || read != generation) return;
                summary.setValue(result.value());
                done("");
            }
            @Override public void onError(AppError error) {
                if (!active || read != generation) return;
                summary.setValue(null);
                failed(error);
            }
        }));
    }

    public void pause() { active = false; ++generation; cancelOperations(); }
    public void clear() { pause(); viewer = null; summary.setValue(null); }
    @Override protected void onCleared() { pause(); super.onCleared(); }
}
