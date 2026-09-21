/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
