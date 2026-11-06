// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.instances.Instances;
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

    public static final ObservableList<GameProcessHolder> processHolders = FXCollections.observableArrayList();

    public static final ReadOnlyIntegerWrapper aliveProcessCount = new ReadOnlyIntegerWrapper(0);

    @FXThread
    private static void countAliveProcesses() {
        aliveProcessCount.set((int) processHolders.stream().filter(holder -> !holder.exitedProperty().get()).count());
    }

    @FXThread
    public static void add(LauncherHelper.HMCLProcessListener processListener) {
        processHolders.add(0, new GameProcessHolder(processListener));
        countAliveProcesses();
    }

    @FXThread
    public static void cleanup() {
        processHolders.removeIf(holder -> holder.exitedProperty().get());
        countAliveProcesses();
    }

    public static final class GameProcessHolder {

        private final WeakReference<ManagedProcess> processRef;

        @SuppressWarnings("FieldCanBeLocal")
        private final WeakListenerHolder holder = new WeakListenerHolder();

        private final String id;
        private final HMCLGameInstance instance;

        private final GameLogsHolder logsHolder;
        private final ReadOnlyBooleanWrapper exited = new ReadOnlyBooleanWrapper();

        private GameProcessHolder(LauncherHelper.HMCLProcessListener processListener) {
            this.processRef = new WeakReference<>(processListener.getProcess());
            this.logsHolder = processListener.getLogsHolder();
            this.instance = processListener.getGameInstance();
            {
                String id = processListener.getGameInstance().getId().id();
                int i = idToLaunchedCount.computeIfAbsent(id, k -> 0) + 1;
                idToLaunchedCount.put(id, i);
                this.id = id + " #" + i;
            }
            holder.onWeakChangeAndOperate(processListener.exitedProperty(), b -> {
                if (b) {
                    this.exited.set(true);
                    countAliveProcesses();
                }
            });
        }

        public String getId() {
            return id;
        }

        public ReadOnlyStringProperty lastLogLineProperty() {
            return logsHolder.lastLogLineProperty();
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
            logsHolder.showWindow();
        }

        public void terminate() {
            var process = processRef.get();
            if (process != null) process.stop();
        }
    }

}
