// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.ui.FXUtils;

public final class LineToggleButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-toggle-button";

    private final JFXToggleButton toggleButton;

    /// Creates a row with a directly attached toggle bound to the selected state.
    public LineToggleButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.toggleButton = new JFXToggleButton();
        toggleButton.selectedProperty().bindBidirectional(selectedProperty());
        toggleButton.setSize(8);
        FXUtils.setLimitHeight(toggleButton, 30);
        container.setTrailing(toggleButton);
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
