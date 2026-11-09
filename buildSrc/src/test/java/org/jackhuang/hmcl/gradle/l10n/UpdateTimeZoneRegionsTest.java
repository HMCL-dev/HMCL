// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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

    /// Records the source version and sorts rows while preserving candidate order.
    @Test
    void convertsZoneTable() {
        String csv = createTask("2026e").toCsv("# Header\r\n\r\n"
                + "US\t+340308-1181434\tAmerica/Los_Angeles\tPacific\r\n"
                + "CH,DE,LI\t+4723+00832\tEurope/Zurich\r\n"
                + "CN\t+3114+12128\tAsia/Shanghai\tBeijing Time\r\n", "");
        assertEquals("# IANA tzdb version: 2026e\n"
                + "# Generated from https://data.iana.org/time-zones/tzdb-2026e/zone1970.tab (public domain).\n"
                + "# Compatibility links from https://data.iana.org/time-zones/tzdb-2026e/backward (public domain).\n"
                + "America/Los_Angeles,US\nAsia/Shanghai,CN\nEurope/Zurich,CH,DE,LI\n", csv);
    }

    /// Rejects empty input, malformed records, and duplicate time zones.
    @ParameterizedTest
    @ValueSource(strings = {
            "# No records\n", "CN\t+3114+12128",
            "CN\t+3114+12128\tAsia/Shanghai\nUS\t+3114+12128\tAsia/Shanghai"
    })
    void rejectsInvalidTables(String table) {
        assertThrows(GradleException.class, () -> createTask("2026e").toCsv(table, ""));
    }

    /// Expands direct and chained links regardless of their order, retaining every candidate.
    @Test
    void expandsCompatibilityLinks() {
        String csv = createTask("2026e").toCsv("BE,LU,NL\t+5050+00420\tEurope/Brussels\n",
                "# Header\r\n\r\n  Link Europe/Amsterdam Europe/Luxembourg # trailing comment\r\n"
                        + "Link\tEurope/Brussels\tEurope/Amsterdam\r\n");
        String records = csv.lines().filter(line -> !line.startsWith("#")).collect(Collectors.joining("\n"));
        assertEquals("Europe/Amsterdam,BE,LU,NL\nEurope/Brussels,BE,LU,NL\nEurope/Luxembourg,BE,LU,NL", records);
        assertTrue(csv.contains("# Compatibility links from https://data.iana.org/time-zones/tzdb-2026e/backward"));
    }

    /// Retains names absent from the executing JDK, including link chains.
    @Test
    void retainsNamesOutsideBundledTzdb() {
        String csv = createTask("2026e").toCsv("CN\t+3114+12128\tUnsupported/Target\n",
                "Link Unsupported/Target Unsupported/Intermediate\n"
                        + "Link Unsupported/Intermediate Asia/Shanghai\n"
                        + "Link Asia/Shanghai PRC\n"
                        + "Link Asia/Shanghai Unsupported/Alias\n");
        String records = csv.lines().filter(line -> !line.startsWith("#")).collect(Collectors.joining("\n"));
        assertEquals("Asia/Shanghai,CN\nPRC,CN\nUnsupported/Alias,CN\nUnsupported/Intermediate,CN\nUnsupported/Target,CN", records);
    }

    /// Applies OpenJDK's exclusions to both table entries and compatibility links.
    @ParameterizedTest
    @ValueSource(strings = {"EST", "HST", "MST", "GMT+0", "GMT-0", "ROC"})
    void excludesOpenJdkCompatibilityNames(String name) {
        var task = createTask("2026e");
        String table = "CN\t+3114+12128\tAsia/Shanghai\n";
        String expected = task.toCsv(table, "");
        assertEquals(expected, task.toCsv(table + "US\t+0000+00000\t" + name + "\n", ""));
        assertEquals(expected, task.toCsv(table, "Link Asia/Shanghai " + name + "\n"));
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

    /// Rejects malformed links, duplicate aliases, and cycles.
    @ParameterizedTest
    @ValueSource(strings = {
            "Link Asia/Shanghai",
            "Link Asia/Shanghai Alias/One\nLink Asia/Shanghai Alias/One",
            "Link Alias/Two Alias/One\nLink Alias/One Alias/Two"
    })
    void rejectsInvalidLinks(String backward) {
        assertThrows(GradleException.class,
                () -> createTask("2026e").toCsv("CN\t+3114+12128\tAsia/Shanghai\n", backward));
    }

    /// Rejects floating versions and values that do not identify a versioned release directory.
    @ParameterizedTest
    @ValueSource(strings = {"latest", "2026e/../tzdb"})
    void rejectsInvalidVersions(String version) {
        assertThrows(GradleException.class,
                () -> createTask(version).toCsv("CN\t+3114+12128\tAsia/Shanghai\n", ""));
    }
}
