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

/// Selects an immediately applied, binary setting using a Material Design 3 switch.
///
/// The selected property represents the setting. Clicking or pressing Space toggles it
/// and fires an action event. Enter also activates the switch on non-macOS platforms.
/// Programmatic selection changes do not fire action events.
/// A primary-button drag anywhere on the control moves the handle by the pointer's horizontal
/// displacement. Releasing chooses the nearest end of the track, firing an action only when
/// that changes selection. A press without a drag retains normal click behavior.
/// A bound selected property prevents activation. Disabling the control, losing focus,
/// changing its scene, or pressing Escape cancels input without committing a drag preview.
/// Labels belong to the surrounding layout; supply an accessible text description for this control.
///
/// The default skin has a 52 by 32 track within a target at least 48 pixels high.
/// [#sizeProperty()] controls the track height and scales the artwork and target
/// proportionally.
/// Its colors can be customized through the `-jfx-switch-primary`, `-jfx-switch-on-primary`,
/// `-jfx-switch-primary-container`, `-jfx-switch-on-primary-container`,
/// `-jfx-switch-surface`, `-jfx-switch-surface-container-highest`,
/// `-jfx-switch-on-surface`, `-jfx-switch-on-surface-variant`, and `-jfx-switch-outline`
/// CSS looked-up colors in the application stylesheet.
@NotNullByDefault
public class JFXSwitch extends Control {

    /// Creates an unselected, keyboard-focusable switch with toggle-button accessibility semantics.
    public JFXSwitch() {
        getStyleClass().setAll("jfx-switch");
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
    }

    /// The selected setting, synchronized with CSS and accessibility state.
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

    /// Active input feedback, exposed read-only to callers.
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

    /// Returns whether an input gesture is armed for activation.
    public final ReadOnlyBooleanProperty armedProperty() {
        return armed.getReadOnlyProperty();
    }

    /// Optional action handler, allocated only when requested.
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

    /// Returns the handler property for user or programmatic activation.
    public final ObjectProperty<@Nullable EventHandler<ActionEvent>> onActionProperty() {
        if (onAction == null) {
            onAction = new SimpleObjectProperty<>(this, "onAction") {
                /// Installs the handler in the control's event dispatch chain.
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
        /// Requests layout on every size change, including consecutive bound updates.
        @Override
        protected void invalidated() {
            get();
            requestLayout();
        }
    };

    /// Sets the requested track height; see [#sizeProperty()] for rendering and CSS semantics.
    public final void setSize(double value) {
        size.set(value);
    }

    /// Returns the requested track height in logical pixels.
    public final double getSize() {
        return size.get();
    }

    /// Returns the track-height property, styled by `-jfx-size`. The default is 32 logical pixels.
    /// Artwork, borders, icons, animation distances, and target dimensions scale proportionally.
    /// A value of 24 produces a 39 by 24 track and a 39 by 36 target. Nonpositive or nonfinite values are retained
    /// in this property but rendered using the default size of 32.
    /// Changes request layout without changing selection or restarting an active transition.
    public final StyleableDoubleProperty sizeProperty() {
        return size;
    }

    /// Lazily allocated selected-icon option; null represents the default false value.
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

    /// Returns the property controlling selected icons.
    public final BooleanProperty showSelectedIconProperty() {
        if (showSelectedIcon == null)
            showSelectedIcon = createOptionProperty("showSelectedIcon", false, false);
        return showSelectedIcon;
    }

    /// Lazily allocated unselected-icon option; null represents the default false value.
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

    /// Returns the property controlling unselected icons. When true, the handle displays a cross
    /// and grows to three quarters of the track height instead of one half. The default is false.
    public final BooleanProperty showUnselectedIconProperty() {
        if (showUnselectedIcon == null)
            showUnselectedIcon = createOptionProperty("showUnselectedIcon", false, false);
        return showUnselectedIcon;
    }

    /// Lazily allocated animation option; null uses the inverse of the global animation-enabled setting.
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

    /// Returns the property controlling immediate completion of handle movement, resizing, and colors.
    /// Defaults to the inverse of the global animation-enabled setting.
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

    /// Creates the skin that renders the track, handle, and interaction states.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXSwitchSkin(this);
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

    /// Indicates that the switch is selected.
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /// Indicates that an active input gesture can activate the switch.
    private static final PseudoClass ARMED = PseudoClass.getPseudoClass("armed");

    /// Creates size metadata alongside the inherited control metadata.
    private static final StyleablePropertyFactory<JFXSwitch> STYLEABLES =
            new StyleablePropertyFactory<>(Control.getClassCssMetaData());

    /// Maps the CSS size to the track-height property, respecting property bindings.
    private static final CssMetaData<JFXSwitch, Number> SIZE =
            STYLEABLES.createSizeCssMetaData("-jfx-size", control -> control.size, 32.0);

    /// Immutable metadata for the inherited properties and switch size.
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

    /// Exposes selection to assistive technology while retaining standard Control attributes.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, @Nullable Object... parameters) {
        return attribute == AccessibleAttribute.SELECTED ? isSelected() : super.queryAccessibleAttribute(attribute, parameters);
    }

    /// Routes assistive activation through the same action path as pointer and keyboard input.
    @Override
    public void executeAccessibleAction(AccessibleAction action, @Nullable Object... parameters) {
        if (action == AccessibleAction.FIRE)
            fire();
        else
            super.executeAccessibleAction(action, parameters);
    }
}
