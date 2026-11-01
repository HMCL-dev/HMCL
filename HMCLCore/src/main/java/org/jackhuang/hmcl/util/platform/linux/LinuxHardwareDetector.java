// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.linux;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Detects Linux hardware information.
///
/// @author Glavo
@NotNullByDefault
public final class LinuxHardwareDetector extends HardwareDetector {

    /// Detects Linux processors, using the default detector when native information is unavailable.
    @Override
    public @Unmodifiable List<CentralProcessor> detectCentralProcessors() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.LINUX)
            return super.detectCentralProcessors();
        @Unmodifiable List<CentralProcessor> processors = LinuxCPUDetector.detect();
        return processors.isEmpty() ? super.detectCentralProcessors() : processors;
    }

    /// Returns an immutable graphics card list, empty if detection is unsupported or fails.
    @Override
    public @Unmodifiable List<GraphicsCard> detectGraphicsCards() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.LINUX)
            return List.of();
        @Unmodifiable List<GraphicsCard> cards = LinuxGPUDetector.detect();
        return cards.isEmpty() ? super.detectGraphicsCards() : cards;
    }

    /// Reads a desktop environment description from this process's environment variables.
    ///
    /// Uses `XDG_CURRENT_DESKTOP`, falling back to `XDG_SESSION_DESKTOP` if the former is absent or blank.
    /// Returns `null` if neither supplies a nonblank name. Otherwise, strips leading and trailing whitespace
    /// from the name and, if `XDG_SESSION_TYPE` is nonblank, appends its stripped value as `name (type)`.
    /// Desktop names and session types are otherwise preserved without normalization or validation.
    @Override
    public @Nullable String detectDesktopEnvironment() {
        @Nullable String desktopName = System.getenv("XDG_CURRENT_DESKTOP");
        if (StringUtils.isBlank(desktopName))
            desktopName = System.getenv("XDG_SESSION_DESKTOP");

        if (StringUtils.isNotBlank(desktopName)) {
            @Nullable String sessionType = System.getenv("XDG_SESSION_TYPE");
            return StringUtils.isNotBlank(sessionType)
                    ? "%s (%s)".formatted(desktopName.strip(), sessionType.strip())
                    : desktopName.strip();
        } else
            return null;
    }

    private static final Path MEMINFO = Paths.get("/proc/meminfo");
    private static final Pattern MEMINFO_PATTERN = Pattern.compile("^.+:\\s*(?<value>\\d+)\\s*kB?$");

    private static long parseMemoryInfoLine(final String line) throws IOException {
        Matcher matcher = MEMINFO_PATTERN.matcher(line);
        if (!matcher.matches())
            throw new IOException("Unable to parse line in /proc/meminfo: " + line);

        return Long.parseLong(matcher.group("value")) * 1024;
    }

    @Override
    public long getTotalMemorySize() {
        try (BufferedReader reader = Files.newBufferedReader(MEMINFO)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    long total = parseMemoryInfoLine(line);
                    if (total <= 0)
                        throw new IOException("Invalid total memory size: " + line + " kB");

                    return total;
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to parse /proc/meminfo", e);
        }

        return super.getTotalMemorySize();
    }

    @Override
    public long getFreeMemorySize() {
        try (BufferedReader reader = Files.newBufferedReader(MEMINFO)) {
            long free = -1L;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    long available = parseMemoryInfoLine(line);
                    if (available < 0)
                        throw new IOException("Invalid available memory size: " + line + " kB");
                    return available;
                }

                if (line.startsWith("MemFree:"))
                    free = parseMemoryInfoLine(line);
            }

            if (free >= 0)
                return free;
        } catch (Throwable e) {
            LOG.warning("Failed to parse /proc/meminfo", e);
        }

        return super.getFreeMemorySize();
    }
}
