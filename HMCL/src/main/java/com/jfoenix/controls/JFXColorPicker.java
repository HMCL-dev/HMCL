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

import com.jfoenix.skins.JFXColorPickerSkin;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableBooleanProperty;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.converter.BooleanConverter;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JFXColorPicker is the metrial design implementation of color picker.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXColorPicker extends ColorPicker {

    /**
     * {@inheritDoc}
     */
    public JFXColorPicker() {
        initialize();
    }

    /**
     * {@inheritDoc}
     */
    public JFXColorPicker(Color color) {
        super(color);
        initialize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXColorPickerSkin(this);
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Initialize the style class to 'jfx-color-picker'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "jfx-color-picker";

    private double[] preDefinedColors = null;

    public double[] getPreDefinedColors() {
        return preDefinedColors;
    }

    public void setPreDefinedColors(double[] preDefinedColors) {
        this.preDefinedColors = preDefinedColors;
    }

    /**
     * disable animation on button action
     */
    private final StyleableBooleanProperty disableAnimation = new SimpleStyleableBooleanProperty(StyleableProperties.DISABLE_ANIMATION,
        JFXColorPicker.this,
        "disableAnimation",
        false);

    public final StyleableBooleanProperty disableAnimationProperty() {
        return this.disableAnimation;
    }

    public final Boolean isDisableAnimation() {
        return disableAnimation != null && this.disableAnimationProperty().get();
    }

    public final void setDisableAnimation(final Boolean disabled) {
        this.disableAnimationProperty().set(disabled);
    }

    private static final class StyleableProperties {

        private static final CssMetaData<JFXColorPicker, Boolean> DISABLE_ANIMATION =
            new CssMetaData<JFXColorPicker, Boolean>("-jfx-disable-animation",
                BooleanConverter.getInstance(), false) {
                @Override
                public boolean isSettable(JFXColorPicker control) {
                    return control.disableAnimation == null || !control.disableAnimation.isBound();
                }

                @Override
                public StyleableBooleanProperty getStyleableProperty(JFXColorPicker control) {
                    return control.disableAnimationProperty();
                }
            };


        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                new ArrayList<>(ColorPicker.getClassCssMetaData());
            Collections.addAll(styleables, DISABLE_ANIMATION);
            CHILD_STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.CHILD_STYLEABLES;
    }
}
