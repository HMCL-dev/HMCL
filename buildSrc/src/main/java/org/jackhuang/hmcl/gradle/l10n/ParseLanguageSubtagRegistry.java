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
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
/// @see [language-subtag-registry](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry)
public abstract class ParseLanguageSubtagRegistry extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getLanguageSubtagRegistryFile();

    @OutputFile
    public abstract RegularFileProperty getSublanguagesFile();

    /// CSV file storing the mapping from subtag to their default scripts.
    @OutputFile
    public abstract RegularFileProperty getDefaultScriptFile();

    @TaskAction
    public void run() throws IOException {
        List<Item> items;

        try (var reader = Files.newBufferedReader(getLanguageSubtagRegistryFile().getAsFile().get().toPath())) {
            var builder = new ItemsBuilder();
            builder.parse(reader);
            items = builder.items;
        }

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
                    String defaultScript = null;
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

        languageToSub.saveToCSV(getSublanguagesFile());
        scriptToSubtag.saveToCSV(getDefaultScriptFile());
    }

    private static final class MultiMap {
        private final TreeMap<String, Set<String>> allValues = new TreeMap<>(TAG_COMPARATOR);

        void add(String key, String value) {
            allValues.computeIfAbsent(key, k -> new TreeSet<>(TAG_COMPARATOR)).add(value);
        }

        void saveToCSV(RegularFileProperty csvFile) throws IOException {
            try (var writer = Files.newBufferedWriter(csvFile.getAsFile().get().toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                for (Map.Entry<String, Set<String>> entry : allValues.entrySet()) {
                    String key = entry.getKey();
                    Set<String> values = entry.getValue();

                    writer.write(key);

                    for (String value : values) {
                        writer.write(',');
                        writer.write(value);
                    }

                    writer.newLine();
                }
            }
        }
    }

    private static final class Item {
        final Map<String, List<String>> values = new LinkedHashMap<>();

        public @NotNull List<String> allValues(String name) {
            return values.getOrDefault(name, List.of());
        }

        public @NotNull Optional<String> firstValue(String name) {
            return Optional.ofNullable(values.get(name)).map(it -> it.get(0));
        }

        public @Nullable String firstValueOrNull(String name) {
            return firstValue(name).orElse(null);
        }

        public @NotNull String firstValueOrThrow(String name) {
            return firstValue(name).orElseThrow(() -> new GradleException("No value found for " + name + " in " + this));
        }

        public void put(String name, String value) {
            values.computeIfAbsent(name, ignored -> new ArrayList<>(1)).add(value);
        }

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

    private static final class ItemsBuilder {
        private final List<Item> items = new ArrayList<>(1024);
        private Item current = new Item();
        private String currentName = null;
        private String currentValue = null;

        private void updateCurrent() {
            if (currentName != null) {
                current.put(currentName, currentValue);
                currentName = null;
                currentValue = null;
            }
        }

        private void updateItems() throws IOException {
            updateCurrent();

            if (current.values.isEmpty())
                return;

            if (current.firstValue("Type").isEmpty()) {
                if (current.firstValue("File-Date").isPresent()) {
                    current.values.clear();
                    return;
                } else {
                    throw new GradleException("Invalid item: " + current);
                }
            }

            items.add(current);
            current = new Item();
        }

        void parse(BufferedReader reader) throws IOException {
            Pattern linePattern = Pattern.compile("^(?<name>[A-Za-z\\-]+): (?<value>.*)$");

            String line;
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

    private static final Comparator<String> TAG_COMPARATOR = (lang1, lang2) -> {
        if (lang1.length() != lang2.length())
            return Integer.compare(lang1.length(), lang2.length());
        else
            return lang1.compareTo(lang2);
    };
}
