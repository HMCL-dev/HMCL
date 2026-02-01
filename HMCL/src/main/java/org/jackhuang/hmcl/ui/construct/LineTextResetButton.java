/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.jfoenix.controls.JFXTextField;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

public class LineTextResetButton extends LineButtonBase {
    JFXTextField textField = new JFXTextField();
    JFXButton resetButton = new JFXButton();

    public LineTextResetButton() {
        resetButton.setFocusTraversable(false);
        resetButton.setGraphic(SVG.RESTORE.createIcon(24));
        resetButton.getStyleClass().add("toggle-icon4");

        HBox rightHBox = new HBox(textField, resetButton);
        rightHBox.setSpacing(8);
        rightHBox.setAlignment(Pos.CENTER_LEFT);
        root.setRight(rightHBox);

        FXUtils.onClicked(container, () -> textField.requestFocus());
    }

    public JFXTextField getTextField() {
        return textField;
    }

    public JFXButton getResetButton() {
        return resetButton;
    }
}
