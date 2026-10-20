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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.TaskManager;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays task progress and allows active tasks to continue with the dialog hidden.
///
/// Construction and lifecycle operations must occur on the JavaFX application thread.
/// Restoring a hidden dialog preserves its task list and does not restart its executor.
@NotNullByDefault
public class TaskExecutorDialogPane extends BorderPane {

    /// The executor whose progress is displayed, or `null` if detached.
    private @Nullable TaskExecutor executor;
    /// The cancellation action, or `null` when cancellation is disabled.
    private @Nullable TaskCancellationAction onCancel;
    /// An optional action invoked when the dialog is first sent to the background.
    private @Nullable Runnable onBackground;
    /// Whether the current executor has started and has not yet reported completion.
    private boolean running;
    /// Keeps the weakly registered speed listener alive while this pane is retained.
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final Consumer<FetchTask.SpeedEvent> speedEventHandler;

    /// The displayed task title.
    private final Label lblTitle;
    /// The aggregate download speed.
    private final Label lblProgress;
    /// The action that requests cancellation.
    private final JFXButton btnCancel;
    /// The action that hides the dialog without cancelling its executor.
    private final JFXButton btnBackground;
    /// The retained task progress view.
    private final TaskListPane taskListPane;

    /// Creates a progress dialog with the given cancellation action.
    ///
    /// @param cancel the action invoked after requesting executor cancellation;
    ///               `null` disables cancellation
    public TaskExecutorDialogPane(@Nullable TaskCancellationAction cancel) {
        this.getStyleClass().add("task-executor-dialog-layout");

        FXUtils.setLimitWidth(this, 500);
        FXUtils.setLimitHeight(this, 300);

        VBox center = new VBox();
        this.setCenter(center);
        center.setPadding(new Insets(16));
        {
            lblTitle = new Label();
            lblTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: BOLD;");

            taskListPane = new TaskListPane();
            VBox.setVgrow(taskListPane, Priority.ALWAYS);

            center.getChildren().setAll(lblTitle, taskListPane);
        }

        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(0, 8, 8, 8));
        bottom.setSpacing(8);
        this.setBottom(bottom);
        {
            lblProgress = new Label();
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            btnCancel = new JFXButton(i18n("button.cancel"));
            btnCancel.getStyleClass().add("dialog-cancel");
            btnBackground = new JFXButton(i18n("task.background"));
            btnBackground.setDisable(true);
            btnBackground.setOnAction(event -> background());
            bottom.getChildren().setAll(lblProgress, spacer, btnBackground, btnCancel);
        }

        setCancel(cancel);

        btnCancel.setOnAction(e -> {
            @Nullable TaskCancellationAction action = onCancel;
            if (!running || action == null || !action.isEnabled())
                return;
            if (executor != null && !executor.isCancelled())
                executor.cancel();
            action.accept(this);
            btnCancel.setDisable(true);
        });

        speedEventHandler = FetchTask.SPEED_EVENT.registerWeak(speedEvent -> {
            String message = I18n.formatSpeed(speedEvent.getSpeed());
            Platform.runLater(() -> lblProgress.setText(message));
        });

        onEscPressed(this, btnCancel::fire);
    }

    /// Attaches an executor before it starts and closes the dialog when it stops.
    ///
    /// @param executor the executor to display, or `null` to detach the current executor
    public void setExecutor(@Nullable TaskExecutor executor) {
        setExecutor(executor, true);
    }

    /// Attaches an executor before it starts and tracks it until it stops.
    ///
    /// Reattaching the same executor has no effect. Replacing an executor removes the
    /// previous dialog entry without cancelling that executor. Detaching does not close
    /// the dialog. Listener callbacks from a replaced executor do not close this dialog.
    ///
    /// @param executor the executor to display, or `null` to detach
    /// @param autoClose whether to close the dialog when this executor stops
    public void setExecutor(@Nullable TaskExecutor executor, boolean autoClose) {
        FXUtils.checkFxUserThread();
        if (this.executor == executor)
            return;
        TaskManager.getInstance().unregister(this);
        this.executor = executor;
        running = false;
        updateButtons();

        if (executor != null) {
            taskListPane.setExecutor(executor);

            executor.addTaskListener(new TaskListener() {
                /// Registers this dialog when its current executor starts.
                @Override
                public void onStart() {
                    runInFX(() -> {
                        if (TaskExecutorDialogPane.this.executor != executor)
                            return;
                        running = true;
                        TaskManager.getInstance().register(TaskExecutorDialogPane.this);
                        updateButtons();
                    });
                }

                /// Removes the completed task and optionally closes its dialog.
                @Override
                public void onStop(boolean success, TaskExecutor stoppedExecutor) {
                    Platform.runLater(() -> {
                        if (TaskExecutorDialogPane.this.executor != stoppedExecutor)
                            return;
                        running = false;
                        TaskManager.getInstance().unregister(TaskExecutorDialogPane.this);
                        updateButtons();
                        if (autoClose) {
                            fireEvent(new DialogCloseEvent());
                            if (Controllers.AUTO_TRIM_HEAP) {
                                // Trim heap memory when tasks finish, allowing GC to return memory to the OS faster,
                                // prevent excessive peak memory from consuming system resources.
                                Platform.runLater(Controllers::trimHeap);
                            }
                        }
                    });
                }
            });
        }
    }

    /// Hides an active dialog without invoking cancellation or changing execution.
    ///
    /// Has no effect if execution has stopped or the dialog is already hidden.
    public void background() {
        FXUtils.checkFxUserThread();
        if (!running || !DialogUtils.isShowing(this))
            return;
        DialogUtils.close(this);
        @Nullable Runnable action = onBackground;
        onBackground = null;
        if (action != null)
            action.run();
    }

    /// Restores an active dialog through the main window without restarting execution.
    ///
    /// Has no effect if execution has stopped or the dialog is already in the dialog stack.
    public void restore() {
        FXUtils.checkFxUserThread();
        if (running && !DialogUtils.isShowing(this))
            Controllers.dialog(this);
    }

    /// Sets an action to run once when the active dialog is next sent to the background.
    ///
    /// @param action the action, or `null` to remove it
    public void setOnBackground(@Nullable Runnable action) {
        FXUtils.checkFxUserThread();
        onBackground = action;
    }

    /// Updates action availability from the current execution and cancellation state.
    private void updateButtons() {
        btnBackground.setDisable(!running);
        btnCancel.setDisable(!running || onCancel == null || !onCancel.isEnabled()
                || executor == null || executor.isCancelled());
    }

    /// Returns the writable title property, which may be bound to a task's current state.
    public StringProperty titleProperty() {
        return lblTitle.textProperty();
    }

    /// Returns the displayed title, which may be `null`.
    public @Nullable String getTitle() {
        return lblTitle.getText();
    }

    /// Sets the displayed title; the title property must not be bound.
    public void setTitle(@Nullable String currentState) {
        lblTitle.setText(currentState);
    }

    /// Updates the cancellation action on the JavaFX application thread.
    ///
    /// May be called from another thread, in which case the update is scheduled.
    ///
    /// @param onCancel the action, or `null` to disable cancellation
    public void setCancel(@Nullable TaskCancellationAction onCancel) {
        runInFX(() -> {
            this.onCancel = onCancel;
            updateButtons();
        });
    }
}
