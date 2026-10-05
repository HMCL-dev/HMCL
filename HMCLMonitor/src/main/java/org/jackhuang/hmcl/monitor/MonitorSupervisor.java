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

import org.jackhuang.hmcl.launch.ExitWaiter;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.launch.StreamPump;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.JavaProcessLauncher;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The monitor process role of the monitor feature: creating the game process, supervising it until
/// it exits, and reporting the result.
///
/// <p>The monitor has no user interface. It owns the game process, so it always knows the real exit
/// code even after the main launcher process has exited. Game output is teed into a session log
/// file and forwarded to the main launcher process over [the protocol][MonitorProtocol]; after the
/// game exits, the post-exit command is run, and HMCL is relaunched if the launch options ask for
/// it and the main launcher process is already gone. The crash result file and the session log are
/// only kept when a relaunched launcher is going to consume them.
@NotNullByDefault
public final class MonitorSupervisor {

    private MonitorSupervisor() {
    }

    /// Handles the `--monitor` entry point.
    ///
    /// @param args the process arguments
    /// @return whether the arguments were consumed; the caller should exit afterward, since
    ///         [MonitorSupervisor#start] blocks until the game process has exited
    public static boolean processArguments(String[] args) {
        if (args.length >= 1 && args[0].equals("--monitor")) {
            if (args.length != 2) {
                LOG.error("Usage: --monitor <specFile>");
            } else {
                try {
                    start(Path.of(args[1]));
                } catch (IOException | InterruptedException e) {
                    LOG.error("Failed to supervise the game process", e);
                }
            }
            return true;
        }
        return false;
    }

    /// Reads the [ProcessSpec] from `specPath`, creates the process it describes, supervises it
    /// until it exits, and reports the result.
    ///
    /// @param specPath the file containing the [ProcessSpec]
    /// @throws IOException          if the spec file is malformed or the game process cannot be created
    /// @throws InterruptedException if interrupted while waiting for the game process
    public static void start(Path specPath) throws IOException, InterruptedException {
        LOG.info("*** HMCL MONITOR ***");

        // stdout carries the monitor's log output, so the protocol goes over raw stderr
        PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.err), true, MonitorProtocol.CHARSET);

        ProcessSpec spec = JsonUtils.fromJsonFile(specPath, ProcessSpec.class);
        if (spec == null || spec.command == null || spec.command.isEmpty() || spec.encoding == null)
            throw new IOException("Malformed process spec file " + specPath);
        Files.delete(specPath);

        ProcessBuilder pb = new ProcessBuilder(spec.command);
        @Nullable String directory = spec.directory;
        if (directory != null && !directory.isEmpty())
            pb.directory(new File(directory));
        if (spec.environment != null)
            pb.environment().putAll(spec.environment);
        if (spec.inheritStdin)
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

        long fallbackStartTime = System.currentTimeMillis();
        Process game = pb.start();
        long processStartTime = game.info().startInstant().map(Instant::toEpochMilli).orElse(fallbackStartTime);

        LOG.info("Game process created, pid " + game.pid() + ", spec " + specPath);
        protocol.println(MonitorProtocol.pidMessage(game.pid(), processStartTime));

        Path logFile = Files.createTempFile("hmcl-monitor-game-", ".log");

        // The monitor owns the game process, so the regular [ManagedProcess] applies, and the exit
        // classification is performed by the very same [ExitWaiter] the direct launch path uses.
        ManagedProcess gameProcess = new ManagedProcess(game, spec.command);

        // Forward one game output line to the log file, the managed process (feeding the exit
        // classification) and the protocol stream. A broken protocol stream (the launcher exited
        // early) only stops forwarding; the log file keeps collecting the remaining output.
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile, MonitorProtocol.CHARSET)) {
            startMonitors(gameProcess, logWriter, protocol, spec, processStartTime, logFile, specPath);
        }
    }

    static private void startMonitors(ManagedProcess gameProcess, BufferedWriter logWriter, PrintStream protocol,
                                      ProcessSpec spec, long processStartTime, Path logFile, Path specPath) throws InterruptedException {
        AtomicBoolean parentGone = new AtomicBoolean(false);
        class LineSink {
            void accept(String line, boolean isErrorStream) {
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
                if (!parentGone.get()) {
                    protocol.println(MonitorProtocol.logMessage(isErrorStream, line));
                    if (protocol.checkError())
                        parentGone.set(true);
                }
            }
        }

        LineSink sink = new LineSink();

        Charset encoding = Charset.forName(Objects.requireNonNullElse(spec.encoding, Charset.defaultCharset().name()));
        Thread stdoutPump = Lang.thread(new StreamPump(gameProcess.getProcess().getInputStream(),
                line -> sink.accept(line, false), encoding), "monitor-stdout-pump", true);
        Thread stderrPump = Lang.thread(new StreamPump(gameProcess.getProcess().getErrorStream(),
                line -> sink.accept(line, true), encoding), "monitor-stderr-pump", true);
        gameProcess.addRelatedThread(stdoutPump);
        gameProcess.addRelatedThread(stderrPump);

        Thread exitWaiter = Lang.thread(new ExitWaiter(gameProcess, List.of(stdoutPump, stderrPump), (exitCode, exitType) -> {
            LOG.info("Game exited with code " + exitCode + "(" + Integer.toHexString(exitCode) + "), type " + exitType);

            // A present spec file can only mean the main launcher marked a manual cancellation
            // right before destroying the game process (see MonitorGameProcess#stop); report the
            // exit as interrupted like the direct launch path does.
            boolean canceled = readCanceled(specPath);
            ProcessListener.ExitType reportedType = canceled ? ProcessListener.ExitType.INTERRUPTED : exitType;
            if (canceled)
                LOG.info("The game process was canceled by the main launcher, reporting the exit as interrupted");
            protocol.println(MonitorProtocol.exitMessage(exitCode, reportedType));

            runPostExitCommand(spec);

            boolean crashed = reportedType != ProcessListener.ExitType.NORMAL;
            try {
                boolean relaunch = !canceled && (spec.relaunchAlways || (spec.relaunchOnCrash && crashed));

                if (protocol.checkError())
                    parentGone.set(true);

                @Nullable Path resultFile = null;
                if (relaunch && parentGone.get()) {
                    if (crashed)
                        resultFile = writeResult(spec, gameProcess.getPid(), processStartTime, exitCode, reportedType, logFile);
                    relaunch(resultFile);
                }

                // The session log is only consumed through a crash result file; deleting it in
                // every other case keeps it from accumulating in the temporary directory.
                if (resultFile == null) {
                    try {
                        Files.deleteIfExists(logFile);
                    } catch (IOException e) {
                        LOG.warning("Failed to delete the session log file " + logFile, e);
                    }
                }
            } catch (IOException e) {
                LOG.error("Failed to write the monitor result file or relaunch the launcher", e);
            }
        }), "exit-waiter", true);

        // Keep the monitor process alive until the game has exited and been reported; the caller
        // exits the process right after this method returns.
        exitWaiter.join();
    }

    private static void runPostExitCommand(ProcessSpec spec) {
        if (spec.postExitCommand == null || spec.postExitCommand.isEmpty())
            return;
        try {
            // The direct launch path runs the post-exit command in the game directory, which is
            // not necessarily the game process's working directory when version isolation is on.
            ProcessBuilder builder = new ProcessBuilder(spec.postExitCommand);
            if (spec.gameDir != null && !spec.gameDir.isEmpty())
                builder.directory(new File(spec.gameDir));
            else if (spec.directory != null && !spec.directory.isEmpty())
                builder.directory(new File(spec.directory));
            if (spec.environment != null)
                builder.environment().putAll(spec.environment);
            SystemUtils.callExternalProcess(builder);
        } catch (Throwable e) {
            LOG.warning("An Exception happened while running exit command.", e);
        }
    }

    /// Returns whether the main launcher rewrote the spec file to mark the game process as manually
    /// canceled, consuming the rewritten file so that it does not outlive this check.
    private static boolean readCanceled(Path specPath) {
        if (!Files.exists(specPath))
            return false;
        boolean canceled = false;
        try {
            ProcessSpec spec = JsonUtils.fromJsonFile(specPath, ProcessSpec.class);
            canceled = spec != null && spec.canceled;
        } catch (IOException e) {
            LOG.warning("Failed to read the rewritten monitor spec file " + specPath, e);
        } finally {
            try {
                Files.deleteIfExists(specPath);
            } catch (IOException e) {
                LOG.warning("Failed to delete the rewritten monitor spec file " + specPath, e);
            }
        }
        return canceled;
    }

    /// Writes the [ResultSpec] consumed by a relaunched launcher to a temp file.
    private static Path writeResult(ProcessSpec spec, long pid, long processStartTime, int exitCode,
                                    ProcessListener.ExitType exitType, Path logFile) throws IOException {
        Path resultFile = Files.createTempFile("hmcl-monitor-result-", ".json");
        ResultSpec result = new ResultSpec();
        result.pid = pid;
        result.processStartTime = processStartTime;
        result.exitCode = exitCode;
        result.exitType = exitType.name();
        result.logFile = logFile.toString();
        result.instanceId = spec.instanceId;
        result.gameDirectoryId = spec.gameDirectoryId;
        result.versionName = spec.versionName;
        result.gameDir = spec.gameDir;
        result.maxMemory = spec.maxMemory;
        result.javaBinary = spec.javaBinary;
        result.javaVersion = spec.javaVersion;
        result.javaArchitecture = spec.javaArchitecture;
        result.commands = spec.command;
        JsonUtils.writeToJsonFile(resultFile, result);
        return resultFile;
    }

    /// Starts a new HMCL main process. Fire-and-forget; the current monitor process exits right after.
    private static void relaunch(@Nullable Path resultFile) throws IOException {
        Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null) {
            LOG.warning("Failed to find the current HMCL jar, cannot relaunch the launcher");
            return;
        }
        if (resultFile != null) { // crashed
            JavaProcessLauncher.startJava(thisJar, "--crash-report", resultFile.toAbsolutePath().toString());
        } else {
            JavaProcessLauncher.startJava(thisJar);
        }
    }
}
