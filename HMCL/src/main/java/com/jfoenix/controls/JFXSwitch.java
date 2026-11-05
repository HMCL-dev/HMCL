// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.skins.JFXSwitchSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// A Material Design 3 switch for a boolean setting.
///
/// Supports clicking, dragging, and Space-key activation. Labels belong to the surrounding layout;
/// supply an accessible text description. Size and colors are configurable through CSS.
@NotNullByDefault
public class JFXSwitch extends Control {

    /// Creates an unselected switch.
    public JFXSwitch() {
        getStyleClass().setAll("jfx-switch");
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
    }

    /// Selection state.
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected") {
        /// Updates visual and accessible selection without firing an action.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(SELECTED, get());
            notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
        }
    };

    /// Changes selection without firing an action event.
    public final void setSelected(boolean value) {
        selected.set(value);
    }

    /// Returns whether the switch is selected.
    public final boolean isSelected() {
        return selected.get();
    }

    /// Returns the bindable selection state; the default is false.
    public final BooleanProperty selectedProperty() {
        return selected;
    }

    /// Read-only activation state.
    private final ReadOnlyBooleanWrapper armed = new ReadOnlyBooleanWrapper(this, "armed") {
        /// Reflects input feedback in CSS.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(ARMED, get());
        }
    };

    /// Returns whether the switch is armed.
    public final boolean isArmed() {
        return armed.get();
    }

    /// Returns the read-only state indicating a pending activation.
    public final ReadOnlyBooleanProperty armedProperty() {
        return armed.getReadOnlyProperty();
    }

    /// Optional action handler.
    private @Nullable ObjectProperty<@Nullable EventHandler<ActionEvent>> onAction;

    /// Sets or clears the action handler.
    public final void setOnAction(@Nullable EventHandler<ActionEvent> handler) {
        if (onAction != null || handler != null)
            onActionProperty().set(handler);
    }

    /// Returns the action handler, or null when none is installed.
    public final @Nullable EventHandler<ActionEvent> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /// Returns the handler property for activation events.
    public final ObjectProperty<@Nullable EventHandler<ActionEvent>> onActionProperty() {
        if (onAction == null) {
            onAction = new SimpleObjectProperty<>(this, "onAction") {
                /// Updates the action handler.
                @Override
                protected void invalidated() {
                    setEventHandler(ActionEvent.ACTION, get());
                }
            };
        }
        return onAction;
    }

    /// The requested track height in logical pixels.
    private final StyleableDoubleProperty size = new SimpleStyleableDoubleProperty(SIZE, this, "size", 32.0) {
        /// Validates the size and requests layout.
        @Override
        protected void invalidated() {
            get();
            requestLayout();
        }
    };

    /// Sets the track height; see [#sizeProperty()].
    public final void setSize(double value) {
        size.set(value);
    }

    /// Returns the requested track height in logical pixels.
    public final double getSize() {
        return size.get();
    }

    /// Track height in logical pixels, styled by `-jfx-size`; the default is 32.
    /// Scales the artwork and interaction target proportionally; nonpositive or nonfinite sizes render at the default size.
    public final StyleableDoubleProperty sizeProperty() {
        return size;
    }

    /// Optional selected-icon property.
    private @Nullable BooleanProperty showSelectedIcon;

    /// Sets the value of [#showSelectedIconProperty()].
    public final void setShowSelectedIcon(boolean value) {
        if (showSelectedIcon != null || value)
            showSelectedIconProperty().set(value);
    }

    /// Returns whether the selected icon is shown.
    public final boolean isShowSelectedIcon() {
        return showSelectedIcon != null && showSelectedIcon.get();
    }

    /// Whether to show a check mark when selected; defaults to false.
    public final BooleanProperty showSelectedIconProperty() {
        if (showSelectedIcon == null)
            showSelectedIcon = createOptionProperty("showSelectedIcon", false, false);
        return showSelectedIcon;
    }

    /// Optional unselected-icon property.
    private @Nullable BooleanProperty showUnselectedIcon;

    /// Sets the value of [#showUnselectedIconProperty()].
    public final void setShowUnselectedIcon(boolean value) {
        if (showUnselectedIcon != null || value)
            showUnselectedIconProperty().set(value);
    }

    /// Returns whether the unselected icon is shown.
    public final boolean isShowUnselectedIcon() {
        return showUnselectedIcon != null && showUnselectedIcon.get();
    }

    /// Whether to show a cross when unselected; defaults to false.
    public final BooleanProperty showUnselectedIconProperty() {
        if (showUnselectedIcon == null)
            showUnselectedIcon = createOptionProperty("showUnselectedIcon", false, false);
        return showUnselectedIcon;
    }

    /// Optional animation policy override.
    private @Nullable BooleanProperty disableAnimation;

    /// Sets the value of [#disableAnimationProperty()].
    public final void setDisableAnimation(boolean value) {
        if (disableAnimation != null || value == AnimationUtils.isAnimationEnabled())
            disableAnimationProperty().set(value);
    }

    /// Returns whether animation is disabled.
    public final boolean isDisableAnimation() {
        return disableAnimation == null ? !AnimationUtils.isAnimationEnabled() : disableAnimation.get();
    }

    /// Disables animation, defaulting to the inverse of the global animation-enabled setting.
    public final BooleanProperty disableAnimationProperty() {
        if (disableAnimation == null)
            disableAnimation = createOptionProperty("disableAnimation", !AnimationUtils.isAnimationEnabled(), true);
        return disableAnimation;
    }

    /// Arms input feedback unless the switch is disabled or its selection is bound.
    public final void arm() {
        if (!isDisabled() && !selected.isBound())
            armed.set(true);
    }

    /// Clears input feedback without firing an action.
    public final void disarm() {
        armed.set(false);
    }

    /// Toggles selection and fires one action; disabled or bound switches ignore activation.
    public void fire() {
        if (!isDisabled() && !selected.isBound()) {
            setSelected(!isSelected());
            fireEvent(new ActionEvent(this, this));
        }
    }

    /// Creates the default switch skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXSwitchSkin(this);
    }

    /// Creates a property that updates the skin, optionally settling active animations.
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

    /// Indicates that the switch is selected.
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /// Indicates a pending activation.
    private static final PseudoClass ARMED = PseudoClass.getPseudoClass("armed");

    /// Factory for switch CSS metadata.
    private static final StyleablePropertyFactory<JFXSwitch> STYLEABLES =
            new StyleablePropertyFactory<>(Control.getClassCssMetaData());

    /// CSS metadata for track height.
    private static final CssMetaData<JFXSwitch, Number> SIZE =
            STYLEABLES.createSizeCssMetaData("-jfx-size", control -> control.size, 32.0);

    /// Switch and inherited CSS metadata.
    private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> CSS_META_DATA =
            List.copyOf(STYLEABLES.getCssMetaData());

    /// Returns immutable CSS metadata, including inherited control properties.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS_META_DATA;
    }

    /// Returns the metadata used to style this switch.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /// Exposes selection to assistive technology.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, @Nullable Object... parameters) {
        return attribute == AccessibleAttribute.SELECTED ? isSelected() : super.queryAccessibleAttribute(attribute, parameters);
    }

    /// Handles accessible activation.
    @Override
    public void executeAccessibleAction(AccessibleAction action, @Nullable Object... parameters) {
        if (action == AccessibleAction.FIRE)
            fire();
        else
            super.executeAccessibleAction(action, parameters);
    }
}
