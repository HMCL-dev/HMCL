// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

/**
 * This exception gets threw when authenticating a yggdrasil account and there is no valid character.
 * (A account may hold more than one characters.)
 */
public final class NoCharacterException extends AuthenticationException {
    public NoCharacterException() {
    }
}
