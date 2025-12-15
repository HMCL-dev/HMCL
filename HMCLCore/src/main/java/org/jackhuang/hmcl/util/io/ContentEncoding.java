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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;

/**
 * @author Glavo
 */
public enum ContentEncoding {
    IDENTITY {
        @Override
        public InputStream wrap(InputStream inputStream) {
            return inputStream;
        }
    },
    GZIP {
        @Override
        public InputStream wrap(InputStream inputStream) throws IOException {
            return new GZIPInputStream(inputStream);
        }
    };

    public static @NotNull ContentEncoding fromConnection(URLConnection connection) throws IOException {
        String encoding = connection.getContentEncoding();
        if (encoding == null || encoding.isEmpty() || "identity".equals(encoding)) {
            return IDENTITY;
        } else if ("gzip".equalsIgnoreCase(encoding)) {
            return GZIP;
        } else {
            throw new IOException("Unsupported content encoding: " + encoding);
        }
    }

    public static @NotNull ContentEncoding fromResponse(HttpResponse<?> connection) throws IOException {
        String encoding = connection.headers().firstValue("content-encoding").orElse("");
        if (encoding.isEmpty() || "identity".equals(encoding)) {
            return IDENTITY;
        } else if ("gzip".equalsIgnoreCase(encoding)) {
            return GZIP;
        } else {
            throw new IOException("Unsupported content encoding: " + encoding);
        }
    }

    public abstract InputStream wrap(InputStream inputStream) throws IOException;
}
