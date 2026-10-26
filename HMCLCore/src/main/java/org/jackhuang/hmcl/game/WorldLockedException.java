// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import java.io.IOException;

/**
 * @author Glavo
 */
public final class WorldLockedException extends IOException {
    public WorldLockedException() {
    }

    public WorldLockedException(String message) {
        super(message);
    }

    public WorldLockedException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorldLockedException(Throwable cause) {
        super(cause);
    }
}
