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

import com.jfoenix.controls.JFXRadioButton;
import javafx.geometry.Insets;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import org.jackhuang.hmcl.ui.FXUtils;

public final class LineRadioButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-radio-button";

    private final JFXRadioButton radioButton;

    public LineRadioButton(ToggleGroup group) {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.radioButton = new JFXRadioButton();
        this.radioButton.setToggleGroup(group);
        FXUtils.setLimitHeight(radioButton, 30);
        setNode(IDX_LEADING, radioButton);
        container.setPadding(Insets.EMPTY);
    }

    @Override
    public void fire() {
        radioButton.fire();
        super.fire();
    }

    public RadioButton getRadioButton() {
        return radioButton;
    }
}
