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

import java.io.IOException;
import java.net.URI;

public final class ResponseCodeException extends IOException {

    private final URI uri;
    private final int responseCode;
    private final String data;

    public ResponseCodeException(URI uri, int responseCode) {
        super("Unable to request url " + uri + ", response code: " + responseCode);
        this.uri = uri;
        this.responseCode = responseCode;
        this.data = null;
    }

    public ResponseCodeException(URI uri, int responseCode, Throwable cause) {
        super("Unable to request url " + uri + ", response code: " + responseCode, cause);
        this.uri = uri;
        this.responseCode = responseCode;
        this.data = null;
    }

    public ResponseCodeException(URI uri, int responseCode, String data) {
        super("Unable to request url " + uri + ", response code: " + responseCode + ", data: " + data);
        this.uri = uri;
        this.responseCode = responseCode;
        this.data = data;
    }

    public URI getUri() {
        return uri;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getData() {
        return data;
    }
}
