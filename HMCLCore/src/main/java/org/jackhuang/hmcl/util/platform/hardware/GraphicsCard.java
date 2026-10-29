// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Describes a graphics adapter and its detected driver metadata.
///
/// @author Glavo
@NotNullByDefault
public final class GraphicsCard {

    /// Normalizes a graphics adapter name for display, or returns `null` for a `null` name.
    @Contract("null -> null; !null -> !null")
    public static @Nullable String cleanName(@Nullable String name) {
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

    /// Creates a builder with no adapter metadata.
    public static Builder builder() {
        return new Builder();
    }

    /// The display name.
    private final String name;

    /// The hardware vendor, or `null` if unset.
    private final @Nullable HardwareVendor vendor;

    /// The adapter type, or `null` if unset.
    private final @Nullable Type type;

    /// The driver name, or `null` if unset.
    private final @Nullable String driver;

    /// The driver version, or `null` if unset.
    private final @Nullable String driverVersion;

    /// The Vulkan driver file paths.
    private final @Unmodifiable List<Path> vulkanDriverFiles;

    /// Creates an adapter with the supplied metadata and detection result.
    private GraphicsCard(String name, @Nullable HardwareVendor vendor, @Nullable Type type, @Nullable String driver, @Nullable String driverVersion, @Unmodifiable List<Path> vulkanDriverFiles) {
        this.name = Objects.requireNonNull(name);
        this.vendor = vendor;
        this.type = type;
        this.driver = driver;
        this.driverVersion = driverVersion;
        this.vulkanDriverFiles = vulkanDriverFiles;
    }

    /// Returns the display name.
    public String getName() {
        return name;
    }

    /// Returns the hardware vendor, or `null` if unknown.
    public @Nullable HardwareVendor getVendor() {
        return vendor;
    }

    /// Returns the driver name, or `null` if unknown.
    public @Nullable String getDriver() {
        return driver;
    }

    /// Returns the driver version, or `null` if unknown.
    public @Nullable String getDriverVersion() {
        return driverVersion;
    }

    /// Returns the adapter type, or `null` if unknown.
    public @Nullable Type getType() {
        return type;
    }

    /// Returns the unmodifiable Vulkan driver file list, empty if none were supplied.
    public @Unmodifiable List<Path> getVulkanDriverFiles() {
        return vulkanDriverFiles;
    }

    /// Returns the adapter name with known driver metadata and type.
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

    /// Classifies an adapter as integrated, discrete, or display-only.
    public enum Type {

        /// An integrated graphics adapter.
        Integrated,

        /// A discrete graphics adapter.
        Discrete,

        /// A display adapter that does not provide its own GPU rendering capability.
        DisplayOnly
    }

    /// Builds graphics adapter information from optional detected metadata.
    public static final class Builder {

        /// The display name, or `null` if unset.
        private @Nullable String name;

        /// The hardware vendor, or `null` if unset.
        private @Nullable HardwareVendor vendor;

        /// The adapter type, or `null` if unset.
        private @Nullable Type type;

        /// The driver name, or `null` if unset.
        private @Nullable String driver;

        /// The driver version, or `null` if unset.
        private @Nullable String driverVersion;

        /// The Vulkan driver file paths, or `null` if unset.
        private @Nullable List<Path> vulkanDriverFiles;

        /// Creates an adapter, supplying a vendor-based or unknown name if no name was set.
        ///
        /// Copies the Vulkan driver file list; its elements must be non-null.
        public GraphicsCard build() {
            @Nullable String name = this.name;
            if (name == null) {
                if (vendor != null)
                    name = vendor + " Graphics";
                else
                    name = "Unknown";
            }

            List<Path> vulkanDriverFiles = Objects.requireNonNullElse(this.vulkanDriverFiles, List.of());

            return new GraphicsCard(name, vendor, type, driver, driverVersion, List.copyOf(vulkanDriverFiles));
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

        /// Returns the adapter type, or `null` if unset.
        public @Nullable Type getType() {
            return type;
        }

        /// Sets the adapter type; `null` clears it. Returns this builder.
        public Builder setType(@Nullable Type type) {
            this.type = type;
            return this;
        }

        /// Returns the driver name, or `null` if unset.
        public @Nullable String getDriver() {
            return driver;
        }

        /// Sets the driver name; `null` clears it. Returns this builder.
        public Builder setDriver(@Nullable String driver) {
            this.driver = driver;
            return this;
        }

        /// Returns the driver version, or `null` if unset.
        public @Nullable String getDriverVersion() {
            return driverVersion;
        }

        /// Sets the driver version; `null` clears it. Returns this builder.
        public Builder setDriverVersion(@Nullable String driverVersion) {
            this.driverVersion = driverVersion;
            return this;
        }

        /// Returns the supplied Vulkan driver file list without copying, or `null` if unset.
        public @Nullable List<Path> getVulkanDriverFiles() {
            return vulkanDriverFiles;
        }

        /// Sets the Vulkan driver file paths; `null` clears it. Returns this builder.
        ///
        /// Retains the list without copying; [#build()] copies its current contents.
        public Builder setVulkanDriverFiles(@Nullable List<Path> files) {
            this.vulkanDriverFiles = files;
            return this;
        }
    }
}
