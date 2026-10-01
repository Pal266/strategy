package com.pidluzsnij.strategy.localization;

import com.pidluzsnij.strategy.ApplicationLauncher;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The application's bundled localization resources: initial metadata and empty language files. */
class BundledLocalizationResourcesTest {

    private static final List<String> FILES =
            List.of("english.properties", "ukrainian.properties", "czech.properties", "hungarian.properties");

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

    @Test
    void referencedLocalizationFilesAreEmptyUtf8Files() throws Exception {
        LocalizationResources bundled = LocalizationResources.bundled();
        for (String file : FILES) {
            byte[] bytes = bundled.read(file);
            assertArrayEquals(new byte[0], bytes, file + " contains no production translation entries");
            assertEquals("", Utf8.decode(bytes));
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

    @Test
    void bundledResourcesInitializeEveryInitialLanguageWithoutTranslations() {
        for (String language : List.of("en", "uk", "cs", "hu")) {
            Localization localization = new LocalizationInitializer(LocalizationResources.bundled())
                    .initialize(language).orElseThrow();
            assertEquals(language, localization.effectiveLanguage());
            assertEquals("any.key", localization.text("any.key"));
        }
    }
}
