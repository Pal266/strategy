package com.pidluzsnij.strategy.menu;

import com.pidluzsnij.strategy.config.persistence.ConfigurationLocation;
import com.pidluzsnij.strategy.ui.UiFoundation;
import com.pidluzsnij.strategy.ui.UiScreen;
import com.pidluzsnij.strategy.ui.UiStartup;
import com.pidluzsnij.strategy.ui.UiStartupConfiguration;
import com.pidluzsnij.strategy.ui.input.UiActivation;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
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

    /** Production UI startup: the {@linkplain #uiConfiguration main-menu configuration} showing the main menu. */
    public static UiStartup uiStartup(ConfigurationLocation location) {
        return new UiStartup(uiConfiguration(location), MainMenu::show);
    }

    /**
     * Shows the main menu, which {@code ui} must have loaded as a required definition during initialization.
     *
     * @param exit performed when the Exit behavior is activated; requests normal application shutdown
     * @return the main-menu screen now shown
     * @throws IllegalStateException when {@code ui} did not load the main menu: the UI startup is misconfigured
     */
    public static UiScreen show(UiFoundation ui, Runnable exit) {
        Objects.requireNonNull(exit, "exit");
        UiScreen screen = ui.requiredScreen(DEFINITION).orElseThrow(() -> new IllegalStateException(
                "the main menu cannot be shown: UI definition '" + DEFINITION.value()
                        + "' was not loaded during UI initialization"));
        ui.show(screen, activation -> perform(activation, exit));
        return screen;
    }

    private static void perform(UiActivation activation, Runnable exit) {
        if (EXIT.equals(activation.behavior())) {
            log.debug("Main menu Exit activated; requesting normal shutdown");
            exit.run();
        }
    }
}
