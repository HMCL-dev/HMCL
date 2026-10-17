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
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Log;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryID;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.GameCrashWindow;
import org.jackhuang.hmcl.util.CircularArrayList;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static javafx.application.Platform.runLater;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The relaunched launcher role of the monitor feature: presenting the crash window of a supervised
/// game process, reusing the regular JavaFX user interface.
@NotNullByDefault
public final class MonitorCrashReporter {

    private MonitorCrashReporter() {
    }

    public static @Nullable Path processArguments(String[] args) {
        if (args.length == 2 && args[0].equals("--crash-report"))
            return Path.of(args[1]);
        return null;
    }

    public static void show(Path resultFile) {
        ResultSpec result;
        try {
            result = JsonUtils.fromJsonFile(resultFile, ResultSpec.class);
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to read the monitor result file " + resultFile, e);
            return;
        }
        try {
            Files.deleteIfExists(resultFile);
        } catch (IOException e) {
            LOG.warning("Failed to delete the monitor result file " + resultFile, e);
        }
        if (result == null || result.launchOptions == null || result.gameDirectoryId == null)
            return;

        // find repository
        GameDirectoryID id = GameDirectoryID.parse(result.gameDirectoryId);
        Optional<GameDirectory> match = GameDirectoryManager.getGameDirectories().stream()
                .filter(directory -> directory.getId().equals(id))
                .findFirst();
        if (match.isEmpty())
            return;
        GameDirectoryManager.setSelectedGameDirectory(match.get());
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();

        // find instance
        GameInstanceID instanceId = result.launchOptions.getInstanceId();
        HMCLGameInstance instance = repository.findInstance(instanceId);

        // instance found
        if (instance != null) {
            presentCrashWindow(result, instance);
            return;
        }

        // repository found but cannot find instance
        if (repository.isLoaded()) {
            logFail(instanceId, result.logFile, repository);
            return;
        }

        LOG.info("Waiting for the game repository to load before showing the crash window");
        repository.refreshAsync().whenComplete(Schedulers.defaultScheduler(), exception -> {
            if (exception == null) {
                runLater(() -> {
                    HMCLGameInstance loaded = repository.findInstance(instanceId);
                    if (loaded != null)
                        presentCrashWindow(result, loaded);
                    else
                        logFail(instanceId, result.logFile, repository);
                });
            } else {
                LOG.warning("Failed to load the game repository while waiting to show the crash window", exception);
                logFail(instanceId, result.logFile, repository);
            }
        }).start();
    }

    private static void logFail(GameInstanceID instanceId, @Nullable String logFile,
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

    private static void presentCrashWindow(ResultSpec result, HMCLGameInstance instance) {
        MonitorGameProcess process = new MonitorGameProcess(
                ProcessHandle.of(result.pid).orElse(null),
                Objects.requireNonNullElseGet(result.commands, List::of),
                result.processStartTime,
                null); // pretend this is the real process.

        @Nullable Path logFile = result.logFile != null ? Path.of(result.logFile) : null;

        ProcessListener.ExitType exitType = ProcessListener.ExitType.valueOf(result.exitType);
        Task.supplyAsync(() -> {
            if (logFile == null)
                return List.<Log>of();

            // limit the lines of log
            int limit = Log.getLogLines();
            CircularArrayList<String> tail = new CircularArrayList<>(limit);
            try (Stream<String> lines = Files.lines(logFile, MonitorProtocol.CHARSET)) {
                lines.forEach(line -> {
                    if (tail.size() == limit)
                        tail.removeFirst();
                    tail.addLast(line);
                });
            } catch (IOException | UncheckedIOException e) {
                LOG.warning("Failed to read the session log file " + logFile, e);
            }

            Files.deleteIfExists(logFile);
            return tail.stream()
                    .map(line -> new Log(line, Log4jLevel.guessLevel(line)))
                    .toList();
        }).whenComplete(Schedulers.javafx(), (logs, exception) -> {
            if (exception != null)
                LOG.warning("Failed to read the session log file " + result.logFile, exception);
            new GameCrashWindow(process, exitType, instance, Objects.requireNonNull(result.launchOptions),
                    exception == null ? logs : List.of()).show();
        }).start();
    }
}
