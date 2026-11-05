// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

public class ServerDisconnectException extends AuthenticationException {
    public ServerDisconnectException() {
    }

    public ServerDisconnectException(String message) {
        super(message);
    }

    public ServerDisconnectException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServerDisconnectException(Throwable cause) {
        super(cause);
    }
}
