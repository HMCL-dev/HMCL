// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server;

import java.io.IOException;

public class ServerBlockedByMojangException extends IOException {
    public ServerBlockedByMojangException(String message) {
        super(message);
    }
}
