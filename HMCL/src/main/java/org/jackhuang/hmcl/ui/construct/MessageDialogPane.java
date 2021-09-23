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

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MessageDialogPane extends StackPane {

    public enum MessageType {
        ERROR,
        INFO,
        WARNING,
        QUESTION,
        SUCCESS;

        public String getDisplayName() {
            return i18n("message." + name().toLowerCase(Locale.ROOT));
        }
    }

    @FXML
    private Label content;
    @FXML
    private Label graphic;
    @FXML
    private Label title;
    @FXML
    private HBox actions;

    private @Nullable ButtonBase cancelButton;

    public MessageDialogPane(@NotNull String text, @Nullable String title, @NotNull MessageType type) {
        FXUtils.loadFXML(this, "/assets/fxml/message-dialog.fxml");

        content.setText(text);

        if (title != null)
            this.title.setText(title);

        switch (type) {
            case INFO:
                graphic.setGraphic(SVG.infoCircle(Theme.blackFillBinding(), 40, 40));
                break;
            case ERROR:
                graphic.setGraphic(SVG.closeCircle(Theme.blackFillBinding(), 40, 40));
                break;
            case SUCCESS:
                graphic.setGraphic(SVG.checkCircle(Theme.blackFillBinding(), 40, 40));
                break;
            case WARNING:
                graphic.setGraphic(SVG.alert(Theme.blackFillBinding(), 40, 40));
                break;
            case QUESTION:
                graphic.setGraphic(SVG.helpCircle(Theme.blackFillBinding(), 40, 40));
                break;
            default:
                throw new IllegalArgumentException("Unrecognized message box message type " + type);
        }

        onEscPressed(this, () -> {
            if (cancelButton != null) {
                cancelButton.fire();
            }
        });
    }

    public void addButton(ButtonBase btn) {
        btn.addEventHandler(ActionEvent.ACTION, e -> fireEvent(new DialogCloseEvent()));
        actions.getChildren().add(btn);
    }

    public void setCancelButton(@Nullable ButtonBase btn) {
        cancelButton = btn;
    }

    public static MessageDialogPane ok(String text, String title, MessageType type, Runnable ok) {
        MessageDialogPane dialog = new MessageDialogPane(text, title, type);

        JFXButton btnOk = new JFXButton(i18n("button.ok"));
        btnOk.getStyleClass().add("dialog-accept");
        if (ok != null) {
            btnOk.setOnAction(e -> ok.run());
        }
        dialog.addButton(btnOk);
        dialog.setCancelButton(btnOk);

        return dialog;
    }

    public static MessageDialogPane yesOrNo(String text, String title, MessageType type, Runnable yes, Runnable no) {
        MessageDialogPane dialog = new MessageDialogPane(text, title, type);

        JFXButton btnYes = new JFXButton(i18n("button.yes"));
        btnYes.getStyleClass().add("dialog-accept");
        if (yes != null) {
            btnYes.setOnAction(e -> yes.run());
        }
        dialog.addButton(btnYes);

        JFXButton btnNo = new JFXButton(i18n("button.no"));
        btnNo.getStyleClass().add("dialog-cancel");
        if (no != null) {
            btnNo.setOnAction(e -> no.run());
        }
        dialog.addButton(btnNo);
        dialog.setCancelButton(btnNo);

        return dialog;
    }

    public static MessageDialogPane actionOrCancel(String text, String title, MessageType type, ButtonBase actionButton, Runnable cancel) {
        MessageDialogPane dialog = new MessageDialogPane(text, title, type);
        dialog.addButton(actionButton);

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        if (cancel != null) {
            btnCancel.setOnAction(e -> cancel.run());
        }
        dialog.addButton(btnCancel);
        dialog.setCancelButton(btnCancel);

        return dialog;
    }
}
