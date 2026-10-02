package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.localization.LocalizationResources;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.testsupport.FakeWindowSystem;
import com.pidluzsnij.strategy.testsupport.FixtureResources;
import com.pidluzsnij.strategy.testsupport.FixtureResources.Unavailability;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.RecordingStorage;
import com.pidluzsnij.strategy.window.Resolution;
import com.pidluzsnij.strategy.window.WindowState;
import com.pidluzsnij.strategy.window.WindowSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.pidluzsnij.strategy.testsupport.LocalizationRun.FAILURE;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.FALLBACK;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.SUCCESS;
import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The localized application window title within startup, with isolated logging, configuration and
 * localization resources and a controlled window system that captures the title passed to window creation.
 */
class WindowTitleLocalizationTest {

    private static final String KEY = "application.window.title";
    private static final String VIDEO = "[video]\nfullscreen = false\n\n[video.resolution]\nwidth = 1600\nheight = 900\n";
    private static final String OPENED = "Window opened displaying black: title '";
    private static final Map<String, String> TITLES = new LinkedHashMap<>();

    static {
        TITLES.put("en", "My strategy");
        TITLES.put("uk", "Моя стратегія");
        TITLES.put("cs", "Moje strategie");
        TITLES.put("hu", "Az én stratégiám");
    }

    private static final Map<String, String> FILES = Map.of("en", "english.properties", "uk", "ukrainian.properties",
            "cs", "czech.properties", "hu", "hungarian.properties");

    /** Empty, spaces, tabs and other Unicode White_Space characters. */
    private static final List<String> BLANK_VALUES = List.of("", "   ", "\t\t", " \t ",
            "  　 \u0085\u000B\u000C");

    private static Map<Path, byte[]> bundledBefore;

    @TempDir
    Path temp;

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private LogHarness harness;
    private RecordingStorage storage;
    private FakeWindowSystem windowSystem;

    @BeforeAll
    static void recordBundledResources() throws Exception {
        bundledBefore = bundledResourceBytes();
    }

    /** Fixture cases never modify the bundled production resources. */
    @AfterAll
    static void bundledResourcesAreUnchanged() throws Exception {
        Map<Path, byte[]> after = bundledResourceBytes();
        assertEquals(bundledBefore.keySet(), after.keySet());
        bundledBefore.forEach((file, bytes) -> assertArrayEquals(bytes, after.get(file), file.toString()));
    }

    private static Map<Path, byte[]> bundledResourceBytes() throws IOException, URISyntaxException {
        Path folder = Path.of(LocalizationResources.class.getClassLoader().getResource("localization").toURI());
        Map<Path, byte[]> bytes = new HashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                bytes.put(file, Files.readAllBytes(file));
            }
        }
        return bytes;
    }

    private Path prepare(String name, LocalizationResources resources) throws IOException {
        Path root = Files.createDirectories(temp.resolve(name));
        events.clear();
        harness = new LogHarness(root);
        storage = new RecordingStorage(events);
        harness.storage = storage;
        harness.localizationResources = fileName -> {
            events.add("loc-read " + fileName);
            return resources.read(fileName);
        };
        windowSystem = new FakeWindowSystem(events);
        return root;
    }

    private void writeConfig(String toml) throws IOException {
        Files.createDirectories(harness.settingsFile().getParent());
        Files.writeString(harness.settingsFile(), toml, StandardCharsets.UTF_8);
    }

    private void configureLanguage(String language) throws IOException {
        writeConfig(VIDEO + "\n[localization]\nlanguage = \"" + language + "\"\n");
    }

    private record Outcome(int exit, List<String> records, String log) {
        List<String> containing(String level, String text) {
            return records.stream().filter(r -> r.contains(" " + level + " [") && r.contains(text)).toList();
        }

        List<String> withLevel(String level) {
            return records.stream().filter(r -> r.contains(" " + level + " [")).toList();
        }
    }

    private Outcome launch(LoggingMode mode) {
        int exit = harness.launch(mode, recordingLogOperations(), harness.location(), windowSystem);
        return new Outcome(exit, harness.records(), harness.log());
    }

    private FileOperations recordingLogOperations() {
        return new FileOperations() {
            @Override
            public FileChannel open(Path file) throws IOException {
                events.add("log-open " + file);
                return FileOperations.super.open(file);
            }

            @Override
            public void write(FileChannel channel, ByteBuffer data) throws IOException {
                String text = StandardCharsets.UTF_8.decode(data.duplicate()).toString();
                FileOperations.super.write(channel, data);
                events.add("log-write " + text.strip());
            }

            @Override
            public void release(FileLock lock) throws IOException {
                events.add("log-release");
                FileOperations.super.release(lock);
            }

            @Override
            public void close(FileChannel channel) throws IOException {
                events.add("log-close");
                FileOperations.super.close(channel);
            }
        };
    }

    /** Asserts a successful start whose single window creation carried {@code title}. */
    private void assertStartedWithTitle(Outcome outcome, String effective, boolean fallback, String title) {
        assertEquals(0, outcome.exit(), harness.stderr() + outcome.log());
        List<String> success = outcome.containing("INFO ", SUCCESS);
        assertEquals(1, success.size(), outcome.log());
        assertTrue(success.get(0).contains("effective language '" + effective + "'"), success.get(0));
        assertTrue(success.get(0).contains("English fallback used: " + (fallback ? "yes" : "no")), success.get(0));
        assertEquals(1, events.stream().filter("createWindow"::equals).count(), "exactly one window is created");
        assertNotNull(windowSystem.createdSettings);
        assertEquals(title, windowSystem.createdSettings.title());
        assertTrue(outcome.withLevel("ERROR").isEmpty(), outcome.log());
    }

    private static String expectedOpened(String title, WindowState state) {
        return OPENED + title + "', resolution " + state.resolution() + ", fullscreen " + state.fullscreen();
    }

    private void assertNoProductionFileAccess(Path root) {
        List<Path> accessed = new ArrayList<>(storage.paths);
        events.stream().filter(e -> e.startsWith("log-open "))
                .map(e -> Path.of(e.substring(e.indexOf(' ') + 1))).forEach(accessed::add);
        assertFalse(accessed.isEmpty());
        for (Path path : accessed) {
            assertTrue(path.toAbsolutePath().startsWith(root), "outside the isolated locations: " + path);
        }
    }

    // --- Default and configured languages with the bundled resources ---------------------------

    @Test
    void defaultLanguageTitleIsMyStrategy() throws Exception {
        Path root = prepare("default", LocalizationResources.bundled());

        Outcome outcome = launch(LoggingMode.DEFAULT);

        assertStartedWithTitle(outcome, "en", false, "My strategy");
        String saved = Files.readString(harness.settingsFile(), StandardCharsets.UTF_8);
        assertTrue(saved.contains("[localization]") && saved.contains("language = \"en\""), saved);
        assertFalse(saved.toLowerCase(Locale.ROOT).contains("title"), "no separate title setting: " + saved);
        assertNoProductionFileAccess(root);
    }

    @Test
    void configuredLanguageSelectsTheTitleRegardlessOfTheOperatingSystemLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            for (Map.Entry<String, String> language : TITLES.entrySet()) {
                Path root = prepare("language-" + language.getKey(), LocalizationResources.bundled());
                configureLanguage(language.getKey());
                byte[] configuration = Files.readAllBytes(harness.settingsFile());
                Locale.setDefault(language.getKey().equals("uk") ? Locale.forLanguageTag("hu-HU")
                        : Locale.forLanguageTag("uk-UA"));

                Outcome outcome = launch(LoggingMode.DEFAULT);

                assertStartedWithTitle(outcome, language.getKey(), false, language.getValue());
                assertArrayEquals(configuration, Files.readAllBytes(harness.settingsFile()),
                        "language selection alone causes no configuration rewrite");
                assertNoProductionFileAccess(root);
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void windowSystemHasNoTitleChangeAfterCreation() {
        for (Method method : WindowSystem.class.getMethods()) {
            assertFalse(method.getName().toLowerCase(Locale.ROOT).contains("title"), method.toString());
        }
    }

    // --- Lookup semantics -------------------------------------------------------------------

    @Test
    void lookupResultIsPassedUnchanged() throws Exception {
        String value = "  Ťitul — «стратегія» = ő\t ";
        FixtureResources resources = new FixtureResources()
                .put("ukrainian.properties", "# comment\n" + KEY + "=" + value + "\nother.key=x\n")
                .put("english.properties", KEY + "=My strategy\n");
        prepare("passthrough", resources);
        configureLanguage("uk");

        Outcome outcome = launch(LoggingMode.DEFAULT);

        assertStartedWithTitle(outcome, "uk", false, value);
        assertFalse(resources.reads.contains("english.properties"), "English is not loaded");
    }

    @ParameterizedTest
    @ValueSource(strings = {"uk", "cs", "hu"})
    void missingKeyBecomesTheTitleWithoutEnglishLookup(String language) throws Exception {
        FixtureResources resources = new FixtureResources()
                .put(FILES.get(language), "unrelated.key=value\n")
                .put("english.properties", KEY + "=My strategy\n");
        prepare("missing-" + language, resources);
        configureLanguage(language);

        Outcome outcome = launch(LoggingMode.DEVELOPMENT);

        assertStartedWithTitle(outcome, language, false, KEY);
        assertFalse(resources.reads.contains("english.properties"), "no individual English lookup");
        assertTrue(outcome.withLevel("WARN ").isEmpty(), outcome.log());
    }

    @Test
    void emptyAndWhitespaceOnlyValuesBecomeTheKey() throws Exception {
        int run = 0;
        for (String language : List.of("en", "hu")) {
            for (String blank : BLANK_VALUES) {
                FixtureResources resources = new FixtureResources()
                        .put(FILES.get(language), KEY + "=" + blank + "\n");
                if (!language.equals("en")) {
                    resources.put("english.properties", KEY + "=My strategy\n");
                }
                prepare("blank-" + run++, resources);
                configureLanguage(language);

                Outcome outcome = launch(LoggingMode.DEFAULT);

                assertStartedWithTitle(outcome, language, false, KEY);
                assertTrue(outcome.withLevel("WARN ").isEmpty(), outcome.log());
                if (!language.equals("en")) {
                    assertFalse(resources.reads.contains("english.properties"), "no per-key English fallback");
                }
            }
        }
    }

    // --- Whole-file fallback ----------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Unavailability.class)
    void wholeFileFallbackUsesTheEnglishTitle(Unavailability unavailability) throws Exception {
        FixtureResources resources = new FixtureResources()
                .put("czech.properties", KEY + "=Moje strategie\n")
                .put("english.properties", KEY + "=My strategy\n")
                .makeUnavailable("czech.properties", unavailability);
        prepare("fallback-" + unavailability, resources);
        configureLanguage("cs");
        byte[] configuration = Files.readAllBytes(harness.settingsFile());

        Outcome outcome = launch(LoggingMode.DEVELOPMENT);

        assertStartedWithTitle(outcome, "en", true, "My strategy");
        assertArrayEquals(configuration, Files.readAllBytes(harness.settingsFile()), "preference preserved");
        assertEquals(1, outcome.containing("WARN ", FALLBACK).size(), outcome.log());
        assertEquals(1, outcome.withLevel("WARN ").size(), "no further warning from title handling");
    }

    @Test
    void englishFallbackWithoutUsableTitleUsesTheKey() throws Exception {
        List<String> englishFiles = new ArrayList<>();
        englishFiles.add("unrelated.key=value\n");
        for (String blank : BLANK_VALUES) {
            englishFiles.add(KEY + "=" + blank + "\n");
        }
        int run = 0;
        for (String english : englishFiles) {
            FixtureResources resources = new FixtureResources()
                    .put("english.properties", english)
                    .makeUnavailable("ukrainian.properties", Unavailability.MISSING);
            prepare("fallback-blank-" + run++, resources);
            configureLanguage("uk");

            Outcome outcome = launch(LoggingMode.DEFAULT);

            assertStartedWithTitle(outcome, "en", true, KEY);
            assertEquals(1, resources.reads.stream().filter("english.properties"::equals).count(),
                    "English is loaded once, without recursive fallback");
            assertEquals(1, outcome.containing("WARN ", FALLBACK).size(), outcome.log());
        }
    }

    // --- Fatal localization failure ---------------------------------------------------------

    enum Fatal { INVALID_METADATA, CONFIGURED_ENGLISH, ENGLISH_FALLBACK }

    @ParameterizedTest
    @EnumSource(Fatal.class)
    void fatalLocalizationFailureCreatesNoWindow(Fatal fatal) throws Exception {
        FixtureResources resources = new FixtureResources()
                .put("english.properties", KEY + "=My strategy\n")
                .put("ukrainian.properties", KEY + "=Моя стратегія\n");
        prepare("fatal-" + fatal, resources);
        switch (fatal) {
            case INVALID_METADATA -> resources.put(LocalizationResources.METADATA_FILE, "not,a,valid\nheader\n");
            case CONFIGURED_ENGLISH -> resources.makeUnavailable("english.properties", Unavailability.MALFORMED);
            case ENGLISH_FALLBACK -> {
                configureLanguage("uk");
                resources.remove("ukrainian.properties").remove("english.properties");
            }
            default -> throw new AssertionError(fatal);
        }

        Outcome outcome = launch(LoggingMode.DEVELOPMENT);

        assertEquals(1, outcome.exit());
        assertFalse(harness.infrastructureStarted.get(), "no window system is created");
        assertFalse(events.contains("createWindow"));
        assertTrue(outcome.records().stream().noneMatch(r -> r.contains(OPENED)), "no successful-window INFO");
        assertEquals(1, outcome.withLevel("ERROR").size(), outcome.log());
        assertEquals(1, count(outcome.log(), FAILURE));
        assertFalse(outcome.log().contains("Normal shutdown completed"));
        assertFalse(outcome.log().contains(KEY), "title handling adds no diagnostic");
        int release = events.indexOf("log-release");
        int close = events.indexOf("log-close");
        assertTrue(release >= 0 && release < close, events.toString());
        assertEquals(close, events.size() - 1, "nothing is written after logging closes");
        try (FileChannel channel = FileChannel.open(harness.logFile(), StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false);
            assertNotNull(lock, "log ownership was released");
            lock.release();
        }
    }

    // --- Startup order and next-startup language change -------------------------------------

    @Test
    void titleIsResolvedAfterLocalizationAndLanguageChangesApplyOnTheNextStartup() throws Exception {
        Path root = prepare("order", LocalizationResources.bundled());
        configureLanguage("en");
        List<String> titlesSeenWhileRunning = new ArrayList<>();
        windowSystem.iterationsBeforeClose = 3;
        windowSystem.onProcessEvents = () -> {
            if (windowSystem.eventIterations == 1) {
                try {
                    Files.writeString(harness.settingsFile(), VIDEO + "\n[localization]\nlanguage = \"uk\"\n",
                            StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            titlesSeenWhileRunning.add(windowSystem.createdSettings.title());
        };

        Outcome first = launch(LoggingMode.DEFAULT);

        assertStartedWithTitle(first, "en", false, "My strategy");
        int logOpen = indexOf("log-open");
        int configRead = indexOf("config-read");
        int configured = indexContaining("Configuration initialized");
        int localizationRead = indexOf("loc-read");
        int localized = indexContaining(SUCCESS);
        int glfw = events.indexOf("initialize");
        int created = events.indexOf("createWindow");
        assertTrue(logOpen >= 0 && logOpen < configRead && configRead < configured, events.toString());
        assertTrue(configured < localizationRead && localizationRead < localized, events.toString());
        assertTrue(localized < glfw && glfw < created, events.toString());
        assertEquals(List.of("My strategy", "My strategy", "My strategy", "My strategy"), titlesSeenWhileRunning,
                "the running window keeps its title");
        assertEquals(1, first.records().stream().filter(r -> r.contains(OPENED)).count());

        windowSystem = new FakeWindowSystem(events);
        events.clear();
        Outcome second = launch(LoggingMode.DEFAULT);

        assertStartedWithTitle(second, "uk", false, "Моя стратегія");
        assertNoProductionFileAccess(root);
    }

    private int indexOf(String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private int indexContaining(String text) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    // --- Successful-window diagnostic -------------------------------------------------------

    private record DiagnosticCase(String name, LocalizationResources resources, String language, String title,
                                  List<String> forbidden) {
    }

    private static List<DiagnosticCase> diagnosticCases() {
        List<DiagnosticCase> cases = new ArrayList<>();
        for (Map.Entry<String, String> language : TITLES.entrySet()) {
            List<String> others = new ArrayList<>(TITLES.values());
            others.remove(language.getValue());
            cases.add(new DiagnosticCase("bundled-" + language.getKey(), LocalizationResources.bundled(),
                    language.getKey(), language.getValue(), others));
        }
        cases.add(new DiagnosticCase("fallback", new FixtureResources()
                .put("english.properties", KEY + "=My strategy\nother.key=SECRET-OTHER-EN\n")
                .remove("hungarian.properties"), "hu", "My strategy", List.of("SECRET-OTHER-EN")));
        cases.add(new DiagnosticCase("key-as-title", new FixtureResources()
                .put("czech.properties", "other.key=SECRET-OTHER-CS\n")
                .put("english.properties", KEY + "=SECRET-ENGLISH-TITLE\n"), "cs", KEY,
                List.of("SECRET-OTHER-CS", "SECRET-ENGLISH-TITLE")));
        return cases;
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void successfulWindowEventReportsTheTitleActuallyUsed(LoggingMode mode) throws Exception {
        for (DiagnosticCase diagnostic : diagnosticCases()) {
            prepare(mode.id() + "-" + diagnostic.name(), diagnostic.resources());
            configureLanguage(diagnostic.language());
            WindowState actual = new WindowState(new Resolution(1366, 768), true);
            windowSystem.actualState = actual;

            Outcome outcome = launch(mode);

            assertEquals(0, outcome.exit(), outcome.log());
            assertEquals(diagnostic.title(), windowSystem.createdSettings.title());
            List<String> opened = outcome.containing("INFO ", OPENED);
            assertEquals(1, opened.size(), outcome.log());
            assertTrue(opened.get(0).endsWith(expectedOpened(diagnostic.title(), actual)), opened.get(0));
            assertEquals(1, outcome.records().stream().filter(r -> r.contains(diagnostic.title())).count(),
                    "no additional title-specific event: " + outcome.log());
            for (String forbidden : diagnostic.forbidden()) {
                assertFalse(outcome.log().contains(forbidden), diagnostic.name() + ": " + forbidden);
            }
            assertFalse(outcome.log().contains(KEY + "="), "no raw localization file");
            assertFalse(outcome.log().contains("[video") || outcome.log().contains("[localization]"),
                    "no settings snapshot");
        }
    }

    // --- Window and configuration regression ------------------------------------------------

    private record WindowRun(String title, boolean fullscreen, Resolution resolution, List<String> windowEvents,
                             boolean rendered, int exit, boolean configurationUnchanged) {
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onlyTheTitleChangesWithTheLanguage(boolean fullscreen) throws Exception {
        Map<String, WindowRun> runs = new LinkedHashMap<>();
        for (String language : TITLES.keySet()) {
            prepare("regression-" + fullscreen + "-" + language, LocalizationResources.bundled());
            writeConfig("[video]\nfullscreen = " + fullscreen
                    + "\n\n[video.resolution]\nwidth = 1600\nheight = 900\n\n[localization]\nlanguage = \""
                    + language + "\"\n");
            byte[] configuration = Files.readAllBytes(harness.settingsFile());
            windowSystem.iterationsBeforeClose = 2;

            Outcome outcome = launch(LoggingMode.DEFAULT);

            List<String> windowEvents = events.stream()
                    .filter(e -> Set.of("initialize", "startingMonitor", "createWindow", "initializeGraphics",
                            "destroyWindow", "terminate").contains(e)).toList();
            runs.put(language, new WindowRun(windowSystem.createdSettings.title(),
                    windowSystem.createdSettings.fullscreen(), windowSystem.createdResolution, windowEvents,
                    windowSystem.framesRendered > 2, outcome.exit(),
                    Arrays.equals(configuration, Files.readAllBytes(harness.settingsFile()))));
            assertTrue(outcome.log().contains("Normal shutdown completed"));
        }
        WindowRun english = runs.get("en");
        assertEquals(new WindowRun("My strategy", fullscreen, new Resolution(1600, 900),
                List.of("initialize", "startingMonitor", "createWindow", "initializeGraphics", "destroyWindow",
                        "terminate"), true, 0, true), english);
        runs.forEach((language, run) -> {
            assertEquals(TITLES.get(language), run.title());
            assertEquals(new WindowRun(english.title(), run.fullscreen(), run.resolution(), run.windowEvents(),
                    run.rendered(), run.exit(), run.configurationUnchanged()), english, language);
        });
    }
}
