package org.jackhuang.hmcl.util.logging;

import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
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

    private String format(LogEvent.DoLog event) {
        StringBuilder builder = this.builder;
        builder.setLength(0);
        builder.append('[');
        TIME_FORMATTER.formatTo(Instant.ofEpochMilli(event.time), builder);
        builder.append(']');
        builder.append(" [")
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

    public void start(Path logFolder) {
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").format(LocalDateTime.now());
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
                } catch (IOException e) {
                    System.err.println("An exception occurred while dumping log file to xz format");
                    e.printStackTrace(System.err);
                } finally {
                    logWriter.close();

                    Instant now = Instant.now();
                    Duration duration = Duration.of(30, ChronoUnit.DAYS);

                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(logFolder)) {
                        Pattern fileNamePattern = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}(.*)\\.log(\\.(gz|xz))?");
                        for (Path path : stream) {
                            if (!fileNamePattern.matcher(path.getFileName().toString()).matches())
                                continue;

                            try {
                                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                                if (attributes.isRegularFile() && Duration.between(attributes.lastModifiedTime().toInstant(), now).compareTo(duration) >= 0) {
                                    Files.delete(path);
                                }
                            } catch (IOException e) {
                                System.err.println("Failed to delete old file: " + path);
                                e.printStackTrace(System.err);
                            }
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
