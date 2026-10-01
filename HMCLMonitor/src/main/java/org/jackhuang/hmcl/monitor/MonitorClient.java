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

import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.JVMLaunchFailedEvent;
import org.jackhuang.hmcl.event.ProcessExitedAbnormallyEvent;
import org.jackhuang.hmcl.event.ProcessStoppedEvent;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.launch.DefaultLauncher.MonitorLaunchContext;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.JavaProcessLauncher;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The main launcher process role of the monitor feature: launching the game process through a
/// freshly spawned monitor process and consuming its protocol stream.
@NotNullByDefault
public final class MonitorClient {

    /// Whether the monitor feature is enabled through the {@code HMCL_USE_HMCLMONITOR} environment variable.
    private static final boolean DAEMON_ENABLED = System.getenv("HMCL_USE_HMCLMONITOR") != null;

    private MonitorClient() {
    }

    /// Returns whether the monitor feature is enabled.
    public static boolean isEnabled() {
        return DAEMON_ENABLED;
    }

    /// Launches the game process via a new HMCL monitor process and returns a view of it.
    ///
    /// <p>Blocks until the monitor has created the game process and reported its id back; game
    /// output and the exit event are dispatched to the [listener][MonitorLaunchContext#listener()]
    /// asynchronously afterward.
    ///
    /// @param context the launch context prepared by [org.jackhuang.hmcl.launch.DefaultLauncher]
    /// @return a view of the game process managed by the monitor
    /// @throws IOException if the monitor process could not be started, failed to create the game
    ///                     process, or did not complete the handshake in time
    public static MonitorGameProcess launchNewMonitor(MonitorLaunchContext context) throws IOException {
        Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null)
            throw new IOException("Failed to find current HMCL location");

        LaunchOptions options = context.options();

        Path specFile = Files.createTempFile("hmcl-monitor-spec-", ".json");
        JsonUtils.writeToJsonFile(specFile, buildSpec(context, options));

        Process daemon = startDaemonProcess(thisJar, specFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader(daemon.getErrorStream(), MonitorProtocol.CHARSET));

        String[] handshake = readHandshake(daemon, reader);
        long pid;
        long processStartTime;
        try {
            pid = Long.parseLong(handshake[1]);
            processStartTime = Long.parseLong(handshake[2]);
        } catch (NumberFormatException e) {
            throw new IOException("Malformed handshake message from HMCL monitor: " + String.join("\t", handshake), e);
        }

        ProcessHandle gameHandle = ProcessHandle.of(pid).orElse(null);
        MonitorGameProcess process = new MonitorGameProcess(gameHandle, context.builder().command(), processStartTime, specFile);

        ProcessListener listener = context.listener();
        if (listener != null)
            listener.setProcess(process);

        // The reader thread keeps draining the protocol stream until the monitor exits, so that the
        // exit message of a canceled launch is still dispatched and classified as interrupted. It
        // must drain even without a listener, since an undrained pipe would eventually block the
        // monitor.
        Lang.thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    dispatchProtocolMessage(line, listener, process);
                }
            } catch (IOException e) {
                LOG.error("Failed to read from the HMCL monitor", e);
            }
        }, "hmcl-monitor-reader", true);

        return process;
    }

    /// Builds the [ProcessSpec] to exchange with the monitor as JSON.
    private static ProcessSpec buildSpec(MonitorLaunchContext context, LaunchOptions options) {
        ProcessBuilder builder = context.builder();
        ProcessSpec spec = new ProcessSpec();
        spec.command = new ArrayList<>(builder.command());
        if (builder.directory() != null)
            spec.directory = builder.directory().getAbsolutePath();
        spec.environment = new LinkedHashMap<>(builder.environment());
        spec.encoding = context.encoding().name();
        spec.inheritStdin = context.listener() == null;
        spec.postExitCommand = context.postExitCommand();
        MonitorLaunchContext.RelaunchPolicy relaunchPolicy = context.relaunchPolicy();
        spec.relaunchAlways = relaunchPolicy == MonitorLaunchContext.RelaunchPolicy.ALWAYS;
        spec.relaunchOnCrash = relaunchPolicy == MonitorLaunchContext.RelaunchPolicy.ON_CRASH;
        if (options.getInstanceId() != null)
            spec.instanceId = options.getInstanceId().toString();
        spec.versionName = options.getVersionName();
        if (options.getGameDir() != null)
            spec.gameDir = options.getGameDir().toString();
        spec.maxMemory = options.getMaxMemory();
        JavaRuntime java = options.getJava();
        if (java != null) {
            spec.javaBinary = java.getBinary().toString();
            spec.javaVersion = java.getVersion();
            spec.javaArchitecture = java.getArchitecture().name();
        }
        return spec;
    }

    /// Dispatches one protocol message received from the monitor.
    private static void dispatchProtocolMessage(String line, @Nullable ProcessListener listener, MonitorGameProcess process) {
        String[] parts = line.split("\t", 3);
        switch (parts[0]) {
            case MonitorProtocol.TAG_LOG -> {
                String log = parts.length > 2 ? parts[2] : "";
                if (listener != null)
                    listener.onLog(log, "1".equals(parts[1]));
            }
            case MonitorProtocol.TAG_EXIT -> {
                if (listener != null && parts.length >= 3) {
                    try {
                        int exitCode = Integer.parseInt(parts[1]);
                        ProcessListener.ExitType exitType = ProcessListener.ExitType.valueOf(parts[2]);

                        if (exitType == ProcessListener.ExitType.JVM_ERROR)
                            EventBus.EVENT_BUS.fireEvent(new JVMLaunchFailedEvent(MonitorClient.class, process));
                        else if (exitType != ProcessListener.ExitType.NORMAL)
                            EventBus.EVENT_BUS.fireEvent(new ProcessExitedAbnormallyEvent(MonitorClient.class, process));
                        EventBus.EVENT_BUS.fireEvent(new ProcessStoppedEvent(MonitorClient.class, process));

                        listener.onExit(exitCode, exitType);
                    } catch (IllegalArgumentException e) {
                        LOG.warning("Malformed exit message from HMCL monitor: " + line, e);
                    }
                }
            }
            default -> LOG.warning("Unknown protocol message from HMCL monitor: " + line);
        }
    }

    /// Reads the handshake message `P\t<pid>\t<startTime>` from the monitor.
    ///
    /// @throws IOException if the monitor exits or fails before the handshake, sends a malformed
    ///                     message, or does not answer within [MonitorProtocol#HANDSHAKE_TIMEOUT]
    private static String[] readHandshake(Process daemon, BufferedReader reader) throws IOException {
        CompletableFuture<String[]> future = new CompletableFuture<>();
        Thread waiter = Lang.thread(() -> {
            try {
                String line = reader.readLine();
                if (line == null) {
                    future.completeExceptionally(new IOException("The HMCL monitor exited before the handshake"));
                    return;
                }
                String[] parts = line.split("\t");
                if (!MonitorProtocol.TAG_PID.equals(parts[0]) || parts.length < 3) {
                    future.completeExceptionally(new IOException("Unexpected handshake message from HMCL monitor: " + line));
                } else {
                    future.complete(parts);
                }
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        }, "hmcl-monitor-handshake", true);

        boolean handshakeCompleted = false;
        try {
            String[] handshake = future.get(MonitorProtocol.HANDSHAKE_TIMEOUT, TimeUnit.MILLISECONDS);
            handshakeCompleted = true;
            return handshake;
        } catch (TimeoutException e) {
            throw new IOException("Timed out waiting for the HMCL monitor handshake", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the HMCL monitor handshake", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException(cause.getMessage(), cause);
        } finally {
            waiter.interrupt();
            // The monitor must not outlive a failed handshake: it would create and supervise the
            // game process on its own, leaving behind an orphaned game, e.g. when the user cancels
            // the launch while the handshake is still pending.
            if (!handshakeCompleted)
                daemon.destroy();
        }
    }

    /// Starts the monitor process. Its stderr carries the protocol stream and is therefore piped;
    /// its stdout, where the monitor's log output goes, is inherited for diagnostics.
    private static Process startDaemonProcess(Path thisJar, Path specFile) throws IOException {
        List<String> commandline = JavaProcessLauncher.buildJavaCommandLine(thisJar,
                "--monitor", specFile.toAbsolutePath().toString());
        LOG.info("Starting monitor process: " + JavaProcessLauncher.maskCommandLine(commandline));
        return new ProcessBuilder(commandline)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
    }
}
