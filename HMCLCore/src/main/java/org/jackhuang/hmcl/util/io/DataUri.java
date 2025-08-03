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
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Base64;

public final class DataUri {
    public static final String SCHEME = "data";

    private static IllegalArgumentException invalidUri(URI uri) {
        return new IllegalArgumentException("Invalid data URI: " + uri);
    }

    public static byte[] readBytes(URI uri) throws IOException {
        DataUri dataUri = new DataUri(uri);
        if (dataUri.base64) {
            return Base64.getDecoder().decode(dataUri.body);
        } else {
            return dataUri.body.getBytes(dataUri.charset);
        }
    }

    public static String readString(URI uri) throws IOException {
        DataUri dataUri = new DataUri(uri);
        if (dataUri.base64) {
            return new String(Base64.getDecoder().decode(dataUri.body), dataUri.charset);
        } else {
            return dataUri.body;
        }
    }

    private final @NotNull String mediaType;
    private final @NotNull Charset charset;
    private final boolean base64;
    private final @NotNull String body;

    DataUri(URI uri) throws IOException {
        if (!uri.getScheme().equals(SCHEME)) {
            throw new IllegalArgumentException("URI scheme must be " + SCHEME);
        }

        String schemeSpecificPart = uri.getSchemeSpecificPart();
        if (schemeSpecificPart == null)
            throw invalidUri(uri);

        int comma = schemeSpecificPart.indexOf(',');
        if (comma <= 0)
            throw invalidUri(uri);

        String mediaType = schemeSpecificPart.substring(0, comma);
        boolean base64 = mediaType.endsWith(";base64");
        if (base64)
            mediaType = mediaType.substring(0, mediaType.length() - ";base64".length());


        this.mediaType = mediaType;
        this.charset = NetworkUtils.getCharsetFromContentType(mediaType);
        this.base64 = base64;
        this.body = schemeSpecificPart.substring(comma + 1);
    }
}
