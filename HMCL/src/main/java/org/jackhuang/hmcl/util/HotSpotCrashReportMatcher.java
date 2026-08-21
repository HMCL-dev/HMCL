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

import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.Metadata.CURRENT_DIRECTORY;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Matches HotSpot error reports to HMCL launcher logs.
@NotNullByDefault
public final class HotSpotCrashReportMatcher {
    /// Matches HotSpot error report file names and captures their process IDs.
    private static final Pattern ERROR_FILE_PATTERN = Pattern.compile("hs_err_pid(?<pid>\\d+)\\.log");

    /// Matches uncompressed HMCL log file names and captures their start times.
    private static final Pattern LOG_FILE_PATTERN = Pattern.compile("(?<time>\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2})(?:\\.\\d+)?\\.log");

    /// Matches the process ID recorded in a HotSpot error report.
    private static final Pattern ERROR_PID_PATTERN = Pattern.compile("^#.*\\bpid=(?<pid>\\d+),.*$");

    /// Matches the report time and elapsed process time recorded by HotSpot.
    private static final Pattern ERROR_TIME_PATTERN = Pattern.compile(
            "^Time: \\w{3} (?<month>\\w{3})\\s+(?<day>\\d{1,2}) (?<clock>\\d{2}:\\d{2}:\\d{2}) (?<year>\\d{4})(?: .+)? elapsed time: (?<elapsed>\\d+(?:\\.\\d+)?) seconds.*$");

    /// Parses timestamps used in HMCL log file names.
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    /// Parses timestamps used in HotSpot error reports.
    private static final DateTimeFormatter ERROR_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d HH:mm:ss yyyy", Locale.ENGLISH);

    /// Allows for the delay between JVM startup and logger initialization.
    private static final Duration START_TIME_TOLERANCE = Duration.ofSeconds(5);

    /// Prevents instantiation of this utility class.
    private HotSpotCrashReportMatcher() {
    }

    /// Finds reports in the current directory that match one of the supplied HMCL logs.
    public static @Unmodifiable List<Path> findMatchingReports(Iterable<Path> logFiles) {
        List<Pair<LocalDateTime, String>> sessions = new ArrayList<>();
        for (Path logFile : logFiles) {
            try {
                @Nullable Pair<LocalDateTime, String> session = readLauncherSession(logFile);
                if (session != null)
                    sessions.add(session);
            } catch (IOException e) {
                LOG.warning("Failed to read HMCL log " + logFile, e);
            }
        }
        if (sessions.isEmpty())
            return List.of();

        List<Path> reports = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CURRENT_DIRECTORY, "hs_err_pid*.log")) {
            for (Path report : stream) {
                if (!Files.isRegularFile(report) || !ERROR_FILE_PATTERN.matcher(report.getFileName().toString()).matches())
                    continue;

                try {
                    if (matches(report, sessions))
                        reports.add(report);
                } catch (IOException e) {
                    LOG.warning("Failed to read HotSpot error report " + report, e);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to find HotSpot error reports in " + CURRENT_DIRECTORY, e);
        }
        return List.copyOf(reports);
    }

    /// Reads the start time and launcher name from an uncompressed HMCL log.
    private static @Nullable Pair<LocalDateTime, String> readLauncherSession(Path logFile) throws IOException {
        @Nullable LocalDateTime logStartTime = parseLogStartTime(logFile);
        if (logStartTime == null)
            return null;

        boolean hmclLog = false;
        boolean sameDirectory = false;
        @Nullable String launcherName = null;

        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            @Nullable String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("*** HMCL ")) {
                    hmclLog = true;
                } else if (line.contains("Current Directory: ")) {
                    String value = line.substring(line.indexOf("Current Directory: ") + "Current Directory: ".length());
                    try {
                        sameDirectory = CURRENT_DIRECTORY.equals(Path.of(value).toAbsolutePath().normalize());
                    } catch (InvalidPathException ignored) {
                        return null;
                    }
                } else if (line.contains("HMCL Jar Path: ")) {
                    String value = line.substring(line.indexOf("HMCL Jar Path: ") + "HMCL Jar Path: ".length());
                    try {
                        @Nullable Path fileName = Path.of(value).getFileName();
                        launcherName = fileName == null ? null : fileName.toString();
                    } catch (InvalidPathException ignored) {
                        return null;
                    }
                }

                if (hmclLog && sameDirectory && launcherName != null)
                    return new Pair<>(logStartTime, launcherName);
            }
        }
        return null;
    }

    /// Parses the start time from an uncompressed HMCL log file name.
    private static @Nullable LocalDateTime parseLogStartTime(Path logFile) {
        Matcher matcher = LOG_FILE_PATTERN.matcher(logFile.getFileName().toString());
        if (!matcher.matches())
            return null;

        try {
            return LocalDateTime.parse(matcher.group("time"), LOG_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /// Returns whether a HotSpot error report matches one of the HMCL sessions.
    private static boolean matches(Path report, List<Pair<LocalDateTime, String>> sessions) throws IOException {
        Matcher fileMatcher = ERROR_FILE_PATTERN.matcher(report.getFileName().toString());
        if (!fileMatcher.matches())
            return false;

        String filePid = fileMatcher.group("pid");
        boolean fatalError = false;
        @Nullable String reportPid = null;
        @Nullable String commandLine = null;
        @Nullable LocalDateTime processStartTime = null;

        try (BufferedReader reader = IOUtils.newBufferedReaderMaybeNativeEncoding(report)) {
            @Nullable String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("# A fatal error has been detected by the Java Runtime Environment:")) {
                    fatalError = true;
                    continue;
                }

                Matcher pidMatcher = ERROR_PID_PATTERN.matcher(line);
                if (pidMatcher.matches()) {
                    reportPid = pidMatcher.group("pid");
                    continue;
                }

                if (line.startsWith("Command Line: ")) {
                    commandLine = line.substring("Command Line: ".length());
                    continue;
                }

                Matcher timeMatcher = ERROR_TIME_PATTERN.matcher(line);
                if (timeMatcher.matches()) {
                    try {
                        LocalDateTime reportTime = LocalDateTime.parse(
                                timeMatcher.group("month") + " " + timeMatcher.group("day") + " "
                                        + timeMatcher.group("clock") + " " + timeMatcher.group("year"),
                                ERROR_TIME_FORMATTER);
                        double elapsedSeconds = Double.parseDouble(timeMatcher.group("elapsed"));
                        if (elapsedSeconds > Long.MAX_VALUE / 1000.0)
                            return false;
                        processStartTime = reportTime.minus(Duration.ofMillis(Math.round(elapsedSeconds * 1000)));
                    } catch (DateTimeParseException | NumberFormatException e) {
                        return false;
                    }
                }

                if (line.startsWith("---------------  T H R E A D"))
                    break;
            }
        }

        if (!fatalError || !filePid.equals(reportPid) || commandLine == null || processStartTime == null)
            return false;

        for (Pair<LocalDateTime, String> session : sessions) {
            if (commandLine.contains(session.value())
                    && Duration.between(session.key(), processStartTime).abs().compareTo(START_TIME_TOLERANCE) <= 0)
                return true;
        }
        return false;
    }
}
