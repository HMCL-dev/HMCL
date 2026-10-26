// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

public class ModpackCompletionException extends Exception {
    public ModpackCompletionException() {
    }

    public ModpackCompletionException(String message) {
        super(message);
    }

    public ModpackCompletionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModpackCompletionException(Throwable cause) {
        super(cause);
    }
}
