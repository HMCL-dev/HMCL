package org.jackhuang.hmcl.util.logging;

import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Glavo
 */
public final class Logger {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final BlockingQueue<LogEvent> queue = new LinkedBlockingQueue<>();
    private final StringBuilder builder = new StringBuilder(512);

    private Path logFile;
    private ByteArrayOutputStream rawLogs;
    private FileChannel logFileChannel;
    private PrintWriter logWriter;

    private Thread loggerThread;

    private boolean shutdown = false;

    private int logRetention = 0;

    private String format(LogEvent.DoLog event) {
        StringBuilder builder = this.builder;
        builder.setLength(0);
        builder.append('[');
        TIME_FORMATTER.formatTo(Instant.ofEpochMilli(event.time), builder);
        builder.append("] [")
                .append(event.caller)
                .append('/')
                .append(event.level)
                .append("] ")
                .append(Logging.filterForbiddenToken(event.message));
        return builder.toString();
    }

    private void handle(LogEvent event) {
        if (event instanceof LogEvent.DoLog) {
            String log = format((LogEvent.DoLog) event);
            Throwable exception = ((LogEvent.DoLog) event).exception;

            System.out.println(log);
            if (exception != null)
                exception.printStackTrace(System.out);

            logWriter.println(log);
            if (exception != null)
                exception.printStackTrace(logWriter);
        } else if (event instanceof LogEvent.ExportLog) {
            LogEvent.ExportLog exportEvent = (LogEvent.ExportLog) event;
            logWriter.flush();
            try {
                if (logFile != null) {
                    long position = logFileChannel.position();
                    try {
                        logFileChannel.position(0);
                        IOUtils.copyTo(Channels.newInputStream(logFileChannel), exportEvent.output);
                    } finally {
                        logFileChannel.position(position);
                    }
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

    public void setLogRetention(int logRetention) {
        this.logRetention = Math.max(0, logRetention);
    }

    public void start(Path logFolder) {
        if (logFolder != null) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"));
            try {
                for (int n = 0; ; n++) {
                    Path file = logFolder.resolve(time + (n == 0 ? "" : "." + n) + ".log");

                    try {
                        logFileChannel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
                        logWriter = new PrintWriter(new OutputStreamWriter(Channels.newOutputStream(logFileChannel), StandardCharsets.UTF_8));
                        logFile = file;
                        break;
                    } catch (FileAlreadyExistsException ignored) {
                    }
                }
            } catch (IOException e) {
                String caller = Logger.class.getName() + "." + "start";
                log(Level.WARNING, caller, "An exception occurred while creating the log file", e);
                if (logFileChannel != null) {
                    try {
                        logFileChannel.close();
                    } catch (IOException ex) {
                        log(Level.WARNING, caller, "Failed to close channel", null);
                    } finally {
                        logFileChannel = null;
                        logWriter = null;
                        logFile = null;
                    }
                }
            }
        }

        if (logWriter == null) {
            rawLogs = new ByteArrayOutputStream(256 * 1024);
            logWriter = new PrintWriter(new OutputStreamWriter(rawLogs, StandardCharsets.UTF_8));
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
                throw new AssertionError(e);
            }
        });
        loggerThread.setName("HMCL Logger Thread");
        loggerThread.start();

        Thread cleanerThread = new Thread(() -> {
            try {
                loggerThread.join();
            } catch (InterruptedException ignored) {
            }

            ArrayList<LogEvent> logs = new ArrayList<>();
            queue.drainTo(logs);
            for (LogEvent log : logs) {
                handle(log);
            }

            if (logFile != null) {
                logWriter.flush();
                try {
                    logFileChannel.position(0);
                    Path xzFile = logFile.resolveSibling(logFile.getFileName() + ".xz");
                    try (InputStream input = Channels.newInputStream(logFileChannel);
                         XZOutputStream output = new XZOutputStream(Files.newOutputStream(xzFile), new LZMA2Options())) {
                        IOUtils.copyTo(input, output);
                    }

                    Files.delete(logFile);
                    logFile = xzFile;
                } catch (IOException e) {
                    System.err.println("An exception occurred while dumping log file to xz format");
                    e.printStackTrace(System.err);
                } finally {
                    logWriter.close();
                }

                if (logRetention <= 0) {
                    return;
                }

                List<Pair<Path, int[]>> list = new ArrayList<>();
                Pattern fileNamePattern = Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})T(?<hour>\\d{2})-(?<minute>\\d{2})-(?<second>\\d{2})(\\.(?<n>\\d+))?\\.log(\\.(gz|xz))?");
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(logFolder)) {
                    for (Path path : stream) {
                        Matcher matcher = fileNamePattern.matcher(path.getFileName().toString());
                        if (matcher.matches() && Files.isRegularFile(path)) {
                            int year = Integer.parseInt(matcher.group("year"));
                            int month = Integer.parseInt(matcher.group("month"));
                            int day = Integer.parseInt(matcher.group("day"));
                            int hour = Integer.parseInt(matcher.group("hour"));
                            int minute = Integer.parseInt(matcher.group("minute"));
                            int second = Integer.parseInt(matcher.group("second"));
                            int n = Optional.ofNullable(matcher.group("n")).map(Integer::parseInt).orElse(0);

                            list.add(Pair.pair(path, new int[]{year, month, day, hour, minute, second, n}));
                        }
                    }
                } catch (IOException e) {
                    System.err.println("An exception occurred while enumerating files");
                    e.printStackTrace(System.err);
                }

                if (list.size() <= logRetention) {
                    return;
                }

                list.sort((a, b) -> {
                    int[] v1 = a.getValue();
                    int[] v2 = b.getValue();

                    assert v1.length == v2.length;

                    for (int i = 0; i < v1.length; i++) {
                        int c = Integer.compare(v1[i], v2[i]);
                        if (c != 0)
                            return c;
                    }

                    return 0;
                });

                for (int i = 0, end = list.size() - logRetention; i < end; i++) {
                    Pair<Path, int[]> pair = list.get(i);

                    try {
                        if (!Files.isSameFile(pair.getKey(), logFile)) {
                            Files.delete(pair.getKey());
                        }
                    } catch (IOException e) {
                        System.err.println("An exception occurred while deleting old logs");
                        e.printStackTrace(System.err);
                    }
                }
            }
        });
        cleanerThread.setName("HMCL Logger Shutdown Hook");
        Runtime.getRuntime().addShutdownHook(cleanerThread);
    }

    public void shutdown() {
        queue.add(new LogEvent.Shutdown());
    }

    public void exportLogs(OutputStream output) throws IOException {
        Objects.requireNonNull(output);
        LogEvent.ExportLog event = new LogEvent.ExportLog(output);
        try {
            queue.put(event);
            event.await();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        if (event.exception != null) {
            throw event.exception;
        }
    }

    private void log(Level level, String caller, String msg, Throwable exception) {
        queue.add(new LogEvent.DoLog(System.currentTimeMillis(), caller, level, msg, exception));
    }

    // TODO: Remove dependency on java.logging
    public void log(Level level, String msg) {
        log(level, CallerFinder.getCaller(), msg, null);
    }

    public void log(Level level, String msg, Throwable exception) {
        log(level, CallerFinder.getCaller(), msg, exception);
    }

    public void severe(String msg) {
        log(Level.SEVERE, CallerFinder.getCaller(), msg, null);
    }

    public void warning(String msg) {
        log(Level.WARNING, CallerFinder.getCaller(), msg, null);
    }

    public void info(String msg) {
        log(Level.INFO, CallerFinder.getCaller(), msg, null);
    }
}
