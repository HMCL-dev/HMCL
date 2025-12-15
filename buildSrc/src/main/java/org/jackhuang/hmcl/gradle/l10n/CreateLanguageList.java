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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/// @author Glavo
public abstract class CreateLanguageList extends DefaultTask {
    @InputDirectory
    public abstract DirectoryProperty getResourceBundleDir();

    @Input
    public abstract Property<@NotNull String> getResourceBundleBaseName();

    @Input
    public abstract ListProperty<@NotNull String> getAdditionalLanguages();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void run() throws IOException {
        Path inputDir = getResourceBundleDir().get().getAsFile().toPath();
        if (!Files.isDirectory(inputDir))
            throw new GradleException("Input directory not exists: " + inputDir);


        SortedSet<Locale> locales = new TreeSet<>(LocalizationUtils::compareLocale);
        locales.addAll(getAdditionalLanguages().getOrElse(List.of()).stream()
                .map(Locale::forLanguageTag)
                .toList());

        String baseName = getResourceBundleBaseName().get();
        String suffix = ".properties";

        try (var stream = Files.newDirectoryStream(inputDir, file -> {
            String fileName = file.getFileName().toString();
            return fileName.startsWith(baseName) && fileName.endsWith(suffix);
        })) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (fileName.length() == baseName.length() + suffix.length())
                    locales.add(Locale.ENGLISH);
                else if (fileName.charAt(baseName.length()) == '_') {
                    String localeName = fileName.substring(baseName.length() + 1, fileName.length() - suffix.length());

                    // TODO: Delete this if the I18N file naming is changed
                    if (baseName.equals("I18N")) {
                        if (localeName.equals("zh"))
                            locales.add(Locale.forLanguageTag("zh-Hant"));
                        else if (localeName.equals("zh_CN"))
                            locales.add(Locale.forLanguageTag("zh-Hans"));
                        else
                            locales.add(Locale.forLanguageTag(localeName.replace('_', '-')));
                    } else {
                        if (localeName.equals("zh"))
                            locales.add(Locale.forLanguageTag("zh-Hans"));
                        else
                            locales.add(Locale.forLanguageTag(localeName.replace('_', '-')));
                    }
                }
            }
        }

        Path outputFile = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, locales.stream().map(locale -> '"' + locale.toLanguageTag() + '"')
                .collect(Collectors.joining(", ", "[", "]")));
    }

}
