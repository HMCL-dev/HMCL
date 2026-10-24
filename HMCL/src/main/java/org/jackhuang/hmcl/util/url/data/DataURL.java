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

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;

/// Parses a data URL's media type, charset, and percent-decoded payload.
/// Query content is included in the payload; the fragment is excluded.
/// Percent escapes are decoded as UTF-8 before the declared charset is applied by the read methods.
///
/// @author Glavo
public final class DataURL {
    /// Scheme identifying inline data URLs.
    public static final String SCHEME = "data";

    /// Creates a parsing failure for a data URL without a metadata/payload separator.
    private static IllegalArgumentException invalidUri(WebURL url) {
        return new IllegalArgumentException("Invalid data URL: " + url);
    }

    /// Returns whether the URL uses the data scheme, or `false` for `null`.
    public static boolean isDataUri(@Nullable WebURL url) {
        return url != null && SCHEME.equals(url.getScheme());
    }

    /// Trimmed media type and parameters, excluding the Base64 marker.
    private final @NotNull String mediaType;
    /// Declared charset, or UTF-8 when absent or unrecognized.
    private final @NotNull Charset charset;
    /// Whether the metadata ends with the case-sensitive Base64 marker.
    private final boolean base64;
    /// Percent-decoded payload before optional Base64 decoding.
    private final @NotNull String rawData;

    /// Parses metadata and payload without decoding Base64 content.
    ///
    /// @param url the data URL
    /// @throws IllegalArgumentException if the scheme is not data or the decoded content has no comma
    public DataURL(WebURL url) {
        if (!SCHEME.equals(url.getScheme())) {
            throw new IllegalArgumentException("URL scheme must be " + SCHEME);
        }

        String schemeSpecificPart = url.getPath();
        @Nullable String authority = url.getAuthority();
        if (authority != null)
            schemeSpecificPart = "//" + authority + schemeSpecificPart;

        // The query delimiter and content belong to the data, while the fragment does not.
        @Nullable String query = url.getQuery();
        if (query != null)
            schemeSpecificPart += "?" + query;

        int comma = schemeSpecificPart.indexOf(',');
        if (comma < 0)
            throw invalidUri(url);

        String mediaType = schemeSpecificPart.substring(0, comma);
        boolean base64 = mediaType.endsWith(";base64");
        if (base64)
            mediaType = mediaType.substring(0, mediaType.length() - ";base64".length());

        this.mediaType = mediaType.trim();
        this.charset = NetworkUtils.getCharsetFromContentType(mediaType);
        this.base64 = base64;
        this.rawData = schemeSpecificPart.substring(comma + 1);
    }

    /// Returns the trimmed media type and parameters without the Base64 marker; may be empty.
    public @NotNull String getMediaType() {
        return mediaType;
    }

    /// Returns the declared charset, or UTF-8 when absent or unrecognized.
    public @NotNull Charset getCharset() {
        return charset;
    }

    /// Returns whether the payload is marked for Base64 decoding.
    public boolean isBase64() {
        return base64;
    }

    /// Returns the percent-decoded payload before optional Base64 decoding, preserving literal plus signs.
    public @NotNull String getRawData() {
        return rawData;
    }

    /// Returns a new byte array by decoding Base64 or encoding the text payload with [#getCharset()].
    ///
    /// @throws IOException if a Base64 payload cannot be decoded
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

    /// Returns the text payload, decoding Base64 bytes with [#getCharset()] when marked as Base64.
    ///
    /// @throws IOException if a Base64 payload cannot be decoded
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
