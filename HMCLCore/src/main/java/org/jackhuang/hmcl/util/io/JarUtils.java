/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

    @Nullable
    public static Path thisJarPath() {
        return THIS_JAR;
    }

    public static String getAttribute(String name, String defaultValue) {
        return properties.getProperty(name, defaultValue);
    }
}
