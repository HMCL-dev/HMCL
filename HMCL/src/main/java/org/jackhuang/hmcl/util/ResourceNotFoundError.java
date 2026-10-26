// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import java.io.InputStream;

/**
 * Suppress the throwable when we make sure the resource cannot miss.
 * @see CrashReporter
 */
public final class ResourceNotFoundError extends Error {
    public ResourceNotFoundError(String message) {
        super(message);
    }

    public ResourceNotFoundError(String message, Throwable cause) {
        super(message, cause);
    }

    public static InputStream getResourceAsStream(String url) {
        InputStream stream = ResourceNotFoundError.class.getResourceAsStream(url);
        if (stream == null)
            throw new ResourceNotFoundError("Resource not found: " + url);
        return stream;
    }
}
