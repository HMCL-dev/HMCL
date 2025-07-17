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

import java.util.Locale;

public final class HardwareVendor {
    public static final HardwareVendor INTEL = new HardwareVendor("Intel");
    public static final HardwareVendor NVIDIA = new HardwareVendor("NVIDIA");
    public static final HardwareVendor AMD = new HardwareVendor("AMD");
    public static final HardwareVendor APPLE = new HardwareVendor("Apple");
    public static final HardwareVendor ARM = new HardwareVendor("ARM");
    public static final HardwareVendor QUALCOMM = new HardwareVendor("Qualcomm");
    public static final HardwareVendor MTK = new HardwareVendor("MTK");
    public static final HardwareVendor VMWARE = new HardwareVendor("VMware");
    public static final HardwareVendor PARALLEL = new HardwareVendor("Parallel");
    public static final HardwareVendor MICROSOFT = new HardwareVendor("Microsoft");
    public static final HardwareVendor MOORE_THREADS = new HardwareVendor("Moore Threads");
    public static final HardwareVendor BROADCOM = new HardwareVendor("Broadcom");
    public static final HardwareVendor IMG = new HardwareVendor("Imagination");
    public static final HardwareVendor LOONGSON = new HardwareVendor("Loongson");
    public static final HardwareVendor JINGJIA_MICRO = new HardwareVendor("Jingjia Micro");
    public static final HardwareVendor HUAWEI = new HardwareVendor("Huawei");
    public static final HardwareVendor ZHAOXIN = new HardwareVendor("Zhaoxin");
    public static final HardwareVendor SAMSUNG = new HardwareVendor("Samsung");
    public static final HardwareVendor MARVELL = new HardwareVendor("Marvell");
    public static final HardwareVendor AMPERE = new HardwareVendor("Ampere");
    public static final HardwareVendor ROCKCHIP = new HardwareVendor("Rockchip");

    // RISC-V
    public static final HardwareVendor THEAD = new HardwareVendor("T-Head");
    public static final HardwareVendor STARFIVE = new HardwareVendor("StarFive");
    public static final HardwareVendor ESWIN = new HardwareVendor("ESWIN");
    public static final HardwareVendor SPACEMIT = new HardwareVendor("SpacemiT");

    public static @Nullable HardwareVendor getKnown(String name) {
        if (name == null)
            return null;

        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("intel") || lower.startsWith("genuineintel")) return INTEL;
        if (lower.startsWith("nvidia")) return NVIDIA;
        if (lower.startsWith("advanced micro devices")
            || lower.startsWith("authenticamd")
            || (lower.startsWith("amd") && !(lower.length() > 3 && Character.isAlphabetic(lower.charAt(3)))))
            return AMD;
        if (lower.equals("brcm") || lower.startsWith("broadcom")) return BROADCOM;
        if (lower.startsWith("mediatek")) return MTK;
        if (lower.equals("qcom") || lower.startsWith("qualcomm")) return QUALCOMM;
        if (lower.startsWith("apple")) return APPLE;
        if (lower.startsWith("microsoft")) return MICROSOFT;
        if (lower.startsWith("imagination") || lower.equals("img")) return IMG;
        if (lower.startsWith("loongson")) return LOONGSON;
        if (lower.startsWith("moore threads")) return MOORE_THREADS;
        if (lower.startsWith("jingjia")) return JINGJIA_MICRO;
        if (lower.startsWith("huawei") || lower.startsWith("hisilicon")) return HUAWEI;
        if (lower.startsWith("zhaoxin")) return ZHAOXIN;
        if (lower.startsWith("marvell")) return MARVELL;
        if (lower.startsWith("samsung")) return SAMSUNG;
        if (lower.startsWith("ampere")) return AMPERE;
        if (lower.startsWith("rockchip")) return ROCKCHIP;
        if (lower.startsWith("thead") || lower.startsWith("t-head")) return THEAD;
        if (lower.startsWith("starfive")) return STARFIVE;
        if (lower.startsWith("eswin")) return ESWIN;
        if (lower.startsWith("spacemit")) return SPACEMIT;

        return null;
    }

    @Contract("null -> null; !null -> !null")
    public static HardwareVendor of(String name) {
        if (name == null)
            return null;

        HardwareVendor known = getKnown(name);
        return known != null ? known : new HardwareVendor(name);
    }

    public static @Nullable HardwareVendor ofPciVendorId(int vendorId) {
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

    public static @Nullable HardwareVendor ofArmImplementerId(int implementerId) {
        // https://github.com/util-linux/util-linux/blob/0a21358af3e50fcb13a9bf3702779f11a4739667/sys-utils/lscpu-arm.c#L301
        switch (implementerId) {
            case 0x41:
                return ARM;
            case 0x42:
                return BROADCOM;
            case 0x48:
                return HUAWEI;
            case 0x4e:
                return NVIDIA;
            case 0x51:
                return QUALCOMM;
            case 0x53:
                return SAMSUNG;
            case 0x56:
                return MARVELL;
            case 0x61:
                return APPLE;
            case 0x69:
                return INTEL;
            case 0x6D:
                return MICROSOFT;
            case 0xc0:
                return AMPERE;
            default:
                return null;
        }
    }

    private final String name;

    public HardwareVendor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HardwareVendor && name.equals(((HardwareVendor) o).name);
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
