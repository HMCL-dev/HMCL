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

import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Log;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.setting.GameDirectoryID;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.GameCrashWindow;
import org.jackhuang.hmcl.util.CircularArrayList;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
    /// @return the path of the monitor result file to present, or `null` when the arguments do not
    /// request a crash report
    public static @Nullable Path processArguments(String[] args) {
        if (args.length == 2 && args[0].equals("--crash-report"))
            return Path.of(args[1]);
        return null;
    }

    /// Shows the game crash window for the given monitor result file.
    ///
    /// <p>Must be called on the JavaFX application thread after the main window is set up, and
    /// deletes the result file once consumed. When the launched instance cannot be resolved
    /// immediately, the resolution is deferred until its repository finishes loading; the crash
    /// window is skipped if it still cannot be resolved or the repository fails to load.
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

        HMCLGameRepository repository = resolveRepository(result.gameDirectoryId);
        HMCLGameInstance instance = findInstance(repository, result.instanceId);
        if (instance != null) {
            presentCrashWindow(result, instance);
            return;
        }

        // The relaunched launcher shows its main window before the repository has finished loading
        // asynchronously, so the launched instance may not be resolvable yet. A loaded repository
        // will never gain the instance and a missing repository has nothing to wait for.
        if (repository == null || repository.isLoaded()) {
            giveUp(result.instanceId, result.logFile, repository);
            return;
        }

        LOG.info("Waiting for the game repository to load before showing the crash window");
        // Waiting on the refresh task rather than snapshot publication, so that a failed load also
        // ends the wait; the loaded snapshot is published before the task completes. The selection
        // flow's own refresh may overlap harmlessly (both scans are read-only).
        repository.refreshAsync().whenComplete(Schedulers.defaultScheduler(), exception -> {
            if (exception == null) {
                runLater(() -> resolveAndPresent(result, repository));
            } else {
                LOG.warning("Failed to load the game repository while waiting to show the crash window", exception);
                runLater(() -> giveUp(result.instanceId, result.logFile, repository));
            }
        }).start();
    }

    /// Returns the repository of the registered game directory with the given id.
    ///
    /// @param gameDirectoryId the persistent game directory id recorded by the monitor, or `null`
    /// when unknown
    /// @return the matching repository, or `null` when no registered game directory has this id,
    /// e.g. because the user removed the game directory while the game was running
    private static @Nullable HMCLGameRepository resolveRepository(@Nullable String gameDirectoryId) {
        if (gameDirectoryId == null)
            return null;
        GameDirectoryID id;
        try {
            id = GameDirectoryID.parse(gameDirectoryId);
        } catch (IllegalArgumentException e) {
            LOG.warning("Malformed game directory id in the monitor result file: " + gameDirectoryId);
            return null;
        }
        return GameDirectoryManager.getOrCreateRepositoryByDirectoryId(id);
    }

    /// Resolves the launched instance after the repository has loaded, and shows the crash window
    /// when it is found.
    private static void resolveAndPresent(ResultSpec result, HMCLGameRepository repository) {
        HMCLGameInstance instance = findInstance(repository, result.instanceId);
        if (instance != null)
            presentCrashWindow(result, instance);
        else
            giveUp(result.instanceId, result.logFile, repository);
    }

    /// Logs that the launched instance could not be resolved and the crash window is skipped.
    private static void giveUp(@Nullable String instanceId, @Nullable String logFile,
            @Nullable HMCLGameRepository repository) {
        LOG.warning("Cannot resolve the launched instance " + instanceId + " ("
                + (repository != null
                        ? repository.getSnapshot().getInstanceCount() + " instances, loaded="
                                + repository.isLoaded() + ", base directory "
                                + repository.getLayout().getBaseDirectory()
                        : "no registered game directory has the recorded id")
                + "), skip showing the crash window."
                + " The session log is at " + logFile);
    }

    /// Builds and shows the crash window from the monitor result and the resolved instance.
    private static void presentCrashWindow(ResultSpec result, HMCLGameInstance instance) {
        try {
            List<String> commands = result.commands != null ? result.commands : List.of();
            ProcessHandle gameHandle = result.pid > 0 ? ProcessHandle.of(result.pid).orElse(null) : null;
            MonitorGameProcess process = new MonitorGameProcess(gameHandle, commands, result.processStartTime, null);

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
                Files.deleteIfExists(Path.of(result.logFile));
            }

            new GameCrashWindow(process, exitType, instance, launchOptions, logs).show();
        } catch (Throwable e) {
            LOG.warning("Failed to show the crash window for the relaunched launcher", e);
        }
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

    /// Reads the tail of the session log file into [Log] entries, guessing each line's level. At
    /// most [Log#getLogLines] lines are kept, matching the direct launch path's in-memory limit.
    /// Returns an empty list when the file is missing or unreadable.
    private static @Unmodifiable List<Log> readSessionLogs(@Nullable String logFile) {
        if (logFile == null)
            return List.of();
        int limit = Log.getLogLines();
        try (Stream<String> lines = Files.lines(Path.of(logFile), MonitorProtocol.CHARSET)) {
            CircularArrayList<Log> retained = new CircularArrayList<>(limit + 1);
            lines.forEach(line -> {
                if (retained.size() == limit)
                    retained.removeFirst();
                retained.addLast(new Log(line, Log4jLevel.guessLevel(line)));
            });
            return List.copyOf(retained);
        } catch (IOException | UncheckedIOException e) {
            LOG.warning("Failed to read the session log file " + logFile, e);
            return List.of();
        }
    }

    /// Resolves the launched instance by its id in the given repository.
    ///
    /// @param repository the repository of the recorded game directory, or `null` when no
    /// registered game directory matches
    /// @param instanceId the instance id recorded by the monitor, or `null` when unknown
    /// @return the instance, or `null` when it cannot be resolved
    private static @Nullable HMCLGameInstance findInstance(@Nullable HMCLGameRepository repository,
            @Nullable String instanceId) {
        if (repository == null || instanceId == null)
            return null;

        GameInstanceID id;
        try {
            id = new GameInstanceID(instanceId);
        } catch (IllegalArgumentException e) {
            LOG.warning("Malformed instance id in the monitor result file: " + instanceId);
            return null;
        }
        return repository.findInstance(id);
    }
}
