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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Glavo
 */
public final class GraphicsCard {

    public static String cleanName(String name) {
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

    private GraphicsCard(String name, @Nullable HardwareVendor vendor, @Nullable Type type, @Nullable String driver, @Nullable String driverVersion) {
        this.name = Objects.requireNonNull(name);
        this.vendor = vendor;
        this.type = type;
        this.driver = driver;
        this.driverVersion = driverVersion;
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

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(name);

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
        private String name;
        private HardwareVendor vendor;
        private Type type;
        private String driver;
        private String driverVersion;

        public GraphicsCard build() {
            String name = this.name;
            if (name == null) {
                if (vendor != null)
                    name = vendor + " Graphics";
                else
                    name = "Unknown";
            }

            return new GraphicsCard(name, vendor, type, driver, driverVersion);
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

        public Type getType() {
            return type;
        }

        public Builder setType(Type type) {
            this.type = type;
            return this;
        }

        public String getDriver() {
            return driver;
        }

        public Builder setDriver(String driver) {
            this.driver = driver;
            return this;
        }

        public String getDriverVersion() {
            return driverVersion;
        }

        public Builder setDriverVersion(String driverVersion) {
            this.driverVersion = driverVersion;
            return this;
        }
    }
}
