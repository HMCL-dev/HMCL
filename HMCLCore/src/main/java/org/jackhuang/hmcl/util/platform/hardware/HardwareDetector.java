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

import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * @author Glavo
 */
public class HardwareDetector {
    private static final boolean USE_FAST_FETCH = "true".equalsIgnoreCase(System.getProperty("hmcl.hardware.fastfetch", "true"));

    public @Nullable CentralProcessor detectCentralProcessor() {
        return USE_FAST_FETCH ? FastFetchUtils.detectCentralProcessor() : null;
    }

    public @Nullable List<GraphicsCard> detectGraphicsCards() {
        return USE_FAST_FETCH ? FastFetchUtils.detectGraphicsCards() : null;
    }

    public long getTotalMemorySize() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
                return bean.getTotalMemorySize();
            }
        } catch (NoClassDefFoundError ignored) {
        }

        return 0L;
    }

    public long getFreeMemorySize() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
                return bean.getFreeMemorySize();
            }
        } catch (NoClassDefFoundError ignored) {
        }

        return 0L;
    }
}
