package com.pidluzsnij.strategy.localization;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

/**
 * Read-only access to the files of one localization resource folder: the language metadata
 * {@value #METADATA_FILE} and the localization files it references.
 * <p>
 * Implementations never create or modify resources. Automated tests supply isolated implementations
 * instead of the application's bundled resources.
 */
public interface LocalizationResources {

    /** Name of the application-owned localization resource folder. */
    String FOLDER = "localization";

    /** Name of the language metadata file within {@link #FOLDER}. */
    String METADATA_FILE = "languages.csv";

    /**
     * Reads one file of the localization folder.
     *
     * @param fileName a simple, already validated file name within the localization folder
     * @return the file's complete contents
     * @throws NoSuchFileException when the file does not exist
     * @throws IOException         when the file exists but cannot be read
     */
    byte[] read(String fileName) throws IOException;

    /** @return the name of {@code fileName} used in diagnostics, relative to the resource root */
    default String resourceName(String fileName) {
        return FOLDER + "/" + fileName;
    }

    /** @return the localization resources bundled with the application on its runtime class path */
    static LocalizationResources bundled() {
        return new ClasspathLocalizationResources(LocalizationResources.class.getClassLoader());
    }
}
