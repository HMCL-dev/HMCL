// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.linux;

import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/// SoC identities inferred from observed Linux Arm CPU data when direct names are unavailable.
/// Virtual CPUs can hide or duplicate host cores, so these rules are heuristics, not hardware identifiers.
@NotNullByDefault
enum LinuxArmSoCModel {
    /// Xiaomi XRING O3 physical topology, confirmed from host procfs:
    ///
    /// | CPU IDs | Count | Arm core   | Implementer / part | Variant / revision |
    /// |---------|-------|------------|--------------------|--------------------|
    /// | 0–3     | 4     | C1-Pro     | `0x41` / `0xd8b`   | `1` / `2`          |
    /// | 4–7     | 4     | C1-Premium | `0x41` / `0xd90`   | `1` / `0`          |
    /// | 8–9     | 2     | C1-Ultra   | `0x41` / `0xd8c`   | `1` / `0`          |
    ///
    /// Core names follow [util-linux's Arm part mapping](https://github.com/util-linux/util-linux/blob/master/sys-utils/lscpu-arm.c).
    XIAOMI_XRING_O3("Xiaomi XRING O3", HardwareVendor.XIAOMI) {
        /// Requires Pro at CPU 0–3, Premium at CPU 4–7 and Premium or Ultra at CPU 8–9.
        /// Capacity and frequency are ignored; another SoC exposing the same ordered identities is indistinguishable.
        @Override
        boolean matches(Map<Integer, Map<String, String>> cpuInfo) {
            if (cpuInfo.size() != 10)
                return false;

            for (int id = 0; id < 10; id++) {
                @Nullable Map<String, String> fields = cpuInfo.get(id);
                if (fields == null)
                    return false;

                boolean matches;
                if (id < 4) {
                    matches = matchesArmCore(fields, 0xd8b, 1, 2);
                } else if (id < 8) {
                    matches = matchesArmCore(fields, 0xd90, 1, 0);
                } else {
                    // On the observed Android Terminal guest (6.12.92-android16), paused Ultra cores
                    // allowed vCPU 8–9 to run on Premium cores. Guest procfs retained Premium identities
                    // even after runtime MIDRs switched between Premium and Ultra under load.
                    matches = matchesArmCore(fields, 0xd90, 1, 0)
                            || matchesArmCore(fields, 0xd8c, 1, 0);
                }

                if (!matches)
                    return false;
            }
            return true;
        }
    },

    /// SM8650: two Cortex-A520, five Cortex-A720 and one Cortex-X4, all r0p1.
    /// Confirmed by the [Galaxy S24 Ultra procfs snapshot](https://github.com/ExperimentalMachines/execubench/blob/fffa8ee723ff95c053ed0cd41dcad29288c1af5e/data/devices/probe/2026-10-04-v2/SM-S928U1-android14/artifacts/probe/cpuinfo.txt).
    QUALCOMM_SNAPDRAGON_8_GEN_3("Qualcomm Snapdragon 8 Gen 3", HardwareVendor.QUALCOMM, Map.of(
            new CoreIdentity(0x41, 0xd80, 0, 1), 2,
            new CoreIdentity(0x41, 0xd81, 0, 1), 5,
            new CoreIdentity(0x41, 0xd82, 0, 1), 1)),

    /// SM8735: seven Cortex-A720 and one Cortex-X4, all r0p1.
    /// See the [POCO F7 hardware report](https://specdeviceinfo.com/devices/xiaomi/25053pc47g-112635/).
    QUALCOMM_SNAPDRAGON_8S_GEN_4("Qualcomm Snapdragon 8s Gen 4", HardwareVendor.QUALCOMM, Map.of(
            new CoreIdentity(0x41, 0xd81, 0, 1), 7,
            new CoreIdentity(0x41, 0xd82, 0, 1), 1)),

    /// SM8750: six Oryon r4p4 and two r3p4 cores, all part 1.
    /// Confirmed by the [Galaxy S25 procfs snapshot](https://github.com/ExperimentalMachines/execubench/blob/fffa8ee723ff95c053ed0cd41dcad29288c1af5e/data/devices/probe/2026-10-04-v2/SM-S931U1-android16/artifacts/probe/cpuinfo.txt).
    /// Seven-core models and guests with missing cores are intentionally not inferred.
    QUALCOMM_SNAPDRAGON_8_ELITE("Qualcomm Snapdragon 8 Elite", HardwareVendor.QUALCOMM, Map.of(
            new CoreIdentity(0x51, 0x001, 4, 4), 6,
            new CoreIdentity(0x51, 0x001, 3, 4), 2)),

    /// SM8845: six Oryon r5p0 and two r4p0 cores, all part 2.
    /// See the [OnePlus 15R hardware report](https://specdeviceinfo.com/devices/oneplus/cph2767-121636/)
    /// and its [MIDR identifier](https://browser.geekbench.com/v6/compute/6816477).
    QUALCOMM_SNAPDRAGON_8_GEN_5("Qualcomm Snapdragon 8 Gen 5", HardwareVendor.QUALCOMM, Map.of(
            new CoreIdentity(0x51, 0x002, 5, 0), 6,
            new CoreIdentity(0x51, 0x002, 4, 0), 2)),

    /// SM8850: six Oryon r2p1 and two r3p1 cores, all part 2.
    /// Confirmed by the [Galaxy S26 procfs snapshot](https://github.com/ExperimentalMachines/execubench/blob/fffa8ee723ff95c053ed0cd41dcad29288c1af5e/data/devices/probe/2026-10-04-v2/SM-S942U1-android16/artifacts/probe/cpuinfo.txt).
    QUALCOMM_SNAPDRAGON_8_ELITE_GEN_5("Qualcomm Snapdragon 8 Elite Gen 5", HardwareVendor.QUALCOMM, Map.of(
            new CoreIdentity(0x51, 0x002, 2, 1), 6,
            new CoreIdentity(0x51, 0x002, 3, 1), 2)),

    /// Snapdragon X Elite observed in WSL2: 12 Oryon cores with MIDR `0x511f0011`.
    /// The [Oryon part ID](https://github.com/util-linux/util-linux/blob/master/sys-utils/lscpu-arm.c)
    /// and [12-core configuration](https://www.qualcomm.com/laptops/products/snapdragon-x-elite)
    /// identify the family heuristically; they cannot distinguish retail SKUs or the developer kit.
    QUALCOMM_SNAPDRAGON_X_ELITE("Qualcomm Snapdragon X Elite", HardwareVendor.QUALCOMM) {
        /// Requires all 12 observed Oryon r1p1 cores; reduced CPU counts cannot distinguish X Elite from X Plus.
        @Override
        boolean matches(Map<Integer, Map<String, String>> cpuInfo) {
            return cpuInfo.size() == 12 && cpuInfo.values().stream()
                    .allMatch(fields -> matchesCore(fields, 0x51, 0x001, 1, 1));
        }
    },

    /// X2 Elite: six performance and six prime cores, all Oryon part 2.
    /// Variants follow [Glymur's CPU identities](https://github.com/torvalds/linux/blob/master/arch/arm64/boot/dts/qcom/glymur.dtsi);
    /// counts follow the [X2 Elite product brief](https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Snapdragon-X2-Elite-Product-Brief.pdf).
    /// Revisions are not specified by these sources; any valid revision is accepted.
    QUALCOMM_SNAPDRAGON_X2_ELITE("Qualcomm Snapdragon X2 Elite", HardwareVendor.QUALCOMM, Map.of(
            new CoreIdentity(0x51, 0x002, 2, -1), 6,
            new CoreIdentity(0x51, 0x002, 1, -1), 6)),

    /// The 18-core X2 Elite and Elite Extreme share six performance and twelve prime cores.
    /// Counts and MIDRs cannot distinguish their SKUs; a capped guest may also match the 12-core family.
    QUALCOMM_SNAPDRAGON_X2_ELITE_OR_EXTREME("Qualcomm Snapdragon X2 Elite / X2 Elite Extreme", HardwareVendor.QUALCOMM, Map.of(
            new CoreIdentity(0x51, 0x002, 2, -1), 6,
            new CoreIdentity(0x51, 0x002, 1, -1), 12));

    /// Display name assigned when this model matches.
    private final String displayName;

    /// SoC manufacturer assigned when this model matches.
    private final HardwareVendor vendor;

    /// Required core identities and counts, or an empty map for a model with a custom matching rule.
    private final @Unmodifiable Map<CoreIdentity, Integer> topology;

    /// Associates the model's matching rule with its display identity.
    LinuxArmSoCModel(String displayName, HardwareVendor vendor) {
        this(displayName, vendor, Map.of());
    }

    /// Associates an unordered, complete core signature with its display identity.
    LinuxArmSoCModel(String displayName, HardwareVendor vendor, Map<CoreIdentity, Integer> topology) {
        this.displayName = displayName;
        this.vendor = vendor;
        this.topology = Map.copyOf(topology);
    }

    /// Returns the model's display name.
    String getDisplayName() {
        return displayName;
    }

    /// Returns the SoC manufacturer.
    HardwareVendor getVendor() {
        return vendor;
    }

    /// Resolves exact Qualcomm device-tree SoC codes before using ambiguous CPU-only heuristics.
    /// Unknown codes return `null`; frequency bins and Galaxy editions retain the base family name.
    static @Nullable String qualcommCodeToName(String code) {
        return switch (code) {
            case "sm7675" -> "Qualcomm Snapdragon 7+ Gen 3";
            case "sm8635" -> "Qualcomm Snapdragon 8s Gen 3";
            case "sm8650" -> QUALCOMM_SNAPDRAGON_8_GEN_3.displayName;
            case "sm8735" -> QUALCOMM_SNAPDRAGON_8S_GEN_4.displayName;
            case "sm8750" -> QUALCOMM_SNAPDRAGON_8_ELITE.displayName;
            case "sm8845" -> QUALCOMM_SNAPDRAGON_8_GEN_5.displayName;
            case "sm8850", "kaanapali" -> QUALCOMM_SNAPDRAGON_8_ELITE_GEN_5.displayName;
            default -> null;
        };
    }

    /// Returns a matching heuristic, or `null` for unknown, incomplete or malformed CPU identities.
    static @Nullable LinuxArmSoCModel detect(Map<Integer, Map<String, String>> cpuInfo) {
        for (LinuxArmSoCModel model : values()) {
            try {
                if (model.matches(cpuInfo))
                    return model;
            } catch (NumberFormatException e) {
                // Incomplete evidence for one rule must not prevent checking the remaining rules.
            }
        }
        return null;
    }

    /// Matches procfs identities without I/O or modifying the supplied data.
    /// Throws `NumberFormatException` if a required identity field is absent or malformed.
    boolean matches(Map<Integer, Map<String, String>> cpuInfo) {
        if (topology.isEmpty() || cpuInfo.size() != topology.values().stream().mapToInt(Integer::intValue).sum())
            return false;

        for (var entry : topology.entrySet()) {
            long count = cpuInfo.values().stream().filter(entry.getKey()::matches).count();
            if (count != entry.getValue())
                return false;
        }
        return true;
    }

    /// One disjoint core identity in a complete topology signature.
    ///
    /// @param implementer MIDR implementer
    /// @param part MIDR part number, not the marketed Oryon generation
    /// @param variant MIDR variant, which can distinguish Qualcomm core types
    /// @param revision MIDR revision, or `-1` to accept any valid revision
    @NotNullByDefault
    private record CoreIdentity(int implementer, int part, int variant, int revision) {
        /// Tests one procfs processor against this identity.
        boolean matches(Map<String, String> fields) {
            return matchesCore(fields, implementer, part, variant, revision);
        }
    }

    /// Matches an Arm core's part and revision, rejecting absent or malformed fields.
    private static boolean matchesArmCore(Map<String, String> fields, int part, int variant, int revision) {
        return matchesCore(fields, 0x41, part, variant, revision);
    }

    /// Matches a core identity; revision `-1` accepts any four-bit revision, but never a missing one.
    private static boolean matchesCore(Map<String, String> fields, int implementer, int part, int variant, int revision) {
        if (parseCPUField(fields.get("CPU implementer")) != implementer
                || parseCPUField(fields.get("CPU part")) != part
                || parseCPUField(fields.get("CPU variant")) != variant)
            return false;
        int actualRevision = parseCPUField(fields.get("CPU revision"));
        return revision < 0 ? actualRevision <= 15 : actualRevision == revision;
    }

    /// Parses decimal or hexadecimal procfs identity fields, rejecting missing or negative values.
    private static int parseCPUField(@Nullable String value) {
        if (value == null)
            throw new NumberFormatException("Missing CPU identity field");
        value = value.trim();
        int result = value.regionMatches(true, 0, "0x", 0, 2)
                ? Integer.parseInt(value.substring(2), 16) : Integer.parseInt(value);
        if (result < 0)
            throw new NumberFormatException("Negative CPU identity field");
        return result;
    }
}
