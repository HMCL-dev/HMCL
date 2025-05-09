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
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jackhuang.hmcl.util.platform.linux.LinuxHardwareDetector;
import org.jackhuang.hmcl.util.platform.macos.MacOSHardwareDetector;
import org.jackhuang.hmcl.util.platform.windows.WindowsHardwareDetector;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class SystemInfo {

    private static final class Holder {
        public static final HardwareDetector DETECTOR;

        static {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                DETECTOR = new WindowsHardwareDetector();
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX)
                DETECTOR = new LinuxHardwareDetector();
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
                DETECTOR = new MacOSHardwareDetector();
            else
                DETECTOR = new HardwareDetector();
        }

        public static final @Nullable List<GraphicsCard> GRAPHICS_CARDS = DETECTOR.detectGraphicsCards();
    }

    public static void initialize() {
        StringBuilder builder = new StringBuilder("System Info:");
        List<GraphicsCard> graphicsCards = getGraphicsCards();

        if (graphicsCards != null) {
            if (graphicsCards.isEmpty())
                builder.append("\n - GPU: Not Found");
            else if (graphicsCards.size() == 1)
                builder.append("\n - GPU: ").append(graphicsCards.get(0));
            else {
                int index = 1;
                for (GraphicsCard graphicsCard : graphicsCards) {
                    builder.append("\n - GPU ").append(index++).append(": ").append(graphicsCard);
                }
            }
        }

        OperatingSystem.PhysicalMemoryStatus memoryStatus = OperatingSystem.getPhysicalMemoryStatus();
        if (memoryStatus.getTotal() > 0 && memoryStatus.getAvailable() > 0) {
            builder.append("\n - Memory: ")
                    .append(String.format("%.2f GiB / %.2f GiB (%d%%)",
                            memoryStatus.getUsedGB(), memoryStatus.getTotalGB(),
                            (int) (((double) memoryStatus.getUsed() / memoryStatus.getTotal()) * 100)
                    ));
        }

        LOG.info(builder.toString());
    }

    public static @Nullable List<GraphicsCard> getGraphicsCards() {
        return Holder.GRAPHICS_CARDS;
    }

    private SystemInfo() {
    }
}
