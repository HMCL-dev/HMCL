/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.Objects;

/// Stores a path string together with whether the path is absolute.
///
/// Relative paths use `/` as their portable separator. Absolute paths are kept as
/// provided so their platform-specific separators are preserved.
///
/// @author Glavo
@NotNullByDefault
public final class PortablePath {
    /// The separator used by relative portable paths.
    public static final char SEPARATOR = '/';

    /// Creates a portable path.
    ///
    /// @param path the path string
    /// @return the portable path
    public static PortablePath of(String path) {
        Objects.requireNonNull(path);

        boolean absolute = isAbsolute(path);
        return new PortablePath(absolute ? path : path.replace('\\', SEPARATOR), absolute);
    }

    /// Creates a portable path from a [Path].
    ///
    /// @param path the path to convert
    /// @return the portable path
    public static PortablePath fromPath(Path path) {
        return of(Objects.requireNonNull(path).toString());
    }

    /// Returns whether the given path string is absolute.
    private static boolean isAbsolute(String path) {
        if (path.startsWith("/") || path.startsWith("\\")) {
            return true;
        }

        if (path.length() >= 2 && path.charAt(1) == ':') {
            char ch = path.charAt(0);
            return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
        }

        return false;
    }

    /// The stored path string.
    private final String path;

    /// Whether the stored path is absolute.
    private final boolean absolute;

    /// Creates a portable path with a normalized relative path string and an absolute flag.
    private PortablePath(String path, boolean absolute) {
        this.path = path;
        this.absolute = absolute;
    }

    /// Returns the stored path string.
    ///
    /// @return the stored path string
    public String getPath() {
        return path;
    }

    /// Returns whether this path is absolute.
    ///
    /// @return whether this path is absolute
    public boolean isAbsolute() {
        return absolute;
    }

    /// Converts this portable path to a [Path] on the current platform.
    ///
    /// @return the converted path
    public Path toPath() {
        return Path.of(path);
    }

    /// Returns the stored path string.
    ///
    /// @return the stored path string
    @Override
    public String toString() {
        return path;
    }
}
