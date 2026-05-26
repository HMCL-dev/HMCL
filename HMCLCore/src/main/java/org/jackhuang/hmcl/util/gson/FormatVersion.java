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
package org.jackhuang.hmcl.util.gson;

import org.jetbrains.annotations.NotNullByDefault;

/// Semantic version marker for a serialized file format.
///
/// The string representation is the strict `major.minor` form.
///
/// @param major the major format version
/// @param minor the minor format version
/// @author Glavo
@NotNullByDefault
public record FormatVersion(int major, int minor) implements Comparable<FormatVersion> {
    /// @param major the major format version
    /// @param minor the minor format version
    public FormatVersion {
        if (major < 0) throw new IllegalArgumentException("Major version must be non-negative: " + major);
        if (minor < 0) throw new IllegalArgumentException("Minor version must be non-negative: " + minor);
    }

    /// Parses a format version string.
    ///
    /// @param version the version string in `major.minor` form
    /// @return the parsed format version
    /// @throws IllegalArgumentException if the version string is invalid
    public static FormatVersion parse(String version) {
        int dot = version.indexOf('.');
        if (dot <= 0 || dot != version.lastIndexOf('.') || dot == version.length() - 1) {
            throw new IllegalArgumentException("Invalid format version: " + version);
        }

        try {
            return new FormatVersion(parsePart(version, 0, dot), parsePart(version, dot + 1, version.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid format version: " + version, e);
        }
    }

    /// Parses a decimal version part.
    private static int parsePart(String version, int start, int end) {
        for (int i = start; i < end; i++) {
            char ch = version.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new IllegalArgumentException("Invalid format version: " + version);
            }
        }

        return Integer.parseInt(version.substring(start, end));
    }

    /// Compares this version with another format version.
    ///
    /// @param o the other version to compare to
    /// @return a negative integer, zero, or a positive integer as this version
    ///         is less than, equal to, or greater than the specified version
    @Override
    public int compareTo(FormatVersion o) {
        return major != o.major
                ? Integer.compare(major, o.major)
                : Integer.compare(minor, o.minor);
    }

    /// Returns the canonical `major.minor` string representation.
    @Override
    public String toString() {
        return major + "." + minor;
    }
}
