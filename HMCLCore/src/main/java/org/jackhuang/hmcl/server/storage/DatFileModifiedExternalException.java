// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.storage;

import java.io.IOException;

public class DatFileModifiedExternalException extends IOException {
    private final ServerStorage storage;

    public DatFileModifiedExternalException(ServerStorage storage, String message) {
        super(message);
        this.storage = storage;
    }

    public ServerStorage getStorage() {
        return storage;
    }
}
