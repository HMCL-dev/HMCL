/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.task;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class TaskCenter {
    private static final TaskCenter INSTANCE = new TaskCenter();

    public static TaskCenter getInstance() {
        return INSTANCE;
    }

    public enum TaskKind {
        GAME_INSTALL,
        MODPACK_INSTALL,
        JAVA_DOWNLOAD,
        MOD_UPDATE,
        OTHER
    }

    public static final class Entry {
        private final TaskExecutor executor;
        private final String title;
        private final String detail;
        private final TaskKind kind;
        private final String name;

        public Entry(TaskExecutor executor, String title, String detail, TaskKind kind, String name) {
            this.executor = executor;
            this.title = title;
            this.detail = detail;
            this.kind = kind;
            this.name = name;
        }

        public TaskExecutor getExecutor() {
            return executor;
        }

        public String getTitle() {
            return title;
        }

        public String getDetail() {
            return detail;
        }

        public TaskKind getKind() {
            return kind;
        }

        public String getName() {
            return name;
        }
    }

    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private final ObservableList<Entry> completedEntries = FXCollections.observableArrayList();
    private final ObservableList<Entry> failedEntries = FXCollections.observableArrayList();

    private final Deque<Entry> queue = new ArrayDeque<>();
    private final Map<TaskExecutor, Entry> entryIndex = new HashMap<>();
    private final Map<TaskExecutor, Boolean> started = new HashMap<>();
    private Entry running;

    public ObservableList<Entry> getEntries() {
        return entries;
    }

    public Entry getRunningEntry() {
        return running;
    }

    public ObservableList<Entry> getCompletedEntries() {
        return completedEntries;
    }

    public ObservableList<Entry> getFailedEntries() {
        return failedEntries;
    }

    private void assertFxThread() {
        assert Platform.isFxApplicationThread() : "TaskCenter must be accessed from FX Application Thread";
    }

    public void enqueue(TaskExecutor executor, String title, String detail) {
        enqueue(executor, title, detail, TaskKind.OTHER, null);
    }

    public void enqueue(TaskExecutor executor, String title, String detail, TaskKind kind, String name) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> enqueue(executor, title, detail, kind, name));
            return;
        }

        assertFxThread();

        if (entryIndex.containsKey(executor)) {
            return;
        }

        Entry entry = new Entry(executor, title, detail, kind, name);
        entryIndex.put(executor, entry);
        entries.add(entry);
        queue.add(entry);
        tryStartNext();
    }

    private void tryStartNext() {
        assertFxThread();

        while (running == null) {
            Entry next = queue.poll();
            if (next == null) return;

            TaskExecutor executor = next.getExecutor();
            if (Boolean.TRUE.equals(started.get(executor))) {
                continue;
            }

            started.put(executor, true);
            running = next;

            executor.addTaskListener(new TaskListener() {
                @Override
                public void onStop(boolean success, TaskExecutor executor) {
                    Platform.runLater(() -> onTaskStopped(executor, success));
                }
            });

            executor.start();
            return;
        }
    }

    private void onTaskStopped(TaskExecutor executor, boolean success) {
        assertFxThread();

        Entry stoppedEntry = entryIndex.remove(executor);
        started.remove(executor);

        if (stoppedEntry != null) {
            entries.remove(stoppedEntry);
            if (success) {
                completedEntries.add(stoppedEntry);
            } else {
                failedEntries.add(stoppedEntry);
            }

            // Show toast notification for background tasks
            String detail = stoppedEntry.getDetail() != null ? stoppedEntry.getDetail() : stoppedEntry.getTitle();
            if (success) {
                Controllers.showToast(i18n("task.toast.success", detail));
            } else {
                Controllers.showToast(i18n("task.toast.failed", detail));
            }
        }

        if (running != null && running.getExecutor() == executor) {
            running = null;
        }

        tryStartNext();
    }

    public boolean contains(TaskExecutor executor) {
        assertFxThread();
        return entryIndex.containsKey(executor);
    }

    public boolean isStarted(TaskExecutor executor) {
        assertFxThread();
        return Boolean.TRUE.equals(started.get(executor));
    }

    public boolean hasQueuedInstallName(TaskKind kind, String name) {
        assertFxThread();

        if (name == null || kind == null) {
            return false;
        }
        for (Entry entry : entries) {
            if (entry.getKind() != kind || entry.getName() == null) {
                continue;
            }
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                if (entry.getName().equalsIgnoreCase(name)) return true;
            } else {
                if (entry.getName().equals(name)) return true;
            }
        }
        return false;
    }

    public boolean cancelQueued(TaskExecutor executor) {
        assertFxThread();

        Entry entry = entryIndex.get(executor);
        if (entry == null) {
            return false;
        }

        if (Boolean.TRUE.equals(started.get(executor))) {
            // Task is already running — cancel it properly
            executor.cancel();
        }

        queue.remove(entry);
        entries.remove(entry);
        entryIndex.remove(executor);
        started.remove(executor);

        if (running != null && running.getExecutor() == executor) {
            running = null;
        }

        failedEntries.add(entry);
        tryStartNext();
        return true;
    }

}
