// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

/**
 * @author Glavo
 */
public final class UnsupportedPlatformException extends Exception {
    public UnsupportedPlatformException() {
    }

    public UnsupportedPlatformException(String message) {
        super(message);
    }
}
