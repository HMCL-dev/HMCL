// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

/**
 * Thrown when a previously existing character cannot be found.
 */
public final class CharacterDeletedException extends AuthenticationException {
    public CharacterDeletedException() {
    }
}
