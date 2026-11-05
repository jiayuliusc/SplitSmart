package edu.usc.splitsmart.presentation.history;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

/** Number of expenses, total group spending and spending per category for a selected month. */
public final class MonthlySummaryScreen extends ProtectedScreen {
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.US);
    private MonthlySummaryViewModel model;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen(R.layout.screen_monthly_summary);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.summary_title), true);

        String groupId = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status, getString(R.string.groups_missing_scope));
            show(R.id.access_retry, false); show(R.id.access_logout, false);
            return;
        }

        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new MonthlySummaryViewModel(saved, services().history(), groupId)))
            .get(MonthlySummaryViewModel.class);
        bindErrors(model);

        model.month.observe(this, this::renderMonth);
        model.summary.observe(this, this::render);
        model.busy.observe(this, busy -> {
            boolean loading = Boolean.TRUE.equals(busy);
            show(R.id.feature_progress, loading);
            findViewById(R.id.summary_previous).setEnabled(!loading);
            findViewById(R.id.summary_next).setEnabled(!loading);
        });

        findViewById(R.id.summary_previous).setOnClickListener(v -> model.previousMonth());
        findViewById(R.id.summary_next).setOnClickListener(v -> model.nextMonth());
        protect();
    }

    private void renderMonth(YearMonth month) {
        text(R.id.summary_month, month.format(MONTH_LABEL));   // e.g. "November 2026"
    }

    private void render(MonthlySummary summary) {
        show(R.id.summary_content, summary != null);
        if (summary == null) return;

        boolean none = summary.expenseCount() == 0;
        show(R.id.summary_empty, none);
        show(R.id.summary_figures, !none);
        if (none) return;

        text(R.id.summary_count, String.valueOf(summary.expenseCount()));
        text(R.id.summary_total, money(summary.totalCents()));

        LinearLayout rows = findViewById(R.id.summary_categories);
        rows.removeAllViews();
        for (Category category : Category.values()) {   // all five, in a fixed order, including zeros
            View row = getLayoutInflater().inflate(R.layout.row_summary_category, rows, false);
            ((TextView) row.findViewById(R.id.row_summary_category)).setText(label(category));
            Long cents = summary.categoryTotalsCents().get(category);
            ((TextView) row.findViewById(R.id.row_summary_amount)).setText(money(cents == null ? 0 : cents));
            rows.addView(row);
        }
    }

    /** FOOD -> "Food". */
    private static String label(Category category) {
        String name = category.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String money(long cents) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2));
    }

    @Override protected void onAuthorized(User user) {
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) model.activate(user.uid());
    }
    @Override protected void onPause() { super.onPause(); if (model != null) model.pause(); }
    @Override protected void onAccessLost() { if (model != null) model.clear(); }
}
