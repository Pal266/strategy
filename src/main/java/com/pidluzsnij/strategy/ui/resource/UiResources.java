package com.pidluzsnij.strategy.ui.resource;

import com.pidluzsnij.strategy.ui.UiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Read-only resolution of UI resources. Each requested relative path is resolved independently: an existing
 * file at the same relative path under the external override root is authoritative; otherwise the resource
 * bundled under the {@value #BUNDLED_ROOT} folder of the runtime class path is used.
 * <p>
 * An existing external file that cannot be used is a failure; the bundled version of that resource is never
 * substituted for it. Nothing is ever created, modified or deleted in either root.
 */
public final class UiResources {

    /** Class-path folder holding the application's bundled default UI resources. */
    public static final String BUNDLED_ROOT = "ui";

    /** Name of the application's per-user directory, shared with configuration and logging. */
    public static final String APPLICATION_DIRECTORY = "strategy";

    /** Name of the external override root within the application's per-user directory. */
    public static final String EXTERNAL_DIRECTORY = "ui";

    static final String STAGE = "resolve UI resource";

    private final Logger log = LoggerFactory.getLogger(UiResources.class);
    private final ClassLoader bundled;
    private final Path externalRoot;

    /**
     * @param bundled      class loader whose {@value #BUNDLED_ROOT} folder holds the bundled resources
     * @param externalRoot absolute external override root; it need not exist
     */
    public UiResources(ClassLoader bundled, Path externalRoot) {
        this.bundled = Objects.requireNonNull(bundled, "bundled");
        this.externalRoot = Objects.requireNonNull(externalRoot, "externalRoot").toAbsolutePath().normalize();
    }

    /** @return the external override root under the per-user configuration base directory */
    public static Path externalRoot(Path configurationBaseDirectory) {
        return configurationBaseDirectory.toAbsolutePath().resolve(APPLICATION_DIRECTORY).resolve(EXTERNAL_DIRECTORY);
    }

    /** @return the absolute external override root */
    public Path externalRoot() {
        return externalRoot;
    }

    /**
     * Resolves and reads one resource.
     *
     * @throws UiException when an existing external override cannot be used, or when neither an external
     *                     override nor a bundled resource exists or the bundled resource cannot be read
     */
    public ResolvedUiResource resolve(UiResourcePath path) throws UiException {
        Objects.requireNonNull(path, "path");
        Path external = externalFile(path);
        if (external != null) {
            ResolvedUiResource resource = new ResolvedUiResource(path, UiResourceOrigin.EXTERNAL, readExternal(path, external));
            log.debug("UI resource '{}' resolved from external override {}", path, external);
            return resource;
        }
        String name = BUNDLED_ROOT + "/" + path.value();
        byte[] bytes;
        try (InputStream in = bundled.getResourceAsStream(name)) {
            if (in == null) {
                throw new UiException(STAGE, path.value(),
                        "the resource exists neither as an external override nor as a bundled resource");
            }
            bytes = in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            throw new UiException(STAGE, "bundled " + path, "the bundled resource could not be read", e);
        }
        log.debug("UI resource '{}' resolved from bundled resources", path);
        return new ResolvedUiResource(path, UiResourceOrigin.BUNDLED, bytes);
    }

    /** @return the existing external file for {@code path}, or {@code null} when there is no override */
    private Path externalFile(UiResourcePath path) throws UiException {
        String resource = "external " + path;
        if (!Files.exists(externalRoot, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isDirectory(externalRoot)) {
            throw new UiException(STAGE, resource, "the external UI override root is not a directory");
        }
        Path file = externalRoot;
        for (String segment : path.segments()) {
            file = file.resolve(segment);
        }
        if (!file.normalize().startsWith(externalRoot)) {
            throw new UiException(STAGE, resource, "the path leaves the external UI override root");
        }
        // A symbolic link counts as an existing override even when its target is missing.
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? file : null;
    }

    private byte[] readExternal(UiResourcePath path, Path file) throws UiException {
        String resource = "external " + path;
        try {
            Path real = file.toRealPath();
            if (!real.startsWith(externalRoot.toRealPath())) {
                throw new UiException(STAGE, resource, "the external override resolves outside the external UI override root");
            }
            if (!Files.isRegularFile(real)) {
                throw new UiException(STAGE, resource, "the external override is not a regular file");
            }
            return Files.readAllBytes(real);
        } catch (IOException | RuntimeException e) {
            throw new UiException(STAGE, resource, "the external override could not be read", e);
        }
    }
}
