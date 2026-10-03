package com.pidluzsnij.strategy.ui;

import java.util.Objects;

/**
 * How the application's UI starts: the UI-foundation configuration and the first screen application code shows
 * once the foundation is initialized. Both come from the same place, so the first screen always matches the
 * definitions the configuration loads, and the window lifecycle needs to know neither.
 */
public record UiStartup(UiStartupConfiguration configuration, FirstScreen firstScreen) {

    /** Shows the application's first screen after UI-foundation initialization. */
    @FunctionalInterface
    public interface FirstScreen {

        /** Shows nothing: the foundation renders nothing and ignores pointer input. */
        FirstScreen NONE = (ui, requestShutdown) -> { };

        /**
         * @param ui              the initialized UI foundation
         * @param requestShutdown requests the application's normal shutdown
         * @throws RuntimeException when the screen cannot be shown; startup then fails
         */
        void show(UiFoundation ui, Runnable requestShutdown);
    }

    public UiStartup {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(firstScreen, "firstScreen");
    }

    /** @return a startup that loads {@code configuration} and shows nothing */
    public static UiStartup withoutFirstScreen(UiStartupConfiguration configuration) {
        return new UiStartup(configuration, FirstScreen.NONE);
    }
}
