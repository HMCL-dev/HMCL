// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
/// @param url the response URL
/// @param headers the response headers
/// @author Glavo
public record UrlResponseInfo(int responseCode, WebURL url, HttpHeaders headers) {
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
