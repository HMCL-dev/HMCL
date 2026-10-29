// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.util.DataSizeUnit;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jackhuang.hmcl.util.platform.hardware.PhysicalMemoryStatus;
import org.jackhuang.hmcl.util.platform.linux.LinuxHardwareDetector;
import org.jackhuang.hmcl.util.platform.macos.MacOSHardwareDetector;
import org.jackhuang.hmcl.util.platform.windows.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Provides cached hardware information and current physical memory usage.
@NotNullByDefault
public final class SystemInfo {

    /// Lazily initializes the platform detector and cached hardware information.
    private static final class Holder {

        /// The hardware detector for the current operating system.
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

        /// The detected total physical memory size, in bytes.
        public static final long TOTAL_MEMORY = DETECTOR.getTotalMemorySize();

        /// A nonempty immutable processor snapshot supplied by the detector.
        public static final @Unmodifiable List<CentralProcessor> CENTRAL_PROCESSORS = DETECTOR.detectCentralProcessors();

        /// The immutable graphics card snapshot, empty if detection is unavailable or fails.
        public static final @Unmodifiable List<GraphicsCard> GRAPHICS_CARDS = DETECTOR.detectGraphicsCards();
    }

    /// Initializes cached hardware information if needed and logs a system summary on each call.
    public static void initialize() {
        StringBuilder builder = new StringBuilder("System Info:");

        // CPU
        @Unmodifiable List<CentralProcessor> processors = getCentralProcessors();
        for (int i = 0; i < processors.size(); i++) {
            builder.append("\n - CPU");
            if (processors.size() > 1)
                builder.append(' ').append(i + 1);
            builder.append(": ").append(processors.get(i));
        }

        // Graphics Card
        @Unmodifiable List<GraphicsCard> graphicsCards = getGraphicsCards();
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

    /// Returns a snapshot using the cached total and currently available physical memory.
    ///
    /// Returns [PhysicalMemoryStatus#INVALID] if the total is non-positive or available memory is negative.
    public static PhysicalMemoryStatus getPhysicalMemoryStatus() {
        long totalMemorySize = getTotalMemorySize();
        long freeMemorySize = getFreeMemorySize();

        return totalMemorySize > 0 && freeMemorySize >= 0
                ? new PhysicalMemoryStatus(totalMemorySize, freeMemorySize)
                : PhysicalMemoryStatus.INVALID;
    }

    /// Returns the cached total physical memory size in bytes, or zero if detection is unsupported.
    public static long getTotalMemorySize() {
        return Holder.TOTAL_MEMORY;
    }

    /// Queries the available physical memory in bytes, including reclaimable memory where supported.
    ///
    /// Returns zero if detection is unsupported.
    public static long getFreeMemorySize() {
        return Holder.DETECTOR.getFreeMemorySize();
    }

    /// Returns the cached total minus the currently available physical memory, in bytes, clamped to zero.
    ///
    /// Returns zero without querying available memory if the cached total is non-positive.
    public static long getUsedMemorySize() {
        long totalMemorySize = getTotalMemorySize();
        if (totalMemorySize <= 0)
            return 0;

        return Long.max(0, totalMemorySize - getFreeMemorySize());
    }

    /// Returns a nonempty immutable list of processor snapshots in stable order.
    ///
    /// Sources without package topology may provide only the first processor identity.
    /// If detection returns no processors, a synthetic `Unknown` processor with no vendor is supplied.
    /// For a single entry with no core counts, logical and performance counts use [Runtime#availableProcessors()].
    /// These display estimates do not establish the hardware's physical core count or package topology.
    public static @Unmodifiable List<CentralProcessor> getCentralProcessors() {
        return Holder.CENTRAL_PROCESSORS;
    }

    /// Returns the cached immutable graphics card list, empty if no cards were reported or detection failed.
    public static @Unmodifiable List<GraphicsCard> getGraphicsCards() {
        return Holder.GRAPHICS_CARDS;
    }

    /// Prevents instantiation of this utility class.
    private SystemInfo() {
    }
}
