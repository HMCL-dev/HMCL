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
import org.jackhuang.hmcl.task.TaskHandle;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TaskHandleDialogPane;
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
/// is activated. Entries without a pending action are removed when their executions finish.
/// Explicit child executions share their parent's execution slot. The next independent task
/// starts only after every handle in that slot has reported completion and its cleanup listeners
/// have run. Hiding or detaching a dialog does not release its slot.
@NotNullByDefault
public final class TaskManager {

    /// Summarizes tasks that need attention outside their progress dialogs.
    public enum Status {
        /// No task is queued, running in the background, or awaiting user action; foreground tasks may exist.
        IDLE,
        /// At least one task is running in the background without awaiting user action or an execution slot.
        RUNNING,
        /// At least one task awaits user action or an execution slot, with no task running in the background.
        PENDING
    }

    /// The application-wide task manager.
    private static final TaskManager INSTANCE = new TaskManager();

    /// Queued and active dialogs and completed dialogs awaiting user action.
    private final ObservableList<TaskHandleDialogPane> tasks =
            FXCollections.observableArrayList(pane -> new Observable[]{pane.runningInBackgroundProperty(), pane.pendingActionProperty(), pane.queuedProperty()});

    /// Independent executions waiting in submission order.
    private final ArrayDeque<Execution> queue = new ArrayDeque<>();

    /// Submitted executions, retained independently of their dialogs' visibility or attachment.
    private final IdentityHashMap<TaskHandle, Execution> executions = new IdentityHashMap<>();

    /// The group currently owning the execution slot, or `null` when idle.
    private @Nullable Group activeGroup;

    /// A live view that prevents callers from modifying the task list.
    private final @UnmodifiableView ObservableList<TaskHandleDialogPane> tasksView =
            FXCollections.unmodifiableObservableList(tasks);

    /// The aggregate status, with background execution taking priority over waiting tasks.
    private final ReadOnlyObjectWrapper<Status> status = new ReadOnlyObjectWrapper<>(this, "status", Status.IDLE);

    /// Creates the application-wide manager.
    private TaskManager() {
        status.bind(Bindings.createObjectBinding(() -> {
            boolean pending = false;
            for (TaskHandleDialogPane pane : tasks) {
                if (pane.pendingActionProperty().get() != null || pane.queuedProperty().get())
                    pending = true;
                else if (pane.runningInBackgroundProperty().get())
                    return Status.RUNNING;
            }
            return pending ? Status.PENDING : Status.IDLE;
        }, tasks));
    }

    /// Returns the application-wide task manager.
    public static TaskManager getInstance() {
        return INSTANCE;
    }

    /// Returns an unmodifiable, observable view of queued tasks, active dialogs, and pending completion actions.
    public @UnmodifiableView ObservableList<TaskHandleDialogPane> getTasks() {
        FXUtils.checkFxUserThread();
        return tasksView;
    }

    /// Returns the non-null aggregate task status as a read-only property.
    ///
    /// Background execution takes priority over queued tasks and pending user actions. Foreground tasks alone
    /// leave the status at [Status#IDLE]. Updates as tasks enter or leave these states.
    public ReadOnlyObjectProperty<Status> statusProperty() {
        FXUtils.checkFxUserThread();
        return status.getReadOnlyProperty();
    }

    /// Submits an unstarted handle, returning the same future while it remains registered.
    /// All handle listeners must be installed before submission. Callers must not subsequently
    /// invoke [TaskHandle#start()] or [TaskHandle#test()] directly.
    ///
    /// @param handle the handle to submit, which must not have completed
    /// @param parent an executing parent whose slot this handle shares, or `null` to queue
    ///               an independent execution; a parent must still belong to the active group
    /// @return a future completed after the handle's stop listeners and their preceding JavaFX
    ///         callbacks have run; its value indicates execution success
    /// @throws IllegalArgumentException if `parent` is not executing in the active group
    public CompletableFuture<Boolean> submit(TaskHandle handle, @Nullable TaskHandle parent) {
        FXUtils.checkFxUserThread();
        @Nullable Execution existing = executions.get(handle);
        if (existing != null)
            return existing.completion;

        Group group;
        if (parent == null) {
            group = new Group();
        } else {
            @Nullable Execution owner = executions.get(parent);
            if (owner == null || owner.group != activeGroup)
                throw new IllegalArgumentException("Parent handle is not running");
            group = owner.group;
        }
        Execution execution = new Execution(handle, group);
        executions.put(handle, execution);
        handle.addTaskListener(new TaskListener() {
            /// Releases the slot after earlier stop listeners and their JavaFX callbacks.
            @Override
            public void onStop(boolean success, TaskHandle stoppedHandle) {
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
        execution.handle.start();
    }

    /// Completes a submitted execution once, releasing its group after its last member stops.
    private void complete(Execution execution, boolean success) {
        if (executions.remove(execution.handle) == null)
            return;
        if (--execution.group.members == 0)
            activeGroup = null;
        execution.completion.complete(success);
        startNext();
    }

    /// Counts executions that must finish before an independent task may start.
    private static final class Group {
        /// The number of started group members whose stop notifications have not been processed.
        private int members;
    }

    /// Retains scheduling state even if its progress dialog is hidden or detached.
    private static final class Execution {
        /// The handle started when its group owns the slot.
        private final TaskHandle handle;
        /// The execution slot shared with explicitly declared children.
        private final Group group;
        /// Completion observed after cleanup listeners have run.
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();

        /// Creates an unstarted execution in the supplied group.
        private Execution(TaskHandle handle, Group group) {
            this.handle = handle;
            this.group = group;
        }
    }

    /// Registers a dialog; registering an existing entry has no effect.
    public void register(TaskHandleDialogPane pane) {
        FXUtils.checkFxUserThread();
        if (!tasks.contains(pane))
            tasks.add(pane);
    }

    /// Removes a dialog; removing an absent entry has no effect.
    public void unregister(TaskHandleDialogPane pane) {
        FXUtils.checkFxUserThread();
        tasks.remove(pane);
    }
}
