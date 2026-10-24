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

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

/// Updates the checked-in time zone mapping from IANA's public-domain `zone.tab`.
@NotNullByDefault
@UntrackedTask(because = "The current IANA time zone table is a remote input")
public abstract class UpdateTimeZoneRegions extends DefaultTask {
    /// The IANA table containing one country code per time zone identifier.
    private static final String SOURCE_URL = "https://data.iana.org/time-zones/tzdb/zone.tab";

    /// Returns the CSV destination, with each row containing a region followed by its time zone identifiers.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Downloads and validates the table before replacing the CSV file.
    ///
    /// @throws IOException if downloading or writing fails
    /// @throws GradleException if the table is empty or malformed
    @TaskAction
    public void run() throws IOException {
        var connection = URI.create(SOURCE_URL).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        String csv;
        try (var input = connection.getInputStream()) {
            csv = toCsv(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        Files.writeString(getOutputFile().get().getAsFile().toPath(), csv);
    }

    /// Converts tab-separated records to CSV sorted by region and time zone, ignoring comments and blank lines.
    ///
    /// @throws GradleException if a record is malformed, a time zone is duplicated, or no records are present
    static String toCsv(String table) {
        var regions = new TreeMap<String, TreeSet<String>>();
        var timeZones = new HashSet<String>();
        table.lines().forEach(line -> {
            if (line.isBlank() || line.startsWith("#")) {
                return;
            }
            String[] fields = line.split("\t", 4);
            if (fields.length < 3 || !fields[0].matches("[A-Z]{2}")
                    || !fields[2].matches("[A-Za-z0-9_+/-]+")) {
                throw new GradleException("Invalid zone.tab record: " + line);
            }
            if (!timeZones.add(fields[2])) {
                throw new GradleException("Duplicate time zone: " + fields[2]);
            }
            regions.computeIfAbsent(fields[0], ignored -> new TreeSet<>()).add(fields[2]);
        });
        if (regions.isEmpty()) {
            throw new GradleException("No time zones found in zone.tab");
        }

        var csv = new StringBuilder("# Generated from " + SOURCE_URL + " (public domain).\n"
                + "# Update with ./gradlew updateTimeZoneRegions.\n");
        regions.forEach((region, zones) -> csv.append(region).append(',')
                .append(String.join(",", zones)).append('\n'));
        return csv.toString();
    }
}
