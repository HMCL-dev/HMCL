// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
