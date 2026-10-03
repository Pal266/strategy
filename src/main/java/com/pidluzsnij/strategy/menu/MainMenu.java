package com.pidluzsnij.strategy.menu;

import com.pidluzsnij.strategy.config.persistence.ConfigurationLocation;
import com.pidluzsnij.strategy.ui.UiFoundation;
import com.pidluzsnij.strategy.ui.UiScreen;
import com.pidluzsnij.strategy.ui.UiStartupConfiguration;
import com.pidluzsnij.strategy.ui.input.UiActivation;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The main menu: the production UI shown after successful startup. Its presentation comes entirely from the
 * {@linkplain #DEFINITION main-menu definition} and the resources it references; this class owns only the
 * semantic behavior identifiers of the menu and what application code does when they are activated.
 * <p>
 * Only {@link #EXIT} has an effect: it requests the application's normal shutdown. The other identifiers are
 * recognized so the definition may reference them, but they have no application behavior yet.
 */
public final class MainMenu {

    /** The bundled production main-menu definition, relative to the UI resource root. */
    public static final UiResourcePath DEFINITION = UiResourcePath.of("definitions/main-menu.json");

    public static final String NEW_GAME = "menu.new_game";
    public static final String LOAD_GAME = "menu.load_game";
    public static final String SETTINGS = "menu.settings";
    public static final String EXIT = "menu.exit";

    /** Semantic behavior identifiers the main-menu definition may reference. */
    public static final Set<String> BEHAVIORS = Set.of(NEW_GAME, LOAD_GAME, SETTINGS, EXIT);

    private static final Logger log = LoggerFactory.getLogger(MainMenu.class);

    private MainMenu() {
    }

    /**
     * Production UI configuration: the application's bundled UI resources, external overrides under the
     * established per-user configuration location, the main-menu definition as a required definition and the
     * main-menu behavior identifiers.
     */
    public static UiStartupConfiguration uiConfiguration(ConfigurationLocation location) {
        return new UiStartupConfiguration(MainMenu.class.getClassLoader(), location, List.of(DEFINITION), BEHAVIORS);
    }

    /**
     * Shows the main menu when {@code ui} loaded it during initialization.
     *
     * @param exit performed when the Exit behavior is activated; requests normal application shutdown
     * @return the main-menu screen now shown, or empty when {@code ui} did not load the main menu
     */
    public static Optional<UiScreen> show(UiFoundation ui, Runnable exit) {
        Objects.requireNonNull(exit, "exit");
        Optional<UiScreen> screen = ui.requiredScreen(DEFINITION);
        screen.ifPresent(menu -> ui.show(menu, activation -> perform(activation, exit)));
        return screen;
    }

    private static void perform(UiActivation activation, Runnable exit) {
        if (EXIT.equals(activation.behavior())) {
            log.debug("Main menu Exit activated; requesting normal shutdown");
            exit.run();
        }
    }
}
