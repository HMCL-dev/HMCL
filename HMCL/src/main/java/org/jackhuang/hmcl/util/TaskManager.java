// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

/// Tracks active task dialogs and pending completion actions in registration order.
///
/// All operations and access to the observable list must occur on the JavaFX application thread.
/// Entries remain registered while hidden. Completed entries remain until their pending action
/// is activated; entries without a pending action are removed when their executors stop.
@NotNullByDefault
public final class TaskManager {

    /// Summarizes tasks that need attention outside their progress dialogs.
    public enum Status {
        /// No task is running in the background or awaiting user action; foreground tasks may exist.
        IDLE,
        /// At least one task is running in the background and none awaits user action.
        RUNNING,
        /// At least one task awaits user action, regardless of other tasks' execution state.
        PENDING
    }

    /// The application-wide task manager.
    private static final TaskManager INSTANCE = new TaskManager();

    /// Active dialogs and completed dialogs awaiting user action.
    private final ObservableList<TaskExecutorDialogPane> tasks =
            FXCollections.observableArrayList(pane -> new Observable[]{pane.runningInBackgroundProperty(), pane.pendingActionProperty()});

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
                if (pane.pendingActionProperty().get() != null)
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

    /// Returns an unmodifiable, observable view of active dialogs and pending completion actions.
    public @UnmodifiableView ObservableList<TaskExecutorDialogPane> getTasks() {
        FXUtils.checkFxUserThread();
        return tasksView;
    }

    /// Returns the non-null aggregate task status as a read-only property.
    ///
    /// Pending user actions take priority over background execution. Foreground tasks alone
    /// leave the status at [Status#IDLE]. Updates as tasks enter or leave these states.
    public ReadOnlyObjectProperty<Status> statusProperty() {
        FXUtils.checkFxUserThread();
        return status.getReadOnlyProperty();
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
