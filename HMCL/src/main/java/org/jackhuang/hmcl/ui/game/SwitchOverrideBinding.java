// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.game;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3SwitchSettingItem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Connects a switch displaying the effective setting to its local value and override state.
@NotNullByDefault
final class SwitchOverrideBinding {
    /// The switch row displaying the effective value.
    final M3SwitchSettingItem row = new M3SwitchSettingItem();
    /// The local value written when an override is active.
    final BooleanProperty rawValue = new SimpleBooleanProperty();
    /// Whether the setting overrides its parent.
    final BooleanProperty overridden = new SimpleBooleanProperty();

    /// Installs an optional inheritance action alongside the headline.
    SwitchOverrideBinding(@Nullable M3IconButton inheritanceButton) {
        row.setOnToggleRequest(event -> {
            event.consume();
            rawValue.set(event.getRequestedValue());
            overridden.set(true);
            row.fireEvent(new ActionEvent(row, row));
        });
        if (inheritanceButton != null) {
            row.setHeadlineTrailing(inheritanceButton);
            inheritanceButton.setOnAction(event -> {
                event.consume();
                if (overridden.get()) {
                    overridden.set(false);
                } else {
                    rawValue.set(row.isSelected());
                    overridden.set(true);
                }
                row.fireEvent(new ActionEvent(row, row));
            });
            overridden.addListener(observable ->
                    GameSettingsPage.updateInheritanceButton(inheritanceButton, !overridden.get()));
        }
    }
}
