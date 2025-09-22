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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/// @author Glavo
public abstract class CreateLocaleNamesResourceBundle extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getLanguagesFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    private static String mapToFileName(String base, String ext, Locale locale) {
        if (locale.equals(Locale.ENGLISH))
            return base + "." + ext;
        else if (locale.toLanguageTag().equals("zh-Hans"))
            return base + "_zh." + ext;
        else
            return base + "_" + locale.toLanguageTag().replace('-', '_') + "." + ext;
    }

    private static final ResourceBundle.Control CONTROL = new ResourceBundle.Control() {
    };

    @TaskAction
    public void run() throws IOException {
        Path languagesFile = getLanguagesFile().get().getAsFile().toPath();
        Path outputDir = getOutputDirectory().get().getAsFile().toPath();

        if (Files.isDirectory(outputDir)) {
            Files.walkFileTree(outputDir, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) throws IOException {
                    if (!dir.equals(outputDir))
                        Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Files.deleteIfExists(outputDir);
        Files.createDirectories(outputDir);

        List<Locale> supportedLanguages;
        try (var reader = Files.newBufferedReader(languagesFile)) {
            supportedLanguages = new Gson().fromJson(reader, new TypeToken<List<String>>() {
                    }).stream()
                    .map(Locale::forLanguageTag)
                    .sorted(LocalizationUtils::compareLocale)
                    .toList();
        }

        if (!supportedLanguages.contains(Locale.ENGLISH))
            throw new GradleException("Missing english in supported languages: " + supportedLanguages);

        // Ensure English is at the first position, this assumption will be used later
        if (!supportedLanguages.get(0).equals(Locale.ENGLISH)) {
            supportedLanguages = new ArrayList<>(supportedLanguages);
            supportedLanguages.remove(Locale.ENGLISH);
            supportedLanguages.add(0, Locale.ENGLISH);
        }

        EnumMap<LocaleField, SortedSet<String>> names = new EnumMap<>(LocaleField.class);
        for (LocaleField field : LocaleField.values()) {
            names.put(field, supportedLanguages.stream()
                    .map(field::get)
                    .filter(it -> !it.isBlank())
                    .collect(Collectors.toCollection(() -> new TreeSet<>(field))));
        }

        Map<Locale, Properties> overrides = new HashMap<>();
        for (Locale currentLanguage : supportedLanguages) {
            InputStream overrideFile = CreateLocaleNamesResourceBundle.class.getResourceAsStream(
                    mapToFileName("LocaleNamesOverride", "properties", currentLanguage));
            Properties overrideProperties = new Properties();
            if (overrideFile != null) {
                try (var reader = new InputStreamReader(overrideFile, StandardCharsets.UTF_8)) {
                    overrideProperties.load(reader);
                }
            }
            overrides.put(currentLanguage, overrideProperties);
        }

        Map<Locale, LocaleNames> allLocaleNames = new HashMap<>();

        // For Upside Down English
        UpsideDownTranslate.Translator upsideDownTranslator = new UpsideDownTranslate.Translator();
        for (Locale currentLocale : supportedLanguages) {
            Properties currentOverrides = overrides.get(currentLocale);
            if (currentLocale.getLanguage().length() > 2 && currentOverrides.isEmpty()) {
                // The JDK does not provide localized texts for these languages
                continue;
            }

            LocaleNames currentDisplayNames = new LocaleNames();

            for (LocaleField field : LocaleField.values()) {
                SortedMap<String, String> nameToDisplayName = currentDisplayNames.getNameToDisplayName(field);

                loop:
                for (String name : names.get(field)) {
                    String displayName = currentOverrides.getProperty(name);

                    getDisplayName:
                    if (displayName == null) {
                        if (currentLocale.equals(UpsideDownTranslate.EN_QABS)) {
                            String englishDisplayName = allLocaleNames.get(Locale.ENGLISH).getNameToDisplayName(field).get(name);
                            if (englishDisplayName != null) {
                                displayName = upsideDownTranslator.translate(englishDisplayName);
                                break getDisplayName;
                            }
                        }

                        // Although it cannot correctly handle the inheritance relationship between languages,
                        // we will not apply this function to sublanguages.
                        List<Locale> candidateLocales = CONTROL.getCandidateLocales("", currentLocale);

                        for (Locale candidateLocale : candidateLocales) {
                            Properties candidateOverride = overrides.get(candidateLocale);
                            if (candidateOverride != null && candidateOverride.containsKey(name)) {
                                continue loop;
                            }
                        }

                        displayName = field.getDisplayName(name, currentLocale);

                        // JDK does not have a built-in translation
                        if (displayName.isBlank() || displayName.equals(name)) {
                            continue loop;
                        }

                        // If it is just a duplicate of the parent content, ignored it
                        for (Locale candidateLocale : candidateLocales) {
                            LocaleNames candidateLocaleNames = allLocaleNames.get(candidateLocale);
                            if (candidateLocaleNames != null) {
                                String candidateDisplayName = candidateLocaleNames.getNameToDisplayName(field).get(name);
                                if (displayName.equals(candidateDisplayName)) {
                                    continue loop;
                                }
                                break;
                            }
                        }

                        // Ignore it if the JDK falls back to English when querying the display name
                        if (!currentLocale.equals(Locale.ENGLISH)
                                && displayName.equals(allLocaleNames.get(Locale.ENGLISH).getNameToDisplayName(field).get(name))) {
                            continue loop;
                        }
                    }

                    nameToDisplayName.put(name, displayName);
                }
            }

            allLocaleNames.put(currentLocale, currentDisplayNames);
        }

        for (Map.Entry<Locale, LocaleNames> entry : allLocaleNames.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                Path targetFile = outputDir.resolve(mapToFileName("LocaleNames", "properties", entry.getKey()));
                entry.getValue().writeTo(targetFile);
            }
        }
    }

    private static final class LocaleNames {
        private final EnumMap<LocaleField, SortedMap<String, String>> displayNames = new EnumMap<>(LocaleField.class);

        LocaleNames() {
            for (LocaleField field : LocaleField.values()) {
                displayNames.put(field, new TreeMap<>(field));
            }
        }

        boolean isEmpty() {
            return displayNames.values().stream().allMatch(Map::isEmpty);
        }

        SortedMap<String, String> getNameToDisplayName(LocaleField field) {
            return displayNames.get(field);
        }

        void writeTo(Path file) throws IOException {
            try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW)) {
                boolean firstBlock = true;

                for (var entry : displayNames.entrySet()) {
                    LocaleField field = entry.getKey();
                    SortedMap<String, String> values = entry.getValue();

                    if (!values.isEmpty()) {
                        if (firstBlock)
                            firstBlock = false;
                        else
                            writer.newLine();

                        writer.write("# " + field.blockHeader + "\n");

                        for (var nameToDisplay : values.entrySet()) {
                            writer.write(nameToDisplay.getKey() + "=" + nameToDisplay.getValue() + "\n");
                        }
                    }
                }
            }
        }
    }

    private enum LocaleField implements Comparator<String> {
        LANGUAGE("Languages") {
            @Override
            public String get(Locale locale) {
                return locale.getLanguage();
            }

            @Override
            public String getDisplayName(String fieldValue, Locale inLocale) {
                return new Locale.Builder()
                        .setLanguage(fieldValue)
                        .build()
                        .getDisplayLanguage(inLocale);
            }

            @Override
            public int compare(String l1, String l2) {
                return LocalizationUtils.compareLanguage(l1, l2);
            }
        },
        SCRIPT("Scripts") {
            @Override
            public String get(Locale locale) {
                return locale.getScript();
            }

            @Override
            public String getDisplayName(String fieldValue, Locale inLocale) {
                return new Locale.Builder()
                        .setScript(fieldValue)
                        .build()
                        .getDisplayScript(inLocale);
            }

            @Override
            public int compare(String s1, String s2) {
                return LocalizationUtils.compareScript(s1, s2);
            }
        };

        final String blockHeader;

        LocaleField(String blockHeader) {
            this.blockHeader = blockHeader;
        }

        public abstract String get(Locale locale);

        public abstract String getDisplayName(String fieldValue, Locale inLocale);
    }
}
