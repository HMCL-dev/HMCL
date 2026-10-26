// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
                    locales.add(Locale.forLanguageTag(localeName.replace('_', '-')));
                }
            }
        }

        Path outputFile = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, locales.stream().map(locale -> '"' + locale.toLanguageTag() + '"')
                .collect(Collectors.joining(", ", "[", "]")));
    }

}
