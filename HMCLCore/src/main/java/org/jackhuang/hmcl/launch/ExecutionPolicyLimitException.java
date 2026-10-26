// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.launch;

public final class ExecutionPolicyLimitException extends RuntimeException {
    public ExecutionPolicyLimitException() {
    }

    public ExecutionPolicyLimitException(String message) {
        super(message);
    }

    public ExecutionPolicyLimitException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExecutionPolicyLimitException(Throwable cause) {
        super(cause);
    }
}
