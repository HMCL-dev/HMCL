// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.auth.AuthenticationException;

/**
 * @author yushijinhun
 */
public class AuthlibInjectorDownloadException extends AuthenticationException {

    public AuthlibInjectorDownloadException() {
    }

    public AuthlibInjectorDownloadException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthlibInjectorDownloadException(String message) {
        super(message);
    }

    public AuthlibInjectorDownloadException(Throwable cause) {
        super(cause);
    }
}
