// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.monitor;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.launch.ManagedProcessAttacher;
import org.jackhuang.hmcl.launch.ProcessListener;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Lightweight monitor to watch game process
@NotNullByDefault
public final class Monitor {
    /// the result of the latest launched game process
    private static @Nullable Monitor.Result result;

    private Monitor() {
    }

    /// @return the [result] of the latest launched game process, or `null` when no game was launched by monitor
    public static @Nullable Monitor.Result getResult() {
        return result;
    }

    /// the latest launched game was crashed
    public static boolean wasCrashed() {
        return result != null && result.crashed;
    }

    /// @return `true` if we should exit, or `false` if we should continue and launch HMCL
    public static boolean processArguments(String[] args) {
        if (args.length != 2 || !args[0].equals("--monitor"))
            return false;
        try {
            return start(Path.of(args[1]));
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to start the game process", e);
            return true;
        }
    }

    /// Launch game process from spec file.
    ///
    /// @return `true` if we should exit, or `false` if we should continue and launch HMCL
    /// @see MonitorProtocol for more detail sending message and reading control with launcher
    public static boolean start(Path specPath) throws IOException, InterruptedException {
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

        // Read commands from launcher while the game is running.
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
            launcherDetached.complete(true); // launcher died
        }, "monitor-control-reader", true);


        // shake hand & send pid
        PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.err), true, MonitorProtocol.CHARSET);
        protocol.println(new MonitorProtocol.MonitorMessage.Pid(gameProcess.pid(), gameProcess.processStartTime()).encode());

        CompletableFuture<Boolean> shouldRelaunch = new CompletableFuture<>();
        ProcessListener listener = new ProcessListener() {
            @Override
            public void onLog(String log, boolean isErrorStream) {
                protocol.println(new MonitorProtocol.MonitorMessage.Log(isErrorStream, log).encode());
            }

            @Override
            public void onExit(int exitCode, ExitType exitType) {
                LOG.info("Game exited with code " + exitCode + "(" + Integer.toHexString(exitCode) + "), type " + exitType);

                // send exit msg to launcher
                ProcessListener.ExitType reportedType = canceled.get() ? ProcessListener.ExitType.INTERRUPTED : exitType;
                protocol.println(new MonitorProtocol.MonitorMessage.Exit(exitCode, reportedType).encode());

                // run post exit command
                if (!spec.postExitCommand.isEmpty())
                    try {
                        ProcessBuilder builder = new ProcessBuilder(spec.postExitCommand).directory(spec.options.getGameDir().toFile());
                        builder.environment().putAll(spec.environment());
                        SystemUtils.callExternalProcess(builder);
                    } catch (Throwable e) {
                        LOG.warning("An Exception happened while running exit command.", e);
                    }

                // Exit message may overtake the launcher's handling of the last LWJGL log.
                // So an explicit decision from launcher is needed.
                boolean detached;
                try {
                    detached = launcherDetached.get(MonitorProtocol.TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    detached = true; // launcher did not respond to our exit message, so regard it as dead
                }

                boolean crashed = reportedType != ProcessListener.ExitType.NORMAL && reportedType != ProcessListener.ExitType.INTERRUPTED;
                result = new Result(gameProcess, reportedType, spec.gameDirectoryId, crashed, spec.options);
                shouldRelaunch.complete(detached && switch (spec.relaunchPolicy) {
                    case ALWAYS -> true;
                    case ON_CRASH -> crashed;
                    case NEVER -> false;
                });
            }
        };
        ManagedProcessAttacher.attachListener(gameProcess, listener, Charset.forName(spec.encoding), null, true);

        return !shouldRelaunch.join();
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
    /// @param process         The game process, which has already exited.
    /// @param exitType        The reported exit type, including [ProcessListener.ExitType#INTERRUPTED] for a recognized cancellation.
    /// @param gameDirectoryId The persistent id of the registered game directory owning the launched instance.
    /// @param crashed         Whether the game crashed.
    /// @param launchOptions   The launch options used to identify the instance and present its crash window.
    @NotNullByDefault
    public record Result(ManagedProcess process, ProcessListener.ExitType exitType, String gameDirectoryId,
                         boolean crashed, LaunchOptions launchOptions) {
    }

    /// One-time game configuration, consumed and deleted before the game starts.
    public record MonitorLaunchSpec(List<String> command, Path directory, Map<String, String> environment,
                                    String encoding, LaunchOptions options,
                                    String gameDirectoryId, List<String> postExitCommand,
                                    RelaunchPolicy relaunchPolicy) {
    }
}
