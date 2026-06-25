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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Response URI and headers captured from a URL response.
///
/// @author Glavo
public record UrlResponseInfo(URI uri, HttpHeaders headers) {
    /// Creates response metadata from a URL connection.
    public static UrlResponseInfo of(URLConnection connection) throws IOException {
        return new UrlResponseInfo(toURI(connection.getURL()), HttpHeaders.of(headers(connection), (name, value) -> true));
    }

    /// Creates response metadata from an HTTP client response.
    public static UrlResponseInfo of(HttpResponse<?> httpResponse) {
        return new UrlResponseInfo(httpResponse.uri(), httpResponse.headers());
    }

    /// Creates response metadata from an HTTP client response-info object.
    public static UrlResponseInfo of(URI uri, HttpResponse.ResponseInfo info) {
        return new UrlResponseInfo(uri, info.headers());
    }

    /// Converts a response URL into a URI.
    private static URI toURI(URL url) throws IOException {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid response URL: " + url, e);
        }
    }

    /// Copies named header fields from a URL connection.
    private static Map<String, List<String>> headers(URLConnection connection) {
        Map<String, List<String>> headerFields = connection.getHeaderFields();
        if (headerFields == null || headerFields.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            if (name != null && values != null) {
                headers.put(name, List.copyOf(values));
            }
        }
        return headers;
    }
}
