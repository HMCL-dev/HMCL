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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.LogWindow;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.util.CircularArrayList;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.platform.ManagedProcess;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/// Manager of game processes launched by HMCL.
///
/// @author Calboot
public final class GameProcessManager {

    private GameProcessManager() {
    }

    @FXThread
    private static final Map<String, Integer> idToLaunchedCount = new HashMap<>();

    private static final ObservableList<GameProcessHolder> aliveProcessHolders = FXCollections.observableArrayList();

    public static final ObservableList<GameProcessHolder> displayedHolders = FXCollections.observableArrayList();

    public static final IntegerBinding aliveProcessCount = Bindings.size(aliveProcessHolders);

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
            aliveProcessHolders.removeIf(holder -> holder.exited.get());
            displayedHolders.setAll(aliveProcessHolders);
        });
    }

    public static void add(LauncherHelper.HMCLProcessListener processListener) {
        FXUtils.runInFX(() -> {
            var holder = new GameProcessHolder(processListener);
            aliveProcessHolders.add(0, holder);
            if (display.get()) displayedHolders.add(0, holder);
        });
    }

    private static void remove(GameProcessHolder holder) {
        FXUtils.runInFX(() -> aliveProcessHolders.remove(holder));
    }

    public static final class GameProcessHolder {

        private final WeakReference<ManagedProcess> processRef;
        private WeakReference<LogWindow> logWindowRef;

        @SuppressWarnings("FieldCanBeLocal")
        private final WeakListenerHolder holder = new WeakListenerHolder();

        private final String id;
        private final HMCLGameInstance instance;

        private final CircularArrayList<Log> logs;
        private final ReadOnlyStringWrapper lastLogLine = new ReadOnlyStringWrapper(null);
        private final ReadOnlyBooleanWrapper exited = new ReadOnlyBooleanWrapper();

        private GameProcessHolder(LauncherHelper.HMCLProcessListener processListener) {
            this.processRef = new WeakReference<>(processListener.getProcess());
            this.logWindowRef = new WeakReference<>(processListener.getLogWindow());
            this.instance = processListener.getGameInstance();
            this.logs = processListener.getLogs();
            this.lastLogLine.bind(processListener.getLogWindow().lastLogLineProperty());
            {
                String id = processListener.getGameInstance().getId().id();
                int i = idToLaunchedCount.computeIfAbsent(id, k -> 0) + 1;
                idToLaunchedCount.put(id, i);
                this.id = id + " #" + i;
            }
            holder.onWeakChangeAndOperate(processListener.exitedProperty(), b -> {
                if (b) {
                    remove(this);
                    this.exited.set(true);
                }
            });
        }

        public String getId() {
            return id;
        }

        public ReadOnlyStringProperty lastLogLineProperty() {
            return lastLogLine.getReadOnlyProperty();
        }

        public ReadOnlyBooleanProperty exitedProperty() {
            return exited.getReadOnlyProperty();
        }

        public void openSettings() {
            Instances.modifyGameSettings(instance);
        }

        public void relaunch() {
            if (exitedProperty().get()) Instances.launch(instance);
        }

        public void showLogWindow() {
            LogWindow logWindow;
            LogWindow cached;
            if ((cached = logWindowRef.get()) == null) {
                logWindow = new LogWindow();
                logWindow.logLines(logs);
                logWindowRef = new WeakReference<>(logWindow);
            } else {
                logWindow = cached;
            }
            logWindow.show();
            logWindow.requestFocus();
        }

        public void terminate() {
            var process = processRef.get();
            if (process != null) process.stop();
        }
    }

}
