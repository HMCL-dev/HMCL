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
