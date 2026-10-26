// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.url;

import org.jackhuang.hmcl.util.url.data.DataURLHandle;

import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

/**
 * @author Glavo
 */
public final class HMCLURLStreamHandlerProvider extends URLStreamHandlerProvider {
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        switch (protocol) {
            case "data":
                return new DataURLHandle();
        }
        return null;
    }
}
