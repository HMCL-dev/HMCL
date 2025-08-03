/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author Glavo
 */
public final class UriUtils {

    /// @throws IllegalArgumentException if the string is not a valid URI
    public static @NotNull URI toURI(@NotNull String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            try {
                return new URI(uri.replaceAll(" ", "%20"));
            } catch (URISyntaxException ignored) {
                throw new IllegalArgumentException("Invalid URI: " + uri, e);
            }
        }
    }

    public static @NotNull URI toURI(@NotNull URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            try {
                return new URI(url.toExternalForm().replaceAll(" ", "%20"));
            } catch (URISyntaxException ignored) {
                throw new IllegalArgumentException("Invalid URI: " + url, e);
            }
        }
    }

    private UriUtils() {
    }
}
