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
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.ProxyOption;
import org.jackhuang.hmcl.game.Renderer;
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

@NotNullByDefault
public final class MonitorLauncher {
    public static final boolean MONITOR_ENABLED = System.getenv("HMCL_USE_HMCLMONITOR") != null;
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

    public static MonitorGameProcess launchNewMonitor(List<String> commandLine,
                                                      Map<String, String> envVars,
                                                      Charset encoding,
                                                      @Nullable ProcessListener listener,
                                                      HMCLGameInstance hmclGameInstance,
                                                      LaunchOptions options) throws IOException {
        Path specFile = writeSpecFile(commandLine, envVars, encoding, listener, hmclGameInstance, options);
        return startProcess(commandLine, listener, specFile);
    }

    private static Path writeSpecFile(List<String> commandLine,
                                      Map<String, String> envVars,
                                      Charset encoding,
                                      @Nullable ProcessListener listener,
                                      HMCLGameInstance hmclGameInstance,
                                      LaunchOptions options) throws IOException {
        Path runDirectory = hmclGameInstance.getRunDirectory().toAbsolutePath();
        String gameDirectoryId = hmclGameInstance.getRepository().getGameDirectory().getId().toString();

        Map<String, String> gameEnv = new HashMap<>();
        Path appdata = options.getGameDir().toAbsolutePath().getParent();
        if (appdata != null) gameEnv.put("APPDATA", appdata.toString());
        gameEnv.putAll(envVars);

        List<String> postExitCommand = StringUtils.tokenize(options.getPostExitCommand(), envVars);

        GameSettings.Effective setting = hmclGameInstance.getEffectiveSettings();
        LauncherVisibility x = setting.getInheritable(GameSettings::launcherVisibilityProperty);
        boolean showLogs = setting.getInheritable(GameSettings::showLogsProperty);
        Monitor.RelaunchPolicy relaunchPolicy = showLogs
                ? Monitor.RelaunchPolicy.NEVER
                : switch (x) {
            case HIDE_AND_REOPEN -> Monitor.RelaunchPolicy.ALWAYS;
            case HIDE -> Monitor.RelaunchPolicy.ON_CRASH;
            default -> Monitor.RelaunchPolicy.NEVER;
        };

        Path specFile = Files.createTempFile("hmcl-monitor-spec-", ".json");
        try (var writer = Files.newBufferedWriter(specFile)) {
            gsonForGameOptions.toJson(new Monitor.MonitorLaunchSpec(
                    commandLine,
                    runDirectory,
                    gameEnv,
                    listener == null,
                    encoding.name(),
                    options,
                    gameDirectoryId,
                    postExitCommand,
                    relaunchPolicy
            ), writer);
        }
        return specFile;
    }

    private static MonitorGameProcess startProcess(List<String> commandLine, @Nullable ProcessListener listener, Path specFile) throws IOException {
        @Nullable Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null)
            throw new IOException("Failed to find current HMCL location");

        Process monitor = JavaProcessLauncher.buildJava(thisJar, "--monitor", specFile.toAbsolutePath().toString())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.PIPE).start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(monitor.getErrorStream(), MonitorProtocol.CHARSET));

        MonitorProtocol.Pid handshake = MonitorProtocol.readHandshake(reader, () -> {
            monitor.destroy();
            try {
                Files.deleteIfExists(specFile);
            } catch (IOException e) {
                LOG.warning("Failed to clean specFile" + e);
            }
        });
        ProcessHandle gameHandle = ProcessHandle.of(handshake.pid()).orElse(null);
        MonitorGameProcess process = new MonitorGameProcess(gameHandle, commandLine, handshake.processStartTime(), specFile);

        if (listener != null)
            listener.setProcess(process);

        Lang.thread(() -> {
            try {
                MonitorProtocol.readAll(reader, msg -> {
                    if (listener == null)
                        return;

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
                }, (line, e) -> LOG.warning("Unexpected message from HMCL monitor: " + line, e));
            } catch (IOException e) {
                LOG.error("IOException occurred during communicating with HMCL monitor.", e);
            }
        }, "hmcl-monitor-reader", true);
        return process;
    }
}
