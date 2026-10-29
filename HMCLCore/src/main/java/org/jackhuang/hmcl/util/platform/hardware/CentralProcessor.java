// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Describes a processor, or synthetic display information when detection is unavailable.
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

    /// The hardware vendor, or `null` if unknown.
    private final @Nullable HardwareVendor vendor;

    /// The processor's core counts, or `null` if unavailable.
    private final @Nullable Cores cores;

    /// Creates a processor snapshot.
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

    /// Returns observed or synthetic core counts, or `null` if unavailable.
    public @Nullable Cores getCores() {
        return cores;
    }

    /// Returns the name with P/E counts or a core total, appending threads when their count differs.
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(name);
        if (cores != null) {
            builder.append(" (");
            if (cores.efficiency > 0)
                builder.append(cores.performance).append("P + ").append(cores.efficiency).append('E');
            else
                builder.append(cores.physical()).append(" Cores");
            if (cores.logical != cores.physical())
                builder.append(" / ").append(cores.logical).append(" Threads");
            builder.append(')');
        }
        return builder.toString();
    }

    /// Positive physical and logical core totals for a processor or synthetic display fallback.
    ///
    /// Performance and efficiency counts deliberately merge performance tiers to simplify display;
    /// they do not represent physical clusters. Unclassified physical cores are counted as performance cores.
    /// Logical counts may be lower than physical totals when the source reports fewer available threads.
    /// Synthetic fallbacks may use the JVM's available processor count for both logical and performance totals.
    ///
    /// @param logical the positive logical processor count
    /// @param performance the nonnegative physical core count excluding identified efficiency cores
    /// @param efficiency the nonnegative physical core count classified as efficiency cores
    public record Cores(int logical, int performance, int efficiency) {
        /// Requires positive logical and physical totals, nonnegative categories, and no physical total overflow.
        public Cores {
            long physical = (long) performance + efficiency;
            if (logical <= 0 || performance < 0 || efficiency < 0 || physical <= 0 || physical > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Invalid processor core counts");
        }

        /// Returns the physical core count as the sum of performance and efficiency cores.
        public int physical() {
            return performance + efficiency;
        }
    }

    /// Builds processor information.
    public static final class Builder {
        /// The display name, or `null` if unset.
        private @Nullable String name;

        /// The hardware vendor, or `null` if unset.
        private @Nullable HardwareVendor vendor;

        /// The processor's core counts, or `null` if unset.
        private @Nullable Cores cores;

        /// Creates a snapshot, using a vendor-based name or `Unknown` if no name was set.
        public CentralProcessor build() {
            @Nullable String name = this.name;
            if (name == null)
                name = vendor != null ? vendor + " Processor" : "Unknown";
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

        /// Returns the processor's core counts, or `null` if unset.
        public @Nullable Cores getCores() {
            return cores;
        }

        /// Sets the processor's core counts; `null` clears them. Returns this builder.
        public Builder setCores(@Nullable Cores cores) {
            this.cores = cores;
            return this;
        }
    }
}
