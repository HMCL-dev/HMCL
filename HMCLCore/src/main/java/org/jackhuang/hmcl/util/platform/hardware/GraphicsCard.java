// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
@NotNullByDefault
public final class GraphicsCard {

    @UnknownNullability
    public static String cleanName(@UnknownNullability String name) {
        if (name == null)
            return null;

        name = name.replaceAll("\\((TM|R)\\)(?=\\s|$)", "");
        name = name.replace(" GPU", "");

        if (name.contains("Snapdragon")) {
            name = StringUtils.normalizeWhitespaces(name);
            if (name.startsWith("Snapdragon ")) {
                Matcher matcher = Pattern.compile("Snapdragon X Elite - (?<id>X1E\\S+) - Qualcomm Adreno").matcher(name);
                if (matcher.matches()) {
                    name = "Qualcomm Adreno Graphics";
                }
            }
        }

        return StringUtils.normalizeWhitespaces(name);
    }

    public static Builder builder() {
        return new Builder();
    }

    private final String name;
    private final @Nullable HardwareVendor vendor;
    private final @Nullable Type type;
    private final @Nullable String driver;
    private final @Nullable String driverVersion;
    private final @Unmodifiable List<Path> vulkanDriverFiles;

    /// Whether the detector identified this adapter as a known virtual display adapter.
    private final boolean virtualDisplayAdapter;

    /// Creates an adapter with the supplied metadata and detection result.
    private GraphicsCard(String name, @Nullable HardwareVendor vendor, @Nullable Type type, @Nullable String driver, @Nullable String driverVersion, @Unmodifiable List<Path> vulkanDriverFiles, boolean virtualDisplayAdapter) {
        this.name = Objects.requireNonNull(name);
        this.vendor = vendor;
        this.type = type;
        this.driver = driver;
        this.driverVersion = driverVersion;
        this.vulkanDriverFiles = vulkanDriverFiles;
        this.virtualDisplayAdapter = virtualDisplayAdapter;
    }

    /// Returns whether this adapter was identified as a known virtual display adapter.
    /// A false result also includes adapters whose classification is unknown;
    /// it does not establish that the adapter is physical.
    public boolean isVirtualDisplayAdapter() {
        return virtualDisplayAdapter;
    }

    public String getName() {
        return name;
    }

    public @Nullable HardwareVendor getVendor() {
        return vendor;
    }

    public @Nullable String getDriverVersion() {
        return driverVersion;
    }

    public @Nullable Type getType() {
        return type;
    }

    public List<Path> getVulkanDriverFiles() {
        return vulkanDriverFiles;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(name);

        if (driver != null || driverVersion != null) {
            builder.append(" (");
            if (driver != null) {
                builder.append(driver);
                if (driverVersion != null) {
                    builder.append(' ');
                }
            }
            if (driverVersion != null) {
                builder.append(driverVersion);
            }
            builder.append(')');
        }

        if (type != null) {
            builder.append(" [").append(type).append(']');
        }

        return builder.toString();
    }

    public enum Type {
        Integrated,
        Discrete
    }

    public static final class Builder {
        private @Nullable String name;
        private @Nullable HardwareVendor vendor;
        private @Nullable Type type;
        private @Nullable String driver;
        private @Nullable String driverVersion;
        private @Nullable List<Path> vulkanDriverFiles;

        /// Whether the adapter was identified as a known virtual display adapter; initially false.
        private boolean virtualDisplayAdapter;

        /// Creates an adapter, supplying a vendor-based or unknown name if no name was set.
        public GraphicsCard build() {
            @Nullable String name = this.name;
            if (name == null) {
                if (vendor != null)
                    name = vendor + " Graphics";
                else
                    name = "Unknown";
            }

            List<Path> vulkanDriverFiles = Objects.requireNonNullElse(this.vulkanDriverFiles, List.of());

            return new GraphicsCard(name, vendor, type, driver, driverVersion, List.copyOf(vulkanDriverFiles), virtualDisplayAdapter);
        }

        /// Sets whether the detector identified the adapter as a known virtual display adapter.
        ///
        /// @param virtualDisplayAdapter true for an identified virtual display adapter;
        ///                              false otherwise, including when classification is unknown
        /// @return this builder
        public Builder setVirtualDisplayAdapter(boolean virtualDisplayAdapter) {
            this.virtualDisplayAdapter = virtualDisplayAdapter;
            return this;
        }

        public @Nullable String getName() {
            return name;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public @Nullable HardwareVendor getVendor() {
            return vendor;
        }

        public Builder setVendor(HardwareVendor vendor) {
            this.vendor = vendor;
            return this;
        }

        public @Nullable Type getType() {
            return type;
        }

        public Builder setType(Type type) {
            this.type = type;
            return this;
        }

        public @Nullable String getDriver() {
            return driver;
        }

        public Builder setDriver(String driver) {
            this.driver = driver;
            return this;
        }

        public @Nullable String getDriverVersion() {
            return driverVersion;
        }

        public Builder setDriverVersion(String driverVersion) {
            this.driverVersion = driverVersion;
            return this;
        }

        public @Nullable List<Path> getVulkanDriverFiles() {
            return vulkanDriverFiles;
        }

        public Builder setVulkanDriverFiles(@Nullable List<Path> files) {
            this.vulkanDriverFiles = files;
            return this;
        }
    }
}
