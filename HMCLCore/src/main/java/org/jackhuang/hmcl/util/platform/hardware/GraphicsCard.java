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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Glavo
 */
public final class GraphicsCard {

    public static Builder builder() {
        return new Builder();
    }

    private final String name;
    private final @Nullable Vendor vendor;
    private final @Nullable Type type;
    private final @Nullable String driver;
    private final @Nullable String driverVersion;

    private GraphicsCard(String name, @Nullable Vendor vendor, @Nullable Type type, @Nullable String driver, @Nullable String driverVersion) {
        this.name = Objects.requireNonNull(name);
        this.vendor = vendor;
        this.type = type;
        this.driver = driver;
        this.driverVersion = driverVersion;
    }

    public String getName() {
        return name;
    }

    public @Nullable Vendor getVendor() {
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

    public static final class Vendor {
        public static final Vendor INTEL = new Vendor("Intel");
        public static final Vendor NVIDIA = new Vendor("NVIDIA");
        public static final Vendor AMD = new Vendor("AMD");
        public static final Vendor APPLE = new Vendor("Apple");
        public static final Vendor QUALCOMM = new Vendor("Qualcomm");
        public static final Vendor MTK = new Vendor("MTK");
        public static final Vendor VMWARE = new Vendor("VMware");
        public static final Vendor PARALLEL = new Vendor("Parallel");
        public static final Vendor MICROSOFT = new Vendor("Microsoft");
        public static final Vendor MOORE_THREADS = new Vendor("Moore Threads");
        public static final Vendor BROADCOM = new Vendor("Broadcom");
        public static final Vendor IMG = new Vendor("Imagination");
        public static final Vendor LOONGSON = new Vendor("Loongson");
        public static final Vendor JINGJIA_MICRO = new Vendor("Jingjia Micro");
        public static final Vendor HUAWEI = new Vendor("Huawei");
        public static final Vendor ZHAOXIN = new Vendor("Zhaoxin");

        public static @Nullable Vendor getKnown(String name) {
            if (name == null)
                return null;

            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("intel")) return INTEL;
            if (lower.startsWith("nvidia")) return NVIDIA;
            if (lower.startsWith("advanced micro devices")
                    || (lower.startsWith("amd") && !(lower.length() > 3 && Character.isAlphabetic(lower.charAt(3)))))
                return AMD;
            if (lower.equals("brcm") || lower.startsWith("broadcom")) return BROADCOM;
            if (lower.startsWith("mediatek")) return MTK;
            if (lower.startsWith("qualcomm")) return QUALCOMM;
            if (lower.startsWith("apple")) return APPLE;
            if (lower.startsWith("microsoft")) return MICROSOFT;
            if (lower.startsWith("imagination") || lower.equals("img")) return IMG;

            if (lower.startsWith("loongson")) return LOONGSON;
            if (lower.startsWith("moore threads")) return MOORE_THREADS;
            if (lower.startsWith("jingjia")) return JINGJIA_MICRO;
            if (lower.startsWith("huawei")) return HUAWEI;
            if (lower.startsWith("zhaoxin")) return ZHAOXIN;

            return null;
        }

        @Contract("null -> null; !null -> !null")
        public static Vendor of(String name) {
            if (name == null)
                return null;

            Vendor known = getKnown(name);
            return known != null ? known : new Vendor(name);
        }

        public static @Nullable Vendor ofId(int vendorId) {
            // https://devicehunt.com/all-pci-vendors
            switch (vendorId) {
                case 0x106b:
                    return APPLE;
                case 0x1002:
                case 0x1022:
                case 0x1dd8: // AMD Pensando Systems
                case 0x1924: // AMD Solarflare
                    return AMD;
                case 0x8086:
                case 0x8087:
                case 0x03e7:
                    return INTEL;
                case 0x0955:
                case 0x10de:
                case 0x12d2:
                    return NVIDIA;
                case 0x1ed5:
                    return MOORE_THREADS;
                case 0x168c:
                case 0x5143:
                    return QUALCOMM;
                case 0x14c3:
                    return MTK;
                case 0x15ad:
                    return VMWARE;
                case 0x1ab8:
                    return PARALLEL;
                case 0x1414:
                    return MICROSOFT;
                case 0x182f:
                case 0x14e4:
                    return BROADCOM;
                case 0x0014:
                    return LOONGSON;
                case 0x0731:
                    return JINGJIA_MICRO;
                case 0x19e5:
                    return HUAWEI;
                case 0x1d17:
                    return ZHAOXIN;
                default:
                    return null;
            }
        }

        private final String name;

        public Vendor(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Vendor && name.equals(((Vendor) o).name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Type {
        Integrated,
        Discrete
    }

    public static final class Builder {
        private String name;
        private Vendor vendor;
        private Type type;
        private String driver;
        private String driverVersion;

        public GraphicsCard build() {
            if (name == null)
                throw new IllegalStateException("Name not set");

            return new GraphicsCard(name, vendor, type, driver, driverVersion);
        }

        public String getName() {
            return name;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Vendor getVendor() {
            return vendor;
        }

        public Builder setVendor(Vendor vendor) {
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
