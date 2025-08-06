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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author Glavo
 */
public final class DataURLConnection extends URLConnection {
    private final DataUri dataUri;
    private byte[] data;
    private InputStream inputStream;

    DataURLConnection(URL url, DataUri dataUri) {
        super(url);
        this.dataUri = dataUri;
    }

    @Override
    public void connect() throws IOException {
        if (!connected) {
            connected = true;
            data = dataUri.readBytes();
            inputStream = new ByteArrayInputStream(this.data);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return inputStream;
    }

    @Override
    public String getContentType() {
        return dataUri.getMediaType();
    }

    @Override
    public long getContentLengthLong() {
        return data != null ? data.length : -1;
    }
}
