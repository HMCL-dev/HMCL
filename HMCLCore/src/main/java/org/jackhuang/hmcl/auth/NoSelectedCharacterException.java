// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

/**
 * This exception gets threw when a monitor of {@link CharacterSelector} cannot select a
 * valid character.
 *
 * @see CharacterSelector
 * @author huangyuhui
 */
public final class NoSelectedCharacterException extends AuthenticationException {
    public NoSelectedCharacterException() {
    }
}
