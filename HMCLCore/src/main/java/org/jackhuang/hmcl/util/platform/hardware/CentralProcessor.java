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

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Glavo
 */
public final class CentralProcessor {

    public static String cleanName(String name) {
        if (name == null)
            return null;

        int idx = name.indexOf('@');
        if (idx > 0)
            name = name.substring(0, idx);

        name = name.replaceFirst(" (\\d+|Dual|Quad|Six|Eight|Ten)-[Cc]ores?", "");
        name = name.replaceAll(" (CPU|FPU|APU|Processor)", "");
        name = name.replaceAll("\\((TM|R)\\)(?=\\s|$)", "");

        if (name.contains("Intel")) {
            name = name.replaceFirst("^(\\d+th Gen )?Intel\\s+", "Intel ");
            name = name.replace("Core(TM)2", "Core 2");
        } else if (name.contains("AMD")) {
            name = name.replace("(tm)", "");
            idx = name.indexOf(" w/ Radeon "); // Radeon 780M Graphics
            if (idx < 0)
                idx = name.indexOf(" with Radeon ");
            if (idx < 0)
                idx = name.indexOf(" with AMD Radeon ");
            if (idx > 0)
                name = name.substring(0, idx);
        } else if (name.contains("Loongson")) {
            name = name.replaceFirst("^Loongson-3A R\\d \\((Loongson-[^)]+)\\)", "$1");
        } else if (name.contains("Snapdragon")) {
            name = StringUtils.normalizeWhitespaces(name);

            if (name.startsWith("Snapdragon ")) {
                Matcher matcher = Pattern.compile("Snapdragon X Elite - (?<id>X1E\\S+) - Qualcomm Oryon").matcher(name);
                if (matcher.matches()) {
                    name = "Qualcomm Snapdragon X Elite " + matcher.group("id");
                } else if (!name.contains("Qualcomm")) {
                    name = "Qualcomm " + name;
                }
            }
        }

        return StringUtils.normalizeWhitespaces(name);
    }

    private final String name;
    private final @Nullable HardwareVendor vendor;
    private final @Nullable Cores cores;

    private CentralProcessor(String name, @Nullable HardwareVendor vendor, @Nullable Cores cores) {
        this.name = name;
        this.vendor = vendor;
        this.cores = cores;
    }

    public String getName() {
        return name;
    }

    public @Nullable HardwareVendor getVendor() {
        return vendor;
    }

    public @Nullable Cores getCores() {
        return cores;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(128);
        if (cores != null && cores.packages > 1)
            builder.append(cores.packages).append(" x ");

        builder.append(name);

        if (cores != null) {
            builder.append(" (");
            builder.append(cores.physical).append(" Cores");
            if (cores.logical > 0 && cores.logical != cores.physical)
                builder.append(" / ").append(cores.logical).append(" Threads");
            builder.append(")");
        }

        return builder.toString();
    }

    public static final class Cores {
        public final int physical;
        public final int logical;
        public final int packages;

        public Cores(int logical) {
            this(logical, logical, 1);
        }

        public Cores(int physical, int logical, int packages) {
            this.physical = physical;
            this.logical = logical;
            this.packages = packages;
        }

        @Override
        public String toString() {
            return String.format("Cores[physical=%d, logical=%d, packages=%d]", physical, logical, packages);
        }
    }

    public static final class Builder {
        private String name;
        private @Nullable HardwareVendor vendor;
        private @Nullable Cores cores;

        public CentralProcessor build() {
            String name = this.name;
            if (name == null) {
                if (vendor != null)
                    name = vendor + " Processor";
                else
                    name = "Unknown";
            }

            return new CentralProcessor(name, vendor, cores);
        }

        public String getName() {
            return name;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public HardwareVendor getVendor() {
            return vendor;
        }

        public Builder setVendor(HardwareVendor vendor) {
            this.vendor = vendor;
            return this;
        }

        public Cores getCores() {
            return cores;
        }

        public Builder setCores(Cores cores) {
            this.cores = cores;
            return this;
        }
    }
}
