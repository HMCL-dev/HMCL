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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

/// Updates the checked-in time zone mapping from IANA's public-domain `zone1970.tab` and `backward` links.
@NotNullByDefault
@UntrackedTask(because = "The current IANA time zone table is a remote input")
public abstract class UpdateTimeZoneRegions extends DefaultTask {
    /// The IANA table listing countries that share each time zone's civil timestamps since 1970.
    private static final String SOURCE_URL = "https://data.iana.org/time-zones/tzdb/zone1970.tab";

    /// The IANA compatibility links from alternate time zone names to their targets.
    private static final String BACKWARD_URL = "https://data.iana.org/time-zones/tzdb/backward";

    /// Returns the CSV destination, with each row containing a time zone followed by its candidate region codes.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Downloads the table and compatibility links before replacing the CSV file.
    ///
    /// @throws IOException if downloading or writing fails
    /// @throws GradleException if the table is empty, an input record is malformed or duplicated, or links contain a cycle
    @TaskAction
    public void run() throws IOException {
        String csv = toCsv(readSource(SOURCE_URL), readSource(BACKWARD_URL));
        Files.writeString(getOutputFile().get().getAsFile().toPath(), csv);
    }

    /// Downloads a UTF-8 source using bounded connection and read timeouts.
    ///
    /// @throws IOException if the source cannot be read
    private static String readSource(String url) throws IOException {
        var connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        try (var input = connection.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /// Converts tab-separated records to CSV sorted by time zone, ignoring comments and blank lines.
    /// Preserves all country codes in IANA's original order for each time zone.
    /// Resolves compatibility link chains to the same candidate lists, preserving explicit table entries.
    /// Omits links whose final targets have no country mapping, such as `Etc/UTC`.
    /// Non-link directives and comments in the compatibility file are ignored.
    ///
    /// @throws GradleException if a record is malformed or duplicated, links contain a cycle, or the table has no records
    static String toCsv(String table, String backward) {
        var regions = new TreeMap<String, String>();
        table.lines().forEach(line -> {
            if (line.isBlank() || line.startsWith("#")) {
                return;
            }
            String[] fields = line.split("\t", 4);
            if (fields.length < 3 || !fields[0].matches("[A-Z]{2}(,[A-Z]{2})*")
                    || !fields[2].matches("[A-Za-z0-9_+/-]+")) {
                throw new GradleException("Invalid zone1970.tab record: " + line);
            }
            if (regions.putIfAbsent(fields[2], fields[0]) != null) {
                throw new GradleException("Duplicate time zone: " + fields[2]);
            }
        });
        if (regions.isEmpty()) {
            throw new GradleException("No time zones found in zone1970.tab");
        }

        var links = new HashMap<String, String>();
        backward.lines().forEach(line -> {
            int comment = line.indexOf('#');
            String record = (comment >= 0 ? line.substring(0, comment) : line).strip();
            if (record.isEmpty())
                return;
            String[] fields = record.split("\\s+");
            if (!fields[0].equals("Link"))
                return;
            if (fields.length != 3 || !fields[1].matches("[A-Za-z0-9_+/-]+")
                    || !fields[2].matches("[A-Za-z0-9_+/-]+")) {
                throw new GradleException("Invalid backward link: " + line);
            }
            if (links.putIfAbsent(fields[2], fields[1]) != null) {
                throw new GradleException("Duplicate time zone link: " + fields[2]);
            }
        });
        for (String alias : links.keySet()) {
            var visited = new HashSet<String>();
            String target = alias;
            while (true) {
                if (!visited.add(target)) {
                    throw new GradleException("Cyclic time zone link: " + alias);
                }
                @Nullable String candidates = regions.get(target);
                if (candidates != null) {
                    regions.put(alias, candidates);
                    break;
                }
                @Nullable String next = links.get(target);
                if (next == null)
                    break;
                target = next;
            }
        }

        var csv = new StringBuilder("# Generated from " + SOURCE_URL + " (public domain).\n"
                + "# Compatibility links from " + BACKWARD_URL + " (public domain).\n"
                + "# Update with ./gradlew updateTimeZoneRegions.\n");
        regions.forEach((zone, candidates) -> csv.append(zone).append(',').append(candidates).append('\n'));
        return csv.toString();
    }
}
