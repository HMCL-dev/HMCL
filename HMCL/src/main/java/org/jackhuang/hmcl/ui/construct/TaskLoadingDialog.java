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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jetbrains.annotations.NotNull;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class TaskLoadingDialog extends JFXDialogLayout {
    private final Label lblTitle;

    public TaskLoadingDialog(String title, @NotNull TaskCancellationAction onCancel, TaskExecutor executor) {
        var vbox = new VBox(20);
        vbox.setPadding(new Insets(10));
        vbox.setAlignment(Pos.CENTER);

        lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: BOLD;");

        JFXSpinner spinner = new JFXSpinner();

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));

        if (onCancel.getCancellationAction() != null) {
            cancelButton.setOnAction(event -> onCancel.getCancellationAction().accept(this));
        } else cancelButton.setDisable(true);

        cancelButton.setTextFill(Themes.getColorScheme().getOnSurface());

        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                Platform.runLater(() -> fireEvent(new DialogCloseEvent()));
            }
        });

        vbox.getChildren().addAll(lblTitle, spinner, cancelButton);
        getChildren().setAll(vbox);
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
}
