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

import java.io.IOException;
import java.net.*;

/// Opens data URL connections using [WebURL] to parse the address.
///
/// @author Glavo
public final class DataURLHandle extends URLStreamHandler {
    /// Creates a connection without decoding its Base64 payload.
    ///
    /// @throws MalformedURLException if the address cannot be parsed as a data URL
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        try {
            return new DataURLConnection(u, new DataURL(WebURL.of(u)));
        } catch (IllegalArgumentException e) {
            MalformedURLException exception = new MalformedURLException(e.getMessage());
            exception.initCause(e);
            throw exception;
        }
    }
}
