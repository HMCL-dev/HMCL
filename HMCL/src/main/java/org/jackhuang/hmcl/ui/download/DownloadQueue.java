// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TaskListPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Queue;

/// Runs submitted download tasks sequentially while allowing each task to retain its own concurrency.
@NotNullByDefault
public final class DownloadQueue {
    /// Tasks visible in the download list.
    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    /// Read-only observable view of [#entries].
    private final @UnmodifiableView ObservableList<Entry> entriesView = FXCollections.unmodifiableObservableList(entries);
    /// Tasks waiting for the current task to finish.
    private final Queue<Entry> pending = new ArrayDeque<>();
    /// The task currently using the queue executor, if any.
    private @Nullable Entry running;

    /// Represents a submitted task and its queue status.
    @NotNullByDefault
    public static final class Entry {
        /// Display name supplied by the download workflow.
        private final String title;
        /// Executor responsible for this task and its dependent work.
        private final TaskExecutor executor;
        /// Progress view attached before the task executor starts.
        private final TaskListPane progressPane = new TaskListPane();
        /// Observable queue status.
        private final ReadOnlyObjectWrapper<Status> status = new ReadOnlyObjectWrapper<>(Status.WAITING);

        /// Creates an entry for a task that has not started yet.
        private Entry(Task<?> task, String title) {
            this(task.executor(), title);
        }

        /// Creates an entry for an executor that has not started yet.
        private Entry(TaskExecutor executor, String title) {
            this.title = title;
            this.executor = executor;
            this.progressPane.setExecutor(executor);
        }

        /// Returns the display name for this entry.
        public String getTitle() {
            return title;
        }

        /// Returns the task executor for this entry.
        public TaskExecutor getExecutor() {
            return executor;
        }

        /// Returns the progress view that observes this task executor.
        public TaskListPane getProgressPane() {
            return progressPane;
        }

        /// Returns the current queue status.
        public Status getStatus() {
            return status.get();
        }

        /// Returns the read-only queue status property.
        public ReadOnlyObjectProperty<Status> statusProperty() {
            return status.getReadOnlyProperty();
        }
    }

    /// Lifecycle states shown in the download list.
    public enum Status {
        /// The task has not started yet.
        WAITING,
        /// The task is currently running.
        RUNNING,
        /// Cancellation was requested for the active task.
        CANCELLING,
        /// The task completed successfully.
        SUCCEEDED,
        /// The task failed.
        FAILED,
        /// The task was cancelled.
        CANCELLED
    }

    /// Returns the observable list of queued and completed tasks.
    public @UnmodifiableView ObservableList<Entry> getEntries() {
        return entriesView;
    }

    /// Adds a task to the end of the queue and starts it when it reaches the front.
    public void add(Task<?> task, String title) {
        add(task.executor(), title);
    }

    /// Adds an executor to the end of the queue and starts it when it reaches the front.
    public void add(TaskExecutor executor, String title) {
        FXUtils.runInFX(() -> {
            Entry entry = new Entry(executor, title);
            entries.add(entry);
            pending.add(entry);
            startNext();
        });
    }

    /// Cancels a waiting or active entry.
    public void cancel(Entry entry) {
        FXUtils.runInFX(() -> {
            if (entry == running) {
                if (entry.getStatus() == Status.RUNNING) {
                    entry.status.set(Status.CANCELLING);
                    entry.executor.cancel();
                }
            } else if (pending.remove(entry)) {
                entry.status.set(Status.CANCELLED);
            }
        });
    }

    /// Removes an entry that is not running or waiting.
    public void remove(Entry entry) {
        FXUtils.runInFX(() -> {
            if (entry == running)
                return;
            if (pending.remove(entry))
                entry.status.set(Status.CANCELLED);
            entries.remove(entry);
        });
    }

    /// Removes all successful, failed, and cancelled entries.
    public void clearCompleted() {
        FXUtils.runInFX(() -> entries.removeIf(entry -> isFinished(entry.getStatus())));
    }

    /// Starts the next waiting task if the queue is idle.
    private void startNext() {
        if (running != null)
            return;

        Entry entry = pending.poll();
        if (entry == null)
            return;

        running = entry;
        entry.status.set(Status.RUNNING);
        entry.executor.addTaskListener(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                FXUtils.runInFX(() -> finish(entry, success, executor));
            }
        });
        entry.executor.start();
    }

    /// Records task completion and advances the queue.
    private void finish(Entry entry, boolean success, TaskExecutor executor) {
        if (running != entry)
            return;

        if (executor.isCancelled()) {
            entry.status.set(Status.CANCELLED);
        } else {
            entry.status.set(success ? Status.SUCCEEDED : Status.FAILED);
        }
        running = null;
        startNext();
    }

    /// Returns whether a status represents a task that has stopped running.
    private static boolean isFinished(Status status) {
        return status == Status.SUCCEEDED || status == Status.FAILED || status == Status.CANCELLED;
    }
}