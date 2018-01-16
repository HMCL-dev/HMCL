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

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import org.jackhuang.hmcl.Main;
import javax.swing.UIManager;

import java.util.Optional;

public final class MessageBox {
    private MessageBox() {
    }

    private static final String TITLE = Main.i18n("message.info");

    /**
     * User Operation: Yes
     */
    public static final int YES_OPTION = 0;

    /**
     * User Operation: No
     */
    public static final int NO_OPTION = 1;

    /**
     * User Operation: Cancel
     */
    public static final int CANCEL_OPTION = 2;

    /**
     * User Operation: OK
     */
    public static final int OK_OPTION = 0;

    /**
     * User Operation: Closed Message Box
     */
    public static final int CLOSED_OPTION = -1;

    /**
     * Buttons: Yes No
     */
    public static final int YES_NO_OPTION = 10;
    /**
     * Buttons: Yes No Cancel
     */
    public static final int YES_NO_CANCEL_OPTION = 11;
    /**
     * Buttons: OK Cancel
     */
    public static final int OK_CANCEL_OPTION = 12;

    /**
     * Message Box Type: Error
     */
    public static final int ERROR_MESSAGE = 0;
    /**
     * Message Box Type: Info
     */
    public static final int INFORMATION_MESSAGE = 1;
    /**
     * Message Box Type: Warning
     */
    public static final int WARNING_MESSAGE = 2;
    /**
     * Message Box Type: Question
     */
    public static final int QUESTION_MESSAGE = 3;
    /**
     * Message Box Type: Plain
     */
    public static final int PLAIN_MESSAGE = -1;


    public static void show(String message) {
        show(message, TITLE);
    }

    /**
     * Show MsgBox with title and options
     *
     * @param message The Message
     * @param title   The title of MsgBox.
     * @return user operation.
     */
    public static void show(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static int confirm(String message, String title) {
        return confirm(message, title, -1);
    }

    public static int confirm(String message, int option) {
        return confirm(message, TITLE, option);
    }

    public static int confirm(String message, String title, int option) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        switch (option) {
            case YES_NO_OPTION:
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                break;
            case YES_NO_CANCEL_OPTION:
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                break;
            case OK_CANCEL_OPTION:
                alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                break;
        }
        Optional<ButtonType> buttonType = alert.showAndWait();
        if (!buttonType.isPresent()) return CLOSED_OPTION;
        else if (buttonType.get() == ButtonType.OK) return OK_OPTION;
        else if (buttonType.get() == ButtonType.YES) return YES_OPTION;
        else if (buttonType.get() == ButtonType.NO) return NO_OPTION;
        else if (buttonType.get() == ButtonType.CANCEL) return CANCEL_OPTION;
        else throw new IllegalStateException("Unrecognized button type:" + buttonType.get());
    }

    public static Optional<String> input(String message) {
        return input(message, UIManager.getString("OptionPane.inputDialogTitle"));
    }

    public static Optional<String> input(String message, String title) {
        return input(message, title, "");
    }

    public static Optional<String> input(String message, String title, String initialValue) {
        TextInputDialog dialog = new TextInputDialog(initialValue);
        dialog.setTitle(title);
        dialog.setHeaderText(message);
        dialog.setContentText(message);

        return dialog.showAndWait();
    }
}
