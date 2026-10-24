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
package org.jackhuang.hmcl.gradle.l10n;

import org.gradle.api.GradleException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies deterministic conversion of the IANA table and rejection of invalid input.
@NotNullByDefault
final class UpdateTimeZoneRegionsTest {
    /// Sorts records by time zone while discarding coordinates and optional comments.
    @Test
    void convertsZoneTable() {
        String csv = UpdateTimeZoneRegions.toCsv("# Header\r\n\r\n"
                + "US\t+340308-1181434\tAmerica/Los_Angeles\tPacific\r\n"
                + "CN\t+4348+08735\tAsia/Urumqi\r\n"
                + "CN\t+3114+12128\tAsia/Shanghai\tBeijing Time\r\n");
        assertTrue(csv.endsWith("America/Los_Angeles,US\nAsia/Shanghai,CN\nAsia/Urumqi,CN\n"));
        assertTrue(csv.startsWith("# Generated from https://data.iana.org/time-zones/tzdb/zone1970.tab (public domain).\n"));
        assertFalse(csv.contains("\r"));
    }

    /// Retains every country for shared time zones in IANA's original order.
    @Test
    void convertsSharedTimeZones() {
        String csv = UpdateTimeZoneRegions.toCsv("JP,AU\t+353916+1394441\tAsia/Tokyo\tEyre Bird Observatory\n"
                + "CH,DE,LI\t+4723+00832\tEurope/Zurich\tBusingen\n"
                + "AU\t-3352+15113\tAustralia/Sydney\n");
        String records = csv.lines().filter(line -> !line.startsWith("#")).collect(Collectors.joining("\n"));
        assertEquals("Asia/Tokyo,JP,AU\nAustralia/Sydney,AU\nEurope/Zurich,CH,DE,LI", records);
    }

    /// Rejects missing columns, malformed country lists or identifiers, and duplicate time zones.
    @ParameterizedTest
    @ValueSource(strings = {
            "# No records\n", "CN\t+3114+12128", "CN,\t+3114+12128\tAsia/Shanghai",
            ",CN\t+3114+12128\tAsia/Shanghai", "CN,,US\t+3114+12128\tAsia/Shanghai",
            "CN,USA\t+3114+12128\tAsia/Shanghai", "CN,us\t+3114+12128\tAsia/Shanghai",
            "CN\t+3114+12128\t", "CN\t+3114+12128\tAsia/Shanghai,Asia/Urumqi",
            "CN\t+3114+12128\tAsia/Shanghai\nUS\t+3114+12128\tAsia/Shanghai",
            "CH,DE,LI\t+4723+00832\tEurope/Zurich\nDE\t+4723+00832\tEurope/Zurich"
    })
    void rejectsInvalidTables(String table) {
        assertThrows(GradleException.class, () -> UpdateTimeZoneRegions.toCsv(table));
    }
}
