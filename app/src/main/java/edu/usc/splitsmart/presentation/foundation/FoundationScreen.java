package edu.usc.splitsmart.presentation.foundation;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import edu.usc.splitsmart.databinding.ScreenFoundationBinding;

/** Static launch screen for the development foundation; no product workflow yet. */
public final class FoundationScreen extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ScreenFoundationBinding binding = ScreenFoundationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        int padding = getResources().getDimensionPixelSize(
                edu.usc.splitsmart.R.dimen.foundation_padding);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(padding + bars.left, padding + bars.top,
                    padding + bars.right, padding + bars.bottom);
            return windowInsets;
        });
    }
}
