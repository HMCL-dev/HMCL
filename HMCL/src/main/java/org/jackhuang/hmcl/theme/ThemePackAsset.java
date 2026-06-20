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
package org.jackhuang.hmcl.theme;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.Objects;

/// One file copied into an exported theme-pack zip.
///
/// @param source the local file to copy
/// @param entryName the normalized zip entry name under `assets/`
@NotNullByDefault
public record ThemePackAsset(Path source, String entryName) {

    /// Required prefix for theme-pack asset entries.
    private static final String ASSETS_PREFIX = "assets/";

    /// Creates a theme-pack asset entry.
    ///
    /// @param source the local file to copy
    /// @param entryName the normalized zip entry name under `assets/`
    public ThemePackAsset {
        source = Objects.requireNonNull(source).toAbsolutePath().normalize();
        entryName = normalizeEntryName(entryName);
    }

    /// Normalizes and validates a zip entry name.
    ///
    /// @param entryName the entry name to validate
    /// @return the normalized entry name
    /// @throws IllegalArgumentException if the entry name is unsafe or outside `assets/`
    static String normalizeEntryName(String entryName) {
        Objects.requireNonNull(entryName);

        String normalized = entryName.trim().replace('\\', '/');
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Theme-pack asset entry is empty");
        }
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Theme-pack asset entry must be relative: " + entryName);
        }
        if (!normalized.startsWith(ASSETS_PREFIX)) {
            throw new IllegalArgumentException("Theme-pack asset entry must be under assets/: " + entryName);
        }
        if (normalized.endsWith("/")) {
            throw new IllegalArgumentException("Theme-pack asset entry must be a file: " + entryName);
        }

        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Theme-pack asset entry contains an unsafe segment: " + entryName);
            }
            for (int i = 0; i < segment.length(); i++) {
                char ch = segment.charAt(i);
                if (Character.isISOControl(ch) || ch == '\0') {
                    throw new IllegalArgumentException("Theme-pack asset entry contains a control character: " + entryName);
                }
            }
        }
        return normalized;
    }
}
