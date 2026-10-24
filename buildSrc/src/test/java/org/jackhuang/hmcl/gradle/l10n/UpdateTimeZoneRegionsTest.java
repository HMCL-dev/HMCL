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
import org.gradle.testfixtures.ProjectBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies deterministic conversion of the IANA table and rejection of invalid input.
@NotNullByDefault
final class UpdateTimeZoneRegionsTest {
    /// Creates a mapping task configured for the given release without downloading data.
    private static UpdateTimeZoneRegions createTask(String version) {
        var task = ProjectBuilder.builder().build().getTasks()
                .register("updateTimeZoneRegions", UpdateTimeZoneRegions.class).get();
        task.getTzdbVersion().set(version);
        return task;
    }

    /// Sorts records by time zone while discarding coordinates and optional comments.
    @Test
    void convertsZoneTable() {
        String csv = createTask("2026e").toCsv("# Header\r\n\r\n"
                + "US\t+340308-1181434\tAmerica/Los_Angeles\tPacific\r\n"
                + "CN\t+4348+08735\tAsia/Urumqi\r\n"
                + "CN\t+3114+12128\tAsia/Shanghai\tBeijing Time\r\n", "");
        assertTrue(csv.endsWith("America/Los_Angeles,US\nAsia/Shanghai,CN\nAsia/Urumqi,CN\n"));
        assertTrue(csv.startsWith("# IANA tzdb version: 2026e\n"
                + "# Generated from https://data.iana.org/time-zones/tzdb-2026e/zone1970.tab (public domain).\n"));
        assertFalse(csv.contains("\r"));
    }

    /// Retains every country for shared time zones in IANA's original order.
    @Test
    void convertsSharedTimeZones() {
        String csv = createTask("2026e").toCsv("JP,AU\t+353916+1394441\tAsia/Tokyo\tEyre Bird Observatory\n"
                + "CH,DE,LI\t+4723+00832\tEurope/Zurich\tBusingen\n"
                + "AU\t-3352+15113\tAustralia/Sydney\n", "");
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
        assertThrows(GradleException.class, () -> createTask("2026e").toCsv(table, ""));
    }

    /// Expands direct and chained links regardless of their order, retaining every candidate.
    @Test
    void expandsCompatibilityLinks() {
        String csv = createTask("2026e").toCsv("BE,LU,NL\t+5050+00420\tEurope/Brussels\n",
                "# Header\r\n\r\n  Link Europe/Amsterdam Alias/Chained # trailing comment\r\n"
                        + "Link\tEurope/Brussels\tEurope/Amsterdam\r\n");
        String records = csv.lines().filter(line -> !line.startsWith("#")).collect(Collectors.joining("\n"));
        assertEquals("Alias/Chained,BE,LU,NL\nEurope/Amsterdam,BE,LU,NL\nEurope/Brussels,BE,LU,NL", records);
        assertTrue(csv.contains("# Compatibility links from https://data.iana.org/time-zones/tzdb-2026e/backward"));
    }

    /// Leaves UTC and other unmapped targets without a country, including through chains.
    @Test
    void omitsLinksWithoutCountryMappings() {
        String csv = createTask("2026e").toCsv("CN\t+3114+12128\tAsia/Shanghai\n",
                "Zone Etc/UTC 0 - UTC\nLink Etc/UTC UTC\nLink UTC Universal\nLink Missing/Zone Alias/Missing\n");
        String records = csv.lines().filter(line -> !line.startsWith("#")).collect(Collectors.joining("\n"));
        assertEquals("Asia/Shanghai,CN", records);
    }

    /// Keeps an explicit table entry instead of replacing it with a link target's candidates.
    @Test
    void preservesExplicitTableEntries() {
        String csv = createTask("2026e").toCsv("BE,LU,NL\t+5050+00420\tEurope/Brussels\n"
                + "NL\t+5222+00454\tEurope/Amsterdam\n", "Link Europe/Brussels Europe/Amsterdam\n");
        assertTrue(csv.contains("\nEurope/Amsterdam,NL\n"));
    }

    /// Rejects malformed links, duplicate aliases, and direct or indirect cycles.
    @ParameterizedTest
    @ValueSource(strings = {
            "Link Asia/Shanghai", "Link Asia/Shanghai Alias/One extra",
            "Link Asia/Shanghai Alias,One", "Link Asia,Shanghai Alias/One",
            "Link Asia/Shanghai Alias/One\nLink Asia/Shanghai Alias/One",
            "Link Alias/One Alias/One",
            "Link Alias/Two Alias/One\nLink Alias/One Alias/Two"
    })
    void rejectsInvalidLinks(String backward) {
        assertThrows(GradleException.class,
                () -> createTask("2026e").toCsv("CN\t+3114+12128\tAsia/Shanghai\n", backward));
    }

    /// Records the selected release consistently in both source URLs and the reproduction command.
    @ParameterizedTest
    @ValueSource(strings = {"2024b", "2025b", "2026e"})
    void recordsVersion(String version) {
        String csv = createTask(version).toCsv("CN\t+3114+12128\tAsia/Shanghai\n", "");
        assertEquals("# IANA tzdb version: " + version + "\n"
                + "# Generated from https://data.iana.org/time-zones/tzdb-" + version + "/zone1970.tab (public domain).\n"
                + "# Compatibility links from https://data.iana.org/time-zones/tzdb-" + version + "/backward (public domain).\n"
                + "# Update with ./gradlew updateTimeZoneRegions --tzdb-version " + version + ".\n"
                + "Asia/Shanghai,CN\n", csv);
    }

    /// Rejects floating versions and values that do not identify a versioned release directory.
    @ParameterizedTest
    @ValueSource(strings = {"", "latest", "2026", "2026E", "2026e/../tzdb", "2026e\n"})
    void rejectsInvalidVersions(String version) {
        assertThrows(GradleException.class,
                () -> createTask(version).toCsv("CN\t+3114+12128\tAsia/Shanghai\n", ""));
    }
}
