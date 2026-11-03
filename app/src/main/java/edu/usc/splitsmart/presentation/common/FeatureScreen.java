package edu.usc.splitsmart.presentation.common;

import android.content.Intent;
import android.view.*;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.*;
import edu.usc.splitsmart.presentation.identity.LoginScreen;

public abstract class FeatureScreen extends AppCompatActivity {
    protected IdentityGroupServices services() { return ((SplitSmartApplication) getApplication()).getContainer(); }
    protected void screen(int layout) {
        EdgeToEdge.enable(this); setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.feature_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        findViewById(R.id.feature_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        ((TextView) findViewById(R.id.feature_message)).setText("");
        findViewById(R.id.feature_message).setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    protected String input(int id) { return ((EditText) findViewById(id)).getText().toString(); }
    protected void text(int id, String text) { ((TextView) findViewById(id)).setText(text); }
    protected void show(int id, boolean visible) { findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE); }
    protected void bind(FeatureViewModel model, int... buttons) {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (model.isBusy() || model.pending()) {
                    model.message.setValue(getString(R.string.identity_pending_navigation));
                } else finish();
            }
        });
        model.message.observe(this, value -> text(R.id.feature_message, value));
        model.busy.observe(this, busy -> {
            show(R.id.feature_progress, Boolean.TRUE.equals(busy));
            for (int id : buttons) findViewById(id).setEnabled(!Boolean.TRUE.equals(busy) && !model.pending());
            lockInputs((ViewGroup) findViewById(R.id.feature_content), Boolean.TRUE.equals(busy) || model.pending());
            View retry = findViewById(R.id.identity_retry);
            if (retry != null) { retry.setVisibility(model.pending() ? View.VISIBLE : View.GONE); retry.setEnabled(!Boolean.TRUE.equals(busy)); }
        });
    }
    private void lockInputs(ViewGroup group, boolean locked) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof EditText) child.setEnabled(!locked);
            else if (child instanceof ViewGroup nested) lockInputs(nested, locked);
        }
    }
    protected void login() { startActivity(new Intent(this, LoginScreen.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish(); }
}
