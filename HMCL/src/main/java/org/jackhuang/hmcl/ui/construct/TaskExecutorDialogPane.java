/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXProgressBar;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TaskListPane;

import java.util.Optional;

public class TaskExecutorDialogPane extends StackPane {
    private TaskExecutor executor;

    @FXML
    private JFXProgressBar pgsTasks;
    @FXML
    private Label lblCurrentState;
    @FXML
    private Label lblSteps;
    @FXML
    private JFXButton btnCancel;
    @FXML
    private TaskListPane taskListPane;

    public TaskExecutorDialogPane(Runnable cancel) {
        FXUtils.loadFXML(this, "/assets/fxml/launching-steps.fxml");

        FXUtils.limitHeight(this, 200);
        FXUtils.limitWidth(this, 400);

        if (cancel == null)
            btnCancel.setDisable(true);

        btnCancel.setOnMouseClicked(e -> {
            Optional.ofNullable(executor).ifPresent(TaskExecutor::cancel);
            cancel.run();
        });
    }

    public void setExecutor(TaskExecutor executor) {
        this.executor = executor;
        taskListPane.setExecutor(executor);
    }

    public StringProperty currentStateProperty() {
        return lblCurrentState.textProperty();
    }

    public String getCurrentState() {
        return lblCurrentState.getText();
    }

    public void setCurrentState(String currentState) {
        lblCurrentState.setText(currentState);
    }

    public StringProperty stepsProperty() {
        return lblSteps.textProperty();
    }

    public String getSteps() {
        return lblSteps.getText();
    }

    public void setSteps(String steps) {
        lblSteps.setText(steps);
    }

    public void setProgress(double progress) {
        if (progress == Double.MAX_VALUE)
            pgsTasks.setVisible(false);
        else
            pgsTasks.setProgress(progress);
    }
}
