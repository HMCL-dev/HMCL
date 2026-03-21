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
import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

public class LineToggleResetButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-toggle-button";
    private static final int IDX_RESET_BUTTON = IDX_TRAILING + 1;

    private final JFXToggleButton toggleButton;
    private final JFXButton resetButton;
    private final StackPane resetButtonWrapperPane;

    public LineToggleResetButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.toggleButton = new JFXToggleButton();
        toggleButton.selectedProperty().bindBidirectional(selectedProperty());
        toggleButton.setSize(8);
        FXUtils.setLimitHeight(toggleButton, 30);
        setNode(IDX_TRAILING, toggleButton);

        resetButton = new JFXButton();
        resetButton.setGraphic(SVG.RESTORE.createIcon(24));
        resetButton.getStyleClass().add("toggle-icon4");
        resetButton.setFocusTraversable(false); // Prevent the focus from automatically moving to other node when it is disabled after clicking.
        resetButtonWrapperPane = new StackPane(resetButton);
        resetButtonWrapperPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setNode(IDX_RESET_BUTTON, resetButtonWrapperPane);

        resetButton.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            Boolean def = defaultSelect.get();
            return def == null || def.equals(selected.get());
        }, defaultSelect, selected));

        resetButton.setOnAction(e -> {
            if (getDefaultSelect() != null) {
                setSelected(getDefaultSelect());
            }
        });
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

    private final ObjectProperty<Boolean> defaultSelect = new SimpleObjectProperty<>(this, "defaultSelect", null);

    public ObjectProperty<Boolean> defaultSelectProperty() {
        return defaultSelect;
    }

    public Boolean getDefaultSelect() {
        return defaultSelectProperty().get();
    }

    public void setDefaultSelect(Boolean defaultSelected) {
        defaultSelectProperty().set(defaultSelected);
    }

    public void setResetButtonTooltipWhenEnable(String tooltip) {
        FXUtils.installFastTooltip(resetButton, tooltip);
    }

    public void setResetButtonTooltipWhenDisable(String tooltip) {
        FXUtils.installFastTooltip(resetButtonWrapperPane, tooltip);
    }
}
