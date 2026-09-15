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
package org.jackhuang.hmcl.util.logging;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/// Diagnostic performance log used to locate bottlenecks in the launcher.
///
/// The whole facility is a no-op unless `-Dhmcl.perf=true` is passed to the launcher JVM, and the
/// flag is a `static final` field so the JIT folds every guard away in a normal run.
///
/// When enabled, records are appended to a plain text file using a tab separated format, one record
/// per line, so the result can be processed with `sort`/`awk` or a spreadsheet without extra
/// tooling:
///
/// - `T` a finished task: `T, endEpochMillis, thread, depth, durationNanos, name, note`
/// - `E` a discrete event: `E, epochMillis, thread, -, -, name, detail`
/// - `S` a periodic sample: `S, epochMillis, -, -, -, key, value`
///
/// Counters are cumulative, so a consumer can derive per-second rates from consecutive samples.
///
/// Call sites on cold paths may pass an eagerly built detail string. Call sites inside a hot loop
/// must guard the whole call with `if (PerfLog.ENABLED)` so that neither the string nor the
/// arguments are built when logging is off.
public final class PerfLog {

    /// Whether performance logging is enabled for this JVM.
    ///
    /// This is deliberately a compile time constant from the JIT's point of view: every call site
    /// starts with `if (!PerfLog.ENABLED) return;`, which lets the optimizer remove the call
    /// entirely when the launcher is started without `-Dhmcl.perf=true`.
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("hmcl.perf", "false"));

    /// Interval between two periodic samples, in milliseconds.
    private static final long SAMPLE_INTERVAL_MILLIS = 1000L;

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final ConcurrentLinkedQueue<String> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Map<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> MAXIMA = new ConcurrentHashMap<>();
    private static final Map<String, LongSupplier> GAUGES = new ConcurrentHashMap<>();

    /// Number of records waiting in [#QUEUE], tracked separately because [ConcurrentLinkedQueue#size()]
    /// is linear in the queue length and this is read on every record.
    private static final AtomicInteger QUEUE_SIZE = new AtomicInteger();

    /// Upper bound of pending records; beyond this the log is dropped rather than growing without bound.
    private static final int MAX_PENDING_RECORDS = 1_000_000;

    /// Nesting depth of the task currently running on this thread.
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private static final @Nullable BufferedWriter WRITER;
    private static final @Nullable Path LOG_FILE;

    static {
        BufferedWriter writer = null;
        Path file = null;

        if (ENABLED) {
            try {
                Path directory = resolveOutputDirectory();
                Files.createDirectories(directory);
                file = directory.resolve("perf-" + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".log");
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException e) {
                System.err.println("Failed to create performance log file");
                e.printStackTrace(System.err);
                file = null;
            }
        }

        WRITER = writer;
        LOG_FILE = file;

        if (writer != null) {
            startBackgroundThreads(writer);
            startFxLatencyMonitor();
        }
    }

    /// Returns the current value of [System#nanoTime()], or `0` when performance logging is disabled.
    ///
    /// Call sites use this instead of guarding every measurement with [#ENABLED], so that a normal
    /// run pays a predictable branch rather than a native clock read.
    public static long now() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    /// Converts a duration produced by [#now()] into whole milliseconds.
    public static long ms(long nanos) {
        return nanos / 1_000_000L;
    }

    /// Polls the JavaFX thread so that stalls of the user interface become visible in the log.
    ///
    /// A runnable posted from a background thread is picked up as soon as the JavaFX thread becomes
    /// free, so the delay before it runs is exactly how long the interface was unresponsive. This
    /// measures what a player actually perceives, which a profiler of background work cannot show.
    private static void startFxLatencyMonitor() {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    return;
                }

                long postedAt = System.nanoTime();
                try {
                    javafx.application.Platform.runLater(() ->
                            invocation("fx.latency", System.nanoTime() - postedAt));
                } catch (IllegalStateException e) {
                    // The JavaFX toolkit has not been started yet; keep polling until it is.
                }
            }
        }, "HMCL PerfLog FX Monitor");

        thread.setDaemon(true);
        thread.start();
    }

    private PerfLog() {
    }

    private static Path resolveOutputDirectory() {
        String configured = System.getProperty("hmcl.perf.dir");
        if (configured != null && !configured.isBlank())
            return Path.of(configured).toAbsolutePath();

        return Path.of(System.getProperty("user.dir"), "build", "perf").toAbsolutePath();
    }

    /// Returns the file records are written to, or `null` when performance logging is disabled.
    public static @Nullable Path getLogFile() {
        return LOG_FILE;
    }

    private static void startBackgroundThreads(BufferedWriter writer) {
        Thread flushThread = new Thread(() -> {
            long lastSampleTime = 0L;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    return;
                }

                try {
                    drain(writer);

                    long now = System.currentTimeMillis();
                    if (now - lastSampleTime >= SAMPLE_INTERVAL_MILLIS) {
                        lastSampleTime = now;
                        writeSystemSamples(writer, now);
                    }

                    writer.flush();
                } catch (IOException e) {
                    System.err.println("Failed to write performance log");
                    e.printStackTrace(System.err);
                    return;
                }
            }
        }, "HMCL PerfLog Writer");

        flushThread.setDaemon(true);
        flushThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                drain(writer);
                writeSystemSamples(writer, System.currentTimeMillis());
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
            }
        }, "HMCL PerfLog Shutdown"));
    }

    private static void drain(BufferedWriter writer) throws IOException {
        String line;
        while ((line = QUEUE.poll()) != null) {
            QUEUE_SIZE.decrementAndGet();
            writer.write(line);
            writer.newLine();
        }
    }

    private static void writeSystemSamples(BufferedWriter writer, long now) throws IOException {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        writeSample(writer, now, "jvm.threads", threadBean.getThreadCount());
        writeSample(writer, now, "jvm.peakThreads", threadBean.getPeakThreadCount());
        writeSample(writer, now, "jvm.heapUsed", memoryBean.getHeapMemoryUsage().getUsed());
        writeSample(writer, now, "jvm.heapCommitted", memoryBean.getHeapMemoryUsage().getCommitted());
        writeSample(writer, now, "jvm.nonHeapUsed", memoryBean.getNonHeapMemoryUsage().getUsed());

        for (Map.Entry<String, LongSupplier> entry : GAUGES.entrySet()) {
            try {
                writeSample(writer, now, entry.getKey(), entry.getValue().getAsLong());
            } catch (Throwable ignored) {
            }
        }

        for (Map.Entry<String, LongAdder> entry : COUNTERS.entrySet()) {
            writeSample(writer, now, entry.getKey(), entry.getValue().sum());
        }

        for (Map.Entry<String, AtomicLong> entry : MAXIMA.entrySet()) {
            writeSample(writer, now, entry.getKey(), entry.getValue().get());
        }
    }

    private static void writeSample(BufferedWriter writer, long now, String key, long value) throws IOException {
        writer.write("S\t" + now + "\t-\t-\t-\t" + key + "\t" + value);
        writer.newLine();
    }

    private static void emit(String line) {
        if (QUEUE_SIZE.get() >= MAX_PENDING_RECORDS)
            return;

        QUEUE_SIZE.incrementAndGet();
        QUEUE.add(line);
    }

    private static String threadName() {
        return Thread.currentThread().getName();
    }

    //region Task timeline

    /// Marks the start of a task execution on the calling thread.
    ///
    /// @return an opaque token that must be passed to [#taskEnd(String, long, Throwable)], or `0`
    ///         when performance logging is disabled
    public static long taskStart() {
        if (!ENABLED)
            return 0L;

        DEPTH.get()[0]++;
        return System.nanoTime();
    }

    /// Records the completion of a task started with [#taskStart()].
    ///
    /// @param name       the task name
    /// @param startNanos the token returned by [#taskStart()]
    /// @param error      the failure of the task, or `null` when it succeeded
    public static void taskEnd(String name, long startNanos, @Nullable Throwable error) {
        if (!ENABLED || startNanos == 0L)
            return;

        int[] depth = DEPTH.get();
        int currentDepth = depth[0] > 0 ? --depth[0] : 0;

        long durationNanos = System.nanoTime() - startNanos;
        long endEpochMillis = System.currentTimeMillis();

        emit("T\t" + endEpochMillis
                + "\t" + threadName()
                + "\t" + currentDepth
                + "\t" + durationNanos
                + "\t" + name
                + "\t" + (error == null ? "" : describe(error)));
    }

    private static String describe(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    //endregion

    //region Events and metrics

    /// Records a discrete event with an optional detail string.
    public static void event(String name, String detail) {
        if (!ENABLED)
            return;

        emit("E\t" + System.currentTimeMillis()
                + "\t" + threadName()
                + "\t-\t-\t" + name
                + "\t" + detail.replace('\t', ' ').replace('\n', ' '));
    }

    /// Emits a single sample immediately, for values that are not cumulative.
    public static void sample(String key, long value) {
        if (!ENABLED)
            return;

        emit("S\t" + System.currentTimeMillis() + "\t-\t-\t-\t" + key + "\t" + value);
    }

    /// Adds `delta` to a cumulative counter reported with every periodic sample.
    public static void count(String key, long delta) {
        if (!ENABLED)
            return;

        COUNTERS.computeIfAbsent(key, ignored -> new LongAdder()).add(delta);
    }

    /// Registers a value that is polled once per periodic sample.
    public static void gauge(String key, LongSupplier supplier) {
        if (!ENABLED)
            return;

        GAUGES.put(key, supplier);
    }

    /// Records a completed invocation of `name` with the given duration.
    ///
    /// Intended for hot methods that are called far too often to log individually. The counter
    /// names follow the `name.count`, `name.totalNanos` and `name.maxNanos` convention.
    public static void invocation(String name, long durationNanos) {
        if (!ENABLED)
            return;

        COUNTERS.computeIfAbsent(name + ".count", ignored -> new LongAdder()).increment();
        COUNTERS.computeIfAbsent(name + ".totalNanos", ignored -> new LongAdder()).add(durationNanos);

        MAXIMA.computeIfAbsent(name + ".maxNanos", ignored -> new AtomicLong())
                .accumulateAndGet(durationNanos, Math::max);
    }

    //endregion
}
