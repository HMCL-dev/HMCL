/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.upgrade;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Logging.LOG;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Optional;
import java.util.logging.Level;

import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.Metadata;

class LocalVersion {

    public static Optional<LocalVersion> current() {
        CodeSource codeSource = Main.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return Optional.empty();
        }

        URL url = codeSource.getLocation();
        if (url == null) {
            return Optional.empty();
        }

        String pathString = url.getFile();
        if (pathString.isEmpty()) {
            return Optional.empty();
        }

        try {
            pathString = URLDecoder.decode(pathString, UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        Path path;
        try {
            path = Paths.get(pathString);
        } catch (InvalidPathException e) {
            LOG.log(Level.WARNING, "Invalid path: " + pathString, e);
            return Optional.empty();
        }

        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        return Optional.of(new LocalVersion(Metadata.VERSION, path));
    }

    private String version;
    private Path location;

    public LocalVersion(String version, Path location) {
        this.version = version;
        this.location = location;
    }

    public String getVersion() {
        return version;
    }

    public Path getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return "[" + version + " at " + location + "]";
    }
}
