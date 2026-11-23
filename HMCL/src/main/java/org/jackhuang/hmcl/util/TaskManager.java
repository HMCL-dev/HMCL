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
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TaskDialogPane;
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
/// starts only after every task in that slot has reported completion and its cleanup listeners
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
    private final ObservableList<TaskDialogPane> tasks =
            FXCollections.observableArrayList(pane -> new Observable[]{pane.runningInBackgroundProperty(), pane.pendingActionProperty(), pane.queuedProperty()});

    /// Independent executions waiting in submission order.
    private final ArrayDeque<Execution> queue = new ArrayDeque<>();

    /// Submitted executions, retained independently of their dialogs' visibility or attachment.
    private final IdentityHashMap<Task<?>, Execution> executions = new IdentityHashMap<>();

    /// The group currently owning the execution slot, or `null` when idle.
    private @Nullable Group activeGroup;

    /// A live view that prevents callers from modifying the task list.
    private final @UnmodifiableView ObservableList<TaskDialogPane> tasksView =
            FXCollections.unmodifiableObservableList(tasks);

    /// The aggregate status, with background execution taking priority over waiting tasks.
    private final ReadOnlyObjectWrapper<Status> status = new ReadOnlyObjectWrapper<>(this, "status", Status.IDLE);

    /// Creates the application-wide manager.
    private TaskManager() {
        status.bind(Bindings.createObjectBinding(() -> {
            boolean pending = false;
            for (TaskDialogPane pane : tasks) {
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
    public @UnmodifiableView ObservableList<TaskDialogPane> getTasks() {
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

    /// Submits an unstarted task, returning the same future while it remains registered.
    /// All task listeners must be installed before submission. Callers must not subsequently
    /// invoke [Task#start()] or [Task#test()] directly.
    ///
    /// @param task the task to submit, which must not have completed
    /// @param parent an executing parent whose slot this task shares, or `null` to queue
    ///               an independent execution; a parent must still belong to the active group
    /// @return a future completed after the task's completion listeners and their preceding JavaFX
    ///         callbacks have run. Its value indicates execution success. If the task
    ///         rejects the start request, the future completes exceptionally with that failure.
    /// @throws IllegalArgumentException if `parent` is not executing in the active group
    public CompletableFuture<Boolean> submit(Task<?> task, @Nullable Task<?> parent) {
        return submit(task, parent, null);
    }

    /// Submits the task and invokes `onStartRejected` on the JavaFX thread if starting it throws.
    /// Changes made by callers to the returned future do not trigger the callback.
    public CompletableFuture<Boolean> submit(Task<?> task, @Nullable Task<?> parent, @Nullable Runnable onStartRejected) {
        FXUtils.checkFxUserThread();
        @Nullable Execution existing = executions.get(task);
        if (existing != null)
            return existing.observe(onStartRejected);

        Group group;
        if (parent == null) {
            group = new Group();
        } else {
            @Nullable Execution owner = executions.get(parent);
            if (owner == null || owner.group != activeGroup)
                throw new IllegalArgumentException("Parent task is not running");
            group = owner.group;
        }
        Execution execution = new Execution(task, group);
        executions.put(task, execution);
        if (parent == null) {
            queue.addLast(execution);
            startNext();
        } else {
            start(execution);
        }
        return execution.observe(onStartRejected);
    }

    /// Starts the oldest independent execution when no group owns the slot.
    private void startNext() {
        while (activeGroup == null && !queue.isEmpty()) {
            Execution next = queue.removeFirst();
            activeGroup = next.group;
            start(next);
        }
    }

    /// Starts a group member while retaining the group's exclusive slot.
    private void start(Execution execution) {
        execution.group.members++;
        try {
            execution.task.addTaskListener(execution);
            execution.listening = true;
            execution.task.start();
        } catch (RuntimeException | Error failure) {
            release(execution);
            execution.completion.completeExceptionally(failure);
        }
    }

    /// Releases this submission once, ignoring late notifications from previous submissions.
    private boolean release(Execution execution) {
        if (!executions.remove(execution.task, execution))
            return false;
        if (execution.listening)
            execution.task.removeTaskListener(execution);
        if (--execution.group.members == 0)
            activeGroup = null;
        return true;
    }

    /// Counts executions that must finish before an independent task may start.
    private static final class Group {
        /// The number of started group members whose completion notifications have not been processed.
        private int members;
    }

    /// Retains scheduling state even if its progress dialog is hidden or detached.
    private final class Execution extends TaskListener {
        /// The task started when its group owns the slot.
        private final Task<?> task;
        /// The execution slot shared with explicitly declared children.
        private final Group group;
        /// Whether the root accepted this listener before its start was attempted.
        private boolean listening;
        /// Completion observed after cleanup listeners have run.
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();
        /// The shared caller-visible future, which callers may cancel independently.
        private final CompletableFuture<Boolean> clientCompletion = completion.copy();

        /// Creates an unstarted execution in the supplied group.
        private Execution(Task<?> task, Group group) {
            this.task = task;
            this.group = group;
        }

        /// Observes actual startup failures independently of changes to the caller's future.
        private CompletableFuture<Boolean> observe(@Nullable Runnable onStartRejected) {
            if (onStartRejected != null) {
                completion.whenComplete((success, failure) -> {
                    if (failure != null)
                        onStartRejected.run();
                });
            }
            return clientCompletion;
        }

        /// Releases the slot after earlier completion listeners and their JavaFX callbacks.
        @Override
        public void onExecutionFinished(Task<?> task, boolean success) {
            Platform.runLater(() -> {
                if (release(this)) {
                    completion.complete(success);
                    startNext();
                }
            });
        }
    }

    /// Registers a dialog; registering an existing entry has no effect.
    public void register(TaskDialogPane pane) {
        FXUtils.checkFxUserThread();
        if (!tasks.contains(pane))
            tasks.add(pane);
    }

    /// Removes a dialog; removing an absent entry has no effect.
    public void unregister(TaskDialogPane pane) {
        FXUtils.checkFxUserThread();
        tasks.remove(pane);
    }
}
