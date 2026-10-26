// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

public class UnsupportedModpackException extends Exception {
    public UnsupportedModpackException() {
    }

    public UnsupportedModpackException(String message) {
        super(message);
    }

    public UnsupportedModpackException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedModpackException(Throwable cause) {
        super(cause);
    }
}
