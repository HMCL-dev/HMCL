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
