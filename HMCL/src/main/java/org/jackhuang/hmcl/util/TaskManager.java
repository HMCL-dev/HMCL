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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Tracks active task dialogs in registration order without changing their scheduling.
///
/// All operations and access to the observable list must occur on the JavaFX application thread.
/// Entries remain registered while hidden and are removed when their executors stop.
@NotNullByDefault
public final class TaskManager {

    /// The application-wide task manager.
    private static final TaskManager INSTANCE = new TaskManager();

    /// Active dialogs, including dialogs currently displayed in the foreground.
    private final ObservableList<TaskExecutorDialogPane> tasks = FXCollections.observableArrayList();

    /// A live view that prevents callers from modifying the task list.
    private final @UnmodifiableView ObservableList<TaskExecutorDialogPane> tasksView =
            FXCollections.unmodifiableObservableList(tasks);

    /// Creates the application-wide manager.
    private TaskManager() {
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

    /// Starts a task with a cancellable progress dialog and returns that dialog.
    ///
    /// The task starts immediately and may run concurrently with other registered tasks.
    public TaskExecutorDialogPane executeTask(Task<?> task) {
        FXUtils.checkFxUserThread();
        return Controllers.taskDialog(task, i18n("message.doing"), TaskCancellationAction.NORMAL);
    }
}
