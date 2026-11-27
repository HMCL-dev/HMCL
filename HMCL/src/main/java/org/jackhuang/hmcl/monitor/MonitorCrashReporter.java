// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.monitor;

import javafx.application.Platform;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Log;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryID;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.GameCrashWindow;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @see show
@NotNullByDefault
public final class MonitorCrashReporter {

    private MonitorCrashReporter() {
    }

    /// find [HMCLGameInstance] from [Monitor.Result] and show [GameCrashWindow]
    public static void show(Monitor.Result result) {
        findInstanceAsync(result).thenAcceptAsync(Schedulers.javafx(), instance -> {
            if (instance == null) {
                LOG.warning("Cannot resolve the launched instance " + result.launchOptions().getInstanceId() + ", skip showing the crash window");
                return;
            }
            instance.markLaunchedAbnormally();
            @Unmodifiable List<String> logs = result.process().getLines(null);
            Platform.runLater(() -> new GameCrashWindow(
                    result.process(),
                    result.exitType(),
                    instance,
                    result.launchOptions(),
                    logs.subList(Math.max(0, logs.size() - Log.getLogLines()), logs.size()).stream()
                            .map(line -> new Log(Logger.filterForbiddenToken(line), Log4jLevel.guessLevel(line)))
                            .toList()).show());
        }).start();
    }

    /// find [HMCLGameInstance] from [Monitor.Result]
    private static Task<@Nullable HMCLGameInstance> findInstanceAsync(Monitor.Result result) {
        // find repository first
        GameDirectoryID id = GameDirectoryID.parse(result.gameDirectoryId());
        Optional<GameDirectory> match = GameDirectoryManager.getGameDirectories().stream()
                .filter(directory -> directory.getId().equals(id))
                .findFirst();
        if (match.isEmpty())
            return Task.completed(null);
        GameDirectoryManager.setSelectedGameDirectory(match.get());
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();

        GameInstanceID instanceId = result.launchOptions().getInstanceId();
        HMCLGameInstance instance = repository.findInstance(instanceId);

        if (instance != null)  // instance found
            return Task.completed(instance);

        LOG.info("Waiting for the game repository to load before showing the crash window");
        return repository.refreshAsync().thenSupplyAsync(() -> repository.findInstance(instanceId));
    }
}
