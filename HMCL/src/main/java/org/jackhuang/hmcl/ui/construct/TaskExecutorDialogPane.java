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
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
import org.jackhuang.hmcl.ui.SVG;
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
/// Background tasks may retain a completion action in the task list for explicit activation.
@NotNullByDefault
public class TaskExecutorDialogPane extends BorderPane implements DialogAware {

    /// The executor whose progress is displayed, or `null` if detached.
    private @Nullable TaskExecutor executor;
    /// The cancellation action, or `null` when cancellation is disabled.
    private @Nullable TaskCancellationAction onCancel;
    /// An optional action invoked when the dialog is first sent to the background.
    private @Nullable Runnable onBackground;
    /// Whether the current executor has started and has not yet reported completion.
    private boolean running;
    /// Whether the active task has been explicitly sent to the background.
    private final ReadOnlyBooleanWrapper runningInBackground = new ReadOnlyBooleanWrapper(this, "runningInBackground");
    /// The next step retained for explicit activation after a background task completes.
    private final ReadOnlyObjectWrapper<@Nullable PendingAction> pendingAction = new ReadOnlyObjectWrapper<>(this, "pendingAction");
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
            lblTitle.setMinWidth(0);
            lblTitle.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(lblTitle, Priority.ALWAYS);

            btnBackground = FXUtils.newToggleButton4(SVG.MINIMIZE_CENTER);
            btnBackground.setAccessibleText(i18n("task.background"));
            FXUtils.installFastTooltip(btnBackground, i18n("task.background"));
            btnBackground.setDisable(true);
            btnBackground.setOnAction(event -> background());

            HBox header = new HBox(8, lblTitle, btnBackground);
            header.setAlignment(Pos.CENTER_LEFT);

            taskListPane = new TaskListPane();
            VBox.setVgrow(taskListPane, Priority.ALWAYS);

            center.getChildren().setAll(header, taskListPane);
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
            bottom.getChildren().setAll(lblProgress, spacer, btnCancel);
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
    /// the dialog. Replacing or detaching an executor discards any pending completion action.
    /// Listener callbacks from a replaced executor do not close this dialog.
    ///
    /// @param executor the executor to display, or `null` to detach
    /// @param autoClose whether to close the dialog when this executor stops
    public void setExecutor(@Nullable TaskExecutor executor, boolean autoClose) {
        FXUtils.checkFxUserThread();
        if (this.executor == executor)
            return;
        TaskManager.getInstance().unregister(this);
        this.executor = executor;
        pendingAction.set(null);
        running = false;
        runningInBackground.set(false);
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

                /// Retains a successful task's pending action, otherwise removes the completed task.
                @Override
                public void onStop(boolean success, TaskExecutor stoppedExecutor) {
                    Platform.runLater(() -> {
                        if (TaskExecutorDialogPane.this.executor != stoppedExecutor)
                            return;
                        running = false;
                        runningInBackground.set(false);
                        if (!success)
                            pendingAction.set(null);
                        if (pendingAction.get() == null)
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
        runningInBackground.set(true);
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

    /// Runs a successful task's next step immediately in the foreground, or retains it in the
    /// task list when this dialog is in the background. Must be called from the executor's
    /// completion callback on the JavaFX thread, before the executor reports that it has stopped.
    /// A failed executor or a replacement executor discards the retained action.
    ///
    /// @param text the action button label
    /// @param description the pending task's status text
    /// @param action the next step, invoked on the JavaFX thread
    /// @return `true` if the action ran immediately, or `false` if it was deferred
    public boolean runOrDeferCompletionAction(String text, String description, Runnable action) {
        FXUtils.checkFxUserThread();
        if (runningInBackground.get()) {
            pendingAction.set(new PendingAction(text, description, action));
            return false;
        }
        action.run();
        return true;
    }

    /// Returns the pending-action property, whose value is `null` when no action awaits user input.
    public ReadOnlyObjectProperty<@Nullable PendingAction> pendingActionProperty() {
        return pendingAction.getReadOnlyProperty();
    }

    /// Removes and runs the pending action on the JavaFX thread, at most once.
    /// Does nothing if no action is pending. Removal occurs before invoking the action.
    public void runPendingAction() {
        FXUtils.checkFxUserThread();
        @Nullable PendingAction action = pendingAction.get();
        if (action == null)
            return;
        pendingAction.set(null);
        TaskManager.getInstance().unregister(this);
        action.action().run();
    }

    /// Describes a next step awaiting explicit user activation.
    ///
    /// @param text the action button label
    /// @param description the status displayed in the task list
    /// @param action the operation performed by [TaskExecutorDialogPane#runPendingAction()]
    @NotNullByDefault
    public record PendingAction(String text, String description, Runnable action) {
    }

    /// Returns a read-only property indicating whether the active task was sent to the background
    /// and has not been shown again.
    ///
    /// The value becomes false when execution stops or the executor is detached or replaced.
    public ReadOnlyBooleanProperty runningInBackgroundProperty() {
        return runningInBackground.getReadOnlyProperty();
    }

    /// Clears the background state when this dialog is displayed, including through direct dialog calls.
    @Override
    public void onDialogShown() {
        runningInBackground.set(false);
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
