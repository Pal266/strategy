package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.localization.Language;
import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.localization.LocalizationInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundaries between configuration, localization infrastructure and its consumers, verified on the
 * compiled production classes' constant pools.
 */
class LocalizationArchitectureTest {

    private static final String BASE = "com/pidluzsnij/strategy/";
    private static final String MODEL = BASE + "config/";
    private static final String LOCALIZATION = BASE + "localization/";
    private static final List<String> INTERNALS = List.of(LOCALIZATION + "MetadataParser",
            LOCALIZATION + "TranslationFile", LOCALIZATION + "LanguageMetadata", LOCALIZATION + "Utf8",
            LOCALIZATION + "MalformedResourceException", LOCALIZATION + "LocalizationFailure",
            LOCALIZATION + "SanitizedException");

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
        assertTrue(classes.containsKey(LOCALIZATION + "LocalizationInitializer"), classes.keySet().toString());
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

    private static boolean inPackage(String className, String packagePath) {
        return className.startsWith(packagePath) && className.indexOf('/', packagePath.length()) < 0;
    }

    private static List<String> violations(String packagePath, String... forbidden) {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            if (inPackage(name, packagePath)) {
                for (String reference : strings) {
                    for (String prefix : forbidden) {
                        if (reference.contains(prefix)) {
                            violations.add(name + " -> " + reference);
                        }
                    }
                }
            }
        });
        return violations;
    }

    @Test
    void settingsModelKnowsNothingOfLocalizationResourcesOrTheOperatingSystem() {
        assertEquals(List.of(), violations(MODEL, LOCALIZATION, "java/nio/", "java/io/", "java/util/Locale",
                "java/lang/ClassLoader", "dev/dirs/", ".csv", ".properties"));
        assertTrue(classes.get(MODEL + "LocalizationSettings").stream().anyMatch(s -> s.contains(MODEL + "SettingType")));
        assertTrue(classes.get(MODEL + "ApplicationSettings").stream()
                .anyMatch(s -> s.contains(MODEL + "LocalizationSettings")));
    }

    @Test
    void localizationInfrastructureUsesNoConfigurationStorageWindowingOrWorkingDirectory() {
        assertEquals(List.of(), violations(LOCALIZATION, MODEL + "persistence/", "com/electronwill/", "org/lwjgl/",
                "dev/dirs/", BASE + "window/", BASE + "ApplicationLauncher", BASE + "ConfigurationStartup",
                "java/nio/file/Files", "java/nio/file/Paths", "java/nio/file/Path;", "java/io/File;",
                "java/io/FileInputStream", "user.dir", "java/util/Locale", "java/util/ResourceBundle",
                "java/util/Properties"));
    }

    @Test
    void parsingAndStorageTypesStayInsideTheLocalizationPackage() {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            if (!inPackage(name, LOCALIZATION)) {
                for (String internal : INTERNALS) {
                    if (strings.stream().anyMatch(s -> s.contains(internal))) {
                        violations.add(name + " -> " + internal);
                    }
                }
            }
        });
        assertEquals(List.of(), violations);
        for (String internal : INTERNALS) {
            assertTrue(classes.containsKey(internal), internal);
        }
        assertFalse(classes.get(BASE + "window/Application").stream()
                .anyMatch(s -> s.contains(LOCALIZATION + "LocalizationResources")
                        || s.contains(LOCALIZATION + "ClasspathLocalizationResources")),
                "consumers do not require resource-storage types");
    }

    @Test
    void consumerApiExposesOnlyApplicationOwnedTypes() {
        Set<Class<?>> allowed = Set.of(String.class, boolean.class, List.class, Language.class, Localization.class,
                java.util.Optional.class, Object.class, int.class);
        for (Class<?> type : List.of(Localization.class, Language.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                assertTrue(allowed.contains(method.getReturnType()), method.toString());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertTrue(allowed.contains(parameter), method.toString());
                }
            }
        }
        assertEquals(0, Localization.class.getConstructors().length, "only localization initialization creates it");
        for (RecordComponent component : Language.class.getRecordComponents()) {
            assertEquals(String.class, component.getType());
        }
        assertTrue(Modifier.isPublic(LocalizationInitializer.class.getModifiers()));
    }

    @Test
    void noAdditionalParsingLibraryIsRequired() {
        for (String library : List.of("org.apache.commons.csv.CSVParser", "com.opencsv.CSVReader",
                "com.univocity.parsers.csv.CsvParser", "org.apache.commons.configuration2.PropertiesConfiguration")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(library), library);
        }
    }
}
