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
import org.jackhuang.hmcl.ui.GameCrashWindow;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Presents a supervised game's crash window from the result retained in the monitor JVM after
/// that JVM continues into the launcher UI.
@NotNullByDefault
public final class MonitorCrashReporter {

    private MonitorCrashReporter() {
    }

    public static void show() {
        Monitor.Result result = Monitor.getResult();

        // find repository
        GameDirectoryID id = GameDirectoryID.parse(result.gameDirectoryId());
        Optional<GameDirectory> match = GameDirectoryManager.getGameDirectories().stream()
                .filter(directory -> directory.getId().equals(id))
                .findFirst();
        if (match.isEmpty())
            return;
        GameDirectoryManager.setSelectedGameDirectory(match.get());
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();

        GameInstanceID instanceId = result.launchOptions().getInstanceId();
        HMCLGameInstance instance = repository.findInstance(instanceId);

        if (instance != null) { // instance found
            presentCrashWindow(result, instance);
            return;
        }

        LOG.info("Waiting for the game repository to load before showing the crash window");
        repository.refreshAsync().thenRunAsync(Schedulers.javafx(), () -> {
            HMCLGameInstance loaded = repository.findInstance(instanceId);
            if (loaded != null)
                presentCrashWindow(result, loaded);
            else
                LOG.warning("Cannot resolve the launched instance " + instanceId + ", skip showing the crash window");
        }).start();
    }

    /// Schedules the crash window on the JavaFX thread using the configured tail of captured console output.
    private static void presentCrashWindow(Monitor.Result result, HMCLGameInstance instance) {
        @Unmodifiable List<String> logs = result.process().getLines(null);
        Platform.runLater(() -> new GameCrashWindow(
                result.process(),
                result.exitType(),
                instance,
                result.launchOptions(),
                logs.subList(Math.max(0, logs.size() - Log.getLogLines()), logs.size()).stream()
                        .map(line -> new Log(line, Log4jLevel.guessLevel(line)))
                        .toList()).show());
    }
}
