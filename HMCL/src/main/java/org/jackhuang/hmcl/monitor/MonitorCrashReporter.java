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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.jackhuang.hmcl.game.DefaultGameRepositorySnapshot;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Log;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.GameCrashWindow;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static javafx.application.Platform.runLater;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The relaunched launcher role of the monitor feature: presenting the crash window of a supervised
/// game process, reusing the regular JavaFX user interface.
@NotNullByDefault
public final class MonitorCrashReporter {

    private MonitorCrashReporter() {
    }

    /// Handles the `--crash-report` argument of a launcher process relaunched by the monitor.
    ///
    /// @param args the process arguments
    /// @return the path of the monitor result file to present, or `null` if the arguments do not
    ///         request a crash report
    public static @Nullable Path processArguments(String[] args) {
        if (args.length == 2 && args[0].equals("--crash-report"))
            return Path.of(args[1]);
        return null;
    }

    /// Shows the game crash window for the given monitor result file.
    ///
    /// <p>Must be called on the JavaFX application thread after the main window has been set up.
    /// The result file is deleted once it has been consumed. When the launched instance cannot be
    /// resolved even after every game repository has finished loading, the crash window is skipped;
    /// the session log file remains on disk for inspection.
    ///
    /// @param resultFile the result file written by the monitor
    public static void show(Path resultFile) {
        ResultSpec result;
        try {
            result = JsonUtils.fromJsonFile(resultFile, ResultSpec.class);
        } catch (IOException e) {
            LOG.warning("Failed to read the monitor result file " + resultFile, e);
            return;
        }
        try {
            Files.deleteIfExists(resultFile);
        } catch (IOException e) {
            LOG.warning("Failed to delete the monitor result file " + resultFile, e);
        }
        if (result == null)
            return;

        List<HMCLGameRepository> repositories = GameDirectoryManager.getOrCreateAllRepositories();
        HMCLGameInstance instance = findInstance(repositories, result);
        if (instance != null) {
            presentCrashWindow(result, instance);
            return;
        }

        // The relaunched launcher shows its main window before the game repositories have finished
        // loading their snapshots asynchronously, so the instance may not be resolvable yet; wait
        // for the pending loads and try again when each repository publishes its snapshot.
        List<HMCLGameRepository> pending = repositories.stream()
                .filter(repository -> !repository.isLoaded())
                .toList();
        if (pending.isEmpty()) {
            LOG.warning("Cannot resolve the launched instance " + result.instanceId + " ("
                    + describeRepositories(repositories) + "), skip showing the crash window."
                    + " The session log is at " + result.logFile);
            return;
        }

        LOG.info("Waiting for " + pending.size() + " game repositories to load before showing the crash window");
        AtomicInteger pendingCount = new AtomicInteger(pending.size());
        AtomicBoolean handled = new AtomicBoolean(false);
        for (HMCLGameRepository repository : pending) {
            ChangeListener<DefaultGameRepositorySnapshot> listener = new ChangeListener<>() {
                @Override
                public void changed(ObservableValue<? extends DefaultGameRepositorySnapshot> observable,
                                    DefaultGameRepositorySnapshot oldValue, DefaultGameRepositorySnapshot newValue) {
                    repository.snapshotProperty().removeListener(this);
                    if (handled.get())
                        return;

                    HMCLGameInstance resolved = findInstance(repositories, result);
                    if (resolved != null) {
                        if (handled.compareAndSet(false, true))
                            runLater(() -> presentCrashWindow(result, resolved));
                    } else if (pendingCount.decrementAndGet() == 0 && handled.compareAndSet(false, true)) {
                        LOG.warning("Cannot resolve the launched instance " + result.instanceId + " ("
                                + describeRepositories(repositories) + "), skip showing the crash window."
                                + " The session log is at " + result.logFile);
                    }
                }
            };
            repository.snapshotProperty().addListener(listener);
        }
    }

    /// Builds and shows the crash window from the monitor result and the resolved instance.
    private static void presentCrashWindow(ResultSpec result, HMCLGameInstance instance) {
        try {
            List<String> commands = result.commands != null ? result.commands : List.of();
            ProcessHandle gameHandle = result.pid > 0 ? ProcessHandle.of(result.pid).orElse(null) : null;
            MonitorGameProcess process = new MonitorGameProcess(gameHandle, commands, result.processStartTime);

            ProcessListener.ExitType exitType = ProcessListener.ExitType.APPLICATION_ERROR;
            if (result.exitType != null) {
                try {
                    exitType = ProcessListener.ExitType.valueOf(result.exitType);
                } catch (IllegalArgumentException e) {
                    LOG.warning("Unknown exit type in the monitor result file: " + result.exitType);
                }
            }

            LaunchOptions launchOptions = rebuildLaunchOptions(result);
            List<Log> logs = readSessionLogs(result.logFile);
            if (result.logFile != null) {
                Files.delete(Path.of(result.logFile));
            }

            new GameCrashWindow(process, exitType, instance, launchOptions, logs).show();
        } catch (Throwable e) {
            LOG.warning("Failed to show the crash window for the relaunched launcher", e);
        }
    }

    /// Describes the given repositories with their loaded instance counts, for diagnostics.
    private static String describeRepositories(List<HMCLGameRepository> repositories) {
        return repositories.stream()
                .map(repository -> repository.getSnapshot().getInstanceCount() + " instances, loaded="
                        + repository.isLoaded())
                .collect(Collectors.joining("; ", "[", "]"));
    }

    /// Rebuilds a [LaunchOptions] carrying the display-relevant fields recorded by the monitor.
    ///
    /// The [JavaRuntime] is guaranteed to be present, since the crash window dereferences it for
    /// display; a failure to rebuild the recorded one falls back to the current runtime.
    private static LaunchOptions rebuildLaunchOptions(ResultSpec result) {
        LaunchOptions.Builder builder = new LaunchOptions.Builder();
        if (result.gameDir != null) {
            try {
                builder.setGameDir(Path.of(result.gameDir));
            } catch (IllegalArgumentException e) {
                LOG.warning("Malformed game directory in the monitor result file: " + result.gameDir);
            }
        }
        if (result.versionName != null)
            builder.setVersionName(result.versionName);
        if (result.maxMemory != null)
            builder.setMaxMemory(result.maxMemory);

        JavaRuntime java = null;
        if (result.javaBinary != null && result.javaVersion != null && result.javaArchitecture != null) {
            try {
                Architecture architecture = Architecture.parseArchName(result.javaArchitecture);
                JavaInfo info = new JavaInfo.Builder(
                        Platform.getPlatform(OperatingSystem.CURRENT_OS, architecture), result.javaVersion).build();
                java = JavaRuntime.of(Path.of(result.javaBinary), info, false);
            } catch (Throwable e) {
                LOG.warning("Failed to rebuild the Java runtime from the monitor result file", e);
            }
        }
        if (java == null) {
            LOG.warning("Falling back to the current Java runtime for the crash window display");
            java = JavaRuntime.getDefault();
        }
        builder.setJava(java);
        return builder.create();
    }

    /// Reads the session log file written by the monitor into [Log] entries, guessing the level of
    /// each line. Returns an empty list when the log file is missing or unreadable.
    private static List<Log> readSessionLogs(@Nullable String logFile) {
        if (logFile == null)
            return List.of();
        try {
            return Files.readAllLines(Path.of(logFile), MonitorProtocol.CHARSET).stream()
                    .map(line -> new Log(line, Log4jLevel.guessLevel(line)))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.warning("Failed to read the session log file " + logFile, e);
            return List.of();
        }
    }

    /// Resolves the launched instance by its id across the given game directories.
    private static @Nullable HMCLGameInstance findInstance(List<HMCLGameRepository> repositories, ResultSpec result) {
        if (result.instanceId == null)
            return null;

        GameInstanceID id;
        try {
            id = new GameInstanceID(result.instanceId);
        } catch (IllegalArgumentException e) {
            LOG.warning("Malformed instance id in the monitor result file: " + result.instanceId);
            return null;
        }

        for (HMCLGameRepository repository : repositories) {
            HMCLGameInstance instance = repository.findInstance(id);
            if (instance != null)
                return instance;
        }
        return null;
    }
}
