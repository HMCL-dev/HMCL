// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/// Identifies a hardware vendor by its display name.
///
/// @param name the vendor display name
@NotNullByDefault
public record HardwareVendor(String name) {

    /// The Intel vendor.
    public static final HardwareVendor INTEL = new HardwareVendor("Intel");

    /// The NVIDIA vendor.
    public static final HardwareVendor NVIDIA = new HardwareVendor("NVIDIA");

    /// The AMD vendor.
    public static final HardwareVendor AMD = new HardwareVendor("AMD");

    /// The Apple vendor.
    public static final HardwareVendor APPLE = new HardwareVendor("Apple");

    /// The ARM vendor.
    public static final HardwareVendor ARM = new HardwareVendor("ARM");

    /// The Qualcomm vendor.
    public static final HardwareVendor QUALCOMM = new HardwareVendor("Qualcomm");

    /// The MTK vendor.
    public static final HardwareVendor MTK = new HardwareVendor("MTK");

    /// The VMware vendor.
    public static final HardwareVendor VMWARE = new HardwareVendor("VMware");

    /// The Parallel vendor.
    public static final HardwareVendor PARALLEL = new HardwareVendor("Parallel");

    /// The Microsoft vendor.
    public static final HardwareVendor MICROSOFT = new HardwareVendor("Microsoft");

    /// The Moore Threads vendor.
    public static final HardwareVendor MOORE_THREADS = new HardwareVendor("Moore Threads");

    /// The Broadcom vendor.
    public static final HardwareVendor BROADCOM = new HardwareVendor("Broadcom");

    /// The Imagination vendor.
    public static final HardwareVendor IMG = new HardwareVendor("Imagination");

    /// The Loongson vendor.
    public static final HardwareVendor LOONGSON = new HardwareVendor("Loongson");

    /// The Jingjia Micro vendor.
    public static final HardwareVendor JINGJIA_MICRO = new HardwareVendor("Jingjia Micro");

    /// The Huawei vendor.
    public static final HardwareVendor HUAWEI = new HardwareVendor("Huawei");

    /// The Zhaoxin vendor.
    public static final HardwareVendor ZHAOXIN = new HardwareVendor("Zhaoxin");

    /// The Samsung vendor.
    public static final HardwareVendor SAMSUNG = new HardwareVendor("Samsung");

    /// The Marvell vendor.
    public static final HardwareVendor MARVELL = new HardwareVendor("Marvell");

    /// The Ampere vendor.
    public static final HardwareVendor AMPERE = new HardwareVendor("Ampere");

    /// The Rockchip vendor.
    public static final HardwareVendor ROCKCHIP = new HardwareVendor("Rockchip");

    // RISC-V

    /// The T-Head vendor.
    public static final HardwareVendor THEAD = new HardwareVendor("T-Head");

    /// The StarFive vendor.
    public static final HardwareVendor STARFIVE = new HardwareVendor("StarFive");

    /// The ESWIN vendor.
    public static final HardwareVendor ESWIN = new HardwareVendor("ESWIN");

    /// The SpacemiT vendor.
    public static final HardwareVendor SPACEMIT = new HardwareVendor("SpacemiT");

    /// Returns a recognized vendor using case-insensitive name matching, or `null` for an unknown or `null` name.
    public static @Nullable HardwareVendor getKnown(@Nullable String name) {
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

    /// Returns a recognized vendor or preserves the supplied name in a new vendor; returns `null` for `null`.
    @Contract("null -> null; !null -> !null")
    public static @Nullable HardwareVendor of(@Nullable String name) {
        if (name == null)
            return null;

        @Nullable HardwareVendor known = getKnown(name);
        return known != null ? known : new HardwareVendor(name);
    }

    /// Returns the vendor mapped to a PCI vendor ID, or `null` if unmapped.
    public static @Nullable HardwareVendor ofPciVendorId(int vendorId) {
        // https://devicehunt.com/all-pci-vendors
        return switch (vendorId) {
            case 0x106b -> APPLE; // AMD Pensando Systems
            case 0x1002, 0x1022, 0x1dd8, 0x1924 -> AMD; // AMD Solarflare
            case 0x8086, 0x8087, 0x03e7 -> INTEL;
            case 0x0955, 0x10de, 0x12d2 -> NVIDIA;
            case 0x1ed5 -> MOORE_THREADS;
            case 0x168c, 0x5143 -> QUALCOMM;
            case 0x14c3 -> MTK;
            case 0x15ad -> VMWARE;
            case 0x1ab8 -> PARALLEL;
            case 0x1414 -> MICROSOFT;
            case 0x182f, 0x14e4 -> BROADCOM;
            case 0x0014 -> LOONGSON;
            case 0x0731 -> JINGJIA_MICRO;
            case 0x19e5 -> HUAWEI;
            case 0x1d17 -> ZHAOXIN;
            default -> null;
        };
    }

    /// Returns the vendor mapped to an ARM implementer ID, or `null` if unmapped.
    public static @Nullable HardwareVendor ofArmImplementerId(int implementerId) {
        // https://github.com/util-linux/util-linux/blob/0a21358af3e50fcb13a9bf3702779f11a4739667/sys-utils/lscpu-arm.c#L301
        return switch (implementerId) {
            case 0x41 -> ARM;
            case 0x42 -> BROADCOM;
            case 0x48 -> HUAWEI;
            case 0x4e -> NVIDIA;
            case 0x51 -> QUALCOMM;
            case 0x53 -> SAMSUNG;
            case 0x56 -> MARVELL;
            case 0x61 -> APPLE;
            case 0x69 -> INTEL;
            case 0x6D -> MICROSOFT;
            case 0xc0 -> AMPERE;
            default -> null;
        };
    }

    /// Returns the vendor display name.
    @Override
    public String toString() {
        return name;
    }
}
