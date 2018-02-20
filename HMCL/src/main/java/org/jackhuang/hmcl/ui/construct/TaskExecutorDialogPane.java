/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXProgressBar;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.Optional;

public class TaskExecutorDialogPane extends StackPane {
    private TaskExecutor executor;
    private Runnable onCancel;

    @FXML
    private JFXProgressBar progressBar;
    @FXML
    private Label lblTitle;
    @FXML
    private Label lblSubtitle;
    @FXML
    private JFXButton btnCancel;
    @FXML
    private TaskListPane taskListPane;

    public TaskExecutorDialogPane(Runnable cancel) {
        FXUtils.loadFXML(this, "/assets/fxml/task-dialog.fxml");

        setCancel(cancel);

        btnCancel.setOnMouseClicked(e -> {
            Optional.ofNullable(executor).ifPresent(TaskExecutor::cancel);
            onCancel.run();
        });
    }

    public void setExecutor(TaskExecutor executor) {
        this.executor = executor;
        taskListPane.setExecutor(executor);
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

    public StringProperty subtitleProperty() {
        return lblSubtitle.textProperty();
    }

    public String getSubtitle() {
        return lblSubtitle.getText();
    }

    public void setSubtitle(String subtitle) {
        lblSubtitle.setText(subtitle);
    }

    public void setProgress(double progress) {
        if (progress == Double.MAX_VALUE)
            progressBar.setVisible(false);
        else
            progressBar.setProgress(progress);
    }

    public void setCancel(Runnable onCancel) {
        this.onCancel = onCancel;

        JFXUtilities.runInFX(() -> btnCancel.setDisable(onCancel == null));
    }
}
