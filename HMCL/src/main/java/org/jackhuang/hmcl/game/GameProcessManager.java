// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.setting.GameDirectoryID;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jetbrains.annotations.Nullable;

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
    private static final Map<Pair<GameDirectoryID, GameInstanceID>, Integer> idToLaunchedCount = new HashMap<>();

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
        private @Nullable HMCLGameInstance.Optional instanceOptional;

        private final GameLogsHolder logsHolder;
        private final ReadOnlyBooleanWrapper exited = new ReadOnlyBooleanWrapper();

        private GameProcessHolder(LauncherHelper.HMCLProcessListener processListener) {
            this.processRef = new WeakReference<>(processListener.getProcess());
            this.logsHolder = processListener.getLogsHolder();
            this.instanceOptional = HMCLGameInstance.Optional.of(processListener.getGameInstance());
            {
                var instanceID = processListener.getGameInstance().getId();
                var key = Pair.pair(processListener.getGameInstance().getRepository().getGameDirectory().getId(), instanceID);
                int i = idToLaunchedCount.computeIfAbsent(key, k -> 0) + 1;
                idToLaunchedCount.put(key, i);
                this.id = instanceID.id() + " #" + i;
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

        public @Nullable HMCLGameInstance getInstance() {
            if (instanceOptional == null) return null;

            HMCLGameRepository repo = instanceOptional.repository();
            if (GameDirectoryManager.hasRepository(repo) && repo.isLoaded()) {
                instanceOptional = instanceOptional.refreshed();
                return instanceOptional.instance();
            }

            instanceOptional = null;
            return null;
        }

        public void openSettings() {
            var instance = getInstance();
            if (instance != null) Instances.modifyGameSettings(instance);
        }

        public void relaunch() {
            if (exitedProperty().get()) {
                var instance = getInstance();
                if (instance != null) Instances.launch(instance);
            }
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
