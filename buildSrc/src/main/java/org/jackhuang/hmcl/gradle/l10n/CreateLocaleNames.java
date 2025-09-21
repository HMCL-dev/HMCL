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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/// @author Glavo
public abstract class CreateLocaleNames extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getLanguagesFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    private static String mapToFileName(String base, String ext, Locale locale) {
        if (locale.getLanguage().isEmpty() || locale.equals(Locale.ENGLISH))
            return base + "." + ext;
        else if (locale.toLanguageTag().equals("zh-Hans"))
            return base + "_zh." + ext;
        else
            return base + "_" + locale.toLanguageTag().replace('-', '_') + "." + ext;
    }

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
                    .toList();
        }

        if (!supportedLanguages.get(0).equals(Locale.ENGLISH))
            throw new GradleException("The first language must be english.");

        // For Upside Down English
        UpsideDownTranslate.Translator upsideDownTranslator = new UpsideDownTranslate.Translator();
        Map<String, String> englishDisplayNames = new HashMap<>();

        SortedSet<String> languages = supportedLanguages.stream()
                .map(Locale::getLanguage)
                .filter(it -> !it.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));

        SortedSet<String> scripts = supportedLanguages.stream()
                .map(Locale::getScript)
                .filter(it -> !it.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));

        for (Locale currentLanguage : supportedLanguages) {
            InputStream overrideFile = CreateLocaleNames.class.getResourceAsStream(
                    mapToFileName("LocaleNamesOverride", "properties", currentLanguage));

            Properties overrideProperties = new Properties();
            if (overrideFile != null) {
                try (var reader = new InputStreamReader(overrideFile, StandardCharsets.UTF_8)) {
                    overrideProperties.load(reader);
                }
            }

            Path targetFile = outputDir.resolve(mapToFileName("LocaleNames", "properties", currentLanguage));
            if (Files.exists(targetFile))
                throw new GradleException(String.format("File %s already exists", targetFile));

            try (var writer = Files.newBufferedWriter(targetFile)) {
                writer.write("""
                        #
                        # Hello Minecraft! Launcher
                        # Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
                        #
                        # This program is free software: you can redistribute it and/or modify
                        # it under the terms of the GNU General Public License as published by
                        # the Free Software Foundation, either version 3 of the License, or
                        # (at your option) any later version.
                        #
                        # This program is distributed in the hope that it will be useful,
                        # but WITHOUT ANY WARRANTY; without even the implied warranty of
                        # MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
                        # GNU General Public License for more details.
                        #
                        # You should have received a copy of the GNU General Public License
                        # along with this program.  If not, see <https://www.gnu.org/licenses/>.
                        #
                        
                        """);

                writer.write("# Languages\n");
                for (String language : languages) {
                    String displayName = overrideProperties.getProperty(language);
                    if (displayName == null) {
                        if (currentLanguage.equals(UpsideDownTranslate.EN_QABS) && englishDisplayNames.containsKey(language)) {
                            displayName = upsideDownTranslator.translate(englishDisplayNames.get(language));
                        } else {
                            displayName = new Locale.Builder()
                                    .setLanguage(language)
                                    .build()
                                    .getDisplayLanguage(currentLanguage);

                            if (displayName.equals(language)
                                    || (!currentLanguage.equals(Locale.ENGLISH) && displayName.equals(englishDisplayNames.get(language))))
                                continue; // Skip
                        }
                    }

                    if (currentLanguage.equals(Locale.ENGLISH))
                        englishDisplayNames.put(language, displayName);

                    writer.write(language + "=" + displayName + "\n");
                }
                writer.write('\n');

                writer.write("# Scripts\n");
                for (String script : scripts) {
                    String displayName = overrideProperties.getProperty(script);
                    if (displayName == null) {
                        if (currentLanguage.equals(UpsideDownTranslate.EN_QABS) && englishDisplayNames.containsKey(script)) {
                            displayName = upsideDownTranslator.translate(englishDisplayNames.get(script));
                        } else {
                            displayName = new Locale.Builder()
                                    .setScript(script)
                                    .build()
                                    .getDisplayScript(currentLanguage);

                            if (displayName.equals(script)
                                    || (!currentLanguage.equals(Locale.ENGLISH) && displayName.equals(englishDisplayNames.get(script))))
                                continue; // Skip
                        }
                    }

                    if (currentLanguage.equals(Locale.ENGLISH))
                        englishDisplayNames.put(script, displayName);

                    writer.write(script + "=" + displayName + "\n");
                }
                writer.write('\n');
            }
        }
    }
}
