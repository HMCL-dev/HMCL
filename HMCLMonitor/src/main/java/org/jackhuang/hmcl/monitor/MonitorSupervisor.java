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
package org.jackhuang.hmcl.monitor;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.launch.ExitWaiter;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.launch.StreamPump;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The monitor process role of the monitor feature: creating the game process, supervising it until
/// it exits, and reporting the result.
///
/// The monitor owns the game process and collects its output in a session log while forwarding it
/// to the main launcher over [the protocol][MonitorProtocol]. After the game exits, it runs the
/// post-exit command and stores the result in memory. When the protocol stream has failed and the
/// relaunch policy requests it, the entry point continues into the launcher UI in this same JVM.
/// No crash result file is written; the crash window reads the stored result and session log.
@NotNullByDefault
public final class MonitorSupervisor {
    private static boolean shouldRestart = false;
    private static boolean crashed = false;
    private static @Nullable ResultSpec resultSpec;

    private MonitorSupervisor() {
    }

    public static @Nullable ResultSpec getResultSpec() {
        return resultSpec;
    }

    public static boolean isCrashed() {
        return crashed;
    }

    /// Handles the `--monitor` entry point.
    ///
    /// @param args the process arguments
    /// @return `true` when monitor mode has finished and the caller should exit; `false` for
    ///         non-monitor arguments or when this JVM should continue into the launcher UI
    public static boolean processArguments(String[] args) {
        if (args.length != 2 || !args[0].equals("--monitor"))
            return false;
        try {
            start(Path.of(args[1]));
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to supervise the game process", e);
        }
        return !shouldRestart;
    }

    /// Reads the [ProcessSpec] from `specPath`, creates the process it describes, supervises it
    /// until it exits, and reports the result.
    ///
    /// @param specPath the file containing the [ProcessSpec]
    /// @throws IOException          if the spec file is malformed or the game process cannot be created
    /// @throws InterruptedException if interrupted while waiting for the game process
    public static void start(Path specPath) throws IOException, InterruptedException {
        LOG.info("*** HMCL MONITOR ***");
        ProcessSpec spec;
        try {
            spec = JsonUtils.fromJsonFile(specPath, ProcessSpec.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed process spec file " + specPath, e);
        }
        if (spec == null || spec.command == null || spec.command.isEmpty())
            throw new IOException("Malformed process spec file " + specPath);
        Files.deleteIfExists(specPath);

        ProcessBuilder pb = new ProcessBuilder(spec.command).directory(new File(spec.directory));
        pb.environment().putAll(spec.environment);
        if (spec.inheritStdin)
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        ManagedProcess gameProcess = new ManagedProcess(pb.start(), spec.command);

        LOG.info("Game process created, pid " + gameProcess.getPid() + ", spec " + specPath);
        MonitorProtocol.pidMessage(gameProcess.getPid(), gameProcess.getProcessStartTime());

        Path logFile = Files.createTempFile("hmcl-monitor-game-", ".log");


        // Forward one game output line to the log file, the managed process (feeding the exit
        // classification) and the protocol stream. A broken protocol stream (the launcher exited
        // early) only stops forwarding; the log file keeps collecting the remaining output.
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile, MonitorProtocol.CHARSET)) {
            startMonitors(gameProcess, logWriter, spec, logFile, specPath);
        }
    }

    private static void startMonitors(ManagedProcess gameProcess, BufferedWriter logWriter,
                                      ProcessSpec spec, Path logFile, Path specPath) throws InterruptedException {
        BiConsumer<String, Boolean> sink = (line, isErrorStream) -> {
            try {
                synchronized (logWriter) {
                    logWriter.write(line);
                    logWriter.newLine();
                    logWriter.flush();
                }
            } catch (IOException e) {
                LOG.warning("Failed to write the session log", e);
            }
            gameProcess.addLine(line);
            MonitorProtocol.logMessage(isErrorStream, line);
        };

        Charset encoding = Charset.forName(Objects.requireNonNull(spec.encoding));
        Thread stdoutPump = Lang.thread(new StreamPump(gameProcess.getProcess().getInputStream(),
                line -> sink.accept(line, false), encoding), "monitor-stdout-pump", true);
        Thread stderrPump = Lang.thread(new StreamPump(gameProcess.getProcess().getErrorStream(),
                line -> sink.accept(line, true), encoding), "monitor-stderr-pump", true);
        gameProcess.addRelatedThread(stdoutPump);
        gameProcess.addRelatedThread(stderrPump);

        Lang.thread(new ExitWaiter(gameProcess, List.of(stdoutPump, stderrPump), (exitCode, exitType) -> {
            LOG.info("Game exited with code " + exitCode + "(" + Integer.toHexString(exitCode) + "), type " + exitType);
            ProcessListener.ExitType reportedType = readCanceled(specPath) ? ProcessListener.ExitType.INTERRUPTED : exitType;
            crashed = reportedType != ProcessListener.ExitType.NORMAL;

            shouldRestart = MonitorProtocol.parentGone() && switch (spec.relaunchPolicy) {
                case ALWAYS -> true;
                case ON_CRASH -> crashed;
                case NEVER -> false;
            };

            MonitorProtocol.exitMessage(exitCode, reportedType);

            if (spec.postExitCommand != null && !spec.postExitCommand.isEmpty()) {
                try {
                    ProcessBuilder builder = new ProcessBuilder(spec.postExitCommand).directory(new File(spec.directory));
                    builder.environment().putAll(spec.environment);
                    SystemUtils.callExternalProcess(builder);
                } catch (Throwable e) {
                    LOG.warning("An Exception happened while running exit command.", e);
                }
            }

            resultSpec = new ResultSpec(spec, gameProcess, reportedType, logFile);
        }), "exit-waiter", true).join();
    }

    /// Returns whether the main launcher rewrote the spec file to mark the game process as manually
    /// canceled. A successfully read file is deleted before checking for the `CANCELED` prefix.
    ///
    /// [MonitorGameProcess#stop] writes the marker before requesting process termination. The
    /// caller reports a recognized cancellation as [ProcessListener.ExitType#INTERRUPTED].
    private static boolean readCanceled(Path specPath) {
        if (!Files.exists(specPath))
            return false;
        try {
            String canceled = Files.readString(specPath);
            Files.deleteIfExists(specPath);
            return canceled.startsWith("CANCELED");
        } catch (IOException e) {
            LOG.warning("Failed to check whether to cancel " + specPath, e);
            return false;
        }
    }
}
