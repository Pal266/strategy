package com.pidluzsnij.strategy.localization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Validated language metadata: every available language and its localization file, in metadata order. */
final class LanguageMetadata {

    /** One metadata record. */
    record Entry(Language language, String localizationFile) {
    }

    private final List<Entry> entries;
    private final Map<String, Entry> byIdentifier = new LinkedHashMap<>();

    LanguageMetadata(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        for (Entry entry : this.entries) {
            byIdentifier.put(entry.language().identifier(), entry);
        }
    }

    List<Entry> entries() {
        return entries;
    }

    List<Language> languages() {
        return entries.stream().map(Entry::language).toList();
    }

    /** @return the entry whose identifier equals {@code identifier} exactly */
    Optional<Entry> find(String identifier) {
        return Optional.ofNullable(byIdentifier.get(identifier));
    }
}
