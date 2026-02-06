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

public final class TaskCenter {
    private static final TaskCenter INSTANCE = new TaskCenter();

    public static TaskCenter getInstance() {
        return INSTANCE;
    }

    public static final class Entry {
        private final TaskExecutor executor;
        private final String title;
        private final String detail;

        public Entry(TaskExecutor executor, String title, String detail) {
            this.executor = executor;
            this.title = title;
            this.detail = detail;
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

    public synchronized void enqueue(TaskExecutor executor, String title, String detail) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> enqueue(executor, title, detail));
            return;
        }

        if (entryIndex.containsKey(executor)) {
            return;
        }

        Entry entry = new Entry(executor, title, detail);
        entryIndex.put(executor, entry);
        entries.add(entry);
        queue.add(entry);
        tryStartNext();
    }

    private synchronized void tryStartNext() {
        if (running != null) return;
        Entry next = queue.poll();
        if (next == null) return;

        TaskExecutor executor = next.getExecutor();
        if (Boolean.TRUE.equals(started.get(executor))) {
            tryStartNext();
            return;
        }

        started.put(executor, true);
        running = next;

        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                Platform.runLater(() -> {
                    if (running != null) {
                        entries.remove(running);
                        entryIndex.remove(executor);
                        started.remove(executor);

                        if (success) {
                            completedEntries.add(running);
                        } else {
                            failedEntries.add(running);
                        }
                    }

                    running = null;
                    tryStartNext();
                });
            }
        });

        executor.start();
    }

    public synchronized boolean contains(TaskExecutor executor) {
        return entryIndex.containsKey(executor);
    }

    public synchronized boolean isStarted(TaskExecutor executor) {
        return Boolean.TRUE.equals(started.get(executor));
    }
}
