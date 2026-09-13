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
import org.jackhuang.hmcl.ui.LogWindow;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.instances.Instances;
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
    private static final Map<String, Integer> idToLaunchedCount = new HashMap<>();

    @FXThread
    private static final List<GameProcessHolder> processHolders = new ArrayList<>();

    @FXThread
    public static final ObservableList<GameProcessHolder> displayedHolders = FXCollections.observableArrayList();

    @FXThread
    private static final BooleanProperty display = new SimpleBooleanProperty() {
        @Override
        public void invalidated() {
            if (display.get()) {
                updateDisplay();
            } else {
                displayedHolders.clear();
            }
        }
    };

    @FXThread
    public static void setDisplay(boolean display) {
        GameProcessManager.display.set(display);
    }

    public static void updateDisplay() {
        FXUtils.runInFX(() -> {
            processHolders.removeIf(holder -> holder.exited.get());
            displayedHolders.setAll(processHolders);
        });
    }

    public static void add(LauncherHelper.HMCLProcessListener processListener) {
        FXUtils.runInFX(() -> {
            var holder = new GameProcessHolder(processListener);
            processHolders.add(0, holder);
            if (display.get()) displayedHolders.add(0, holder);
        });
    }

    private static void remove(GameProcessHolder holder) {
        FXUtils.runInFX(() -> processHolders.remove(holder));
    }

    public static final class GameProcessHolder {

        private final WeakReference<LauncherHelper.HMCLProcessListener> listenerRef;

        @SuppressWarnings("FieldCanBeLocal")
        private final WeakListenerHolder holder = new WeakListenerHolder();

        private final String id;
        private final HMCLGameInstance instance;

        private final ObservableList<Log> logs = FXCollections.observableArrayList();
        private final ReadOnlyStringWrapper lastLogLine = new ReadOnlyStringWrapper("");
        private final ReadOnlyBooleanWrapper exited = new ReadOnlyBooleanWrapper();

        private GameProcessHolder(LauncherHelper.HMCLProcessListener processListener) {
            this.listenerRef = new WeakReference<>(processListener);
            this.instance = processListener.getGameInstance();
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
                    remove(this);
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

        public HMCLGameInstance getInstance() {
            return instance;
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

        public void relaunch() {
            if (exitedProperty().get()) Instances.launch(getInstance());
        }

        public void showLogWindow() {
            var listener = getListenerRef().get();
            if (listener != null) {
                listener.getLogWindow().show();
            } else {
                LogWindow logWindow = new LogWindow();
                logWindow.logLines(getLogs());
                logWindow.show();
            }
        }

        public void terminate() {
            var listener = getListenerRef().get();
            if (listener != null) listener.getProcess().stop();
        }
    }

}
