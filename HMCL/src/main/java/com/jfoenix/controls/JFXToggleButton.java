/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.controls;

import com.jfoenix.skins.JFXToggleButtonSkin;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableBooleanProperty;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleButton;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A Material Design 3 switch exposed through the historical JFoenix toggle button type.
///
/// The class keeps the `JFXToggleButton` name and selected-state contract used by HMCL, while its default skin
/// renders an M3 switch track, thumb, touch target, state layer, and selection animation.
@NotNullByDefault
public class JFXToggleButton extends ToggleButton {
    /// The legacy style class retained for existing HMCL stylesheets.
    public static final String DEFAULT_STYLE_CLASS = "jfx-toggle-button";

    /// The Material switch style class used by the replacement skin.
    public static final String M3_STYLE_CLASS = "m3-switch";

    /// The default minimum touch target height.
    private static final double DEFAULT_TOUCH_TARGET_SIZE = 40.0;

    /// The default rounded switch track radius.
    private static final double DEFAULT_TRACK_SHAPE = 999.0;

    /// The legacy knob radius that maps to the default M3 switch scale.
    private static final double DEFAULT_SIZE = 8.0;

    /// The styleable minimum touch target height.
    private @Nullable StyleableDoubleProperty touchTargetSize;

    /// The styleable switch track radius.
    private @Nullable StyleableDoubleProperty trackShape;

    /// The legacy styleable knob radius used as the switch scale.
    private @Nullable StyleableDoubleProperty size;

    /// Whether focus state layers should be hidden.
    private @Nullable StyleableBooleanProperty disableVisualFocus;

    /// Whether switch animations should be disabled.
    private @Nullable StyleableBooleanProperty disableAnimation;

    /// Creates an empty switch.
    public JFXToggleButton() {
        initialize();
    }

    /// Creates a switch with text.
    public JFXToggleButton(String text) {
        super(text);
        initialize();
    }

    /// Returns the preferred touch target size.
    public final double getTouchTargetSize() {
        return touchTargetSize == null ? DEFAULT_TOUCH_TARGET_SIZE : touchTargetSize.get();
    }

    /// Sets the preferred touch target size.
    public final void setTouchTargetSize(double touchTargetSize) {
        touchTargetSizeProperty().set(nonNegative(touchTargetSize, "touchTargetSize"));
    }

    /// Returns the preferred touch target size property.
    public final StyleableDoubleProperty touchTargetSizeProperty() {
        if (touchTargetSize == null) {
            touchTargetSize = new SimpleStyleableDoubleProperty(
                    StyleableProperties.TOUCH_TARGET_SIZE,
                    this,
                    "touchTargetSize",
                    DEFAULT_TOUCH_TARGET_SIZE
            ) {
                /// Applies updated layout metrics when the token changes.
                @Override
                protected void invalidated() {
                    set(nonNegative(get(), "touchTargetSize"));
                    updateMetrics();
                }
            };
        }
        return touchTargetSize;
    }

    /// Returns the switch track shape radius.
    public final double getTrackShape() {
        return trackShape == null ? DEFAULT_TRACK_SHAPE : trackShape.get();
    }

    /// Sets the switch track shape radius.
    public final void setTrackShape(double trackShape) {
        trackShapeProperty().set(nonNegative(trackShape, "trackShape"));
    }

    /// Returns the switch track shape radius property.
    public final StyleableDoubleProperty trackShapeProperty() {
        if (trackShape == null) {
            trackShape = new SimpleStyleableDoubleProperty(
                    StyleableProperties.TRACK_SHAPE,
                    this,
                    "trackShape",
                    DEFAULT_TRACK_SHAPE
            ) {
                /// Validates updated track shape values.
                @Override
                protected void invalidated() {
                    set(nonNegative(get(), "trackShape"));
                }
            };
        }
        return trackShape;
    }

    /// Returns the legacy knob radius used to scale the switch.
    public double getSize() {
        return size == null ? DEFAULT_SIZE : size.get();
    }

    /// Sets the legacy knob radius used to scale the switch.
    public void setSize(double size) {
        sizeProperty().set(nonNegative(size, "size"));
    }

    /// Returns the legacy knob radius property used to scale the switch.
    public StyleableDoubleProperty sizeProperty() {
        if (size == null) {
            size = new SimpleStyleableDoubleProperty(
                    StyleableProperties.SIZE,
                    this,
                    "size",
                    DEFAULT_SIZE
            ) {
                /// Applies updated layout metrics when the legacy size changes.
                @Override
                protected void invalidated() {
                    set(nonNegative(get(), "size"));
                    updateMetrics();
                }
            };
        }
        return size;
    }

    /// Returns the visual focus suppression property.
    public final StyleableBooleanProperty disableVisualFocusProperty() {
        if (disableVisualFocus == null) {
            disableVisualFocus = new SimpleStyleableBooleanProperty(
                    StyleableProperties.DISABLE_VISUAL_FOCUS,
                    this,
                    "disableVisualFocus",
                    false
            );
        }
        return disableVisualFocus;
    }

    /// Returns whether focus state layers are hidden.
    public final Boolean isDisableVisualFocus() {
        return disableVisualFocus != null && disableVisualFocus.get();
    }

    /// Sets whether focus state layers should be hidden.
    public final void setDisableVisualFocus(Boolean disabled) {
        disableVisualFocusProperty().set(Objects.requireNonNull(disabled, "disabled"));
    }

    /// Returns the animation suppression property.
    public final StyleableBooleanProperty disableAnimationProperty() {
        if (disableAnimation == null) {
            disableAnimation = new SimpleStyleableBooleanProperty(
                    StyleableProperties.DISABLE_ANIMATION,
                    this,
                    "disableAnimation",
                    !AnimationUtils.isAnimationEnabled()
            );
        }
        return disableAnimation;
    }

    /// Returns whether switch animations are disabled.
    public final Boolean isDisableAnimation() {
        return disableAnimation != null ? disableAnimation.get() : !AnimationUtils.isAnimationEnabled();
    }

    /// Sets whether switch animations should be disabled.
    public final void setDisableAnimation(Boolean disabled) {
        disableAnimationProperty().set(Objects.requireNonNull(disabled, "disabled"));
    }

    /// Creates the default Material Design 3 switch skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXToggleButtonSkin(this);
    }

    /// Returns the CSS metadata for this control class.
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /// Returns the CSS metadata for this control.
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /// Returns accessibility attributes for switch selection state.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        Objects.requireNonNull(attribute, "attribute");
        return switch (attribute) {
            case SELECTED -> isSelected();
            case TOGGLE_STATE -> isSelected()
                    ? AccessibleAttribute.ToggleState.CHECKED
                    : AccessibleAttribute.ToggleState.UNCHECKED;
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    /// Adds base style classes and switch defaults.
    private void initialize() {
        addStyleClass(DEFAULT_STYLE_CLASS);
        addStyleClass(M3_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.CHECK_BOX);
        setAlignment(Pos.CENTER_LEFT);
        setFocusTraversable(true);
        setMnemonicParsing(true);
        updateMetrics();
    }

    /// Adds a style class when it is not already present.
    private void addStyleClass(String styleClass) {
        List<String> styleClasses = getStyleClass();
        if (!styleClasses.contains(styleClass)) {
            styleClasses.add(styleClass);
        }
    }

    /// Applies size-related component tokens to JavaFX layout properties.
    private void updateMetrics() {
        double scaledTrackHeight = 32.0 * getSize() / DEFAULT_SIZE;
        double size = Math.max(getTouchTargetSize(), scaledTrackHeight);
        setMinHeight(size);
        setPrefHeight(size);
    }

    /// Validates that a size token is not negative.
    private static double nonNegative(double value, String name) {
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    /// CSS metadata for switch component tokens.
    @NotNullByDefault
    private static final class StyleableProperties {
        /// CSS metadata for the touch target size token.
        private static final CssMetaData<JFXToggleButton, Number> TOUCH_TARGET_SIZE =
                new CssMetaData<>("-m3-touch-target-size", SizeConverter.getInstance(), DEFAULT_TOUCH_TARGET_SIZE) {
                    /// Returns whether this property can be set by CSS.
                    @Override
                    public boolean isSettable(JFXToggleButton control) {
                        return control.touchTargetSize == null || !control.touchTargetSize.isBound();
                    }

                    /// Returns the styleable property for a control.
                    @Override
                    public StyleableProperty<Number> getStyleableProperty(JFXToggleButton control) {
                        return control.touchTargetSizeProperty();
                    }
                };

        /// CSS metadata for the switch track shape token.
        private static final CssMetaData<JFXToggleButton, Number> TRACK_SHAPE =
                new CssMetaData<>("-m3-track-shape", SizeConverter.getInstance(), DEFAULT_TRACK_SHAPE) {
                    /// Returns whether this property can be set by CSS.
                    @Override
                    public boolean isSettable(JFXToggleButton control) {
                        return control.trackShape == null || !control.trackShape.isBound();
                    }

                    /// Returns the styleable property for a control.
                    @Override
                    public StyleableProperty<Number> getStyleableProperty(JFXToggleButton control) {
                        return control.trackShapeProperty();
                    }
                };

        /// CSS metadata for the legacy switch size token.
        private static final CssMetaData<JFXToggleButton, Number> SIZE =
                new CssMetaData<>("-jfx-size", SizeConverter.getInstance(), DEFAULT_SIZE) {
                    /// Returns whether this property can be set by CSS.
                    @Override
                    public boolean isSettable(JFXToggleButton control) {
                        return control.size == null || !control.size.isBound();
                    }

                    /// Returns the styleable property for a control.
                    @Override
                    public StyleableProperty<Number> getStyleableProperty(JFXToggleButton control) {
                        return control.sizeProperty();
                    }
                };

        /// CSS metadata for the visual focus suppression flag.
        private static final CssMetaData<JFXToggleButton, Boolean> DISABLE_VISUAL_FOCUS =
                new CssMetaData<>("-jfx-disable-visual-focus", BooleanConverter.getInstance(), false) {
                    /// Returns whether this property can be set by CSS.
                    @Override
                    public boolean isSettable(JFXToggleButton control) {
                        return control.disableVisualFocus == null || !control.disableVisualFocus.isBound();
                    }

                    /// Returns the styleable property for a control.
                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(JFXToggleButton control) {
                        return control.disableVisualFocusProperty();
                    }
                };

        /// CSS metadata for the animation suppression flag.
        private static final CssMetaData<JFXToggleButton, Boolean> DISABLE_ANIMATION =
                new CssMetaData<>("-jfx-disable-animation", BooleanConverter.getInstance(), false) {
                    /// Returns whether this property can be set by CSS.
                    @Override
                    public boolean isSettable(JFXToggleButton control) {
                        return control.disableAnimation == null || !control.disableAnimation.isBound();
                    }

                    /// Returns the styleable property for a control.
                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(JFXToggleButton control) {
                        return control.disableAnimationProperty();
                    }
                };

        /// The complete immutable CSS metadata list.
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(ToggleButton.getClassCssMetaData());
            Collections.addAll(
                    styleables,
                    TOUCH_TARGET_SIZE,
                    TRACK_SHAPE,
                    SIZE,
                    DISABLE_VISUAL_FOCUS,
                    DISABLE_ANIMATION
            );
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
