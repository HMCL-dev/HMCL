/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.*;
import java.lang.System.Logger.Level;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

/**
 * @author Glavo
 */
public final class Logger {
    public static final Logger LOG = new Logger();

    private static volatile String[] accessTokens = new String[0];

    public static synchronized void registerAccessToken(String token) {
        if (token == null || token.length() <= 1)
            return;

        final String[] oldAccessTokens = accessTokens;
        final String[] newAccessTokens = Arrays.copyOf(oldAccessTokens, oldAccessTokens.length + 1);

        newAccessTokens[oldAccessTokens.length] = token;

        accessTokens = newAccessTokens;
    }

    public static String filterForbiddenToken(String message) {
        for (String token : accessTokens)
            message = message.replace(token, "<access token>");
        return message;
    }

    static final String PACKAGE_PREFIX = "org.jackhuang.hmcl.";
    static final String CLASS_NAME = Logger.class.getName();

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final BlockingQueue<LogEvent> queue = new LinkedBlockingQueue<>();
    private final StringBuilder builder = new StringBuilder(512);

    private Path logFile;
    private ByteArrayOutputStream rawLogs;
    private PrintWriter logWriter;

    private Thread loggerThread;

    private boolean shutdown = false;

    private int logRetention = 0;

    public void setLogRetention(int logRetention) {
        this.logRetention = Math.max(0, logRetention);
    }

    private String format(LogEvent.DoLog event) {
        StringBuilder builder = this.builder;
        builder.setLength(0);
        builder.append('[');
        TIME_FORMATTER.formatTo(Instant.ofEpochMilli(event.time()), builder);
        builder.append("] [");

        if (event.caller() != null && event.caller().startsWith(PACKAGE_PREFIX)) {
            builder.append("@.").append(event.caller(), PACKAGE_PREFIX.length(), event.caller().length());
        } else {
            builder.append(event.caller());
        }

        builder.append('/')
                .append(event.level())
                .append("] ")
                .append(filterForbiddenToken(event.message()));
        return builder.toString();
    }

    private void handle(LogEvent event) {
        if (event instanceof LogEvent.DoLog doLog) {
            String log = format(doLog);
            Throwable exception = doLog.exception();

            System.out.println(log);
            if (exception != null)
                exception.printStackTrace(System.out);

            logWriter.println(log);
            if (exception != null)
                exception.printStackTrace(logWriter);
        } else if (event instanceof LogEvent.ExportLog exportEvent) {
            logWriter.flush();
            try {
                if (logFile != null) {
                    Files.copy(logFile, exportEvent.output);
                } else {
                    rawLogs.writeTo(exportEvent.output);
                }
            } catch (IOException e) {
                exportEvent.exception = e;
            } finally {
                exportEvent.latch.countDown();
            }
        } else if (event instanceof LogEvent.Shutdown) {
            shutdown = true;
        } else {
            throw new AssertionError("Unknown event: " + event);
        }
    }

    private void onExit() {
        shutdown();
        try {
            loggerThread.join();
        } catch (InterruptedException ignored) {
        }

        String caller = CLASS_NAME + ".onExit";

        if (logRetention > 0 && logFile != null) {
            var list = findRecentLogFiles(Integer.MAX_VALUE);
            if (list.size() > logRetention) {
                for (int i = 0, end = list.size() - logRetention; i < end; i++) {
                    Path file = list.get(i);
                    try {
                        if (!Files.isSameFile(file, logFile)) {
                            log(Level.INFO, caller, "Delete old log file " + file, null);
                            Files.delete(file);
                        }
                    } catch (IOException e) {
                        log(Level.WARNING, caller, "Failed to delete log file " + file, e);
                    }
                }
            }
        }

        ArrayList<LogEvent> logs = new ArrayList<>();
        queue.drainTo(logs);
        for (LogEvent log : logs) {
            handle(log);
        }

        if (logFile == null) {
            return;
        }

        boolean failed = false;
        Path xzFile = logFile.resolveSibling(logFile.getFileName() + ".xz");
        try (XZOutputStream output = new XZOutputStream(Files.newOutputStream(xzFile), new LZMA2Options())) {
            logWriter.flush();
            Files.copy(logFile, output);
        } catch (IOException e) {
            failed = true;
            handle(new LogEvent.DoLog(System.currentTimeMillis(), caller, Level.WARNING, "Failed to dump log file to xz format", e));
        } finally {
            logWriter.close();
        }

        if (!failed)
            try {
                Files.delete(logFile);
            } catch (IOException e) {
                System.err.println("An exception occurred while deleting raw log file");
                e.printStackTrace(System.err);
            }
    }

    public void start(Path logFolder) {
        if (logFolder != null) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"));
            try {
                Files.createDirectories(logFolder);
                for (int n = 0; ; n++) {
                    Path file = logFolder.resolve(time + (n == 0 ? "" : "." + n) + ".log").toAbsolutePath().normalize();
                    try {
                        logWriter = new PrintWriter(Files.newBufferedWriter(file, UTF_8, CREATE_NEW));
                        logFile = file;
                        break;
                    } catch (FileAlreadyExistsException ignored) {
                    }
                }
            } catch (IOException e) {
                log(Level.WARNING, CLASS_NAME + ".start", "Failed to create log file", e);
            }
        }

        if (logWriter == null) {
            rawLogs = new ByteArrayOutputStream(256 * 1024);
            logWriter = new PrintWriter(new OutputStreamWriter(rawLogs, UTF_8));
        }

        loggerThread = new Thread(() -> {
            ArrayList<LogEvent> logs = new ArrayList<>();
            try {
                while (!shutdown) {
                    if (queue.drainTo(logs) > 0) {
                        for (LogEvent log : logs) {
                            handle(log);
                        }
                        logs.clear();
                    } else {
                        logWriter.flush();
                        handle(queue.take());
                    }
                }

                while (queue.drainTo(logs) > 0) {
                    for (LogEvent log : logs) {
                        handle(log);
                    }
                    logs.clear();
                }
            } catch (InterruptedException e) {
                throw new AssertionError("This thread cannot be interrupted", e);
            }
        });
        loggerThread.setName("HMCL Logger Thread");
        loggerThread.start();

        Thread cleanerThread = new Thread(this::onExit);
        cleanerThread.setName("HMCL Logger Shutdown Hook");
        Runtime.getRuntime().addShutdownHook(cleanerThread);
    }

    public void shutdown() {
        queue.add(new LogEvent.Shutdown());
    }

    public Path getLogFile() {
        return logFile;
    }

    public @NotNull List<Path> findRecentLogFiles(int n) {
        if (n <= 0 || logFile == null)
            return List.of();

        var currentLogFile = LogFile.ofFile(logFile);

        Path logDir = logFile.getParent();
        if (logDir == null || !Files.isDirectory(logDir))
            return List.of();

        var logFiles = new ArrayList<LogFile>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir)) {
            for (Path path : stream) {
                LogFile item = LogFile.ofFile(path);
                if (item != null && (currentLogFile == null || item.compareTo(currentLogFile) < 0)) {
                    logFiles.add(item);
                }
            }
        } catch (IOException e) {
            log(Level.WARNING, CLASS_NAME + ".findRecentLogFiles", "Failed to list log files in " + logDir, e);
            return List.of();
        }
        logFiles.sort(Comparator.naturalOrder());

        final int resultLength = Math.min(n, logFiles.size());
        final int offset = logFiles.size() - resultLength;

        var result = new Path[resultLength];
        for (int i = 0; i < resultLength; i++) {
            result[i] = logFiles.get(i + offset).file;
        }
        return List.of(result);
    }

    public void exportLogs(OutputStream output) throws IOException {
        Objects.requireNonNull(output);
        LogEvent.ExportLog event = new LogEvent.ExportLog(output);
        try {
            queue.put(event);
            event.await();
        } catch (InterruptedException e) {
            throw new AssertionError("This thread cannot be interrupted", e);
        }
        if (event.exception != null) {
            throw event.exception;
        }
    }

    public String getLogs() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            exportLogs(output);
            return output.toString(UTF_8);
        } catch (IOException e) {
            log(Level.WARNING, CLASS_NAME + ".getLogs", "Failed to export logs", e);
            return "";
        }
    }

    private void log(Level level, String caller, String msg, Throwable exception) {
        queue.add(new LogEvent.DoLog(System.currentTimeMillis(), caller, level, msg, exception));
    }

    public void log(Level level, String msg) {
        log(level, CallerFinder.getCaller(), msg, null);
    }

    public void log(Level level, String msg, Throwable exception) {
        log(level, CallerFinder.getCaller(), msg, exception);
    }

    public void error(String msg) {
        log(Level.ERROR, CallerFinder.getCaller(), msg, null);
    }

    public void error(String msg, Throwable exception) {
        log(Level.ERROR, CallerFinder.getCaller(), msg, exception);
    }

    public void warning(String msg) {
        log(Level.WARNING, CallerFinder.getCaller(), msg, null);
    }

    public void warning(String msg, Throwable exception) {
        log(Level.WARNING, CallerFinder.getCaller(), msg, exception);
    }

    public void info(String msg) {
        log(Level.INFO, CallerFinder.getCaller(), msg, null);
    }

    public void info(String msg, Throwable exception) {
        log(Level.INFO, CallerFinder.getCaller(), msg, exception);
    }

    public void debug(String msg) {
        log(Level.DEBUG, CallerFinder.getCaller(), msg, null);
    }

    public void debug(String msg, Throwable exception) {
        log(Level.DEBUG, CallerFinder.getCaller(), msg, exception);
    }

    public void trace(String msg) {
        log(Level.TRACE, CallerFinder.getCaller(), msg, null);
    }

    public void trace(String msg, Throwable exception) {
        log(Level.TRACE, CallerFinder.getCaller(), msg, exception);
    }

    private record LogFile(Path file,
                           int year, int month, int day, int hour, int minute, int second,
                           int n) implements Comparable<LogFile> {
        private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})T(?<hour>\\d{2})-(?<minute>\\d{2})-(?<second>\\d{2})(\\.(?<n>\\d+))?\\.log(\\.(gz|xz))?");

        private static @Nullable LogFile ofFile(Path file) {
            if (!Files.isRegularFile(file))
                return null;

            Matcher matcher = FILE_NAME_PATTERN.matcher(file.getFileName().toString());
            if (!matcher.matches())
                return null;

            int year = Integer.parseInt(matcher.group("year"));
            int month = Integer.parseInt(matcher.group("month"));
            int day = Integer.parseInt(matcher.group("day"));
            int hour = Integer.parseInt(matcher.group("hour"));
            int minute = Integer.parseInt(matcher.group("minute"));
            int second = Integer.parseInt(matcher.group("second"));
            int n = Optional.ofNullable(matcher.group("n")).map(Integer::parseInt).orElse(0);

            return new LogFile(file, year, month, day, hour, minute, second, n);
        }

        @Override
        public int compareTo(@NotNull Logger.LogFile that) {
            if (this.year != that.year) return Integer.compare(this.year, that.year);
            if (this.month != that.month) return Integer.compare(this.month, that.month);
            if (this.day != that.day) return Integer.compare(this.day, that.day);
            if (this.hour != that.hour) return Integer.compare(this.hour, that.hour);
            if (this.minute != that.minute) return Integer.compare(this.minute, that.minute);
            if (this.second != that.second) return Integer.compare(this.second, that.second);
            if (this.n != that.n) return Integer.compare(this.n, that.n);
            return 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(year, month, day, hour, minute, second, n);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof LogFile that && compareTo(that) == 0;
        }
    }
}
