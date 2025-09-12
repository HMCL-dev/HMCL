/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util.platform.hardware;

public final class PhysicalMemoryStatus {
    private final long total;
    private final long available;

    public PhysicalMemoryStatus(long total, long available) {
        this.total = total;
        this.available = available;
    }

    public long getTotal() {
        return total;
    }

    public long getUsed() {
        return hasAvailable() ? total - available : 0;
    }

    public long getAvailable() {
        return available;
    }

    public boolean hasAvailable() {
        return available >= 0;
    }

    public static final PhysicalMemoryStatus INVALID = new PhysicalMemoryStatus(0, -1);
}
