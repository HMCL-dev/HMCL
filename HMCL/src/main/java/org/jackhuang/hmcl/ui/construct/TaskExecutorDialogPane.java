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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class TaskExecutorDialogPane extends BorderPane {
    private TaskExecutor executor;
    private TaskCancellationAction onCancel;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final Consumer<FetchTask.SpeedEvent> speedEventHandler;

    private final Label lblTitle;
    private final Label lblProgress;
    private final JFXButton btnCancel;
    private final TaskListPane taskListPane;
    private TaskListener autoCloseListener;
    private Runnable escAction;
    private Runnable cancelAction;

    public void setEscAction(Runnable action) {
        this.escAction = action;
    }

    public TaskExecutorDialogPane(@NotNull TaskCancellationAction cancel) {
        this.getStyleClass().add("task-executor-dialog-layout");

        FXUtils.setLimitWidth(this, 500);
        FXUtils.setLimitHeight(this, 300);

        VBox center = new VBox();
        this.setCenter(center);
        center.setPadding(new Insets(16));
        {
            HBox titleBar = new HBox();
            titleBar.setAlignment(Pos.CENTER_LEFT);
            titleBar.setSpacing(8);

            lblTitle = new Label();
            lblTitle.getStyleClass().add("title-label");

            titleBar.getChildren().setAll(lblTitle);

            taskListPane = new TaskListPane();
            VBox.setVgrow(taskListPane, Priority.ALWAYS);

            center.getChildren().setAll(titleBar, taskListPane);
        }

        BorderPane bottom = new BorderPane();
        this.setBottom(bottom);
        bottom.setPadding(new Insets(0, 8, 8, 8));
        {
            lblProgress = new Label();
            bottom.setLeft(lblProgress);

            btnCancel = new JFXButton(i18n("button.cancel"));
            btnCancel.getStyleClass().add("dialog-cancel");
            bottom.setRight(btnCancel);
        }

        setCancel(cancel);

        btnCancel.setDisable(onCancel.getCancellationAction() == null);
        btnCancel.setOnAction(e -> {
            if (cancelAction != null) {
                cancelAction.run();
                return;
            }
            if (executor != null)
                executor.cancel();
            onCancel.getCancellationAction().accept(this);
        });

        speedEventHandler = FetchTask.SPEED_EVENT.registerWeak(speedEvent -> {
            String message = I18n.formatSpeed(speedEvent.getSpeed());
            Platform.runLater(() -> lblProgress.setText(message));
        });

        escAction = btnCancel::fire;
        onEscPressed(this, () -> {
            if (escAction != null) {
                escAction.run();
            }
        });

        // The dialog is only a view onto the task: once closed, stop listening to the executor so
        // discarded panes don't pile up as listeners on long-running managed tasks.
        addEventHandler(DialogCloseEvent.CLOSE, e -> detachExecutorListeners());
    }

    public void setExecutor(TaskExecutor executor) {
        setExecutor(executor, true);
    }

    public void setExecutor(TaskExecutor executor, boolean autoClose) {
        detachExecutorListeners();
        this.executor = executor;

        if (executor != null) {
            taskListPane.setExecutor(executor);

            if (autoClose) {
                autoCloseListener = new TaskListener() {
                    @Override
                    public void onStop(boolean success, TaskExecutor executor) {
                        Platform.runLater(() -> fireEvent(new DialogCloseEvent()));
                    }
                };
                executor.addTaskListener(autoCloseListener);
            }
        }
    }

    /// Removes every listener this pane attached to the executor. A managed task outlives its dialog
    /// (the dialog is just a detachable view), so a closed pane must stop listening — otherwise each
    /// re-opened detail dialog would leave stale listeners reacting to task events forever.
    private void detachExecutorListeners() {
        if (executor != null && autoCloseListener != null)
            executor.removeTaskListener(autoCloseListener);
        autoCloseListener = null;
        taskListPane.detach();
    }

    public StringProperty titleProperty() {
        return lblTitle.textProperty();
    }

    public String getTitle() {
        return lblTitle.getText();
    }

    public void setTitle(String currentState) {
        lblTitle.setText(currentState);
    }

    public void setCancel(TaskCancellationAction onCancel) {
        this.onCancel = onCancel;

        runInFX(() -> btnCancel.setDisable(onCancel == null));
    }

    public void refreshTaskList() {
        taskListPane.refresh();
    }

    public void setCancelAction(Runnable action) {
        this.cancelAction = action;
    }

    public void setCancelText(String text) {
        btnCancel.setText(text);
    }

    private Label lblWaiting;

    public void setWaitingForBackground(boolean waiting) {
        if (waiting) {
            if (lblWaiting == null) {
                lblWaiting = new Label(i18n("task.waiting_for_background"));
                lblWaiting.getStyleClass().add("task-empty-label");
                lblWaiting.setWrapText(true);
            }
            taskListPane.setVisible(false);
            taskListPane.setManaged(false);
            lblProgress.setVisible(false);
            lblProgress.setManaged(false);
            VBox center = (VBox) getCenter();
            if (!center.getChildren().contains(lblWaiting)) // repeated true→true calls must not add twice
                center.getChildren().add(lblWaiting);
        } else {
            taskListPane.setVisible(true);
            taskListPane.setManaged(true);
            lblProgress.setVisible(true);
            lblProgress.setManaged(true);
            lblProgress.setText("");
            if (lblWaiting != null) {
                ((VBox) getCenter()).getChildren().remove(lblWaiting);
            }
        }
    }
}
