// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Describes a processor and its reported core counts.
///
/// @author Glavo
@NotNullByDefault
public final class CentralProcessor {

    /// Normalizes a processor name for display, or returns `null` for a `null` name.
    @Contract("null -> null; !null -> !null")
    public static @Nullable String cleanName(@Nullable String name) {
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

    /// The display name.
    private final String name;
    /// The hardware vendor, or `null` if unset.
    private final @Nullable HardwareVendor vendor;
    /// The processor core counts, or `null` if unset.
    private final @Nullable Cores cores;

    /// Creates processor information from the supplied name, vendor, and core counts.
    private CentralProcessor(String name, @Nullable HardwareVendor vendor, @Nullable Cores cores) {
        this.name = name;
        this.vendor = vendor;
        this.cores = cores;
    }

    /// Returns the display name.
    public String getName() {
        return name;
    }

    /// Returns the hardware vendor, or `null` if unknown.
    public @Nullable HardwareVendor getVendor() {
        return vendor;
    }

    /// Returns the processor core counts, or `null` if unknown.
    public @Nullable Cores getCores() {
        return cores;
    }

    /// Returns the processor name and known core counts.
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

    /// Reported processor topology.
    ///
    /// @param physical the physical core count
    /// @param logical the logical processor count
    /// @param packages the processor package count
    public record Cores(int physical, int logical, int packages) {
        /// Creates counts for one package, using the logical count as the physical count.
        public Cores(int logical) {
            this(logical, logical, 1);
        }

        /// Returns the physical, logical, and package counts.
        @Override
        public String toString() {
            return String.format("Cores[physical=%d, logical=%d, packages=%d]", physical, logical, packages);
        }
    }

    /// Builds processor information with optional vendor and core counts.
    public static final class Builder {
        /// The display name, or `null` if unset.
        private @Nullable String name;
        /// The hardware vendor, or `null` if unset.
        private @Nullable HardwareVendor vendor;
        /// The processor core counts, or `null` if unset.
        private @Nullable Cores cores;

        /// Creates processor information, using a vendor-based name or `Unknown` if no name was set.
        public CentralProcessor build() {
            @Nullable String name = this.name;
            if (name == null) {
                if (vendor != null)
                    name = vendor + " Processor";
                else
                    name = "Unknown";
            }

            return new CentralProcessor(name, vendor, cores);
        }

        /// Returns the display name, or `null` if unset.
        public @Nullable String getName() {
            return name;
        }

        /// Sets the display name; `null` clears it. Returns this builder.
        public Builder setName(@Nullable String name) {
            this.name = name;
            return this;
        }

        /// Returns the hardware vendor, or `null` if unset.
        public @Nullable HardwareVendor getVendor() {
            return vendor;
        }

        /// Sets the hardware vendor; `null` clears it. Returns this builder.
        public Builder setVendor(@Nullable HardwareVendor vendor) {
            this.vendor = vendor;
            return this;
        }

        /// Returns the processor core counts, or `null` if unset.
        public @Nullable Cores getCores() {
            return cores;
        }

        /// Sets the processor core counts; `null` clears it. Returns this builder.
        public Builder setCores(@Nullable Cores cores) {
            this.cores = cores;
            return this;
        }
    }
}
