package com.pidluzsnij.strategy.localization;

import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.testsupport.FixtureResources;
import com.pidluzsnij.strategy.testsupport.FixtureResources.Unavailability;
import com.pidluzsnij.strategy.testsupport.LocalizationRun;
import com.pidluzsnij.strategy.testsupport.LocalizationRun.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.pidluzsnij.strategy.testsupport.LocalizationRun.FAILURE;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.FALLBACK;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.FILE_LOADED;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.METADATA_LOADED;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Language selection, file fallback, lookup and localization diagnostics, with isolated fixtures. */
class LocalizationInitializerTest {

    private static final List<String> INITIAL = List.of("en", "uk", "cs", "hu");
    private static final Map<String, String> FILES = Map.of("en", "english.properties",
            "uk", "ukrainian.properties", "cs", "czech.properties", "hu", "hungarian.properties");

    @TempDir
    Path temp;

    private int runs;

    private Outcome run(LoggingMode mode, FixtureResources resources, String configured) throws Exception {
        return LocalizationRun.run(temp.resolve("run" + runs++), mode, resources, configured);
    }

    private Outcome run(FixtureResources resources, String configured) throws Exception {
        return run(LoggingMode.DEVELOPMENT, resources, configured);
    }

    /** Successful initialization: one INFO naming the effective language and fallback use, no WARN/ERROR. */
    private static void assertSucceeded(Outcome outcome, String effective, boolean fallback) {
        Localization localization = outcome.get();
        assertEquals(effective, localization.effectiveLanguage());
        assertEquals(fallback, localization.fallbackUsed());
        List<String> info = outcome.containing("INFO ", SUCCESS);
        assertEquals(1, info.size(), outcome.log());
        assertTrue(info.get(0).contains("effective language '" + effective + "'"), info.get(0));
        assertTrue(info.get(0).contains("English fallback used: " + (fallback ? "yes" : "no")), info.get(0));
        assertTrue(outcome.withLevel("ERROR").isEmpty(), outcome.log());
        assertEquals(fallback ? 1 : 0, outcome.containing("WARN ", FALLBACK).size(), outcome.log());
    }

    /** Fatal initialization: exactly one ERROR naming the stage, no success INFO. */
    private static String assertFailed(Outcome outcome, String stage) {
        assertTrue(outcome.localization().isEmpty());
        assertTrue(outcome.containing("INFO ", SUCCESS).isEmpty(), outcome.log());
        List<String> errors = outcome.withLevel("ERROR");
        assertEquals(1, errors.size(), outcome.log());
        assertTrue(errors.get(0).contains(FAILURE), errors.get(0));
        assertTrue(errors.get(0).contains("could not " + stage + " "), errors.get(0));
        return errors.get(0);
    }

    // --- Metadata ---------------------------------------------------------------------------

    @Test
    void validCsvIsDecodedAndExposedInMetadataOrder() throws Exception {
        String metadata = "language_identifier,displayed_name,localization_file\r\n"
                + "\r\n"
                + "hu,Magyar,hungarian.properties\n"
                + "\n"
                + "zz,\"Zed, the \"\"last\"\" one\",zed.properties\r\n"
                + "cs,Čeština,czech.properties\n"
                + "en,English,\"english.properties\"\n"
                + "uk,Українська,ukrainian.properties\n"
                + "\n";
        FixtureResources resources = new FixtureResources().put(LocalizationResources.METADATA_FILE, metadata);

        Outcome outcome = run(resources, "en");

        assertSucceeded(outcome, "en", false);
        assertEquals(List.of(new Language("hu", "Magyar"), new Language("zz", "Zed, the \"last\" one"),
                        new Language("cs", "Čeština"), new Language("en", "English"),
                        new Language("uk", "Українська")),
                outcome.get().languages());
        assertEquals(1, outcome.containing("DEBUG", METADATA_LOADED + " from localization/languages.csv: "
                + "5 language entries").size(), outcome.log());
    }

    @Test
    void additionalMetadataLanguageIsSelectedWithoutAHardcodedMapping() throws Exception {
        FixtureResources resources = new FixtureResources()
                .put(LocalizationResources.METADATA_FILE, FixtureResources.METADATA + "eo,Esperanto,test-fifth.properties\n")
                .put("test-fifth.properties", "test.key=Saluton\n")
                .put("english.properties", "test.key=Hello\n");

        Outcome outcome = run(resources, "eo");

        assertSucceeded(outcome, "eo", false);
        assertEquals("Saluton", outcome.get().text("test.key"));
        assertEquals("eo", outcome.get().configuredLanguage());
        assertEquals(List.of("languages.csv", "test-fifth.properties"), resources.reads, "English is not loaded");
    }

    @ParameterizedTest
    @EnumSource(value = Unavailability.class, names = {"MISSING", "UNREADABLE", "INVALID_UTF8"})
    void unavailableMetadataIsFatal(Unavailability unavailability) throws Exception {
        FixtureResources resources = new FixtureResources()
                .makeUnavailable(LocalizationResources.METADATA_FILE, unavailability);

        Outcome outcome = run(resources, "en");

        String error = assertFailed(outcome, "load language metadata");
        assertTrue(error.contains("resource: localization/languages.csv"), error);
        assertTrue(error.contains(switch (unavailability) {
            case MISSING -> "is missing";
            case UNREADABLE -> "could not be read";
            default -> "is not valid UTF-8";
        }), error);
        if (unavailability == Unavailability.UNREADABLE) {
            assertTrue(error.contains("\tat "), "stack trace is retained: " + error);
            assertTrue(error.contains("java.io.IOException (message omitted)"), error);
        }
        assertEquals(List.of("languages.csv"), resources.reads, "no file is selected or loaded");
        assertTrue(outcome.containing("DEBUG", METADATA_LOADED).isEmpty());
        assertFalse(outcome.log().contains(FixtureResources.UNREADABLE_MARKER));
    }

    static List<String> malformedMetadata() {
        String header = "language_identifier,displayed_name,localization_file\n";
        String en = "en,English,english.properties\n";
        String uk = "uk,Українська,ukrainian.properties\n";
        String cs = "cs,Čeština,czech.properties\n";
        String hu = "hu,Magyar,hungarian.properties\n";
        String rest = uk + cs + hu;
        List<String> cases = new ArrayList<>(List.of(
                "",
                "\n\n",
                en + rest,
                "language_identifier,localization_file,displayed_name\n" + en + rest,
                "Language_identifier,displayed_name,localization_file\n" + en + rest,
                "language_identifier,displayed_name\n" + en + rest,
                "language_identifier,displayed_name,localization_file,extra\n" + en + rest,
                header + "en,English\n" + rest,
                header + "en,English,english.properties,extra\n" + rest,
                header + "en,\"English,english.properties\n" + rest,
                header + "en,\"Eng\"lish,english.properties\n" + rest,
                header + "en,Eng\"lish,english.properties\n" + rest,
                header + "en,\"Multi\nline\",english.properties\n" + rest,
                header + en + rest + "xx,,xx.properties\n",
                header + en + rest + ",Empty,empty.properties\n",
                header + en + rest + "xx,Name,\n",
                header + en + rest + " xx,Name,xx.properties\n",
                header + en + rest + "xx,Name ,xx.properties\n",
                header + en + rest + "xx,\u00A0Name,xx.properties\n",
                header + en + rest + "xx,Name,xx.properties\t\n",
                header + en + rest + "en,English again,other.properties\n",
                header + en + rest + "xx,Duplicate file,english.properties\n",
                header + en + rest + "   \n",
                header + en + rest + "\"\",Quoted empty,q.properties\n"));
        for (String required : List.of(en, uk, cs, hu)) {
            cases.add(header + (en + rest).replace(required, ""));
        }
        cases.add(header + en.replace("English", "Angličtina") + rest);
        cases.add(header + en + rest.replace("ukrainian.properties", "uk.properties"));
        return cases;
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("malformedMetadata")
    void malformedMetadataIsRejectedInFull(String metadata) throws Exception {
        FixtureResources resources = new FixtureResources().put(LocalizationResources.METADATA_FILE, metadata)
                .put("english.properties", "test.key=Hello\n");

        Outcome outcome = run(resources, "en");

        String error = assertFailed(outcome, "load language metadata");
        assertTrue(error.contains("localization/languages.csv is malformed"), error);
        assertEquals(List.of("languages.csv"), resources.reads, "no language is selected from partial metadata");
        assertTrue(outcome.withLevel("WARN ").isEmpty(), outcome.log());
        assertTrue(outcome.containing("DEBUG", METADATA_LOADED).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/etc/english.properties", "C:\\english.properties", "C:english.properties",
            "sub/xx.properties", "sub\\xx.properties", "../xx.properties", "..\\xx.properties", "..", ".",
            "xx.txt", "xx.properties.bak", "xx"})
    void invalidLocalizationFileNamesMakeMetadataMalformed(String fileName) throws Exception {
        String quoted = "\"" + fileName.replace("\"", "\"\"") + "\"";
        FixtureResources resources = new FixtureResources()
                .put(LocalizationResources.METADATA_FILE, FixtureResources.METADATA + "xx,Test," + quoted + "\n");

        Outcome outcome = run(resources, "xx");

        String error = assertFailed(outcome, "load language metadata");
        assertTrue(error.contains("line 6"), error);
        assertEquals(List.of("languages.csv"), resources.reads, "nothing outside the folder is accessed");
    }

    @Test
    void emptyLocalizationFileNameMakesMetadataMalformed() throws Exception {
        FixtureResources resources = new FixtureResources()
                .put(LocalizationResources.METADATA_FILE, FixtureResources.METADATA + "xx,Test,\"\"\n");
        assertFailed(run(resources, "xx"), "load language metadata");
        assertEquals(List.of("languages.csv"), resources.reads);
    }

    // --- Selection --------------------------------------------------------------------------

    @Test
    void configuredLanguageIsSelectedRegardlessOfTheOsLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("uk-UA"), Locale.forLanguageTag("cs-CZ"),
                    Locale.forLanguageTag("hu-HU"), Locale.JAPAN)) {
                Locale.setDefault(locale);
                for (String language : INITIAL) {
                    FixtureResources resources = new FixtureResources();
                    INITIAL.forEach(id -> resources.put(FILES.get(id), "test.key=value-" + id + "\n"));

                    Outcome outcome = run(resources, language);

                    assertSucceeded(outcome, language, false);
                    assertEquals(language, outcome.get().configuredLanguage());
                    assertEquals("value-" + language, outcome.get().text("test.key"), locale + "/" + language);
                    assertEquals(List.of("languages.csv", FILES.get(language)), resources.reads);
                }
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SECRET-UNKNOWN-LANGUAGE", "EN", "Uk", "en-US"})
    void unknownIdentifierFallsBackToEnglishWithoutEchoingIt(String configured) throws Exception {
        FixtureResources resources = new FixtureResources().put("english.properties", "test.key=Hello\n");

        Outcome outcome = run(resources, configured);

        assertSucceeded(outcome, "en", true);
        assertEquals(configured, outcome.get().configuredLanguage(), "the configured preference is kept");
        assertEquals("Hello", outcome.get().text("test.key"));
        String warning = outcome.containing("WARN ", FALLBACK).get(0);
        assertTrue(warning.contains("matches no language metadata entry"), warning);
        assertTrue(warning.contains("falling back to language 'en'"), warning);
        assertTrue(warning.contains("resource: localization/english.properties"), warning);
        assertFalse(outcome.log().contains(configured), "the unknown identifier is not echoed: " + outcome.log());
        assertEquals(List.of("languages.csv", "english.properties"), resources.reads);
    }

    @Test
    void emptySelectedFileIsAValidLanguage() throws Exception {
        for (boolean englishAvailable : List.of(true, false)) {
            FixtureResources resources = new FixtureResources().put("czech.properties", "");
            if (englishAvailable) {
                resources.put("english.properties", "test.key=Hello\n");
            } else {
                resources.remove("english.properties");
            }

            Outcome outcome = run(resources, "cs");

            assertSucceeded(outcome, "cs", false);
            assertEquals("test.key", outcome.get().text("test.key"));
            assertEquals(List.of("languages.csv", "czech.properties"), resources.reads, "English is not loaded");
            assertTrue(outcome.withLevel("WARN ").isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Unavailability.class)
    void nonSelectedFilesAreNeitherLoadedNorValidated(Unavailability unavailability) throws Exception {
        FixtureResources resources = new FixtureResources().put("hungarian.properties", "test.key=Szia\n");
        for (String other : List.of("english.properties", "ukrainian.properties", "czech.properties")) {
            resources.makeUnavailable(other, unavailability);
        }

        Outcome outcome = run(resources, "hu");

        assertSucceeded(outcome, "hu", false);
        assertEquals("Szia", outcome.get().text("test.key"));
        assertEquals(List.of("languages.csv", "hungarian.properties"), resources.reads);
        assertTrue(outcome.withLevel("WARN ").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Unavailability.class)
    void unavailableNonEnglishFileFallsBackToEnglish(Unavailability unavailability) throws Exception {
        FixtureResources resources = new FixtureResources().put("english.properties", "test.key=Hello\n")
                .makeUnavailable("ukrainian.properties", unavailability);

        Outcome outcome = run(resources, "uk");

        assertSucceeded(outcome, "en", true);
        assertEquals("uk", outcome.get().configuredLanguage());
        assertEquals("Hello", outcome.get().text("test.key"), "English supplies the value, not partial Ukrainian");
        String warning = outcome.containing("WARN ", FALLBACK).get(0);
        assertTrue(warning.contains("configured language 'uk' is unavailable"), warning);
        assertTrue(warning.contains("localization/ukrainian.properties"), warning);
        assertTrue(warning.contains(switch (unavailability) {
            case MISSING -> "is missing";
            case UNREADABLE -> "could not be read";
            case INVALID_UTF8 -> "is not valid UTF-8";
            case MALFORMED -> "is malformed (line 2: record has no '=' separator)";
        }), warning);
        assertEquals(List.of("languages.csv", "ukrainian.properties", "english.properties"), resources.reads);
    }

    @Test
    void emptyEnglishFallbackIsAvailable() throws Exception {
        FixtureResources resources = new FixtureResources().put("english.properties", "")
                .remove("czech.properties");

        Outcome outcome = run(LoggingMode.DEFAULT, resources, "cs");

        assertSucceeded(outcome, "en", true);
        assertEquals("test.key", outcome.get().text("test.key"));
    }

    @ParameterizedTest
    @EnumSource(Unavailability.class)
    void unavailableConfiguredEnglishIsFatalWithoutRetry(Unavailability unavailability) throws Exception {
        FixtureResources resources = new FixtureResources().makeUnavailable("english.properties", unavailability);

        Outcome outcome = run(resources, "en");

        String error = assertFailed(outcome, "load localization file");
        assertTrue(error.contains("resource: localization/english.properties"), error);
        assertTrue(outcome.withLevel("WARN ").isEmpty(), "no redundant fallback WARN: " + outcome.log());
        assertEquals(List.of("languages.csv", "english.properties"), resources.reads, "English is not retried");
    }

    @ParameterizedTest
    @EnumSource(Unavailability.class)
    void unavailableEnglishFallbackIsFatalAfterOneWarning(Unavailability unavailability) throws Exception {
        for (String configured : List.of("uk", "SECRET-UNKNOWN")) {
            FixtureResources resources = new FixtureResources().remove("ukrainian.properties")
                    .makeUnavailable("english.properties", unavailability);

            Outcome outcome = run(resources, configured);

            String error = assertFailed(outcome, "load English fallback localization file");
            assertTrue(error.contains("resource: localization/english.properties"), error);
            assertEquals(1, outcome.containing("WARN ", FALLBACK).size(), outcome.log());
            List<String> records = outcome.localizationRecords();
            assertTrue(records.indexOf(outcome.containing("WARN ", FALLBACK).get(0)) < records.indexOf(error));
            long englishReads = resources.reads.stream().filter("english.properties"::equals).count();
            assertEquals(1, englishReads, "no further language is tried");
            assertFalse(outcome.log().contains("SECRET-UNKNOWN"));
        }
    }

    // --- Localization file format -----------------------------------------------------------

    @Test
    void translationFormatPreservesValuesExactly() throws Exception {
        String file = "# leading comment\r\n"
                + "\r\n"
                + "   \t \n"
                + "\u3000\n"
                + "   # indented comment = not a record\n"
                + "\t#tab comment\n"
                + "  test.trimmed \t=value\r\n"
                + "test.equals=a=b==c\n"
                + "test.hash=value # not a comment\n"
                + "test.backslash=C:\\path\\n\\u0041\\\n"
                + "test.continued=first\\\n"
                + "test.space=  padded value \t\n"
                + "ключ.тест=Значення ✓\n"
                + "test.placeholder={0} %s ${x}\n"
                + "test.last=no newline";
        FixtureResources resources = new FixtureResources().put("ukrainian.properties", file);

        Outcome outcome = run(resources, "uk");

        Localization localization = outcome.get();
        assertEquals("value", localization.text("test.trimmed"));
        assertEquals("a=b==c", localization.text("test.equals"));
        assertEquals("value # not a comment", localization.text("test.hash"));
        assertEquals("C:\\path\\n\\u0041\\", localization.text("test.backslash"));
        assertEquals("first\\", localization.text("test.continued"));
        assertEquals("  padded value \t", localization.text("test.space"));
        assertEquals("Значення ✓", localization.text("ключ.тест"));
        assertEquals("{0} %s ${x}", localization.text("test.placeholder"));
        assertEquals("no newline", localization.text("test.last"));
        assertEquals("# indented comment", localization.text("# indented comment"), "comments create no entry");
        assertEquals(1, outcome.containing("DEBUG", FILE_LOADED + " for language 'uk' from "
                + "localization/ukrainian.properties: 9 keys").size(), outcome.log());
    }

    static List<String> malformedTranslations() {
        return List.of(
                "test.key=valid\nno separator\n",
                "test.key=valid\n=value\n",
                "test.key=valid\n \t=value\n",
                "test.key=valid\n\u00A0=value\n",
                "test.key=first\ntest.key=second\n",
                "test.key=first\n  test.key \t=second\n");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("malformedTranslations")
    void malformedFileIsRejectedInFull(String content) throws Exception {
        FixtureResources nonEnglish = new FixtureResources().put("czech.properties", content)
                .put("english.properties", "other.key=English\n");
        Outcome fallback = run(nonEnglish, "cs");
        assertSucceeded(fallback, "en", true);
        assertEquals("test.key", fallback.get().text("test.key"), "no earlier entry or duplicate is retained");
        assertTrue(fallback.containing("WARN ", FALLBACK).get(0).contains("czech.properties is malformed (line 2"));

        FixtureResources english = new FixtureResources().put("english.properties", content);
        Outcome fatal = run(english, "en");
        String error = assertFailed(fatal, "load localization file");
        assertTrue(error.contains("english.properties is malformed (line 2"), error);
    }

    // --- Lookup -----------------------------------------------------------------------------

    @Test
    void presentKeysMatchExactlyAndCaseSensitively() throws Exception {
        FixtureResources resources = new FixtureResources()
                .put("hungarian.properties", "test.title=lower\nTest.Title=Upper\n");

        Localization localization = run(resources, "hu").get();

        assertEquals("lower", localization.text("test.title"));
        assertEquals("Upper", localization.text("Test.Title"));
        assertEquals("TEST.TITLE", localization.text("TEST.TITLE"));
        assertEquals(" test.title", localization.text(" test.title"));
        assertEquals("test.title\t", localization.text("test.title\t"));
    }

    @Test
    void missingKeyReturnsTheKeyWithoutEnglishFallback() throws Exception {
        FixtureResources resources = new FixtureResources().put("ukrainian.properties", "test.present=так\n")
                .put("english.properties", "test.missing=English value\n");

        Localization localization = run(resources, "uk").get();
        int reads = resources.reads.size();

        assertEquals("test.missing", localization.text("test.missing"));
        assertEquals("uk", localization.effectiveLanguage());
        assertEquals(reads, resources.reads.size());
        assertFalse(resources.reads.contains("english.properties"));
    }

    @Test
    void emptyAndWhitespaceOnlyValuesReturnTheKeyWithoutEnglishFallback() throws Exception {
        String values = "test.empty=\n"
                + "test.spaces=   \n"
                + "test.tabs=\t\t\n"
                + "test.unicode=\u00A0\u2003\u3000\u2028\u0085\n"
                + "test.padded= value \n";
        FixtureResources resources = new FixtureResources().put("czech.properties", values)
                .put("english.properties", "test.empty=E\ntest.spaces=E\ntest.tabs=E\ntest.unicode=E\n");

        Localization localization = run(resources, "cs").get();

        for (String key : List.of("test.empty", "test.spaces", "test.tabs", "test.unicode")) {
            assertEquals(key, localization.text(key));
        }
        assertEquals(" value ", localization.text("test.padded"));
        assertEquals("cs", localization.effectiveLanguage());
        assertFalse(resources.reads.contains("english.properties"));
    }

    @Test
    void missingKeysAndValuesInEffectiveEnglishReturnTheKey() throws Exception {
        String english = "test.empty=\ntest.blank= \t\u00A0\ntest.present=Hello\n";
        for (String configured : List.of("en", "uk")) {
            FixtureResources resources = new FixtureResources().put("english.properties", english)
                    .remove("ukrainian.properties");

            Outcome outcome = run(resources, configured);

            assertSucceeded(outcome, "en", !configured.equals("en"));
            Localization localization = outcome.get();
            for (String key : List.of("test.missing", "test.empty", "test.blank")) {
                assertEquals(key, localization.text(key));
            }
            assertEquals("Hello", localization.text("test.present"));
            assertEquals("en", localization.effectiveLanguage());
        }
    }

    @Test
    void invalidLookupArgumentsAreProgrammingErrors() throws Exception {
        FixtureResources resources = new FixtureResources().put("english.properties", "test.key=Hello\n");
        Localization localization = run(resources, "en").get();
        int reads = resources.reads.size();

        assertThrows(NullPointerException.class, () -> localization.text(null));
        for (String invalid : List.of("", " ", "\t\n", "\u00A0", "\u3000 \u2028")) {
            assertThrows(IllegalArgumentException.class, () -> localization.text(invalid), invalid);
        }

        assertEquals("Hello", localization.text("test.key"));
        assertEquals("en", localization.effectiveLanguage());
        assertEquals(reads, resources.reads.size());
    }

    @Test
    void lookupsUseLoadedEntriesUntilTheNextInitialization() throws Exception {
        FixtureResources resources = new FixtureResources()
                .put(LocalizationResources.METADATA_FILE, FixtureResources.METADATA + "eo,Esperanto,test-a.properties\n")
                .put("test-a.properties", "test.key=old\n")
                .put("czech.properties", "test.key=czech\n");
        Localization localization = run(resources, "eo").get();
        int reads = resources.reads.size();

        resources.put("test-a.properties", "test.key=changed\n");
        resources.put(LocalizationResources.METADATA_FILE, FixtureResources.METADATA + "eo,Esperanto,test-b.properties\n")
                .put("test-b.properties", "test.key=remapped\n");
        for (int i = 0; i < 1000; i++) {
            assertEquals("old", localization.text("test.key"));
        }
        assertEquals(reads, resources.reads.size(), "lookups perform no resource reads");
        assertEquals("eo", localization.effectiveLanguage());

        assertEquals("remapped", run(resources, "eo").get().text("test.key"), "changed mapping is observed");
        assertEquals("czech", run(resources, "cs").get().text("test.key"), "changed configured language is observed");
        assertEquals("old", localization.text("test.key"));
    }

    // --- Diagnostics ------------------------------------------------------------------------

    private enum Situation {
        DIRECT_ENGLISH, DIRECT_NON_ENGLISH, UNKNOWN_IDENTIFIER, FILE_FALLBACK, INVALID_METADATA,
        ENGLISH_FAILURE, FALLBACK_FAILURE
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void diagnosticsInBothLoggingModes(LoggingMode mode) throws Exception {
        for (Situation situation : Situation.values()) {
            FixtureResources resources = new FixtureResources()
                    .put("english.properties", "a=1\nb=2\nc=\n")
                    .put("ukrainian.properties", "a=один\n");
            String configured = "uk";
            switch (situation) {
                case DIRECT_ENGLISH -> configured = "en";
                case DIRECT_NON_ENGLISH -> { }
                case UNKNOWN_IDENTIFIER -> configured = "xx";
                case FILE_FALLBACK -> resources.remove("ukrainian.properties");
                case INVALID_METADATA -> resources.put(LocalizationResources.METADATA_FILE, "broken\n");
                case ENGLISH_FAILURE -> {
                    configured = "en";
                    resources.remove("english.properties");
                }
                case FALLBACK_FAILURE -> resources.remove("ukrainian.properties").remove("english.properties");
                default -> throw new AssertionError(situation);
            }

            Outcome outcome = run(mode, resources, configured);
            String context = mode + " " + situation + ": " + outcome.log();

            boolean fails = situation == Situation.INVALID_METADATA || situation == Situation.ENGLISH_FAILURE
                    || situation == Situation.FALLBACK_FAILURE;
            boolean fallback = situation == Situation.UNKNOWN_IDENTIFIER || situation == Situation.FILE_FALLBACK
                    || situation == Situation.FALLBACK_FAILURE;
            assertEquals(fails ? 0 : 1, outcome.containing("INFO ", SUCCESS).size(), context);
            assertEquals(fails ? 1 : 0, outcome.withLevel("ERROR").size(), context);
            assertEquals(fallback ? 1 : 0, outcome.containing("WARN ", FALLBACK).size(), context);
            if (!fails) {
                String effective = situation == Situation.DIRECT_NON_ENGLISH ? "uk" : "en";
                assertSucceeded(outcome, effective, fallback);
            }

            List<String> debug = outcome.withLevel("DEBUG");
            if (mode == LoggingMode.DEFAULT) {
                assertTrue(debug.isEmpty(), context);
                continue;
            }
            assertEquals(situation == Situation.INVALID_METADATA ? 0 : 1,
                    outcome.containing("DEBUG", METADATA_LOADED + " from localization/languages.csv: 4 language "
                            + "entries").size(), context);
            String fileLoaded = switch (situation) {
                case DIRECT_NON_ENGLISH -> "for language 'uk' from localization/ukrainian.properties: 1 keys";
                case DIRECT_ENGLISH, UNKNOWN_IDENTIFIER, FILE_FALLBACK ->
                        "for language 'en' from localization/english.properties: 3 keys";
                default -> null;
            };
            assertEquals(fileLoaded == null ? 0 : 1, outcome.containing("DEBUG", FILE_LOADED).size(), context);
            if (fileLoaded != null) {
                assertEquals(1, outcome.containing("DEBUG", FILE_LOADED + " " + fileLoaded).size(), context);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void diagnosticsNeverDiscloseProtectedContent(LoggingMode mode) throws Exception {
        List<FixtureResources> cases = new ArrayList<>();
        List<String> configured = new ArrayList<>();
        // Malformed metadata containing a marker.
        cases.add(new FixtureResources().put(LocalizationResources.METADATA_FILE,
                FixtureResources.METADATA + "SECRET-CSV-MARKER,\"SECRET-CSV-NAME\"x,secret.properties\n"));
        configured.add("en");
        // Unknown identifier with a marker.
        cases.add(new FixtureResources().put("english.properties", "k=SECRET-VALUE-EN\n"));
        configured.add("SECRET-IDENTIFIER-MARKER");
        // Translation values and malformed translation content with markers, falling back to an unreadable English.
        cases.add(new FixtureResources().put("ukrainian.properties", "SECRET-KEY-MARKER=SECRET-VALUE-UK\n"
                + "SECRET-LINE-MARKER\n").makeUnreadable("english.properties"));
        configured.add("uk");
        // Successful load of values with markers.
        cases.add(new FixtureResources().put("czech.properties", "SECRET-KEY=SECRET-VALUE-CS\n"));
        configured.add("cs");
        // Unreadable metadata whose exception, cause and suppressed exception carry markers.
        cases.add(new FixtureResources().makeUnreadable(LocalizationResources.METADATA_FILE));
        configured.add("en");

        for (int i = 0; i < cases.size(); i++) {
            Outcome outcome = run(mode, cases.get(i), configured.get(i));
            assertFalse(outcome.log().contains("SECRET"), "case " + i + ": " + outcome.log());
            assertFalse(outcome.stderr().contains("SECRET"), outcome.stderr());
        }
    }

    @Test
    void lookupsProduceNoDiagnostics() throws Exception {
        FixtureResources resources = new FixtureResources().put("ukrainian.properties",
                "test.present=так\ntest.empty=\ntest.blank=  \n");
        Path directory = temp.resolve("spam");
        com.pidluzsnij.strategy.testsupport.LogHarness harness = new com.pidluzsnij.strategy.testsupport.LogHarness(
                directory);
        com.pidluzsnij.strategy.logging.LoggingSystem logging = com.pidluzsnij.strategy.logging.LoggingSystem
                .initialize(LoggingMode.DEVELOPMENT, harness.location(),
                        com.pidluzsnij.strategy.logging.FileOperations.SYSTEM, harness.stderr);
        try {
            Localization localization = new LocalizationInitializer(resources).initialize("uk").orElseThrow();
            int startupRecords = harness.records().size();
            for (int i = 0; i < 1000; i++) {
                assertEquals("так", localization.text("test.present"));
                assertEquals("test.missing", localization.text("test.missing"));
                assertEquals("test.empty", localization.text("test.empty"));
                assertEquals("test.blank", localization.text("test.blank"));
            }
            assertEquals(startupRecords, harness.records().size(), harness.log());
            assertEquals(3, startupRecords, "metadata DEBUG, file DEBUG and success INFO: " + harness.log());
        } finally {
            logging.close();
        }
    }

    @Test
    void localizationDiagnosticRepresentationOmitsValues() throws Exception {
        Localization localization = run(new FixtureResources().put("english.properties", "k=SECRET-VALUE\n"), "en")
                .get();
        assertFalse(localization.toString().contains("SECRET"));
        assertSame(localization.languages(), localization.languages());
    }
}
