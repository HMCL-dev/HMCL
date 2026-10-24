/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.gradle.l10n;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Updates the checked-in sublanguage and default-script mappings from the IANA Language Subtag Registry.
///
/// @author Glavo
/// @see [language-subtag-registry](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry)
@NotNullByDefault
@DisableCachingByDefault(because = "Downloads IANA data to update checked-in resources")
public abstract class UpdateLanguageSubtagRegistry extends DefaultTask {

    /// Identifies the current registry published by IANA.
    private static final URI SOURCE = URI.create("https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry");

    /// Creates an updater that checks the remote registry on every invocation.
    public UpdateLanguageSubtagRegistry() {
        getOutputs().upToDateWhen(task -> false);
    }

    /// Returns the CSV destination mapping macrolanguages to their non-deprecated sublanguages.
    @OutputFile
    public abstract RegularFileProperty getSublanguagesFile();

    /// Returns the CSV destination grouping subtags by their default scripts.
    @OutputFile
    public abstract RegularFileProperty getDefaultScriptFile();

    /// Downloads and parses the registry before replacing the two UTF-8 CSV resources.
    ///
    /// @throws IOException if downloading or writing fails; an earlier output may already have been replaced
    /// @throws GradleException if the registry is malformed, has no records, or has no valid file date
    @TaskAction
    public void run() throws IOException {
        update(readSource());
    }

    /// Downloads the UTF-8 registry using bounded connection and read timeouts.
    ///
    /// @throws IOException if downloading fails
    private static String readSource() throws IOException {
        var connection = SOURCE.toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        try (var input = connection.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /// Parses a registry snapshot and writes sorted mappings with its file date and source URI.
    /// Retains the Traditional Chinese default for Classical Chinese and infers a variant script
    /// only when all its prefixes explicitly specify the same script.
    ///
    /// @throws IOException if reading or writing fails
    /// @throws GradleException if the registry is malformed, has no records, or has no valid file date
    void update(String source) throws IOException {
        var builder = new ItemsBuilder();
        try (var reader = new BufferedReader(new StringReader(source))) {
            builder.parse(reader);
        }
        if (builder.items.isEmpty() || builder.fileDate == null) {
            throw new GradleException("Registry must contain a File-Date and at least one record");
        }
        String header = "# IANA registry version: " + builder.fileDate + "\n"
                + "# Generated from " + SOURCE + ".\n";
        List<Item> items = builder.items;

        MultiMap scriptToSubtag = new MultiMap();
        MultiMap languageToSub = new MultiMap();

        // Classical Chinese should use Traditional Chinese characters by default
        scriptToSubtag.add("Hant", "lzh");

        for (Item item : items) {
            String type = item.firstValueOrThrow("Type");
            if (type.equals("grandfathered") || type.equals("redundant")
                    || !item.allValues("Deprecated").isEmpty())
                continue;

            String subtag = item.firstValueOrThrow("Subtag");

            mainSwitch:
            switch (type) {
                case "language", "extlang" -> {
                    item.firstValue("Macrolanguage")
                            .ifPresent(macroLang -> languageToSub.add(macroLang, subtag));

                    item.firstValue("Suppress-Script")
                            .ifPresent(script -> scriptToSubtag.add(script, subtag));
                }
                case "variant" -> {
                    List<String> prefixes = item.allValues("Prefix");
                    @Nullable String defaultScript = null;
                    for (String prefix : prefixes) {
                        String script = Locale.forLanguageTag(prefix).getScript();
                        if (script.isEmpty()) {
                            break mainSwitch;
                        }

                        if (defaultScript == null) {
                            defaultScript = script;
                        } else {
                            if (!defaultScript.equals(script)) {
                                break mainSwitch;
                            }
                        }
                    }

                    if (defaultScript != null) {
                        scriptToSubtag.add(defaultScript, subtag);
                    }
                }
                case "region", "script" -> {
                    // ignored
                }
                default -> throw new GradleException(String.format("Unknown subtag type: %s", type));
            }
        }

        languageToSub.saveToCSV(getSublanguagesFile(), header);
        scriptToSubtag.saveToCSV(getDefaultScriptFile(), header);
    }

    /// Groups unique tags under keys in deterministic order.
    @NotNullByDefault
    private static final class MultiMap {
        /// Stores sorted keys and sorted, deduplicated values.
        private final TreeMap<String, Set<String>> allValues = new TreeMap<>(TAG_COMPARATOR);

        /// Adds a value, ignoring duplicates for the same key.
        void add(String key, String value) {
            allValues.computeIfAbsent(key, k -> new TreeSet<>(TAG_COMPARATOR)).add(value);
        }

        /// Replaces the UTF-8 CSV with the source header and deterministically sorted rows.
        ///
        /// @throws IOException if writing fails
        void saveToCSV(RegularFileProperty csvFile, String header) throws IOException {
            var csv = new StringBuilder(header);
            allValues.forEach((key, values) -> {
                csv.append(key);
                values.forEach(value -> csv.append(',').append(value));
                csv.append('\n');
            });
            Files.writeString(csvFile.get().getAsFile().toPath(), csv);
        }
    }

    /// Stores repeated registry fields in encounter order.
    @NotNullByDefault
    private static final class Item {
        /// Maps field names to their values in encounter order.
        final Map<String, List<String>> values = new LinkedHashMap<>();

        /// Returns the stored values, or an empty list when the field is absent.
        public List<String> allValues(String name) {
            return values.getOrDefault(name, List.of());
        }

        /// Returns the first field value, if present.
        public Optional<String> firstValue(String name) {
            return Optional.ofNullable(values.get(name)).map(it -> it.get(0));
        }

        /// Returns the first field value, throwing [GradleException] when absent.
        public String firstValueOrThrow(String name) {
            return firstValue(name).orElseThrow(() -> new GradleException("No value found for " + name + " in " + this));
        }

        /// Appends a field value.
        public void put(String name, String value) {
            values.computeIfAbsent(name, ignored -> new ArrayList<>(1)).add(value);
        }

        /// Returns the fields in registry syntax for diagnostics.
        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner("\n");

            values.forEach((name, values) -> {
                for (String value : values) {
                    joiner.add(name + ": " + value);
                }
            });

            return joiner.toString();
        }
    }

    /// Parses registry records, unfolding continuation lines and retaining the header date.
    @NotNullByDefault
    private static final class ItemsBuilder {
        /// Contains completed records, excluding the registry header.
        private final List<Item> items = new ArrayList<>(1024);
        /// Accumulates the current record.
        private Item current = new Item();
        /// Holds the pending field name, or null between fields.
        private @Nullable String currentName = null;
        /// Holds the pending unfolded value, or null between fields.
        private @Nullable String currentValue = null;
        /// Holds the validated registry date, or null before the header is parsed.
        private @Nullable String fileDate;

        /// Commits the pending field to the current record.
        private void updateCurrent() {
            if (currentName != null) {
                current.put(currentName, Objects.requireNonNull(currentValue));
                currentName = null;
                currentValue = null;
            }
        }

        /// Commits a record or validates and stores the registry header date.
        private void updateItems() {
            updateCurrent();

            if (current.values.isEmpty())
                return;

            if (current.firstValue("Type").isEmpty()) {
                if (current.firstValue("File-Date").isPresent()) {
                    String date = current.firstValueOrThrow("File-Date");
                    try {
                        LocalDate.parse(date);
                    } catch (DateTimeParseException e) {
                        throw new GradleException("Invalid registry File-Date: " + date, e);
                    }
                    if (fileDate != null || !items.isEmpty()) {
                        throw new GradleException("Unexpected registry header");
                    }
                    fileDate = date;
                    current.values.clear();
                    return;
                } else {
                    throw new GradleException("Invalid item: " + current);
                }
            }

            items.add(current);
            current = new Item();
        }

        /// Reads all records without closing the supplied reader.
        ///
        /// @throws IOException if reading fails
        /// @throws GradleException if a line or record is malformed
        void parse(BufferedReader reader) throws IOException {
            Pattern linePattern = Pattern.compile("^(?<name>[A-Za-z\\-]+): (?<value>.*)$");

            @Nullable String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                } else if (line.equals("%%")) {
                    updateItems();
                } else if (line.startsWith("  ")) {
                    if (currentValue != null) {
                        currentValue = currentValue + " " + line;
                    } else {
                        throw new GradleException("Invalid line: " + line);
                    }
                } else {
                    updateCurrent();

                    Matcher matcher = linePattern.matcher(line);
                    if (matcher.matches()) {
                        currentName = matcher.group("name");
                        currentValue = matcher.group("value");
                    } else {
                        throw new GradleException("Invalid line: " + line);
                    }
                }
            }

            updateItems();
        }
    }

    /// Orders tags by length, then by their case-sensitive lexical order.
    private static final Comparator<String> TAG_COMPARATOR = (lang1, lang2) -> {
        if (lang1.length() != lang2.length())
            return Integer.compare(lang1.length(), lang2.length());
        else
            return lang1.compareTo(lang2);
    };
}
