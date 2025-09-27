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

import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;

/// @author Glavo
public record UrlResponseInfo(URI uri, HttpHeaders headers) {
    public static UrlResponseInfo of(HttpResponse<?> httpResponse) {
        return new UrlResponseInfo(httpResponse.uri(), httpResponse.headers());
    }

    public static UrlResponseInfo of(URI uri, HttpResponse.ResponseInfo info) {
        return new UrlResponseInfo(uri, info.headers());
    }

    public static UrlResponseInfo of(URLConnection connection) {
        return new UrlResponseInfo(NetworkUtils.toURI(connection.getURL()),
                HttpHeaders.of(connection.getHeaderFields(), (a, b) -> true));
    }
}
