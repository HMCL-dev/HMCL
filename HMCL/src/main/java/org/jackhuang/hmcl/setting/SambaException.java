// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

public final class SambaException extends RuntimeException {
    public SambaException() {
    }

    public SambaException(String message) {
        super(message);
    }

    public SambaException(String message, Throwable cause) {
        super(message, cause);
    }

    public SambaException(Throwable cause) {
        super(cause);
    }
}
