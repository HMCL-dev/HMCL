// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.skins.JFXSwitchSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleButton;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Selects an immediately applied, binary setting using a Material Design 3 switch.
///
/// The inherited selected property represents the setting. Mouse and keyboard activation
/// use [ToggleButton] behavior, including action events and toggle-group semantics.
/// Supply text or an accessible text description when the switch has no visible label.
/// The inherited graphic belongs to the label; it is not used as the handle icon.
///
/// The default skin has a 52 by 32 track within a target at least 48 pixels high.
/// Its colors can be customized through the `-jfx-switch-primary`, `-jfx-switch-on-primary`,
/// `-jfx-switch-primary-container`, `-jfx-switch-on-primary-container`,
/// `-jfx-switch-surface`, `-jfx-switch-surface-container-highest`,
/// `-jfx-switch-on-surface`, `-jfx-switch-on-surface-variant`, and `-jfx-switch-outline`
/// CSS looked-up colors. HMCL's theme supplies these colors automatically.
@NotNullByDefault
public class JFXSwitch extends ToggleButton {

    /// Creates an unselected switch without a label.
    public JFXSwitch() {
        this("");
    }

    /// Creates an unselected switch with the given label.
    ///
    /// @param text the label, or null for no text
    public JFXSwitch(@Nullable String text) {
        super(text);
        getStyleClass().setAll("jfx-switch");
    }

    /// Creates the skin that renders the track, handle, and interaction states.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXSwitchSkin(this);
    }

    /// Returns the default switch stylesheet, including standalone fallback colors.
    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(JFXSwitch.class.getResource("/assets/css/jfx-switch.css")).toExternalForm();
    }

    /// Whether the selected handle displays a check mark. The default is false.
    private final BooleanProperty showSelectedIcon = new SimpleBooleanProperty(this, "showSelectedIcon", false);

    /// Returns the property controlling selected icons.
    public final BooleanProperty showSelectedIconProperty() {
        return showSelectedIcon;
    }

    /// Returns whether the selected icon is shown.
    public final boolean isShowSelectedIcon() {
        return showSelectedIcon.get();
    }

    /// Sets the value of [#showSelectedIconProperty()].
    public final void setShowSelectedIcon(boolean value) {
        showSelectedIcon.set(value);
    }

    /// Whether the unselected handle displays a cross and grows to 24 pixels. The default is false.
    private final BooleanProperty showUnselectedIcon = new SimpleBooleanProperty(this, "showUnselectedIcon", false);

    /// Returns the property controlling unselected icons.
    public final BooleanProperty showUnselectedIconProperty() {
        return showUnselectedIcon;
    }

    /// Returns whether the unselected icon is shown.
    public final boolean isShowUnselectedIcon() {
        return showUnselectedIcon.get();
    }

    /// Sets the value of [#showUnselectedIconProperty()].
    public final void setShowUnselectedIcon(boolean value) {
        showUnselectedIcon.set(value);
    }

    /// Whether handle movement and resizing finish immediately. The default is false.
    private final BooleanProperty disableAnimation = new SimpleBooleanProperty(this, "disableAnimation", false);

    /// Returns the property controlling immediate animation completion.
    public final BooleanProperty disableAnimationProperty() {
        return disableAnimation;
    }

    /// Returns whether animation is disabled.
    public final boolean isDisableAnimation() {
        return disableAnimation.get();
    }

    /// Sets the value of [#disableAnimationProperty()].
    public final void setDisableAnimation(boolean value) {
        disableAnimation.set(value);
    }
}
