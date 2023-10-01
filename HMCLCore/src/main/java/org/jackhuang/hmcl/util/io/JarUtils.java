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
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class JarUtils {
    private JarUtils() {
    }

    private static final Path THIS_JAR;

    private static final Manifest manifest;

    static {
        CodeSource cs = JarUtils.class.getProtectionDomain().getCodeSource();
        if (cs == null) {
            THIS_JAR = null;
            manifest = new Manifest();
        } else {
            Path path;
            try {
                path = Paths.get(cs.getLocation().toURI()).toAbsolutePath();
            } catch (FileSystemNotFoundException | IllegalArgumentException | URISyntaxException e) {
                path = null;
            }
            if (path == null || !Files.isRegularFile(path)) {
                THIS_JAR = null;
                manifest = new Manifest();
            } else {
                THIS_JAR = path;
                Manifest mn;
                try (JarFile file = new JarFile(path.toFile())) {
                    mn = file.getManifest();
                } catch (IOException e) {
                    mn = new Manifest();
                }
                manifest = mn;
            }
        }
    }

    @Nullable
    public static Path thisJarPath() {
        return THIS_JAR;
    }

    public static String getManifestAttribute(String name, String defaultValue) {
        String value = manifest.getMainAttributes().getValue(name);
        return value != null ? value : defaultValue;
    }
}
