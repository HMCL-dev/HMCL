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
import org.jackhuang.hmcl.game.LaunchOptions;
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

@NotNullByDefault
public final class Monitor {
    public static final String CANCELED = "CANCELED";
    private static boolean shouldRelaunch = false;
    private static boolean crashed = false;
    private static @Nullable Monitor.Result result;

    private Monitor() {
    }

    public static Monitor.Result getResult() throws NullPointerException {
        return Objects.requireNonNull(result);
    }

    public static boolean wasCrashed() {
        return crashed;
    }

    /// @return `true` if should exit, or `false` if should continue and launch HMCL
    public static boolean processArguments(String[] args) {
        if (args.length != 2 || !args[0].equals("--monitor"))
            return false;
        try {
            start(Path.of(args[1]));
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to supervise the game process", e);
        }
        return !shouldRelaunch;
    }

    public static void start(Path specPath) throws IOException, InterruptedException {
        LOG.info("*** HMCL MONITOR ***");

        // read spec
        MonitorLauncher.MonitorLaunchSpec spec;
        try {
            spec = JsonUtils.fromNonNullJson(Files.readString(specPath), MonitorLauncher.MonitorLaunchSpec.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed process spec file " + specPath, e);
        }
        Files.deleteIfExists(specPath);

        // start game process
        ProcessBuilder pb = new ProcessBuilder(spec.command()).directory(new File(spec.directory()));
        pb.environment().putAll(spec.environment());
        if (spec.inheritStdin())
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        ManagedProcess gameProcess = new ManagedProcess(pb.start(), spec.command());

        LOG.info("Game process created, pid " + gameProcess.getPid() + ", spec " + specPath);
        MonitorProtocol.pidMessage(gameProcess.getPid(), gameProcess.getProcessStartTime());

        BiConsumer<String, Boolean> sink = (line, isErrorStream) -> {
            gameProcess.addLine(line);
            MonitorProtocol.logMessage(isErrorStream, line);
        };
        Charset encoding = Charset.forName(spec.encoding());
        Thread stdoutPump = Lang.thread(new StreamPump(gameProcess.getProcess().getInputStream(),
                line -> sink.accept(line, false), encoding), "monitor-stdout-pump", true);
        Thread stderrPump = Lang.thread(new StreamPump(gameProcess.getProcess().getErrorStream(),
                line -> sink.accept(line, true), encoding), "monitor-stderr-pump", true);
        gameProcess.addRelatedThread(stdoutPump);
        gameProcess.addRelatedThread(stderrPump);

        // wait for stop
        Lang.thread(new ExitWaiter(gameProcess, List.of(stdoutPump, stderrPump), (exitCode, exitType) -> {
            LOG.info("Game exited with code " + exitCode + "(" + Integer.toHexString(exitCode) + "), type " + exitType);

            // reread spec to check canceled
            ProcessListener.ExitType reportedType = exitType;
            try {
                if (Files.exists(specPath) && Files.readString(specPath).startsWith(CANCELED))
                    reportedType = ProcessListener.ExitType.INTERRUPTED;
                Files.deleteIfExists(specPath);
            } catch (IOException e1) {
                LOG.warning("Failed to check whether to cancel " + specPath, e1);
            }
            MonitorProtocol.exitMessage(exitCode, reportedType);

            crashed = reportedType != ProcessListener.ExitType.NORMAL;
            shouldRelaunch = MonitorProtocol.parentGone() && switch (spec.relaunchPolicy()) {
                case ALWAYS -> true;
                case ON_CRASH -> crashed;
                case NEVER -> false;
            };
            result = new Result(gameProcess, reportedType, spec.gameDirectoryId(), spec.command(), spec.launchOptions());

            // run post exit command
            if (spec.postExitCommand() != null && !spec.postExitCommand().isEmpty()) {
                try {
                    ProcessBuilder builder = new ProcessBuilder(spec.postExitCommand()).directory(spec.launchOptions().getGameDir().toFile());
                    builder.environment().putAll(spec.environment());
                    SystemUtils.callExternalProcess(builder);
                } catch (Throwable e) {
                    LOG.warning("An Exception happened while running exit command.", e);
                }
            }
        }), "exit-waiter", true).join();
    }

    /// The in-memory result of a supervised game process, retained when the monitor JVM continues
    /// into the launcher UI to present the game crash window.
    ///
    /// @param process         The supervised game process, which has already exited.
    /// @param exitType        The reported exit type, including `INTERRUPTED` for a recognized cancellation.
    /// @param gameDirectoryId The persistent id of the registered game directory owning the launched
    ///                        instance, or `null` when unknown.
    /// @param commands        The command line of the game process, or `null` when unknown.
    /// @param launchOptions   The launch options used to identify the instance and present its crash window.
    @NotNullByDefault
    public record Result(ManagedProcess process, ProcessListener.ExitType exitType,
                         String gameDirectoryId, @Nullable List<String> commands,
                         LaunchOptions launchOptions) {
    }
}
