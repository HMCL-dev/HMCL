// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.monitor;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.LauncherVisibility;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Launch HMCL Monitor Process
///
/// @see launchNewMonitor
@NotNullByDefault
public final class MonitorLauncher {
    /// Whether the monitor-based game launch is enabled via the `HMCL_USE_HMCLMONITOR` environment variable.
    public static final boolean MONITOR_ENABLED = System.getenv("HMCL_USE_HMCLMONITOR") != null;
    /// fix problem serializing [LaunchOptions] (interfaces cannot be serialized easily, adapters needed)
    private static final Gson gsonForGameOptions = JsonUtils.defaultGsonBuilder()
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return false;
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return ProxyOption.class.isAssignableFrom(clazz)
                            || Renderer.class.isAssignableFrom(clazz)
                            || QuickPlayOption.class.isAssignableFrom(clazz);
                }
            }).create();

    private MonitorLauncher() {
    }

    /// Maps launcher visibility and log-window preference to the monitor relaunch policy.
    public static Monitor.RelaunchPolicy relaunchPolicy(LauncherVisibility visibility, boolean showLogs) {
        if (showLogs)
            return Monitor.RelaunchPolicy.NEVER;
        return switch (visibility) {
            case HIDE_AND_REOPEN -> Monitor.RelaunchPolicy.ALWAYS;
            case HIDE -> Monitor.RelaunchPolicy.ON_CRASH;
            default -> Monitor.RelaunchPolicy.NEVER;
        };
    }

    /// start a new monitor and use it to launch game process
    ///
    /// @return the game process monitor launched
    /// @see Monitor
    public static MonitorGameProcess launchNewMonitor(List<String> commandLine,
                                                      Map<String, String> envVars,
                                                      Charset encoding,
                                                      @Nullable ProcessListener listener,
                                                      HMCLGameInstance hmclGameInstance,
                                                      LaunchOptions options) throws IOException {
        Path specFile = writeSpecFile(commandLine, envVars, encoding, hmclGameInstance, options);
        return startProcess(commandLine, listener, specFile);
    }

    /// Writes the one-time launch specification
    private static Path writeSpecFile(List<String> commandLine,
                                      Map<String, String> envVars,
                                      Charset encoding,
                                      HMCLGameInstance hmclGameInstance,
                                      LaunchOptions options) throws IOException {
        Path runDirectory = hmclGameInstance.getRunDirectory().toAbsolutePath();

        Map<String, String> gameEnv = new HashMap<>();
        @Nullable Path appdata = options.getGameDir().toAbsolutePath().getParent();
        if (appdata != null) gameEnv.put("APPDATA", appdata.toString());
        gameEnv.putAll(envVars);

        String gameDirectoryId = hmclGameInstance.getRepository().getGameDirectory().getId().toString();
        List<String> postExitCommand = StringUtils.tokenize(options.getPostExitCommand(), envVars);

        GameSettings.Effective setting = hmclGameInstance.getEffectiveSettings();
        Monitor.RelaunchPolicy relaunchPolicy = relaunchPolicy(
                setting.getInheritable(GameSettings::launcherVisibilityProperty),
                setting.getInheritable(GameSettings::showLogsProperty));

        Path specFile = Files.createTempFile("hmcl-monitor-spec-", ".json");
        try (var writer = Files.newBufferedWriter(specFile)) {
            gsonForGameOptions.toJson(new Monitor.MonitorLaunchSpec(
                    commandLine,
                    runDirectory,
                    gameEnv,
                    encoding.name(),
                    options,
                    gameDirectoryId,
                    postExitCommand,
                    relaunchPolicy
            ), writer);
        }
        return specFile;
    }

    /// Starts the monitor and attach listener
    ///
    /// @see MonitorProtocol for more detail reading message from monitor
    private static MonitorGameProcess startProcess(List<String> commandLine, @Nullable ProcessListener listener, Path specFile) throws IOException {
        @Nullable Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null)
            throw new IOException("Failed to find current HMCL location");

        Process monitor = JavaProcessLauncher.buildJava(thisJar, "--monitor", specFile.toAbsolutePath().toString())
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.PIPE).start();

        // read handshake (pid message) from monitor
        BufferedReader reader = new BufferedReader(new InputStreamReader(monitor.getErrorStream(), MonitorProtocol.CHARSET));
        MonitorProtocol.MonitorMessage.Pid handshake = MonitorProtocol.readHandshake(reader, () -> { // clean up when handshake failed
            monitor.destroy();
            try {
                Files.deleteIfExists(specFile);
            } catch (IOException e) {
                LOG.warning("Failed to clean specFile" + e);
            }
        });

        // get the game process handle from pid message
        @Nullable ProcessHandle gameHandle = ProcessHandle.of(handshake.pid()).orElse(null);
        MonitorGameProcess process = new MonitorGameProcess(gameHandle, commandLine, handshake.processStartTime(), monitor);

        if (listener != null)
            listener.setProcess(process); // attach listener
        else
            process.detach(); // we no longer need any message from monitor

        Lang.thread(() -> {
            try (reader) {
                MonitorProtocol.readMonitorMessage(reader, msg -> {
                    if (listener == null)
                        return;

                    if (msg instanceof MonitorProtocol.MonitorMessage.Log log)
                        listener.onLog(log.line(), log.isErrorStream());
                    else if (msg instanceof MonitorProtocol.MonitorMessage.Exit exit)
                        process.handleExit(() -> listener.onExit(exit.exitCode(), exit.exitType()));
                });
            } catch (IOException e) {
                LOG.error("IOException occurred during communicating with HMCL monitor.", e);
            }
            if (!process.isAttached()) { // monitor died while we still owned exit handling
                LOG.error("Monitor exited without sending game process exiting message, handling exit ourselves. Game exit info will be unavailable.");
                ProcessHandle h = process.handle();
                if (h != null && listener != null)
                    h.onExit().thenRun(() -> listener.onExit(-1, ProcessListener.ExitType.APPLICATION_ERROR));
            }
        }, "hmcl-monitor-reader", true);
        return process;
    }
}
