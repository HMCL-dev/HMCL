// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.utils;

import org.gradle.api.file.FileContents;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/// @author Glavo
public final class PropertiesUtils {
    public static @NotNull Properties load(FileContents fileContents) throws IOException {
        Properties properties = new Properties();
        try (var inputStream = new ByteArrayInputStream(fileContents.getAsBytes().get())) {
            properties.load(inputStream);
        }
        return properties;
    }

    public static @NotNull Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return properties;
    }

    private PropertiesUtils() {
    }
}
