// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import org.glavo.url.WebURL;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;

/// Reports an unsuccessful HTTP response and its request address.
public final class ResponseCodeException extends IOException {

    /// Request address reported by the caller.
    private final String url;
    /// HTTP status code reported by the server.
    private final int responseCode;
    /// Optional response text supplied by the caller.
    private final @Nullable String data;

    /// Creates a failure for a URL and HTTP status code.
    public ResponseCodeException(WebURL url, int responseCode) {
        this(url.toString(), responseCode);
    }

    /// Creates a failure for a URL and HTTP status code with an optional cause.
    public ResponseCodeException(WebURL url, int responseCode, @Nullable Throwable cause) {
        this(url.toString(), responseCode, cause);
    }

    /// Creates a failure for a URL and HTTP status code with optional response text.
    public ResponseCodeException(WebURL url, int responseCode, @Nullable String data) {
        this(url.toString(), responseCode, data);
    }

    /// Creates a failure for a request address and HTTP status code.
    public ResponseCodeException(String url, int responseCode) {
        super("Unable to request url " + url + ", response code: " + responseCode);
        this.url = url;
        this.responseCode = responseCode;
        this.data = null;
    }

    /// Creates a failure for a request address and HTTP status code with an optional cause.
    public ResponseCodeException(String url, int responseCode, @Nullable Throwable cause) {
        super("Unable to request url " + url + ", response code: " + responseCode, cause);
        this.url = url;
        this.responseCode = responseCode;
        this.data = null;
    }

    /// Creates a failure for a request address and HTTP status code with optional response text.
    public ResponseCodeException(String url, int responseCode, @Nullable String data) {
        super("Unable to request url " + url + ", response code: " + responseCode + ", data: " + data);
        this.url = url;
        this.responseCode = responseCode;
        this.data = data;
    }

    /// Returns the request address supplied by the caller.
    public String getUrl() {
        return url;
    }

    /// Returns the HTTP status code.
    public int getResponseCode() {
        return responseCode;
    }

    /// Returns the supplied response text, or `null` if none was supplied.
    public @Nullable String getData() {
        return data;
    }
}
