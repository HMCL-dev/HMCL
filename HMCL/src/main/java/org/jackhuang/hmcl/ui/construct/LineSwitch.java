// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXSwitch;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jetbrains.annotations.NotNullByDefault;

/// A switch row for a boolean setting.
@NotNullByDefault
public final class LineSwitch extends LineButtonBase {
    /// The style class applied to switch rows.
    private static final String DEFAULT_STYLE_CLASS = "line-switch";

    /// The switch that edits the selected state.
    private final JFXSwitch switchControl;

    /// Creates an unselected switch row.
    public LineSwitch() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.switchControl = new JFXSwitch();
        switchControl.selectedProperty().bindBidirectional(selectedProperty());
        switchControl.accessibleTextProperty().bind(titleProperty());
        setTrailing(switchControl);
    }

    /// The selected setting value.
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");

    /// Sets the selected setting value.
    public void setSelected(boolean selected) {
        selectedProperty().set(selected);
    }

    /// Returns whether the setting is selected.
    public boolean isSelected() {
        return selectedProperty().get();
    }

    /// Returns the selected-state property bound to the switch.
    public BooleanProperty selectedProperty() {
        return selected;
    }

    /// Toggles the selected setting and fires a single row action unless the row is disabled.
    @Override
    public void fire() {
        if (isDisabled())
            return;
        setSelected(!isSelected());
        super.fire();
    }
}
