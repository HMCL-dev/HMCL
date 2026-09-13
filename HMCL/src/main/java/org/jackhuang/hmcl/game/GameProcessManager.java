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
package org.jackhuang.hmcl.game;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.util.FXThread;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GameProcessManager {

    private GameProcessManager() {
    }

    @FXThread
    public static final Map<String, Integer> idToLaunchedCount = new HashMap<>();

    @FXThread
    public static final List<GameProcessHolder> processHolders = new ArrayList<>();

    public static void cleanupProcessListeners() {
        FXUtils.runInFX(() -> processHolders.removeIf(holder -> holder.exited.get()));
    }

    public static void addProcessListener(LauncherHelper.HMCLProcessListener processListener) {
        FXUtils.runInFX(() -> processHolders.add(new GameProcessHolder(processListener)));
    }

    public static final class GameProcessHolder {

        private final WeakReference<LauncherHelper.HMCLProcessListener> listenerRef;

        @SuppressWarnings("FieldCanBeLocal")
        private final WeakListenerHolder holder = new WeakListenerHolder();

        private final String id;

        private final ObservableList<Log> logs = FXCollections.observableArrayList();
        private final ReadOnlyStringWrapper lastLogLine = new ReadOnlyStringWrapper("");
        private final ReadOnlyBooleanWrapper exited = new ReadOnlyBooleanWrapper();

        private GameProcessHolder(LauncherHelper.HMCLProcessListener processListener) {
            this.listenerRef = new WeakReference<>(processListener);
            {
                String id = processListener.getGameInstance().getId().id();
                int i = idToLaunchedCount.computeIfAbsent(id, k -> 0) + 1;
                idToLaunchedCount.put(id, i);
                this.id = id + " #" + i;
            }
            {
                Bindings.bindContent(logs, processListener.getLogWindow().getLogs());
                if (!logs.isEmpty()) {
                    lastLogLine.set(logs.get(logs.size() - 1).getLog());
                }
                logs.addListener((InvalidationListener) o -> {
                    if (!logs.isEmpty()) {
                        lastLogLine.set(logs.get(logs.size() - 1).getLog());
                    } else {
                        lastLogLine.set("");
                    }
                });
            }
            holder.onWeakChangeAndOperate(processListener.exitedProperty(), b -> {
                if (b) {
                    var currentLogs = processListener.getLogWindow().getLogs();
                    this.lastLogLine.set(currentLogs.get(currentLogs.size() - 1).getLog()); // I don't know why but this is necessary
                    processHolders.remove(this);
                    this.exited.set(true);
                }
            });
        }

        public WeakReference<LauncherHelper.HMCLProcessListener> getListenerRef() {
            return listenerRef;
        }

        public String getId() {
            return id;
        }

        public ObservableList<Log> getLogs() {
            return logs;
        }

        public ReadOnlyStringProperty lastLogLineProperty() {
            return lastLogLine.getReadOnlyProperty();
        }

        public ReadOnlyBooleanProperty exitedProperty() {
            return exited.getReadOnlyProperty();
        }
    }

}
