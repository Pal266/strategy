package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistence;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistenceException;
import com.pidluzsnij.strategy.config.persistence.LoadResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Separation of configuration responsibilities, verified on the compiled production classes'
 * constant pools (every referenced class, field and method type appears there).
 */
class ConfigurationArchitectureTest {

    private static final String BASE = "com/pidluzsnij/strategy/";
    private static final String MODEL = BASE + "config/";
    private static final String PERSISTENCE = BASE + "config/persistence/";
    private static final String TOML = BASE + "config/persistence/toml/";
    private static final String NIGHT_CONFIG = "com/electronwill/";

    /** Production class name (internal form) to the constant-pool strings it references. */
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
        assertTrue(classes.containsKey(MODEL + "ApplicationSettings"), classes.keySet().toString());
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
    void nightConfigIsConfinedToTheTomlImplementation() {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            if (!inPackage(name, TOML) && strings.stream().anyMatch(s -> s.contains(NIGHT_CONFIG))) {
                violations.add(name);
            }
        });
        assertEquals(List.of(), violations);
        assertTrue(classes.get(TOML + "TomlConfigurationPersistence").stream().anyMatch(s -> s.contains(NIGHT_CONFIG)));
    }

    @Test
    void applicationSettingsKnowsNothingOfFormatStorageOrStartup() {
        assertEquals(List.of(), violations(MODEL, NIGHT_CONFIG, "java/nio/file/", "java/io/File", "dev/dirs/",
                PERSISTENCE, BASE + "ApplicationLauncher", BASE + "ConfigurationStartup", BASE + "window/",
                BASE + "logging/", "org/slf4j/", "ch/qos/"));
    }

    @Test
    void persistenceAbstractionHasNoFormatOrStartupPolicy() {
        assertEquals(List.of(), violations(PERSISTENCE, NIGHT_CONFIG, TOML, BASE + "ApplicationLauncher",
                BASE + "ConfigurationStartup", BASE + "window/", BASE + "logging/", "org/slf4j/", "ch/qos/"));
    }

    @Test
    void tomlImplementationHasNoStartupPolicy() {
        assertEquals(List.of(), violations(TOML, BASE + "ApplicationLauncher", BASE + "ConfigurationStartup",
                BASE + "window/", BASE + "logging/", "org/slf4j/", "ch/qos/"));
    }

    @Test
    void startupPolicyUsesOnlyTheFormatIndependentAbstraction() {
        Set<String> startup = classes.get(BASE + "ConfigurationStartup");
        assertTrue(startup.stream().anyMatch(s -> s.contains(PERSISTENCE + "LoadResult")));
        assertFalse(startup.stream().anyMatch(s -> s.contains(TOML) || s.contains(NIGHT_CONFIG)), startup.toString());
        assertFalse(classes.get(BASE + "ApplicationLauncher").stream().anyMatch(s -> s.contains(NIGHT_CONFIG)));
    }

    @Test
    void publicSignaturesExposeNoNightConfigTypes() {
        for (Class<?> type : List.of(ApplicationSettings.class, ConfigurationPersistence.class, LoadResult.class,
                LoadResult.Loaded.class, LoadResult.NotFound.class, LoadResult.Failed.class,
                ConfigurationPersistenceException.class,
                com.pidluzsnij.strategy.config.persistence.toml.TomlConfigurationPersistence.class)) {
            List<Executable> members = new ArrayList<>(List.of(type.getDeclaredMethods()));
            members.addAll(List.<Constructor<?>>of(type.getDeclaredConstructors()));
            for (Executable member : members) {
                if (!Modifier.isPublic(member.getModifiers())) {
                    continue;
                }
                List<Class<?>> types = new ArrayList<>(List.of(member.getParameterTypes()));
                types.addAll(List.of(member.getExceptionTypes()));
                if (member instanceof Method method) {
                    types.add(method.getReturnType());
                }
                for (Class<?> used : types) {
                    assertFalse(used.getName().startsWith("com.electronwill"), member.toString());
                }
            }
            if (type.isRecord()) {
                for (RecordComponent component : type.getRecordComponents()) {
                    assertFalse(component.getType().getName().startsWith("com.electronwill"), component.toString());
                }
            }
        }
    }
}
