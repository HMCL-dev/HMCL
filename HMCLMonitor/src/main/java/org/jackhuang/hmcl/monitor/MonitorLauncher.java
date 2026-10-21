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

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.JVMLaunchFailedEvent;
import org.jackhuang.hmcl.event.ProcessExitedAbnormallyEvent;
import org.jackhuang.hmcl.event.ProcessStoppedEvent;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.ProxyOption;
import org.jackhuang.hmcl.game.Renderer;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.*;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.JavaProcessLauncher;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class MonitorLauncher {
    private static final boolean MONITOR_ENABLED = System.getenv("HMCL_USE_HMCLMONITOR") != null;
    private static final Gson gsonForGameOptions = JsonUtils.defaultGsonBuilder()
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return false;
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return ProxyOption.class.isAssignableFrom(clazz) || Renderer.class.isAssignableFrom(clazz);
                }
            }).create();

    private MonitorLauncher() {
    }

    public static boolean isEnabled() {
        return MONITOR_ENABLED;
    }

    public static MonitorGameProcess launchNewMonitor(MonitorLaunchContext context) throws IOException {
        Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null)
            throw new IOException("Failed to find current HMCL location");

        Path specFile = Files.createTempFile("hmcl-monitor-spec-", ".json");
        try (var writer = Files.newBufferedWriter(specFile)) {
            gsonForGameOptions.toJson(new MonitorLaunchSpec(context), writer);
        }

        List<String> commandline = JavaProcessLauncher.buildJavaCommandLine(thisJar,
                "--monitor", specFile.toAbsolutePath().toString());
        LOG.info("Starting monitor process: " + JavaProcessLauncher.maskCommandLine(commandline));

        Process monitor = new ProcessBuilder(commandline).directory(Paths.get("").toAbsolutePath().toFile())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(monitor.getErrorStream(), MonitorProtocol.CHARSET));

        MonitorProtocol.Pid handshake = readHandshake(monitor, reader);
        ProcessHandle gameHandle = ProcessHandle.of(handshake.pid()).orElse(null);
        MonitorGameProcess process = new MonitorGameProcess(gameHandle, context.builder().command(), handshake.processStartTime(), specFile);

        ProcessListener listener = context.listener();
        if (listener != null)
            listener.setProcess(process);

        Lang.thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (listener != null)
                        dispatchProtocolMessage(line, listener, process);
                }
            } catch (IOException e) {
                LOG.error("Failed to read from the HMCL monitor", e);
            }
        }, "hmcl-monitor-reader", true);

        return process;
    }

    /// Dispatches one protocol message received from the monitor.
    private static void dispatchProtocolMessage(String line, ProcessListener listener, MonitorGameProcess process) {
        MonitorProtocol.Message msg;
        try {
            msg = MonitorProtocol.Message.decode(line);
        } catch (IllegalArgumentException e) {
            LOG.warning("Unexpected message from HMCL monitor: " + line, e);
            return;
        }

        if (msg instanceof MonitorProtocol.Log log) {
            listener.onLog(log.line(), log.isErrorStream());
        } else if (msg instanceof MonitorProtocol.Exit exit) {
            if (exit.exitType() == ProcessListener.ExitType.JVM_ERROR)
                EventBus.EVENT_BUS.fireEvent(new JVMLaunchFailedEvent(MonitorLauncher.class, process));
            else if (exit.exitType() != ProcessListener.ExitType.NORMAL)
                EventBus.EVENT_BUS.fireEvent(new ProcessExitedAbnormallyEvent(MonitorLauncher.class, process));
            EventBus.EVENT_BUS.fireEvent(new ProcessStoppedEvent(MonitorLauncher.class, process));

            listener.onExit(exit.exitCode(), exit.exitType());
        }
    }

    /// Reads the handshake message `P\t<pid>\t<startTime>` from the monitor.
    ///
    /// @throws IOException if the monitor exits or fails before the handshake, sends a malformed
    ///                     message, or does not answer within [MonitorProtocol#HANDSHAKE_TIMEOUT]
    private static MonitorProtocol.Pid readHandshake(Process monitor, BufferedReader reader) throws IOException {
        CompletableFuture<MonitorProtocol.Pid> future = new CompletableFuture<>();
        Thread waiter = Lang.thread(() -> {
            try {
                String line = reader.readLine();
                if (line == null)
                    throw new IOException("The HMCL monitor exited before the handshake");
                if (MonitorProtocol.Message.decode(line) instanceof MonitorProtocol.Pid pid) {
                    future.complete(pid);
                    return;
                }
                throw new IOException("Unexpected handshake message from HMCL monitor: " + line);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, "hmcl-monitor-handshake", true);

        boolean handshakeCompleted = false;
        try {
            MonitorProtocol.Pid handshake = future.get(MonitorProtocol.HANDSHAKE_TIMEOUT, TimeUnit.MILLISECONDS);
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
            if (!handshakeCompleted)
                monitor.destroy();
        }
    }

    public enum RelaunchPolicy {
        /// Never continue into the launcher UI after the game exits.
        NEVER,
        /// Continue into the launcher UI after a non-normal exit if the protocol stream has failed.
        ON_CRASH,
        /// Continue into the launcher UI after any exit if the protocol stream has failed.
        ALWAYS
    }

    public record MonitorLaunchContext(
            ProcessBuilder builder,
            @Nullable ProcessListener listener,
            Charset encoding,
            LaunchOptions options,
            String gameDirectoryId,
            List<String> postExitCommand,
            RelaunchPolicy relaunchPolicy) {
    }

    @NotNullByDefault
    public record MonitorLaunchSpec(List<String> command, String directory, Map<String, String> environment,
                                    String encoding, boolean inheritStdin, @Nullable List<String> postExitCommand,
                                    RelaunchPolicy relaunchPolicy, String gameDirectoryId, LaunchOptions launchOptions) {
        public MonitorLaunchSpec(MonitorLaunchContext context) {
            this(new ArrayList<>(context.builder().command()),
                    context.builder().directory().getAbsolutePath(),
                    context.builder().environment(),
                    context.encoding().name(),
                    context.listener() == null,
                    context.postExitCommand(),
                    context.relaunchPolicy(),
                    context.gameDirectoryId(),
                    context.options());
        }
    }
}
