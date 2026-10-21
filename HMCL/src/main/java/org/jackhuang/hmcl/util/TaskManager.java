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
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

/// Tracks active task dialogs in registration order without changing their scheduling.
///
/// All operations and access to the observable list must occur on the JavaFX application thread.
/// Entries remain registered while hidden and are removed when their executors stop.
@NotNullByDefault
public final class TaskManager {

    /// The application-wide task manager.
    private static final TaskManager INSTANCE = new TaskManager();

    /// Active dialogs, including dialogs currently displayed in the foreground.
    private final ObservableList<TaskExecutorDialogPane> tasks =
            FXCollections.observableArrayList(pane -> new Observable[]{pane.runningInBackgroundProperty()});

    /// A live view that prevents callers from modifying the task list.
    private final @UnmodifiableView ObservableList<TaskExecutorDialogPane> tasksView =
            FXCollections.unmodifiableObservableList(tasks);

    /// Whether at least one registered task is currently in the background.
    private final ReadOnlyBooleanWrapper hasBackgroundTasks = new ReadOnlyBooleanWrapper(this, "hasBackgroundTasks");

    /// Creates the application-wide manager.
    private TaskManager() {
        hasBackgroundTasks.bind(Bindings.createBooleanBinding(
                () -> tasks.stream().anyMatch(pane -> pane.runningInBackgroundProperty().get()), tasks));
    }

    /// Returns the application-wide task manager.
    public static TaskManager getInstance() {
        return INSTANCE;
    }

    /// Returns an unmodifiable, observable view of the active dialogs.
    public @UnmodifiableView ObservableList<TaskExecutorDialogPane> getTasks() {
        FXUtils.checkFxUserThread();
        return tasksView;
    }

    /// Returns a read-only property indicating whether a registered task is in the background.
    ///
    /// Updates when tasks are hidden, shown again, registered, or removed.
    public ReadOnlyBooleanProperty hasBackgroundTasksProperty() {
        FXUtils.checkFxUserThread();
        return hasBackgroundTasks.getReadOnlyProperty();
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
