package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.ui.UiStartupConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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

import static com.pidluzsnij.strategy.BuildConstraintsTest.jarName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundaries of the UI foundation, verified on the compiled production classes' constant pools: no widget
 * toolkit, OpenGL confined to the renderer, STB confined to asset decoding, declarative definitions with no
 * code-loading or scripting mechanism, no alternative override root, and no screen-specific presentation.
 */
class UiArchitectureTest {

    private static final String BASE = "com/pidluzsnij/strategy/";
    private static final String UI = BASE + "ui/";
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
        assertTrue(classes.containsKey(UI + "UiFoundation"), classes.keySet().toString());
    }

    static Set<String> constantPoolStrings(InputStream stream) throws IOException {
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

    private static List<String> violations(String packagePrefix, String... forbidden) {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            if (name.startsWith(packagePrefix)) {
                for (String reference : strings) {
                    for (String pattern : forbidden) {
                        if (reference.contains(pattern)) {
                            violations.add(name + " -> " + reference);
                        }
                    }
                }
            }
        });
        return violations;
    }

    @Test
    void noWidgetToolkitIsUsed() {
        assertEquals(List.of(), violations(BASE, "javax/swing/", "java/awt/", "javafx/", "org/eclipse/swt/",
                "com/badlogic/", "imgui/", "org/lwjgl/nuklear/"));
        for (String toolkit : List.of("javafx.application.Application", "org.eclipse.swt.widgets.Display",
                "org.lwjgl.nuklear.Nuklear", "imgui.ImGui")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(toolkit), toolkit);
        }
    }

    @Test
    void openGlIsConfinedToTheRendererAndTheWindowSystem() {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            boolean allowed = name.startsWith(UI + "render/gl/") || name.startsWith(BASE + "window/LwjglWindowSystem");
            if (!allowed && strings.stream().anyMatch(s -> s.startsWith("org/lwjgl/opengl/"))) {
                violations.add(name);
            }
        });
        assertEquals(List.of(), violations);
        assertTrue(classes.get(UI + "render/gl/GlUiGraphics").stream().anyMatch(s -> s.startsWith("org/lwjgl/opengl/GL20")));
        assertEquals(List.of(), violations(UI, "org/lwjgl/glfw/"), "the UI foundation creates no window");
    }

    @Test
    void stbIsConfinedToAssetDecoding() {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            if (!name.startsWith(UI + "asset/") && strings.stream().anyMatch(s -> s.startsWith("org/lwjgl/stb/"))) {
                violations.add(name);
            }
        });
        assertEquals(List.of(), violations);
        assertTrue(classes.get(UI + "asset/UiImageDecoder").contains("org/lwjgl/stb/STBImage"));
        assertTrue(classes.get(UI + "asset/UiFont").contains("org/lwjgl/stb/STBTruetype"));
    }

    @Test
    void uiResourcesCannotLoadOrExecuteCode() {
        assertFalse(classes.entrySet().stream().anyMatch(e -> e.getKey().startsWith(UI)
                && e.getValue().contains("java/lang/Runtime")), "no process or runtime access");
        assertEquals(List.of(), violations(UI, "java/lang/reflect/", "javax/script/", "forName", "loadLibrary",
                "java/lang/Runtime.", "java/lang/ProcessBuilder", "defineClass", "newInstance", "getMethod",
                "getDeclaredMethod", "java/lang/ClassLoader.loadClass", "loadClass", "Nashorn", "groovy/",
                "org/mozilla/javascript"));
    }

    @Test
    void definitionsAndModelKnowNothingOfGraphicsWindowingOrFiles() {
        assertEquals(List.of(), violations(UI + "definition/", "org/lwjgl/", BASE + "window/", "java/nio/file/",
                "java/io/File", "java/lang/ClassLoader", "com/electronwill/"));
        assertEquals(List.of(), violations(UI + "layout/", "org/lwjgl/", BASE + "window/"));
        assertEquals(List.of(), violations(UI + "input/", "org/lwjgl/", BASE + "window/"));
    }

    @Test
    void uiDoesNotDependOnTheWindowLayerOrStartup() {
        assertEquals(List.of(), violations(UI, BASE + "window/", BASE + "ApplicationLauncher", BASE + "logging/",
                BASE + "ConfigurationStartup", BASE + "config/persistence/toml/"));
    }

    @Test
    void thereIsNoAlternativeUiRoot() {
        assertEquals(List.of(), violations(UI, "getenv", "user.dir", "user.home", "getProperty", "dev/dirs/",
                "java/nio/file/Paths"));
        // The only external root is derived from the established per-user configuration location.
        assertTrue(classes.get(UI + "UiStartupConfiguration").contains(BASE + "config/persistence/ConfigurationLocation"));
        long locations = Stream.of(UiStartupConfiguration.class.getRecordComponents())
                .filter(c -> c.getType() == com.pidluzsnij.strategy.config.persistence.ConfigurationLocation.class)
                .count();
        assertEquals(1, locations, "one location; no separate UI-root setting");
        for (RecordComponent component : UiStartupConfiguration.class.getRecordComponents()) {
            assertFalse(component.getType() == Path.class || component.getType() == String.class, component.toString());
        }
    }

    @Test
    void nightConfigIsNotUsedForUiDefinitions() {
        assertEquals(List.of(), violations(UI, "com/electronwill/"));
    }

    @Test
    void noScreenSpecificPresentationIsHardcoded() {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            if (name.startsWith(UI)) {
                for (String string : strings) {
                    boolean resourceName = (string.endsWith(".png") || string.endsWith(".ttf") || string.endsWith(".json"))
                            && !string.equals(".png") && !string.equals(".ttf") && !string.equals(".json");
                    boolean bundledRootConstant = string.replace("\001", "").equals("ui/") && name.equals(UI + "resource/UiResources");
                    if (resourceName || (string.startsWith("ui/") && !bundledRootConstant) || string.startsWith("screens/")
                            || string.startsWith("images/") || string.startsWith("fonts/")) {
                        violations.add(name + " -> " + string);
                    }
                }
            }
        });
        assertEquals(List.of(), violations);
    }

    @Test
    void activationIsSemanticDataForApplicationCode() throws Exception {
        Method listenerType = com.pidluzsnij.strategy.ui.UiFoundation.class.getMethod("show",
                com.pidluzsnij.strategy.ui.UiScreen.class, java.util.function.Consumer.class);
        assertEquals(2, listenerType.getParameterCount());
        Constructor<?> activation = com.pidluzsnij.strategy.ui.input.UiActivation.class.getDeclaredConstructors()[0];
        for (Parameter parameter : activation.getParameters()) {
            assertEquals(String.class, parameter.getType());
        }
    }

    @Test
    void stbAndLwjglResolveAtTheSpecifiedVersions() throws Exception {
        assertEquals("lwjgl-stb-3.4.3.jar", jarName(org.lwjgl.stb.STBImage.class));
        assertEquals("lwjgl-3.4.3.jar", jarName(org.lwjgl.Version.class));
        assertEquals("lwjgl-glfw-3.4.3.jar", jarName(org.lwjgl.glfw.GLFW.class));
        assertEquals("lwjgl-opengl-3.4.3.jar", jarName(org.lwjgl.opengl.GL.class));
    }

    @Test
    void stbNativeLibraryMatchesThePlatformNatives() {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(java.util.Locale.ROOT);
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        boolean x86 = arch.equals("x86") || arch.equals("i386") || arch.equals("i686");
        String platform;
        String classifier;
        String library;
        if (os.contains("win")) {
            platform = "windows";
            classifier = arm64 ? "natives-windows-arm64" : x86 ? "natives-windows-x86" : "natives-windows";
            library = "lwjgl_stb.dll";
        } else if (os.contains("mac")) {
            platform = "macos";
            classifier = arm64 ? "natives-macos-arm64" : "natives-macos";
            library = "liblwjgl_stb.dylib";
        } else {
            platform = "linux";
            classifier = arm64 ? "natives-linux-arm64" : "natives-linux";
            library = "liblwjgl_stb.so";
        }
        String resource = platform + "/" + (arm64 ? "arm64" : x86 ? "x86" : "x64") + "/org/lwjgl/stb/" + library;
        java.net.URL url = UiArchitectureTest.class.getClassLoader().getResource(resource);
        assertTrue(url != null, "native library missing from classpath: " + resource);
        assertTrue(url.toString().contains("/lwjgl-stb-3.4.3-" + classifier + ".jar!"), url.toString());
    }

    @Test
    void noUnlistedDependencyIsAdded() throws Exception {
        Path classes = Path.of(ApplicationSettings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String pom = Files.readString(classes.getParent().getParent().resolve("pom.xml"));
        List<String> artifacts = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<artifactId>([^<]+)</artifactId>").matcher(pom);
        while (matcher.find()) {
            artifacts.add(matcher.group(1));
        }
        Set<String> allowed = Set.of("strategy", "slf4j-api", "logback-classic", "logback-core", "directories", "toml",
                "lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb", "junit-jupiter", "maven-compiler-plugin",
                "maven-surefire-plugin");
        for (String artifact : artifacts) {
            assertTrue(allowed.contains(artifact), artifact);
        }
        assertTrue(pom.contains("<lwjgl.version>3.4.3</lwjgl.version>"));
        assertEquals(2, artifacts.stream().filter("lwjgl-stb"::equals).count(), "API and platform natives");
    }

    // --- SPEC007 main menu ---------------------------------------------------------------------

    @Test
    void mainMenuBehaviorIsApplicationCodeOutsideTheUiFoundation() {
        assertTrue(classes.containsKey(BASE + "menu/MainMenu"), classes.keySet().toString());
        assertEquals(List.of(), violations(UI, BASE + "menu/"), "the UI foundation knows no production screen");
        assertEquals(List.of(), violations(BASE + "menu/", "org/lwjgl/", BASE + "window/", "java/lang/reflect/",
                "javax/script/", "forName", "loadClass", "java/lang/Runtime", "java/lang/ProcessBuilder",
                "java/lang/System.exit", "halt"), "the menu requests shutdown only through the application");
        assertTrue(classes.get(BASE + "window/Application").contains(BASE + "menu/MainMenu"),
                "the application shows the main menu after startup");
    }

    @Test
    void nothingTerminatesTheProcessAbruptly() {
        List<String> violations = new ArrayList<>();
        classes.forEach((name, strings) -> {
            if (!name.equals(BASE + "Main") && strings.contains("exit") && strings.contains("java/lang/System")) {
                violations.add(name);
            }
            if (strings.contains("halt") && strings.contains("java/lang/Runtime")) {
                violations.add(name);
            }
        });
        assertEquals(List.of(), violations, "only Main ends the process, with the launcher's exit code");
    }
}
