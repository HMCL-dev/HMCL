// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.Objects;

/// One resource copied into an exported theme-pack zip.
///
/// @param source    the resource to copy
/// @param entryName the normalized zip entry name under `assets/`
@NotNullByDefault
public record ThemePackAsset(ThemePackResource source, String entryName) {

    /// Required prefix for theme-pack asset entries.
    private static final String ASSETS_PREFIX = "assets/";

    /// Creates a theme-pack asset from a local file.
    ///
    /// @param source    the local file to copy
    /// @param entryName the normalized zip entry name under `assets/`
    public ThemePackAsset(Path source, String entryName) {
        this(new ThemePackResource.File(source), entryName);
    }

    /// Creates a theme-pack asset entry.
    ///
    /// @param source    the resource to copy
    /// @param entryName the normalized zip entry name under `assets/`
    public ThemePackAsset {
        Objects.requireNonNull(source);
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
