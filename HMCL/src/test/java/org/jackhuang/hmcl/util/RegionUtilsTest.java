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
        assertEquals(List.of("CN"), RegionUtils.loadTimeZoneRegions("Asia/Shanghai"));
        assertEquals(List.of("CN"), RegionUtils.loadTimeZoneRegions("Asia/Urumqi"));
        assertEquals(List.of("CH", "DE", "LI"), RegionUtils.loadTimeZoneRegions("Europe/Zurich"));
        @Unmodifiable List<String> candidates = RegionUtils.loadTimeZoneRegions("Asia/Tokyo");
        assertEquals(List.of("JP", "AU"), candidates);
        assertThrows(UnsupportedOperationException.class, () -> candidates.add("US"));
    }

    /// Does not match missing identifiers, partial names, or region fields.
    @Test
    void matchesCompleteTimeZoneField() throws IOException {
        for (String zoneId : List.of("Etc/UTC", "Asia/Shang", "Shanghai", "CN", "")) {
            assertEquals(List.of(), RegionUtils.loadTimeZoneRegions(zoneId));
        }
    }

    /// Finds entries near both ends of the sorted resource.
    @Test
    void findsFirstAndLastTimeZones() throws IOException {
        assertEquals(List.of("CI", "BF", "GH", "GM", "GN", "IS", "ML", "MR", "SH", "SL", "SN", "TG"),
                RegionUtils.loadTimeZoneRegions("Africa/Abidjan"));
        assertEquals(List.of("TO"), RegionUtils.loadTimeZoneRegions("Pacific/Tongatapu"));
    }
}
