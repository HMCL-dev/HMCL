// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies exact time zone lookup and preservation of candidate regions.
@NotNullByDefault
final class RegionUtilsTest {
    /// Reads candidates in source order and exposes an unmodifiable list.
    @Test
    void loadsUnmodifiableCandidateMapping() throws IOException {
        assertEquals(List.of("CN"), RegionUtils.findRegionsByTimeZone("Asia/Shanghai"));
        @Unmodifiable List<String> candidates = RegionUtils.findRegionsByTimeZone("Asia/Tokyo");
        assertEquals(List.of("JP", "AU"), candidates);
        assertThrows(UnsupportedOperationException.class, () -> candidates.add("US"));
    }

    /// Does not match missing identifiers, partial names, or region fields.
    @Test
    void matchesCompleteTimeZoneField() throws IOException {
        for (String zoneId : List.of("Etc/UTC", "Asia/Shang", "CN", "")) {
            assertEquals(List.of(), RegionUtils.findRegionsByTimeZone(zoneId));
        }
    }

    /// Finds system time zone aliases without dropping the target's candidate countries.
    @Test
    void loadsCompatibilityAliases() throws IOException {
        assertEquals(List.of("BE", "LU", "NL"), RegionUtils.findRegionsByTimeZone("Europe/Amsterdam"));
        assertEquals(List.of("DE", "DK", "NO", "SE", "SJ"), RegionUtils.findRegionsByTimeZone("Europe/Stockholm"));
    }
}
