package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.ApplicationLauncher;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.localization.ClasspathLocalizationResources;
import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.localization.LocalizationInitializer;
import com.pidluzsnij.strategy.localization.LocalizationResources;
import com.pidluzsnij.strategy.logging.DirectoriesLogLocation;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.window.RuntimeEnvironment;
import com.pidluzsnij.strategy.window.WindowSystem;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Separate-process application used by multi-process tests.
 * <p>
 * Usage: {@code ProbeMain location} prints the production per-user configuration directory;
 * {@code ProbeMain run|hold <configDir> <marker>} launches the application with a fake window
 * system, using {@code configDir} for both the log and the configuration file. {@code hold}
 * prints {@code READY} once running and closes after a line on stdin. An optional fourth argument
 * names a directory or archive whose {@code localization} folder replaces the bundled resources.
 * <p>
 * {@code ProbeMain localize <resourceRoot> <language> <key>} initializes localization alone from the
 * {@code localization} folder of an isolated class path consisting only of {@code resourceRoot}.
 * <p>
 * {@code ProbeMain uiresolve <bundledRoot> <configDir> <path>...} resolves UI resources with the production
 * UI configuration shape: bundled resources from the {@code ui} folder of an isolated class path consisting
 * only of {@code bundledRoot} (a directory or archive), overrides from {@code configDir/strategy/ui}. It prints
 * {@code RESOLVED <path> <origin> <sha256>} per path.
 */
public final class ProbeMain {

    private ProbeMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("location")) {
            System.out.println("LOCATION " + new DirectoriesLogLocation().configDirectory());
            return;
        }
        if (args[0].equals("uiresolve")) {
            uiResolve(Path.of(args[1]), Path.of(args[2]), java.util.Arrays.copyOfRange(args, 3, args.length));
            return;
        }
        if (args[0].equals("localize")) {
            localize(Path.of(args[1]), args[2], args[3]);
            return;
        }
        boolean hold = args[0].equals("hold");
        Path configDirectory = Path.of(args[1]);
        String marker = args[2];
        Path logFile = configDirectory.resolve("strategy").resolve("log.log");

        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.onInitialize = () -> LoggerFactory.getLogger("probe").info("MARKER {}", marker);
        if (hold) {
            windowSystem.iterationsBeforeClose = Integer.MAX_VALUE;
            windowSystem.onProcessEvents = () -> {
                System.out.println("READY");
                System.out.flush();
                try {
                    new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                windowSystem.requestClose();
            };
        }
        Supplier<WindowSystem> factory = () -> {
            try {
                System.out.println("INFRA_STARTED logSize=" + Files.size(logFile));
            } catch (IOException e) {
                System.out.println("INFRA_STARTED logSize=missing");
            }
            System.out.flush();
            return windowSystem;
        };
        int exitCode = new ApplicationLauncher(LoggingMode.DEFAULT, () -> configDirectory, FileOperations.SYSTEM,
                System.err, ApplicationSettings.SCHEMA, () -> configDirectory, ApplicationLauncher.TOML_PERSISTENCE,
                args.length > 3 ? isolatedResources(Path.of(args[3])) : LocalizationResources.bundled(),
                factory, () -> RuntimeEnvironment.current("probe")).launch();
        System.out.println("EXIT " + exitCode);
        System.out.flush();
        System.exit(exitCode);
    }

    private static LocalizationResources isolatedResources(Path root) throws Exception {
        return new ClasspathLocalizationResources(
                new URLClassLoader(new URL[] {root.toUri().toURL()}, ClassLoader.getPlatformClassLoader()));
    }

    private static void uiResolve(Path bundledRoot, Path configDirectory, String[] paths) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {bundledRoot.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            com.pidluzsnij.strategy.ui.UiStartupConfiguration configuration =
                    com.pidluzsnij.strategy.ui.UiStartupConfiguration.production(() -> configDirectory);
            com.pidluzsnij.strategy.ui.resource.UiResources resources =
                    new com.pidluzsnij.strategy.ui.resource.UiResources(loader,
                            com.pidluzsnij.strategy.ui.resource.UiResources.externalRoot(
                                    configuration.location().configDirectory()));
            for (String path : paths) {
                com.pidluzsnij.strategy.ui.resource.ResolvedUiResource resource =
                        resources.resolve(com.pidluzsnij.strategy.ui.resource.UiResourcePath.of(path));
                System.out.println("RESOLVED " + path + " " + resource.origin() + " " + UiFixtures.sha256(resource.bytes()));
            }
        }
        System.out.flush();
    }

    private static void localize(Path root, String language, String key) throws Exception {
        Optional<Localization> localization = new LocalizationInitializer(isolatedResources(root)).initialize(language);
        java.io.PrintStream out = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),
                true, StandardCharsets.UTF_8);
        if (localization.isEmpty()) {
            out.println("FAILED");
        } else {
            out.println("LANGUAGES " + localization.get().languages().stream()
                    .map(l -> l.identifier() + "=" + l.displayName()).collect(Collectors.joining(";")));
            out.println("EFFECTIVE " + localization.get().effectiveLanguage());
            out.println("TEXT " + localization.get().text(key));
        }
        out.flush();
    }
}
