package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.Setting;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary of the localized window title, verified on the compiled production classes' constant pools
 * and on the bundled resources: the title is looked up by key through application-owned localization
 * access, with no translated title literals, title-specific parsing or fallback, and no other key or setting.
 */
class WindowTitleArchitectureTest {

    private static final String BASE = "com/pidluzsnij/strategy/";
    private static final String KEY = "application.window.title";
    private static final List<String> TRANSLATIONS =
            List.of("My strategy", "Моя стратегія", "Moje strategie", "Az én stratégiám");

    private static Map<String, Set<String>> classes;

    @BeforeAll
    static void readClasses() throws Exception {
        Path root = Path.of(ApplicationSettings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        classes = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/').replace(".class", "");
                try (InputStream in = Files.newInputStream(file)) {
                    classes.put(name, constantPoolStrings(in));
                }
            }
        }
        assertTrue(classes.containsKey(BASE + "window/WindowSettings"), classes.keySet().toString());
    }

    private static Set<String> constantPoolStrings(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        Set<String> strings = new HashSet<>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> strings.add(in.readUTF());
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                case 5, 6 -> {
                    in.skipBytes(8);
                    i++;
                }
                case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                case 15 -> in.skipBytes(3);
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        return strings;
    }

    @Test
    void noProductionClassContainsATranslatedTitle() {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            for (String string : strings) {
                for (String translation : TRANSLATIONS) {
                    if (string.contains(translation)) {
                        violations.add(name + " -> " + string);
                    }
                }
            }
        });
        assertEquals(List.of(), violations);
    }

    @Test
    void windowTitleIsLookedUpByKeyThroughApplicationOwnedLocalization() {
        Set<String> windowSettings = classes.get(BASE + "window/WindowSettings");
        assertTrue(windowSettings.contains(KEY), "the title references the localization key");
        assertTrue(windowSettings.contains(BASE + "localization/Localization"));
        assertTrue(windowSettings.contains("text"), "lookup through Localization.text");

        List<String> keyReferences = new ArrayList<>();
        classes.forEach((name, strings) -> {
            if (strings.contains(KEY)) {
                keyReferences.add(name);
            }
        });
        assertEquals(List.of(BASE + "window/WindowSettings"), keyReferences,
                "the key is referenced in one place");
    }

    @Test
    void titleHandlingHasNoResourceParsingOrSecondFallback() {
        for (String type : List.of("window/WindowSettings", "window/Application")) {
            for (String reference : classes.get(BASE + type)) {
                for (String forbidden : List.of(".properties", ".csv", BASE + "localization/LocalizationResources",
                        BASE + "localization/LocalizationInitializer", "java/util/Properties",
                        "java/util/ResourceBundle", "java/util/Locale", "java/nio/file/", "java/io/")) {
                    assertFalse(reference.contains(forbidden), type + " -> " + reference);
                }
            }
        }
    }

    @Test
    void noTitleSettingIsAdded() {
        for (Setting<?> setting : ApplicationSettings.SCHEMA.settings()) {
            assertFalse(setting.id().toLowerCase(Locale.ROOT).contains("title"), setting.id());
        }
        assertEquals(List.of("video.fullscreen", "video.resolution", "localization.language"),
                ApplicationSettings.SCHEMA.settings().stream().map(Setting::id).toList());
    }

    @Test
    void languageMetadataIsUnchanged() throws Exception {
        try (InputStream in = ApplicationSettings.class.getClassLoader()
                .getResourceAsStream("localization/languages.csv")) {
            String metadata = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals("""
                    language_identifier,displayed_name,localization_file
                    en,English,english.properties
                    uk,Українська,ukrainian.properties
                    cs,Čeština,czech.properties
                    hu,Magyar,hungarian.properties
                    """, metadata);
        }
    }
}
