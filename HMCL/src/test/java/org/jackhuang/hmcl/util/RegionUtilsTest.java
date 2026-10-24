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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies candidate preservation and immutability of the bundled time zone mapping.
@NotNullByDefault
final class RegionUtilsTest {
    /// Reads all candidates in source order from the generated resource and exposes no mutable collections.
    @Test
    void loadsUnmodifiableCandidateMapping() throws IOException {
        @Unmodifiable Map<String, @Unmodifiable List<String>> regions = RegionUtils.loadTimeZoneRegions();
        assertEquals(List.of("CN"), regions.get("Asia/Shanghai"));
        assertEquals(List.of("CN"), regions.get("Asia/Urumqi"));
        assertEquals(List.of("CH", "DE", "LI"), regions.get("Europe/Zurich"));
        assertEquals(List.of("JP", "AU"), regions.get("Asia/Tokyo"));
        assertFalse(regions.containsKey("Etc/UTC"));
        assertThrows(UnsupportedOperationException.class, () -> regions.put("Etc/UTC", List.of("US")));
        for (@Unmodifiable List<String> candidates : regions.values()) {
            assertFalse(candidates.isEmpty());
            assertTrue(candidates.stream().allMatch(region -> region.matches("[A-Z]{2}")));
            assertEquals(candidates.size(), candidates.stream().distinct().count());
            assertThrows(UnsupportedOperationException.class, () -> candidates.add("US"));
        }
    }
}
