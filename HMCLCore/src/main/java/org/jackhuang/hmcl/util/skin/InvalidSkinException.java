// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.skin;

public class InvalidSkinException extends Exception {

    public InvalidSkinException() {}

    public InvalidSkinException(String message) {
        super(message);
    }

    public InvalidSkinException(Throwable cause) {
        super(cause);
    }

    public InvalidSkinException(String message, Throwable cause) {
        super(message, cause);
    }
}
