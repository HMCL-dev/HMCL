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
import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

public class LineToggleResetButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-toggle-button";
    JFXButton resetButton = new JFXButton();

    public LineToggleResetButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        JFXToggleButton toggleButton = new JFXToggleButton();
        toggleButton.selectedProperty().bindBidirectional(selectedProperty());
        toggleButton.setSize(8);
        FXUtils.setLimitHeight(toggleButton, 30);

        resetButton.setFocusTraversable(false);
        resetButton.setGraphic(SVG.RESTORE.createIcon(24));
        resetButton.getStyleClass().add("toggle-icon4");

        HBox hBox = new HBox(toggleButton, resetButton);
        hBox.setAlignment(Pos.CENTER_LEFT);
        root.setRight(hBox);


        FXUtils.onClicked(container, toggleButton::fire);
    }

    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selectedProperty().get();
    }

    public void setSelected(boolean selected) {
        selectedProperty().set(selected);
    }

    public JFXButton getResetButton() {
        return resetButton;
    }
}
