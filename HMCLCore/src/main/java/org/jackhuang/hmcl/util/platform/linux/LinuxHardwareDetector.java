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
package org.jackhuang.hmcl.util.platform.linux;

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class LinuxHardwareDetector extends HardwareDetector {

    @Override
    public @Nullable CentralProcessor detectCentralProcessor() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.LINUX)
            return null;
        CentralProcessor cpu = LinuxCPUDetector.detect();
        return cpu != null ? cpu : super.detectCentralProcessor();
    }

    @Override
    public List<GraphicsCard> detectGraphicsCards() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.LINUX)
            return null;
        List<GraphicsCard> cards = LinuxGPUDetector.detect();
        if (cards == null || cards.isEmpty()) {
            List<GraphicsCard> fastfetchResults = super.detectGraphicsCards();
            if (fastfetchResults != null) // Avoid overwriting empty lists with null
                cards = fastfetchResults;
        }
        return cards;
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
