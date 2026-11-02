// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.linux;

import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/// SoC identities inferred from collected AVF guest CPU data.
@NotNullByDefault
enum AVFSoCModel {
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
    };

    /// Display name assigned when this model matches.
    private final String displayName;

    /// SoC manufacturer assigned when this model matches.
    private final HardwareVendor vendor;

    /// Associates the model's matching rule with its display identity.
    AVFSoCModel(String displayName, HardwareVendor vendor) {
        this.displayName = displayName;
        this.vendor = vendor;
    }

    /// Returns the model's display name.
    String getDisplayName() {
        return displayName;
    }

    /// Returns the SoC manufacturer.
    HardwareVendor getVendor() {
        return vendor;
    }

    /// Matches procfs identities by CPU ID without I/O or modifying the supplied data.
    abstract boolean matches(Map<Integer, Map<String, String>> cpuInfo);

    /// Matches an Arm core's part and revision, rejecting absent or malformed fields.
    private static boolean matchesArmCore(Map<String, String> fields, int part, int variant, int revision) {
        return parseCPUField(fields.get("CPU implementer")) == 0x41
                && parseCPUField(fields.get("CPU part")) == part
                && parseCPUField(fields.get("CPU variant")) == variant
                && parseCPUField(fields.get("CPU revision")) == revision;
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
