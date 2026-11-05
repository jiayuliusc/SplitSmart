package edu.usc.splitsmart.identity;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.config.SplitSmartApplication;
import edu.usc.splitsmart.presentation.identity.LoginScreen;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ErrorBannerScreensTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for banner state");
    }
    private TextView banner(Activity activity) {
        return activity.findViewById(R.id.feature_error_banner);
    }
    @Test public void invalidLoginShowsTopBannerAndRepeatedErrorRestartsDismissal() throws Exception {
        SplitSmartApplication app = (SplitSmartApplication) instrumentation.getTargetContext().getApplicationContext();
        instrumentation.runOnMainSync(() -> app.getContainer().getFirebaseClients().getAuth().signOut());
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), LoginScreen.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            await(() -> activity.findViewById(R.id.identity_submit).isEnabled());
            float[] minimumTranslation = {0};
            instrumentation.runOnMainSync(() -> {
                banner(activity).getViewTreeObserver().addOnPreDrawListener(() -> {
                    if (banner(activity).isShown() && banner(activity).getHeight() > 0) {
                        minimumTranslation[0] = Math.min(minimumTranslation[0], banner(activity).getTranslationY());
                    }
                    return true;
                });
                activity.findViewById(R.id.identity_submit).performClick();
            });
            await(() -> banner(activity) != null && banner(activity).isShown() && banner(activity).getTranslationY() == 0);
            instrumentation.runOnMainSync(() -> {
                TextView error = banner(activity);
                assertTrue("Banner must slide in from above", minimumTranslation[0] < 0);
                assertTrue(error.getText().toString().contains("usc.edu"));
                assertEquals(Color.WHITE, error.getCurrentTextColor());
                assertEquals("", ((TextView) activity.findViewById(R.id.feature_message)).getText().toString());
                int[] bannerPosition = new int[2], titlePosition = new int[2];
                error.getLocationOnScreen(bannerPosition);
                activity.findViewById(R.id.feature_title).getLocationOnScreen(titlePosition);
                assertTrue("Banner belongs above the page title", bannerPosition[1] < titlePosition[1]);
            });
            Thread.sleep(2500);
            instrumentation.runOnMainSync(() -> {
                ((TextView) activity.findViewById(R.id.identity_email)).setText("person@usc.edu");
                activity.findViewById(R.id.identity_submit).performClick();
            });
            await(() -> banner(activity).getText().toString().contains("password"));
            Thread.sleep(2000);
            instrumentation.runOnMainSync(() -> assertEquals(View.VISIBLE, banner(activity).getVisibility()));
            await(() -> banner(activity).getVisibility() == View.GONE);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
