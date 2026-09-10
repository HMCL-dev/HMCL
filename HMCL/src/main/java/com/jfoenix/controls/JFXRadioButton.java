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

import com.jfoenix.skins.JFXRadioButtonSkin;
import javafx.css.*;
import javafx.css.converter.ColorConverter;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// JFXRadioButton is the material design implementation of a radio button.
///
/// @author Bashir Elias & Shadi Shaheen
/// @version 1.0
/// @since 2016-03-09
public class JFXRadioButton extends RadioButton {

    public JFXRadioButton(String text) {
        super(text);
        initialize();
    }

    public JFXRadioButton() {
        initialize();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXRadioButtonSkin(this);
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /// Initialize the style class to 'jfx-radio-button'.
    ///
    /// This is the selector class from which CSS can be used to style
    /// this control.
    private static final String DEFAULT_STYLE_CLASS = "jfx-radio-button";

    /// default color used when the radio button is selected
    private StyleableObjectProperty<Color> selectedColor;

    private static final Color DEFAULT_SELECTED_COLOR = Color.valueOf("#0F9D58");

    public final StyleableObjectProperty<Color> selectedColorProperty() {
        if (selectedColor == null) {
            selectedColor = new SimpleStyleableObjectProperty<>(StyleableProperties.SELECTED_COLOR,
                    JFXRadioButton.this,
                    "selectedColor",
                    DEFAULT_SELECTED_COLOR);
        }
        return this.selectedColor;
    }

    public final Color getSelectedColor() {
        return selectedColor == null ? DEFAULT_SELECTED_COLOR : this.selectedColorProperty().get();
    }

    public final void setSelectedColor(final Color selectedColor) {
        this.selectedColorProperty().set(selectedColor);
    }

    /// default color used when the radio button is not selected
    private StyleableObjectProperty<Color> unSelectedColor;

    private static final Color DEFAULT_UNSELECTED_COLOR = Color.valueOf("#5A5A5A");

    public final StyleableObjectProperty<Color> unSelectedColorProperty() {
        if (unSelectedColor == null) {
            unSelectedColor = new SimpleStyleableObjectProperty<>(
                    StyleableProperties.UNSELECTED_COLOR,
                    JFXRadioButton.this,
                    "unSelectedColor",
                    DEFAULT_UNSELECTED_COLOR);
        }
        return this.unSelectedColor;
    }

    public final Color getUnSelectedColor() {
        return unSelectedColor == null ? DEFAULT_UNSELECTED_COLOR : this.unSelectedColorProperty().get();
    }

    public final void setUnSelectedColor(final Color unSelectedColor) {
        this.unSelectedColorProperty().set(unSelectedColor);
    }

    private static final class StyleableProperties {
        private static final CssMetaData<JFXRadioButton, Color> SELECTED_COLOR =
                new CssMetaData<JFXRadioButton, Color>("-jfx-selected-color",
                        ColorConverter.getInstance(), DEFAULT_SELECTED_COLOR) {
                    @Override
                    public boolean isSettable(JFXRadioButton control) {
                        return control.selectedColor == null || !control.selectedColor.isBound();
                    }

                    @Override
                    public StyleableProperty<Color> getStyleableProperty(JFXRadioButton control) {
                        return control.selectedColorProperty();
                    }
                };
        private static final CssMetaData<JFXRadioButton, Color> UNSELECTED_COLOR =
                new CssMetaData<JFXRadioButton, Color>("-jfx-unselected-color",
                        ColorConverter.getInstance(), DEFAULT_UNSELECTED_COLOR) {
                    @Override
                    public boolean isSettable(JFXRadioButton control) {
                        return control.unSelectedColor == null || !control.unSelectedColor.isBound();
                    }

                    @Override
                    public StyleableProperty<Color> getStyleableProperty(JFXRadioButton control) {
                        return control.unSelectedColorProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RadioButton.getClassCssMetaData());
            Collections.addAll(styleables,
                    SELECTED_COLOR,
                    UNSELECTED_COLOR
            );
            CHILD_STYLEABLES = List.copyOf(styleables);
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
