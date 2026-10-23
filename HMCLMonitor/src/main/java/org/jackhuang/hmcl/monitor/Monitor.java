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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class Monitor {
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

    /// Supervises the game and waits for the launcher's exit decision before restoring its UI.
    public static void start(Path specPath) throws IOException, InterruptedException {
        LOG.info("*** HMCL MONITOR ***");

        // read spec
        MonitorLaunchSpec spec;
        try {
            spec = JsonUtils.fromNonNullJson(Files.readString(specPath), MonitorLaunchSpec.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed process spec file " + specPath, e);
        }
        Files.deleteIfExists(specPath);

        // start game process
        ProcessBuilder pb = new ProcessBuilder(spec.command).directory(spec.directory.toFile());
        pb.environment().putAll(spec.environment);
        ManagedProcess gameProcess = new ManagedProcess(pb);
        LOG.info("Game process created, pid " + gameProcess.pid());

        // Read commands while the game is running. The monitor must record CANCEL
        // before destroying the game so its exit cannot race ahead of cancellation.
        AtomicBoolean canceled = new AtomicBoolean();
        CompletableFuture<Boolean> launcherDetached = new CompletableFuture<>();
        Lang.thread(() -> {
            try {
                MonitorProtocol.readLauncherControl(
                        new BufferedReader(new InputStreamReader(System.in, MonitorProtocol.CHARSET)), (command) -> {
                            switch (command) {
                                case CANCEL -> {
                                    canceled.set(true);
                                    gameProcess.getProcess().destroy();
                                }
                                case DETACH -> launcherDetached.complete(true);
                                case HANDLED -> launcherDetached.complete(false);
                            }
                        });
            } catch (IOException e) {
                LOG.warning("Lost launcher control channel; taking over exit handling", e);
            }
            launcherDetached.complete(true);
        }, "monitor-control-reader", true);


        // shake hand
        PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.err), true, MonitorProtocol.CHARSET);
        protocol.println(new MonitorProtocol.MonitorMessage.Pid(gameProcess.pid(), gameProcess.processStartTime()).encode());

        // pump game io
        BiConsumer<String, Boolean> sink = (line, isErrorStream) -> {
            gameProcess.addLine(line);
            protocol.println(new MonitorProtocol.MonitorMessage.Log(isErrorStream, line).encode());
        };

        Charset encoding = Charset.forName(spec.encoding);
        Thread stdoutPump = Lang.thread(new StreamPump(gameProcess.getProcess().getInputStream(),
                line -> sink.accept(line, false), encoding), "monitor-stdout-pump", true);
        Thread stderrPump = Lang.thread(new StreamPump(gameProcess.getProcess().getErrorStream(),
                line -> sink.accept(line, true), encoding), "monitor-stderr-pump", true);
        gameProcess.addRelatedThread(stdoutPump);
        gameProcess.addRelatedThread(stderrPump);

        // wait for stop
        Lang.thread(new ExitWaiter(gameProcess, List.of(stdoutPump, stderrPump), (exitCode, exitType) -> {
            LOG.info("Game exited with code " + exitCode + "(" + Integer.toHexString(exitCode) + "), type " + exitType);

            ProcessListener.ExitType reportedType = canceled.get() ? ProcessListener.ExitType.INTERRUPTED : exitType;
            protocol.println(new MonitorProtocol.MonitorMessage.Exit(exitCode, reportedType).encode());

            crashed = reportedType != ProcessListener.ExitType.NORMAL && reportedType != ProcessListener.ExitType.INTERRUPTED;
            // E may overtake the launcher's handling of the last LWJGL log. Wait for
            // an explicit decision instead of sampling whether its stderr pipe broke.
            shouldRelaunch = launcherDetached.join() && switch (spec.relaunchPolicy) {
                case ALWAYS -> true;
                case ON_CRASH -> crashed;
                case NEVER -> false;
            };
            result = new Result(gameProcess, reportedType, spec.gameDirectoryId, spec.options);

            // run post exit command
            if (!spec.postExitCommand.isEmpty())
                try {
                    ProcessBuilder builder = new ProcessBuilder(spec.postExitCommand).directory(spec.options.getGameDir().toFile());
                    builder.environment().putAll(spec.environment());
                    SystemUtils.callExternalProcess(builder);
                } catch (Throwable e) {
                    LOG.warning("An Exception happened while running exit command.", e);
                }
        }), "exit-waiter", true).join();
    }

    public enum RelaunchPolicy {
        /// Never continue into the launcher UI after the game exits.
        NEVER,
        /// Continue into the launcher UI after a non-normal exit if the launcher relinquishes exit handling.
        ON_CRASH,
        /// Continue into the launcher UI after any exit if the launcher relinquishes exit handling.
        ALWAYS
    }

    /// launch result of the latest game process
    ///
    /// @param process         The supervised game process, which has already exited.
    /// @param exitType        The reported exit type, including `INTERRUPTED` for a recognized cancellation.
    /// @param gameDirectoryId The persistent id of the registered game directory owning the launched instance.
    /// @param launchOptions   The launch options used to identify the instance and present its crash window.
    @NotNullByDefault
    public record Result(ManagedProcess process, ProcessListener.ExitType exitType, String gameDirectoryId, LaunchOptions launchOptions) {
    }

    /// One-time game configuration, consumed and deleted before the game starts.
    public record MonitorLaunchSpec(List<String> command, Path directory, Map<String, String> environment,
                                    String encoding, LaunchOptions options,
                                    String gameDirectoryId, List<String> postExitCommand,
                                    RelaunchPolicy relaunchPolicy) {
    }
}
