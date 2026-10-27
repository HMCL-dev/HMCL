// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.CompletableFuture;

/// Serializes independent task executions and tracks their dialogs and pending completion actions.
///
/// All operations and access to the observable list must occur on the JavaFX application thread.
/// Entries remain registered while hidden. Completed entries remain until their pending action
/// is activated; entries without a pending action are removed when their executors stop.
/// Explicit child executions share their parent's execution slot. The next independent task
/// starts only after every executor in that slot has reported completion and its cleanup listeners
/// have run. Hiding or detaching a dialog does not release its slot.
@NotNullByDefault
public final class TaskManager {

    /// Summarizes tasks that need attention outside their progress dialogs.
    public enum Status {
        /// No task is queued, running in the background, or awaiting user action; foreground tasks may exist.
        IDLE,
        /// At least one task is running in the background, with no queued tasks or pending user actions.
        RUNNING,
        /// At least one task awaits user action or an execution slot.
        PENDING
    }

    /// The application-wide task manager.
    private static final TaskManager INSTANCE = new TaskManager();

    /// Queued and active dialogs and completed dialogs awaiting user action.
    private final ObservableList<TaskExecutorDialogPane> tasks =
            FXCollections.observableArrayList(pane -> new Observable[]{pane.runningInBackgroundProperty(), pane.pendingActionProperty(), pane.queuedProperty()});

    /// Independent executions waiting in submission order.
    private final ArrayDeque<Execution> queue = new ArrayDeque<>();

    /// Submitted executions, retained independently of their dialogs' visibility or attachment.
    private final IdentityHashMap<TaskExecutor, Execution> executions = new IdentityHashMap<>();

    /// The group currently owning the execution slot, or `null` when idle.
    private @Nullable Group activeGroup;

    /// A live view that prevents callers from modifying the task list.
    private final @UnmodifiableView ObservableList<TaskExecutorDialogPane> tasksView =
            FXCollections.unmodifiableObservableList(tasks);

    /// The aggregate status, with pending user actions taking priority over background execution.
    private final ReadOnlyObjectWrapper<Status> status = new ReadOnlyObjectWrapper<>(this, "status", Status.IDLE);

    /// Creates the application-wide manager.
    private TaskManager() {
        status.bind(Bindings.createObjectBinding(() -> {
            boolean running = false;
            for (TaskExecutorDialogPane pane : tasks) {
                if (pane.pendingActionProperty().get() != null || pane.queuedProperty().get())
                    return Status.PENDING;
                running |= pane.runningInBackgroundProperty().get();
            }
            return running ? Status.RUNNING : Status.IDLE;
        }, tasks));
    }

    /// Returns the application-wide task manager.
    public static TaskManager getInstance() {
        return INSTANCE;
    }

    /// Returns an unmodifiable, observable view of queued tasks, active dialogs, and pending completion actions.
    public @UnmodifiableView ObservableList<TaskExecutorDialogPane> getTasks() {
        FXUtils.checkFxUserThread();
        return tasksView;
    }

    /// Returns the non-null aggregate task status as a read-only property.
    ///
    /// Queued tasks and pending user actions take priority over background execution. Foreground tasks alone
    /// leave the status at [Status#IDLE]. Updates as tasks enter or leave these states.
    public ReadOnlyObjectProperty<Status> statusProperty() {
        FXUtils.checkFxUserThread();
        return status.getReadOnlyProperty();
    }

    /// Submits an unstarted executor, returning the same future while it remains registered.
    /// All executor listeners must be installed before submission. Callers must not subsequently
    /// invoke [TaskExecutor#start()] or [TaskExecutor#test()] directly.
    ///
    /// @param executor the executor to submit; an executor that has completed must not be resubmitted
    /// @param parent an executing parent whose slot this executor shares, or `null` to queue
    ///               an independent execution; a parent must still belong to the active group
    /// @return a future completed after the executor's stop listeners and their preceding JavaFX
    ///         callbacks have run; its value indicates execution success
    /// @throws IllegalArgumentException if `parent` is not executing in the active group
    public CompletableFuture<Boolean> submit(TaskExecutor executor, @Nullable TaskExecutor parent) {
        FXUtils.checkFxUserThread();
        @Nullable Execution existing = executions.get(executor);
        if (existing != null)
            return existing.completion;

        Group group;
        if (parent == null) {
            group = new Group();
        } else {
            @Nullable Execution owner = executions.get(parent);
            if (owner == null || owner.group != activeGroup)
                throw new IllegalArgumentException("Parent executor is not running");
            group = owner.group;
        }
        Execution execution = new Execution(executor, group);
        executions.put(executor, execution);
        executor.addTaskListener(new TaskListener() {
            /// Releases the slot after earlier stop listeners and their JavaFX callbacks.
            @Override
            public void onStop(boolean success, TaskExecutor stoppedExecutor) {
                Platform.runLater(() -> complete(execution, success));
            }
        });
        if (parent == null) {
            queue.addLast(execution);
            startNext();
        } else {
            start(execution);
        }
        return execution.completion;
    }

    /// Starts the oldest independent execution when no group owns the slot.
    private void startNext() {
        if (activeGroup != null || queue.isEmpty())
            return;
        Execution next = queue.removeFirst();
        activeGroup = next.group;
        start(next);
    }

    /// Starts a group member while retaining the group's exclusive slot.
    private void start(Execution execution) {
        execution.group.members++;
        execution.executor.start();
    }

    /// Completes a submitted execution once, releasing its group after its last member stops.
    private void complete(Execution execution, boolean success) {
        if (executions.remove(execution.executor) == null)
            return;
        if (--execution.group.members == 0)
            activeGroup = null;
        execution.completion.complete(success);
        startNext();
    }

    /// Counts executors that must stop before an independent task may start.
    private static final class Group {
        /// The number of started group members whose stop notifications have not been processed.
        private int members;
    }

    /// Retains scheduling state even if its progress dialog is hidden or detached.
    private static final class Execution {
        /// The executor started when its group owns the slot.
        private final TaskExecutor executor;
        /// The execution slot shared with explicitly declared children.
        private final Group group;
        /// Completion observed after cleanup listeners have run.
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();

        /// Creates an unstarted execution in the supplied group.
        private Execution(TaskExecutor executor, Group group) {
            this.executor = executor;
            this.group = group;
        }
    }

    /// Registers a dialog; registering an existing entry has no effect.
    public void register(TaskExecutorDialogPane pane) {
        FXUtils.checkFxUserThread();
        if (!tasks.contains(pane))
            tasks.add(pane);
    }

    /// Removes a dialog; removing an absent entry has no effect.
    public void unregister(TaskExecutorDialogPane pane) {
        FXUtils.checkFxUserThread();
        tasks.remove(pane);
    }
}
