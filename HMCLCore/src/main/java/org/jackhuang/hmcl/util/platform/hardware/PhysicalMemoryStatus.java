// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

public record PhysicalMemoryStatus(long total, long available) {

    public long getUsed() {
        return hasAvailable() ? total - available : 0;
    }

    public boolean hasAvailable() {
        return available >= 0;
    }

    public static final PhysicalMemoryStatus INVALID = new PhysicalMemoryStatus(0, -1);
}
