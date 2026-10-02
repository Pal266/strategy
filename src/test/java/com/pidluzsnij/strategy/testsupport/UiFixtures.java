package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.localization.LocalizationInitializer;
import com.pidluzsnij.strategy.ui.UiStartupConfiguration;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Isolated UI test fixtures: generated PNG images, the synthetic test fonts, UI resource sets written to
 * temporary bundled roots (directories or archives) and external override roots, and test localization.
 * Nothing here touches the application's bundled production resources or the user's per-user directory.
 */
public final class UiFixtures {

    /** Test font with narrow box glyphs. */
    public static final String FONT_A = "UiTestSansA.ttf";
    /** Test font with wide box glyphs. */
    public static final String FONT_B = "UiTestSansB.ttf";

    /** Strings covering the four default languages, including the bundled window titles. */
    public static final List<String> LANGUAGE_SAMPLES = List.of(
            "My strategy", "Моя стратегія", "Moje strategie", "Az én stratégiám",
            "Ґанок, їжак, єнот, ІЇЄҐ, щ, ь, '", "Příliš žluťoučký kůň úpěl ďábelské ódy",
            "Árvíztűrő tükörfúrógép ŐŰ őű");

    private UiFixtures() {
    }

    // --- Images -----------------------------------------------------------------------------

    /** @return a solid-color PNG; {@code rgba} is {@code 0xRRGGBBAA} */
    public static byte[] png(int width, int height, int rgba) {
        return png(width, height, (x, y) -> rgba);
    }

    /** @return a PNG (8-bit RGBA, no interlace) with each pixel from {@code pixel} as {@code 0xRRGGBBAA} */
    public static byte[] png(int width, int height, IntBinaryOperator pixel) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            for (int y = 0; y < height; y++) {
                raw.write(0);
                for (int x = 0; x < width; x++) {
                    int value = pixel.applyAsInt(x, y);
                    raw.write(value >>> 24);
                    raw.write(value >>> 16 & 0xFF);
                    raw.write(value >>> 8 & 0xFF);
                    raw.write(value & 0xFF);
                }
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed, new Deflater(9))) {
                deflater.write(raw.toByteArray());
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(header);
            data.writeInt(width);
            data.writeInt(height);
            data.write(new byte[] {8, 6, 0, 0, 0});
            chunk(png, "IHDR", header.toByteArray());
            chunk(png, "IDAT", compressed.toByteArray());
            chunk(png, "IEND", new byte[0]);
            return png.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        stream.write(typeBytes);
        stream.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }

    /** A GIF image: a real image format that UI image resources do not support. */
    public static byte[] gif() {
        return new byte[] {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, (byte) 0x80, 0, 0, 0, 0, 0, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, '!', (byte) 0xF9, 4, 1, 0, 0, 0, 0, ',', 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2,
                'D', 1, 0, ';'};
    }

    /** PNG signature followed by garbage. */
    public static byte[] malformedPng() {
        byte[] bytes = png(4, 4, 0xFF0000FF);
        byte[] broken = new byte[40];
        System.arraycopy(bytes, 0, broken, 0, 16);
        for (int i = 16; i < broken.length; i++) {
            broken[i] = (byte) (i * 7);
        }
        return broken;
    }

    // --- Fonts ------------------------------------------------------------------------------

    /** @return the bytes of a synthetic test font from the test class path */
    public static byte[] font(String name) {
        try (InputStream in = UiFixtures.class.getClassLoader().getResourceAsStream("uitest/fonts/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing test font " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A CFF-flavored OpenType header, which is not a TrueType font. */
    public static byte[] openTypeCff() {
        byte[] font = font(FONT_A).clone();
        font[0] = 'O';
        font[1] = 'T';
        font[2] = 'T';
        font[3] = 'O';
        return font;
    }

    /** A TrueType header whose table directory points outside the data. */
    public static byte[] truncatedFont() {
        byte[] font = font(FONT_A);
        byte[] truncated = new byte[300];
        System.arraycopy(font, 0, truncated, 0, truncated.length);
        return truncated;
    }

    // --- Resource sets ----------------------------------------------------------------------

    /** UI resources keyed by relative path. */
    public static final class ResourceSet {
        private final Map<String, byte[]> files = new LinkedHashMap<>();

        public ResourceSet put(String path, byte[] content) {
            files.put(path, content.clone());
            return this;
        }

        public ResourceSet put(String path, String content) {
            return put(path, content.getBytes(StandardCharsets.UTF_8));
        }

        /** Writes the files below {@code root}, creating directories as needed. */
        public Path writeTo(Path root) {
            try {
                Files.createDirectories(root);
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    Path target = root.resolve(file.getKey());
                    Files.createDirectories(target.getParent());
                    Files.write(target, file.getValue());
                }
                return root;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Writes an exploded class-path root whose {@code ui} folder holds the files. */
        public Path writeBundledDirectory(Path classpathRoot) {
            writeTo(classpathRoot.resolve("ui"));
            return classpathRoot;
        }

        /** Writes an archive whose {@code ui} folder holds the files. */
        public Path writeBundledArchive(Path jar) {
            try {
                Files.createDirectories(jar.getParent());
                try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
                    for (Map.Entry<String, byte[]> file : files.entrySet()) {
                        out.putNextEntry(new JarEntry("ui/" + file.getKey()));
                        out.write(file.getValue());
                        out.closeEntry();
                    }
                }
                return jar;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Writes the files as external overrides below {@code configBase/strategy/ui}. */
        public Path writeExternal(Path configBase) {
            return writeTo(externalRoot(configBase));
        }
    }

    public static ResourceSet resources() {
        return new ResourceSet();
    }

    /** @return the external override root of an isolated per-user configuration base directory */
    public static Path externalRoot(Path configBase) {
        return configBase.resolve("strategy").resolve("ui");
    }

    /** @return an isolated class loader seeing only {@code root} (a directory or archive) */
    public static URLClassLoader classLoader(Path root) {
        try {
            return new URLClassLoader(new URL[] {root.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return an isolated UI configuration */
    public static UiStartupConfiguration configuration(ClassLoader bundled, Path configBase, List<String> required,
                                                       Set<String> behaviors) {
        return new UiStartupConfiguration(bundled, () -> configBase,
                required.stream().map(UiResourcePath::of).toList(), behaviors);
    }

    // --- Localization -----------------------------------------------------------------------

    /** @return English localization initialized from {@code englishProperties} in isolated resources */
    public static Localization localization(String englishProperties) {
        return new LocalizationInitializer(new FixtureResources().put("english.properties", englishProperties))
                .initialize("en").orElseThrow();
    }

    // --- File snapshots ---------------------------------------------------------------------

    /** @return path → (SHA-256 of bytes, modification time) for every file below {@code roots} */
    public static Map<String, String> snapshot(Path... roots) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                result.put(root.toString(), "absent");
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.sorted().toList()) {
                    if (Files.isRegularFile(file)) {
                        result.put(file.toString(), sha256(Files.readAllBytes(file)) + " "
                                + Files.getLastModifiedTime(file).toMillis());
                    } else {
                        result.put(file.toString(), "directory");
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return result;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- Definitions ------------------------------------------------------------------------

    /** @return a definition document with the given logical size and components (JSON objects) */
    public static String definition(int width, int height, String... components) {
        return "{\n  \"layout\": {\"width\": " + width + ", \"height\": " + height + "},\n  \"components\": [\n    "
                + String.join(",\n    ", components) + "\n  ]\n}\n";
    }

    public static String image(String id, int x, int y, int w, int h, String image) {
        return "{\"id\": \"" + id + "\", \"type\": \"image\", " + box(x, y, w, h) + ", \"image\": \"" + image + "\"}";
    }

    public static String text(String id, int x, int y, int w, int h, String style) {
        return "{\"id\": \"" + id + "\", \"type\": \"text\", " + box(x, y, w, h) + ", \"text\": " + style + "}";
    }

    public static String button(String id, int x, int y, int w, int h, String behavior, String normal,
                                String hovered, String pressed, String label) {
        return "{\"id\": \"" + id + "\", \"type\": \"button\", " + box(x, y, w, h) + ", \"behavior\": \"" + behavior
                + "\", \"states\": {\"normal\": \"" + normal + "\", \"hovered\": \"" + hovered + "\", \"pressed\": \""
                + pressed + "\"}" + (label == null ? "" : ", \"label\": " + label) + "}";
    }

    public static String style(String key, String font, int size, String color, String align) {
        return "{\"key\": \"" + key + "\", \"font\": \"" + font + "\", \"size\": " + size + ", \"color\": \"" + color
                + "\"" + (align == null ? "" : ", \"align\": \"" + align + "\"") + "}";
    }

    private static String box(int x, int y, int w, int h) {
        return "\"x\": " + x + ", \"y\": " + y + ", \"width\": " + w + ", \"height\": " + h;
    }
}
