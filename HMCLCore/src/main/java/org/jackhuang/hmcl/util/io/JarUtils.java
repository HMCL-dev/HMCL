// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Properties;

public final class JarUtils {
    private JarUtils() {
    }

    private static final Path THIS_JAR;
    private static final Properties properties = new Properties();

    static {
        Class<?> entryPointClass = null;
        CodeSource cs = null;
        try {
            entryPointClass = Class.forName("org.jackhuang.hmcl.EntryPoint");
            cs = entryPointClass.getProtectionDomain().getCodeSource();
        } catch (ClassNotFoundException ignored) {
        }

        if (cs == null) {
            THIS_JAR = null;
        } else {
            Path path;
            try {
                path = Path.of(cs.getLocation().toURI()).toAbsolutePath();
            } catch (FileSystemNotFoundException | IllegalArgumentException | URISyntaxException e) {
                path = null;
            }
            THIS_JAR = path != null && Files.isRegularFile(path) ? path : null;
        }

        if (entryPointClass != null) {
            InputStream input = entryPointClass.getResourceAsStream("/assets/hmcl.properties");
            if (input != null) {
                try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /// Get the jar location of current application. Return null if not found
    @Nullable
    public static Path thisJarPath() {
        return THIS_JAR;
    }

    public static String getAttribute(String name, String defaultValue) {
        return properties.getProperty(name, defaultValue);
    }
}
