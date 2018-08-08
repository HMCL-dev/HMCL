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

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.JarUtils;

import java.nio.file.Path;
import java.util.Optional;

class LocalVersion {

    public static Optional<LocalVersion> current() {
        return JarUtils.thisJar().map(path -> new LocalVersion(Metadata.VERSION, path));
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
