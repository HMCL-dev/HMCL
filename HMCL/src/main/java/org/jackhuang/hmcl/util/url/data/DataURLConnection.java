// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
    private final DataURL dataUrl;
    private byte[] data;
    private InputStream inputStream;

    DataURLConnection(URL url, DataURL dataUrl) {
        super(url);
        this.dataUrl = dataUrl;
    }

    @Override
    public void connect() throws IOException {
        if (!connected) {
            connected = true;
            data = dataUrl.readBytes();
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
        return dataUrl.getMediaType();
    }

    @Override
    public long getContentLengthLong() {
        return data != null ? data.length : -1;
    }
}
