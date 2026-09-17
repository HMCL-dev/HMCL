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

import org.glavo.url.WebURL;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpHeaders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Captures the status, URL, and headers of an HTTP response.
///
/// @param responseCode the HTTP response status code
/// @param uri the response URL
/// @param headers the response headers
/// @author Glavo
public record UrlResponseInfo(int responseCode, WebURL uri, HttpHeaders headers) {
    /// Creates response metadata from a URL connection.
    public static UrlResponseInfo of(HttpURLConnection connection) throws IOException {
        return new UrlResponseInfo(connection.getResponseCode(), toWebURL(connection.getURL()), headers(connection));
    }

    /// Parses a response URL, reporting invalid response addresses as I/O failures.
    private static WebURL toWebURL(URL url) throws IOException {
        try {
            return WebURL.of(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid response URL: " + url, e);
        }
    }

    /// Copies named header fields from a URL connection.
    private static HttpHeaders headers(URLConnection connection) {
        @Nullable Map<@Nullable String, @Nullable List<String>> headerFields = connection.getHeaderFields();
        if (headerFields == null || headerFields.isEmpty()) {
            return HttpHeaders.of(Map.of(), (k, v) -> true);
        }

        LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>();
        for (Map.Entry<@Nullable String, @Nullable List<String>> entry : headerFields.entrySet()) {
            @Nullable String name = entry.getKey();
            @Nullable List<String> values = entry.getValue();
            if (name != null && values != null) {
                headers.put(name, List.copyOf(values));
            }
        }
        return HttpHeaders.of(headers, (name, value) -> true);
    }
}
