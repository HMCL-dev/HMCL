/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
        assertEquals(List.of("CN"), RegionUtils.findRegionsByTimeZone("Asia/Urumqi"));
        assertEquals(List.of("CH", "DE", "LI"), RegionUtils.findRegionsByTimeZone("Europe/Zurich"));
        @Unmodifiable List<String> candidates = RegionUtils.findRegionsByTimeZone("Asia/Tokyo");
        assertEquals(List.of("JP", "AU"), candidates);
        assertThrows(UnsupportedOperationException.class, () -> candidates.add("US"));
    }

    /// Does not match missing identifiers, partial names, or region fields.
    @Test
    void matchesCompleteTimeZoneField() throws IOException {
        for (String zoneId : List.of("Etc/UTC", "UTC", "Universal", "Asia/Shang", "Shanghai", "CN", "")) {
            assertEquals(List.of(), RegionUtils.findRegionsByTimeZone(zoneId));
        }
    }

    /// Finds system time zone aliases without dropping the target's candidate countries.
    @Test
    void loadsCompatibilityAliases() throws IOException {
        assertEquals(List.of("BE", "LU", "NL"), RegionUtils.findRegionsByTimeZone("Europe/Amsterdam"));
        assertEquals(List.of("DE", "DK", "NO", "SE", "SJ"), RegionUtils.findRegionsByTimeZone("Europe/Stockholm"));
        assertEquals(List.of("DE", "DK", "NO", "SE", "SJ"), RegionUtils.findRegionsByTimeZone("Europe/Oslo"));
        assertEquals(List.of("CN"), RegionUtils.findRegionsByTimeZone("PRC"));
        assertEquals(List.of("US"), RegionUtils.findRegionsByTimeZone("US/Eastern"));
    }

    /// Finds entries near both ends of the sorted resource.
    @Test
    void findsFirstAndLastTimeZones() throws IOException {
        assertEquals(List.of("CI", "BF", "GH", "GM", "GN", "IS", "ML", "MR", "SH", "SL", "SN", "TG"),
                RegionUtils.findRegionsByTimeZone("Africa/Abidjan"));
        assertEquals(List.of("TO"), RegionUtils.findRegionsByTimeZone("Pacific/Tongatapu"));
    }
}
