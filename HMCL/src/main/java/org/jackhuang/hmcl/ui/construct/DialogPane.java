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
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXProgressBar;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DialogPane extends JFXDialogLayout {
    private final StringProperty title = new SimpleStringProperty();
    private final BooleanProperty valid = new SimpleBooleanProperty(true);
    protected final SpinnerPane acceptPane = new SpinnerPane();
    protected final JFXButton cancelButton = new JFXButton();
    protected final Label warningLabel = new Label();
    private final JFXProgressBar progressBar = new JFXProgressBar();

    public DialogPane() {
        Label titleLabel = new Label();
        titleLabel.textProperty().bind(title);
        setHeading(titleLabel);
        getChildren().add(progressBar);

        progressBar.setVisible(false);
        StackPane.setMargin(progressBar, new Insets(-24.0D, -24.0D, -16.0D, -24.0D));
        StackPane.setAlignment(progressBar, Pos.TOP_CENTER);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        JFXButton acceptButton = new JFXButton(i18n("button.ok"));
        acceptButton.setOnAction(e -> onAccept());
        acceptButton.disableProperty().bind(valid.not());
        acceptButton.getStyleClass().add("dialog-accept");
        acceptPane.getStyleClass().add("small-spinner-pane");
        acceptPane.setContent(acceptButton);

        cancelButton.setText(i18n("button.cancel"));
        cancelButton.setOnAction(e -> onCancel());
        cancelButton.getStyleClass().add("dialog-cancel");
        onEscPressed(this, cancelButton::fire);

        setActions(warningLabel, acceptPane, cancelButton);
    }

    protected JFXProgressBar getProgressBar() {
        return progressBar;
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public boolean isValid() {
        return valid.get();
    }

    public BooleanProperty validProperty() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid.set(valid);
    }

    protected void onCancel() {
        fireEvent(new DialogCloseEvent());
    }

    protected void onAccept() {
        fireEvent(new DialogCloseEvent());
    }

    protected void setLoading() {
        acceptPane.showSpinner();
        warningLabel.setText("");
    }

    protected void onSuccess() {
        acceptPane.hideSpinner();
        fireEvent(new DialogCloseEvent());
    }

    protected void onFailure(String msg) {
        acceptPane.hideSpinner();
        warningLabel.setText(msg);
    }
}
