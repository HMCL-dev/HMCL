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

import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.ui.FXUtils;

public final class LineToggleButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-toggle-button";

    private final JFXToggleButton toggleButton;

    public LineToggleButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.toggleButton = new JFXToggleButton();
        toggleButton.selectedProperty().bindBidirectional(selectedProperty());
        toggleButton.setSize(8);
        FXUtils.setLimitHeight(toggleButton, 30);
        setNode(IDX_TRAILING, toggleButton);
    }

    @Override
    public void fire() {
        toggleButton.fire();
        super.fire();
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
}
