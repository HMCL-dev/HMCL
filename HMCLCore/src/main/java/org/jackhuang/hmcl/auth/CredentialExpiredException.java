// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

/**
 * Thrown when the stored credentials has expired.
 * This exception indicates that a password login should be performed.
 *
 * @author yushijinhun
 * @see Account#logIn()
 */
public class CredentialExpiredException extends AuthenticationException {

    public CredentialExpiredException() {}

    public CredentialExpiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public CredentialExpiredException(String message) {
        super(message);
    }

    public CredentialExpiredException(Throwable cause) {
        super(cause);
    }
}
