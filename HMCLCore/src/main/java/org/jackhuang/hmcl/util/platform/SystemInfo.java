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

import org.jackhuang.hmcl.util.DataSizeUnit;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jackhuang.hmcl.util.platform.hardware.PhysicalMemoryStatus;
import org.jackhuang.hmcl.util.platform.linux.LinuxHardwareDetector;
import org.jackhuang.hmcl.util.platform.macos.MacOSHardwareDetector;
import org.jackhuang.hmcl.util.platform.windows.*;
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
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                DETECTOR = new MacOSHardwareDetector();
            else
                DETECTOR = new HardwareDetector();
        }

        public static final long TOTAL_MEMORY = DETECTOR.getTotalMemorySize();
        public static final @Nullable CentralProcessor CENTRAL_PROCESSOR = DETECTOR.detectCentralProcessor();
        public static final @Nullable List<GraphicsCard> GRAPHICS_CARDS = DETECTOR.detectGraphicsCards();
    }

    public static void initialize() {
        StringBuilder builder = new StringBuilder("System Info:");

        // CPU
        CentralProcessor cpu = getCentralProcessor();
        if (cpu != null)
            builder.append("\n - CPU: ").append(cpu);

        // Graphics Card
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

        // Memory
        long totalMemorySize = getTotalMemorySize();
        long usedMemorySize = getUsedMemorySize();

        builder.append("\n - Memory: ")
                .append(DataSizeUnit.format(usedMemorySize))
                .append(" / ")
                .append(DataSizeUnit.format(totalMemorySize));

        if (totalMemorySize > 0 && usedMemorySize > 0)
            builder.append(" (").append((int) (((double) usedMemorySize / totalMemorySize) * 100)).append("%)");

        LOG.info(builder.toString());
    }

    public static PhysicalMemoryStatus getPhysicalMemoryStatus() {
        long totalMemorySize = getTotalMemorySize();
        long freeMemorySize = getFreeMemorySize();

        return totalMemorySize > 0 && freeMemorySize >= 0
                ? new PhysicalMemoryStatus(totalMemorySize, freeMemorySize)
                : PhysicalMemoryStatus.INVALID;
    }

    public static long getTotalMemorySize() {
        return Holder.TOTAL_MEMORY;
    }

    public static long getFreeMemorySize() {
        return Holder.DETECTOR.getFreeMemorySize();
    }

    public static long getUsedMemorySize() {
        long totalMemorySize = getTotalMemorySize();
        if (totalMemorySize <= 0)
            return 0;

        return Long.max(0, totalMemorySize - getFreeMemorySize());
    }

    public static @Nullable CentralProcessor getCentralProcessor() {
        return Holder.CENTRAL_PROCESSOR;
    }

    public static @Nullable List<GraphicsCard> getGraphicsCards() {
        return Holder.GRAPHICS_CARDS;
    }

    private SystemInfo() {
    }
}
