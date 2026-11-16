// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.launch;

import java.io.IOException;

public class ProcessCreationException extends IOException {
    public ProcessCreationException() {
    }

    public ProcessCreationException(String message) {
        super(message);
    }

    public ProcessCreationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProcessCreationException(Throwable cause) {
        super(cause);
    }
}
