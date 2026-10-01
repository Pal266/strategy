package com.pidluzsnij.strategy.config.persistence.toml;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.NewlineStyle;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlVersion;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.PersistedSettings;
import com.pidluzsnij.strategy.config.SettingsSchema;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistence;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistenceException;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistenceException.Operation;
import com.pidluzsnij.strategy.config.persistence.LoadResult;
import com.pidluzsnij.strategy.config.persistence.StorageOperations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Persists application settings as a TOML 1.0 file {@code strategy/application-settings.toml}
 * under a configuration base directory. TOML and NightConfig concerns are confined here.
 */
public final class TomlConfigurationPersistence implements ConfigurationPersistence {

    public static final String DIRECTORY_NAME = "strategy";
    public static final String FILE_NAME = "application-settings.toml";

    private static final String TEMP_PREFIX = FILE_NAME + ".";
    private static final String TEMP_SUFFIX = ".tmp";
    /** Temporary files left behind when a previous save was interrupted, for example by a crash. */
    private static final String STALE_TEMP_PATTERN = TEMP_PREFIX + "*" + TEMP_SUFFIX;

    private final SettingsSchema schema;
    private final Path directory;
    private final Path file;
    private final StorageOperations storage;
    private final Function<PersistedSettings, String> serializer;

    public TomlConfigurationPersistence(SettingsSchema schema, Path configDirectory, StorageOperations storage) {
        this(schema, configDirectory, storage, TomlConfigurationPersistence::serialize);
    }

    /** Allows tests to exercise serialization failures, which cannot occur with the supported value types. */
    TomlConfigurationPersistence(SettingsSchema schema, Path configDirectory, StorageOperations storage,
                                 Function<PersistedSettings, String> serializer) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.directory = configDirectory.toAbsolutePath().resolve(DIRECTORY_NAME);
        this.file = directory.resolve(FILE_NAME);
    }

    public TomlConfigurationPersistence(SettingsSchema schema, Path configDirectory) {
        this(schema, configDirectory, StorageOperations.SYSTEM);
    }

    /** @return the configuration file under {@code configDirectory} */
    public static Path settingsFile(Path configDirectory) {
        return configDirectory.toAbsolutePath().resolve(DIRECTORY_NAME).resolve(FILE_NAME);
    }

    @Override
    public Path file() {
        return file;
    }

    @Override
    public LoadResult load() {
        byte[] bytes;
        try {
            bytes = storage.readAllBytes(file);
        } catch (NoSuchFileException e) {
            return new LoadResult.NotFound(file);
        } catch (IOException | RuntimeException e) {
            return new LoadResult.Failed(new ConfigurationPersistenceException(Operation.READ, file, null, e));
        }

        Map<String, Object> tree;
        try {
            tree = toTree(parse(decode(bytes)));
        } catch (CharacterCodingException e) {
            return new LoadResult.Failed(new ConfigurationPersistenceException(
                    Operation.PARSE, file, "the file is not valid UTF-8", null));
        } catch (RuntimeException e) {
            // Parser diagnostics may quote file contents and are NightConfig-specific: neither is exposed.
            return new LoadResult.Failed(new ConfigurationPersistenceException(
                    Operation.PARSE, file, "the file is not valid TOML 1.0", null));
        }
        return new LoadResult.Loaded(file, ApplicationSettings.normalize(schema, new PersistedSettings(tree)));
    }

    @Override
    public void save(ApplicationSettings settings) throws ConfigurationPersistenceException {
        if (settings.schema() != schema) {
            throw new IllegalArgumentException("settings use a different schema than this persistence");
        }
        byte[] bytes;
        try {
            bytes = serializer.apply(settings.toPersisted()).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            // Writer diagnostics may quote values and are NightConfig-specific: neither is exposed.
            throw new ConfigurationPersistenceException(Operation.SERIALIZE, file,
                    "the settings could not be written as TOML", null);
        }

        try {
            storage.createDirectories(directory);
        } catch (IOException | RuntimeException e) {
            throw new ConfigurationPersistenceException(Operation.CREATE_DIRECTORY, file, null, e);
        }
        deleteStaleTemporaryFiles();

        Path temporary;
        try {
            temporary = storage.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX);
        } catch (IOException | RuntimeException e) {
            throw new ConfigurationPersistenceException(Operation.WRITE, file, null, e);
        }
        boolean replaced = false;
        try {
            try {
                storage.writeDurably(temporary, bytes);
            } catch (IOException | RuntimeException e) {
                throw new ConfigurationPersistenceException(Operation.WRITE, file, null, e);
            }
            try {
                storage.moveAtomically(temporary, file);
            } catch (IOException | RuntimeException e) {
                throw new ConfigurationPersistenceException(Operation.REPLACE, file, null, e);
            }
            replaced = true;
        } finally {
            if (!replaced) {
                deleteQuietly(temporary);
            }
        }
    }

    /** Best effort: a file that cannot be listed or deleted does not prevent the save. */
    private void deleteStaleTemporaryFiles() {
        List<Path> stale;
        try {
            stale = storage.list(directory, STALE_TEMP_PATTERN);
        } catch (IOException | RuntimeException e) {
            return;
        }
        stale.forEach(this::deleteQuietly);
    }

    private void deleteQuietly(Path temporary) {
        try {
            storage.deleteIfExists(temporary);
        } catch (IOException | RuntimeException ignored) {
            // Best effort: the save's own outcome is what gets reported.
        }
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static CommentedConfig parse(String text) {
        TomlParser parser = TomlFormat.instance().createParser();
        parser.setTomlVersion(TomlVersion.v1_0);
        return parser.parse(text);
    }

    // --- NightConfig <-> format-independent tree --------------------------------------------

    private static Map<String, Object> toTree(UnmodifiableConfig config) {
        Map<String, Object> table = new LinkedHashMap<>();
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            table.put(entry.getKey(), toTreeValue(entry.getRawValue()));
        }
        return table;
    }

    private static Object toTreeValue(Object value) {
        if (value instanceof UnmodifiableConfig nested) {
            return toTree(nested);
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object element : list) {
                converted.add(toTreeValue(element));
            }
            return converted;
        }
        return value;
    }

    static String serialize(PersistedSettings persisted) {
        CommentedConfig config = TomlFormat.newConfig(LinkedHashMap::new);
        fill(config, persisted.root());
        TomlWriter writer = TomlFormat.instance().createWriter();
        writer.setNewline(NewlineStyle.UNIX);
        writer.setIndent("");
        return writer.writeToString(config);
    }

    private static void fill(Config config, Map<String, Object> table) {
        table.forEach((key, value) -> config.set(List.of(key), toConfigValue(config, value)));
    }

    @SuppressWarnings("unchecked")
    private static Object toConfigValue(Config parent, Object value) {
        if (value instanceof Map<?, ?> table) {
            Config nested = parent.createSubConfig();
            fill(nested, (Map<String, Object>) table);
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object element : list) {
                converted.add(toConfigValue(parent, element));
            }
            return converted;
        }
        return value;
    }
}
