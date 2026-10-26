// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.launch;

import java.io.IOException;

public class NotDecompressingNativesException extends IOException {
    public NotDecompressingNativesException() {
    }

    public NotDecompressingNativesException(String message) {
        super(message);
    }

    public NotDecompressingNativesException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotDecompressingNativesException(Throwable cause) {
        super(cause);
    }
}
