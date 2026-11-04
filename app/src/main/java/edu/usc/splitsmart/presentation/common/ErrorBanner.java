package edu.usc.splitsmart.presentation.common;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.view.OneShotPreDrawListener;
import edu.usc.splitsmart.R;

/** A single transient error above the page, with a fresh timeout for each failure. */
final class ErrorBanner {
    private static final long ANIMATION_MS = 220;
    private static final int DISPLAY_MS = 4000;
    private final TextView view;
    private final Runnable dismiss = this::dismiss;
    private OneShotPreDrawListener pendingEnter;

    ErrorBanner(FrameLayout host) {
        view = (TextView) View.inflate(host.getContext(), R.layout.view_error_banner, null);
        host.addView(view, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    void show(String message) {
        view.removeCallbacks(dismiss);
        cancelPendingEnter();
        view.animate().cancel();
        boolean visible = view.getVisibility() == View.VISIBLE;
        view.setText(message);
        view.setVisibility(View.VISIBLE);
        if (!visible) {
            // Start offscreen even before the first layout has measured the message.
            view.setTranslationY(-view.getResources().getDisplayMetrics().heightPixels);
        }
        pendingEnter = OneShotPreDrawListener.add(view, () -> {
            pendingEnter = null;
            enter();
        });
    }

    private void enter() {
        if (view.getTranslationY() < -view.getHeight()) view.setTranslationY(-view.getHeight());
        view.animate().translationY(0).setDuration(ANIMATION_MS)
            .setInterpolator(new DecelerateInterpolator()).withEndAction(() ->
                view.postDelayed(dismiss, displayDuration())).start();
    }

    private int displayDuration() {
        AccessibilityManager accessibility = (AccessibilityManager) view.getContext()
            .getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && accessibility != null) {
            return accessibility.getRecommendedTimeoutMillis(DISPLAY_MS, AccessibilityManager.FLAG_CONTENT_TEXT);
        }
        return DISPLAY_MS;
    }

    private void dismiss() {
        view.animate().translationY(-view.getHeight()).setDuration(ANIMATION_MS)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> view.setVisibility(View.GONE)).start();
    }

    void clear() {
        cancelPendingEnter();
        view.removeCallbacks(dismiss);
        view.animate().cancel();
        view.setVisibility(View.GONE);
        view.setText("");
    }

    private void cancelPendingEnter() {
        if (pendingEnter != null) {
            pendingEnter.removeListener();
            pendingEnter = null;
        }
    }
}
