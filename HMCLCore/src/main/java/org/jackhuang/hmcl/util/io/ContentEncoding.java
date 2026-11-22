// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.net.http.HttpHeaders;
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

    public static @NotNull ContentEncoding fromHeaders(HttpHeaders headers) throws IOException {
        String encoding = headers.firstValue("content-encoding").orElse("");
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
