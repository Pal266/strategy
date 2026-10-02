package com.pidluzsnij.strategy.localization;

import com.pidluzsnij.strategy.ApplicationLauncher;
import com.pidluzsnij.strategy.text.UnicodeWhiteSpace;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The application's bundled localization resources: initial metadata and the window-title translations. */
class BundledLocalizationResourcesTest {

    private static final List<String> FILES =
            List.of("english.properties", "ukrainian.properties", "czech.properties", "hungarian.properties");
    private static final String TITLE_KEY = "application.window.title";
    /** The specified title translation of each bundled file, in metadata order. */
    private static final Map<String, String> TITLES = new LinkedHashMap<>();

    static {
        TITLES.put("english.properties", "My strategy");
        TITLES.put("ukrainian.properties", "Моя стратегія");
        TITLES.put("czech.properties", "Moje strategie");
        TITLES.put("hungarian.properties", "Az én stratégiám");
    }

    @Test
    void initialMetadataListsTheFourLanguagesInOrder() throws Exception {
        LocalizationResources bundled = LocalizationResources.bundled();
        String text = Utf8.decode(bundled.read(LocalizationResources.METADATA_FILE));
        List<String> records = Utf8.lines(text).stream().filter(line -> !line.isEmpty()).toList();

        assertEquals(List.of("language_identifier,displayed_name,localization_file",
                "en,English,english.properties",
                "uk,Українська,ukrainian.properties",
                "cs,Čeština,czech.properties",
                "hu,Magyar,hungarian.properties"), records);
        LanguageMetadata metadata = MetadataParser.parse(text);
        assertEquals(List.of(new Language("en", "English"), new Language("uk", "Українська"),
                new Language("cs", "Čeština"), new Language("hu", "Magyar")), metadata.languages());
        assertEquals(FILES, metadata.entries().stream().map(LanguageMetadata.Entry::localizationFile).toList());
    }

    /**
     * Each referenced file is a valid UTF-8 localization file whose only production entry is the window
     * title, occurring exactly once with the specified translation. Before this change the files were
     * empty, so there are no earlier unrelated entries to preserve.
     */
    @Test
    void referencedLocalizationFilesContainOnlyTheWindowTitleTranslation() throws Exception {
        LocalizationResources bundled = LocalizationResources.bundled();
        for (Map.Entry<String, String> expected : TITLES.entrySet()) {
            String text = Utf8.decode(bundled.read(expected.getKey()));
            TranslationFile file = TranslationFile.parse(text);

            assertEquals(1, file.keyCount(), expected.getKey() + " has no other production key");
            assertEquals(Map.of(TITLE_KEY, expected.getValue()), file.usableValues(), expected.getKey());
            long occurrences = Utf8.lines(text).stream()
                    .filter(line -> line.contains("="))
                    .filter(line -> UnicodeWhiteSpace.trim(line.substring(0, line.indexOf('='))).equals(TITLE_KEY))
                    .count();
            assertEquals(1, occurrences, expected.getKey() + " defines the title key exactly once");
        }
    }

    @Test
    void resourcesAreBundledOnTheRuntimeClassPathInTheLocalizationFolder() throws Exception {
        URL metadata = ApplicationLauncher.class.getClassLoader().getResource("localization/languages.csv");
        assertNotNull(metadata);
        try (InputStream in = metadata.openStream()) {
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).startsWith("language_identifier,"));
        }
        for (String file : FILES) {
            assertNotNull(ApplicationLauncher.class.getClassLoader().getResource("localization/" + file), file);
        }
    }

    /**
     * Class-path lookup of a directory on a case-insensitive file system ignores case, but lookup in an
     * archive does not; every referenced file name must therefore match a bundled file name exactly.
     */
    @Test
    void metadataFileNamesMatchBundledFileNamesExactlyIncludingCase() throws Exception {
        LanguageMetadata metadata = MetadataParser.parse(
                Utf8.decode(LocalizationResources.bundled().read(LocalizationResources.METADATA_FILE)));
        URI folder = ApplicationLauncher.class.getClassLoader().getResource("localization/languages.csv").toURI();
        Set<String> bundled = new HashSet<>();
        if (folder.getScheme().equals("jar")) {
            try (FileSystem jar = FileSystems.newFileSystem(folder, Map.of());
                 Stream<Path> files = Files.list(jar.getPath("/localization"))) {
                files.forEach(file -> bundled.add(file.getFileName().toString()));
            }
        } else {
            try (Stream<Path> files = Files.list(Path.of(folder).getParent())) {
                files.forEach(file -> bundled.add(file.getFileName().toString()));
            }
        }

        assertTrue(bundled.contains(LocalizationResources.METADATA_FILE), bundled.toString());
        for (LanguageMetadata.Entry entry : metadata.entries()) {
            assertTrue(bundled.contains(entry.localizationFile()),
                    entry.localizationFile() + " must match a bundled file exactly: " + bundled);
        }
    }

    @Test
    void bundledResourcesInitializeEveryInitialLanguageWithItsTitle() {
        Map<String, String> titles = Map.of("en", "My strategy", "uk", "Моя стратегія", "cs", "Moje strategie",
                "hu", "Az én stratégiám");
        for (String language : List.of("en", "uk", "cs", "hu")) {
            Localization localization = new LocalizationInitializer(LocalizationResources.bundled())
                    .initialize(language).orElseThrow();
            assertEquals(language, localization.effectiveLanguage());
            assertFalse(localization.fallbackUsed());
            assertEquals(titles.get(language), localization.text(TITLE_KEY));
            assertEquals("any.key", localization.text("any.key"), "no other production translation");
        }
    }
}
