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
package org.jackhuang.hmcl.util.url.data;

import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Base64;

/**
 * @author Glavo
 */
public final class DataUri {
    public static final String SCHEME = "data";

    private static IllegalArgumentException invalidUri(URI uri) {
        return new IllegalArgumentException("Invalid data URI: " + uri);
    }

    public static boolean isDataUri(URI uri) {
        return uri != null && SCHEME.equals(uri.getScheme());
    }

    private final @NotNull String mediaType;
    private final @NotNull Charset charset;
    private final boolean base64;
    private final @NotNull String rawData;

    public DataUri(URI uri) {
        if (!uri.getScheme().equals(SCHEME)) {
            throw new IllegalArgumentException("URI scheme must be " + SCHEME);
        }

        String schemeSpecificPart = uri.getSchemeSpecificPart();
        if (schemeSpecificPart == null)
            throw invalidUri(uri);

        int comma = schemeSpecificPart.indexOf(',');
        if (comma < 0)
            throw invalidUri(uri);

        String mediaType = schemeSpecificPart.substring(0, comma);
        boolean base64 = mediaType.endsWith(";base64");
        if (base64)
            mediaType = mediaType.substring(0, mediaType.length() - ";base64".length());

        this.mediaType = mediaType.trim();
        this.charset = NetworkUtils.getCharsetFromContentType(mediaType);
        this.base64 = base64;
        this.rawData = schemeSpecificPart.substring(comma + 1);
    }

    public @NotNull String getMediaType() {
        return mediaType;
    }

    public @NotNull Charset getCharset() {
        return charset;
    }

    public boolean isBase64() {
        return base64;
    }

    public @NotNull String getRawData() {
        return rawData;
    }

    public byte[] readBytes() throws IOException {
        if (base64) {
            try {
                return Base64.getDecoder().decode(rawData);
            } catch (IllegalArgumentException e) {
                throw new IOException(e);
            }
        } else {
            return rawData.getBytes(charset);
        }
    }

    public String readString() throws IOException {
        if (base64) {
            try {
                return new String(Base64.getDecoder().decode(rawData), charset);
            } catch (IllegalArgumentException e) {
                throw new IOException(e);
            }
        } else {
            return rawData;
        }
    }

    @Override
    public String toString() {
        return String.format("DataUri{mediaType='%s', charset=%s, base64=%s, body='%s'}", mediaType, charset, base64, rawData);
    }
}
