package edu.usc.splitsmart.presentation.identity;

/** Signed-in recovery route; registration can also remain on its form after a partial failure. */
public final class CompleteProfileScreen extends RegisterScreen {
    @Override protected boolean completionOnly() { return true; }
}
