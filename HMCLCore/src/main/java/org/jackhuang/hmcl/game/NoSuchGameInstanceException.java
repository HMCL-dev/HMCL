// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
public class NoSuchGameInstanceException extends RuntimeException {
    private final GameInstanceID id;

    public NoSuchGameInstanceException(GameInstanceID id) {
        super(id.id());
        this.id = id;
    }

    public GameInstanceID getId() {
        return id;
    }
}
