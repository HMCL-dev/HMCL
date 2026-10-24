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

import static org.junit.jupiter.api.Assertions.*;

/// Verifies deterministic conversion of the IANA table and rejection of invalid input.
@NotNullByDefault
final class UpdateTimeZoneRegionsTest {
    /// Groups and sorts records while discarding coordinates and optional comments.
    @Test
    void convertsZoneTable() {
        String csv = UpdateTimeZoneRegions.toCsv("# Header\r\n\r\n"
                + "US\t+340308-1181434\tAmerica/Los_Angeles\tPacific\r\n"
                + "CN\t+4348+08735\tAsia/Urumqi\r\n"
                + "CN\t+3114+12128\tAsia/Shanghai\tBeijing Time\r\n");
        assertTrue(csv.endsWith("CN,Asia/Shanghai,Asia/Urumqi\nUS,America/Los_Angeles\n"));
        assertFalse(csv.contains("\r"));
    }

    /// Rejects missing columns, ambiguous regions, malformed identifiers, and duplicate time zones.
    @ParameterizedTest
    @ValueSource(strings = {
            "# No records\n", "CN\t+3114+12128", "CN,US\t+3114+12128\tAsia/Shanghai",
            "CN\t+3114+12128\t", "CN\t+3114+12128\tAsia/Shanghai,Asia/Urumqi",
            "CN\t+3114+12128\tAsia/Shanghai\nUS\t+3114+12128\tAsia/Shanghai"
    })
    void rejectsInvalidTables(String table) {
        assertThrows(GradleException.class, () -> UpdateTimeZoneRegions.toCsv(table));
    }
}
