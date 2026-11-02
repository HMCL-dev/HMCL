// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.skins.JFXSwitchSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleButton;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Selects an immediately applied, binary setting using a Material Design 3 switch.
///
/// The inherited selected property represents the setting. Mouse and keyboard activation
/// use [ToggleButton] behavior, including action events and toggle-group semantics.
/// A primary-button drag anywhere on the control moves the handle by the pointer's horizontal
/// displacement. Releasing chooses the nearest end of the track, firing an action only when
/// that changes selection. A press without a drag retains normal click behavior.
/// A bound selected property prevents dragging. Disabling the control or changing its scene
/// cancels an active drag without committing the preview.
/// Supply text or an accessible text description when the switch has no visible label.
/// The inherited graphic belongs to the label; it is not used as the handle icon.
///
/// The default skin has a 52 by 32 track within a target at least 48 pixels high.
/// [#sizeProperty()] controls the track height and scales the artwork and target
/// proportionally. Label content is not scaled.
/// Its colors can be customized through the `-jfx-switch-primary`, `-jfx-switch-on-primary`,
/// `-jfx-switch-primary-container`, `-jfx-switch-on-primary-container`,
/// `-jfx-switch-surface`, `-jfx-switch-surface-container-highest`,
/// `-jfx-switch-on-surface`, `-jfx-switch-on-surface-variant`, and `-jfx-switch-outline`
/// CSS looked-up colors in the application stylesheet.
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

    /// Creates size metadata alongside the inherited toggle-button metadata.
    private static final StyleablePropertyFactory<JFXSwitch> STYLEABLES =
            new StyleablePropertyFactory<>(ToggleButton.getClassCssMetaData());

    /// Maps the CSS size to the track-height property, respecting property bindings.
    private static final CssMetaData<JFXSwitch, Number> SIZE =
            STYLEABLES.createSizeCssMetaData("-jfx-size", control -> control.size, 32.0);

    /// Immutable metadata for the inherited properties and switch size.
    private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> CSS_META_DATA =
            List.copyOf(STYLEABLES.getCssMetaData());

    /// The requested track height in logical pixels.
    private final StyleableDoubleProperty size = new SimpleStyleableDoubleProperty(SIZE, this, "size", 32.0) {
        /// Requests layout on every size change, including consecutive bound updates.
        @Override
        protected void invalidated() {
            get();
            requestLayout();
        }
    };

    /// Returns the track-height property, styled by `-jfx-size`. The default is 32 logical pixels.
    /// Artwork, borders, icons, animation distances, and target dimensions scale proportionally;
    /// text and the label gap retain their sizes. A value of 24 produces a 39 by 24 track
    /// and a 39 by 36 target without a label. Nonpositive or nonfinite values are retained
    /// in this property but rendered using the default size of 32.
    /// Changes request layout without changing selection or restarting an active transition.
    public final StyleableDoubleProperty sizeProperty() {
        return size;
    }

    /// Returns the requested track height in logical pixels.
    public final double getSize() {
        return size.get();
    }

    /// Sets the requested track height; see [#sizeProperty()] for rendering and CSS semantics.
    public final void setSize(double value) {
        size.set(value);
    }

    /// Returns immutable CSS metadata, including inherited toggle-button properties.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS_META_DATA;
    }

    /// Returns the metadata used to style this switch.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /// Lazily allocated selected-icon option; null represents the default false value.
    private @Nullable BooleanProperty showSelectedIcon;

    /// Returns the property controlling selected icons.
    public final BooleanProperty showSelectedIconProperty() {
        if (showSelectedIcon == null)
            showSelectedIcon = createOptionProperty("showSelectedIcon", false, false);
        return showSelectedIcon;
    }

    /// Returns whether the selected icon is shown.
    public final boolean isShowSelectedIcon() {
        return showSelectedIcon != null && showSelectedIcon.get();
    }

    /// Sets the value of [#showSelectedIconProperty()].
    public final void setShowSelectedIcon(boolean value) {
        if (showSelectedIcon != null || value)
            showSelectedIconProperty().set(value);
    }

    /// Lazily allocated unselected-icon option; null represents the default false value.
    private @Nullable BooleanProperty showUnselectedIcon;

    /// Returns the property controlling unselected icons. When true, the handle displays a cross
    /// and grows to three quarters of the track height instead of one half. The default is false.
    public final BooleanProperty showUnselectedIconProperty() {
        if (showUnselectedIcon == null)
            showUnselectedIcon = createOptionProperty("showUnselectedIcon", false, false);
        return showUnselectedIcon;
    }

    /// Returns whether the unselected icon is shown.
    public final boolean isShowUnselectedIcon() {
        return showUnselectedIcon != null && showUnselectedIcon.get();
    }

    /// Sets the value of [#showUnselectedIconProperty()].
    public final void setShowUnselectedIcon(boolean value) {
        if (showUnselectedIcon != null || value)
            showUnselectedIconProperty().set(value);
    }

    /// Lazily allocated animation option; null uses the inverse of the global animation-enabled setting.
    private @Nullable BooleanProperty disableAnimation;

    /// Returns the property controlling immediate completion of handle movement, resizing, and colors.
    /// Defaults to the inverse of the global animation-enabled setting.
    public final BooleanProperty disableAnimationProperty() {
        if (disableAnimation == null)
            disableAnimation = createOptionProperty("disableAnimation", !AnimationUtils.isAnimationEnabled(), true);
        return disableAnimation;
    }

    /// Returns whether animation is disabled.
    public final boolean isDisableAnimation() {
        return disableAnimation == null ? !AnimationUtils.isAnimationEnabled() : disableAnimation.get();
    }

    /// Sets the value of [#disableAnimationProperty()].
    public final void setDisableAnimation(boolean value) {
        if (disableAnimation != null || value == AnimationUtils.isAnimationEnabled())
            disableAnimationProperty().set(value);
    }

    /// Creates an option that notifies the installed default skin without requiring a skin-side listener.
    /// Animation-policy changes settle existing transitions; icon changes retain normal animation.
    private BooleanProperty createOptionProperty(String name, boolean initialValue, boolean finishAnimation) {
        return new SimpleBooleanProperty(this, name, initialValue) {
            /// Validates the value even without a skin, so subsequent changes also reach this callback.
            @Override
            protected void invalidated() {
                get();
                if (getSkin() instanceof JFXSwitchSkin skin)
                    skin.updateOptions(finishAnimation);
            }
        };
    }
}
