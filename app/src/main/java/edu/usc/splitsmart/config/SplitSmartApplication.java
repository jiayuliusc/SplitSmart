package edu.usc.splitsmart.config;

import android.app.Application;

/** Composition root. Future dependencies are wired here, never inside screens. */
public final class SplitSmartApplication extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer getContainer() {
        return container;
    }
}
