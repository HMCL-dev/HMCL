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

import org.glavo.url.WebURL;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/// URL reference to a resource stored in an installed theme pack.
///
/// The serialized form is `hmcl://theme-pack/<pack-id>/<version>/<asset-entry>`.
/// The asset entry must use the same `assets/...` layout as theme-pack zip entries.
///
/// @param packId the theme-pack identifier
/// @param version the installed theme-pack version
/// @param entryName the normalized theme-pack asset entry name
@NotNullByDefault
public record ThemePackResourceURL(String packId, String version, String entryName) {
    /// URL scheme used for installed theme-pack resources.
    public static final String SCHEME = "hmcl";

    /// URL host used for installed theme-pack resources.
    private static final String HOST = "theme-pack";

    /// Creates a theme-pack resource URL.
    ///
    /// @param packId the theme-pack identifier
    /// @param version the installed theme-pack version
    /// @param entryName the theme-pack asset entry name
    public ThemePackResourceURL {
        packId = requirePathSegment(packId, "packId");
        version = requirePathSegment(version, "version");
        entryName = ThemePackAsset.normalizeEntryName(entryName);
    }

    /// Creates a theme-pack resource URL for a manifest asset.
    ///
    /// @param manifest the theme-pack manifest
    /// @param entryName the theme-pack asset entry name
    /// @return the theme-pack resource URL
    public static ThemePackResourceURL of(ThemePackManifest manifest, String entryName) {
        Objects.requireNonNull(manifest);
        return new ThemePackResourceURL(manifest.id(), manifest.version(), entryName);
    }

    /// Parses a theme-pack resource URL.
    ///
    /// @param value the serialized URL
    /// @return the parsed URL, or `null` when the value is not a theme-pack resource URL
    /// @throws IllegalArgumentException if the value uses the theme-pack scheme but is malformed
    public static @Nullable ThemePackResourceURL parse(@Nullable String value) {
        if (value == null) {
            return null;
        }

        WebURL url = WebURL.tryParse(value);
        if (url == null || !SCHEME.equals(url.getScheme()) || !HOST.equals(url.getHost())) {
            return null;
        }
        if (!url.getRawUsernameOrEmpty().isEmpty()
                || !url.getRawPasswordOrEmpty().isEmpty()
                || url.getPort() >= 0
                || url.getRawQuery() != null
                || url.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid theme-pack resource URL: " + value);
        }

        String path = url.getPath();
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Invalid theme-pack resource URL: " + value);
        }

        String[] segments = path.substring(1).split("/", 3);
        if (segments.length != 3) {
            throw new IllegalArgumentException("Invalid theme-pack resource URL: " + value);
        }

        return new ThemePackResourceURL(segments[0], segments[1], segments[2]);
    }

    /// Resolves this URL to the installed local file.
    ///
    /// @return the installed local resource file
    /// @throws IOException if the referenced installed resource does not exist
    public Path resolve() throws IOException {
        return ThemePackManager.resolveInstalledAsset(
                ThemePackManager.installedThemePackDirectory(packId, version),
                entryName);
    }

    /// Serializes this resource reference as a URL string.
    ///
    /// @return the serialized URL
    @Override
    public String toString() {
        return WebURL.newBuilder()
                .setScheme(SCHEME)
                .setHost(HOST)
                .setPath("/" + packId + "/" + version + "/" + entryName)
                .build()
                .href();
    }

    /// Returns a non-blank value that can be represented as one URL path segment.
    private static String requirePathSegment(String value, String name) {
        Objects.requireNonNull(value);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme-pack resource URL value is blank: " + name);
        }
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Theme-pack resource URL value must be one path segment: " + name);
        }
        return trimmed;
    }
}
