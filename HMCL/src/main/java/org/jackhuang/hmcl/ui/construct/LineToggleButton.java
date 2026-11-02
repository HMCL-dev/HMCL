// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXSwitch;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;

/// A settings row whose switch is bidirectionally bound to its selected state.
@NotNullByDefault
public final class LineToggleButton extends LineButtonBase {
    /// The style class applied to toggle rows.
    private static final String DEFAULT_STYLE_CLASS = "line-toggle-button";

    /// The switch that edits the selected state.
    private final JFXSwitch toggleButton;

    /// Creates a row with a directly attached toggle bound to the selected state.
    public LineToggleButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.toggleButton = new JFXSwitch();
        toggleButton.selectedProperty().bindBidirectional(selectedProperty());
        toggleButton.setDisableAnimation(!AnimationUtils.isAnimationEnabled());
        toggleButton.accessibleTextProperty().bind(titleProperty());
        setTrailing(toggleButton);
    }

    /// Activates the switch and then fires the row action.
    @Override
    public void fire() {
        toggleButton.fire();
        super.fire();
    }

    /// The selected setting value shared with the switch.
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");

    /// Returns the selected-state property bound to the switch.
    public BooleanProperty selectedProperty() {
        return selected;
    }

    /// Returns whether the setting is selected.
    public boolean isSelected() {
        return selectedProperty().get();
    }

    /// Sets the selected setting value and updates the switch.
    public void setSelected(boolean selected) {
        selectedProperty().set(selected);
    }
}
