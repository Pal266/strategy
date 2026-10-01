package com.pidluzsnij.strategy.localization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.Objects;

/**
 * Localization resources loaded through a class loader from the {@value LocalizationResources#FOLDER}
 * folder of its class path, whether that is an exploded directory or an archive. Access never depends
 * on the process working directory or on the source tree.
 */
public final class ClasspathLocalizationResources implements LocalizationResources {

    private final ClassLoader classLoader;

    public ClasspathLocalizationResources(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    @Override
    public byte[] read(String fileName) throws IOException {
        String resource = resourceName(fileName);
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new NoSuchFileException(resource);
            }
            return in.readAllBytes();
        }
    }
}
