package edu.usc.splitsmart.presentation.common;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import androidx.savedstate.SavedStateRegistryOwner;
import java.util.function.Function;

public final class FeatureFactory extends AbstractSavedStateViewModelFactory {
    private final Function<SavedStateHandle, ? extends ViewModel> create;
    public FeatureFactory(SavedStateRegistryOwner owner, Bundle arguments, Function<SavedStateHandle, ? extends ViewModel> create) {
        super(owner, arguments); this.create = create;
    }
    @NonNull @Override protected <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> type, @NonNull SavedStateHandle state) {
        return type.cast(create.apply(state));
    }
}
