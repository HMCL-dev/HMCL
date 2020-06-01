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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpMultipartRequest implements Closeable {
    private final String boundary = "*****" + System.currentTimeMillis() + "*****";
    private final HttpURLConnection urlConnection;
    private final ByteArrayOutputStream stream;
    private final String endl = "\r\n";

    public HttpMultipartRequest(HttpURLConnection urlConnection) throws IOException {
        this.urlConnection = urlConnection;
        urlConnection.setDoOutput(true);
        urlConnection.setUseCaches(false);
        urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        stream = new ByteArrayOutputStream();
    }

    private void addLine(String content) throws IOException {
        stream.write(content.getBytes(UTF_8));
        stream.write(endl.getBytes(UTF_8));
    }

    public HttpMultipartRequest file(String name, String filename, String contentType, InputStream inputStream) throws IOException {
        addLine("--" + boundary);
        addLine(String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"", name, filename));
        addLine("Content-Type: " + contentType);
        addLine("");
        IOUtils.copyTo(inputStream, stream);
        addLine("");
        return this;
    }

    public HttpMultipartRequest param(String name, String value) throws IOException {
        addLine("--" + boundary);
        addLine(String.format("Content-Disposition: form-data; name=\"%s\"", name));
        addLine("");
        addLine(value);
        return this;
    }

    @Override
    public void close() throws IOException {
        addLine("--" + boundary + "--");
        urlConnection.setRequestProperty("Content-Length", "" + stream.size());
        try (OutputStream os = urlConnection.getOutputStream()) {
            IOUtils.write(stream.toByteArray(), os);
        }
    }
}
