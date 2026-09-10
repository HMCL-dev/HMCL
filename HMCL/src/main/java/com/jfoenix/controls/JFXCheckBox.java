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

import com.jfoenix.skins.JFXCheckBoxSkin;
import javafx.css.*;
import javafx.css.converter.PaintConverter;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// JFXCheckBox is the material design implementation of a checkbox.
/// it shows ripple effect and a custom selection animation.
///
/// @author Shadi Shaheen
/// @version 1.0
/// @since 2016-03-09
public class JFXCheckBox extends CheckBox {

    public JFXCheckBox(String text) {
        super(text);
        initialize();
    }

    public JFXCheckBox() {
        initialize();
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXCheckBoxSkin(this);
    }

    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    /// Initialize the style class to 'jfx-check-box'.
    ///
    /// This is the selector class from which CSS can be used to style
    /// this control.
    private static final String DEFAULT_STYLE_CLASS = "jfx-check-box";

    /// checkbox color property when selected
    private StyleableObjectProperty<Paint> checkedColor;

    private static final Color DEFAULT_CHECKED_COLOR = Color.valueOf("#0F9D58");

    public StyleableObjectProperty<Paint> checkedColorProperty() {
        if (checkedColor == null) {
            checkedColor = new SimpleStyleableObjectProperty<>(StyleableProperties.CHECKED_COLOR,
                    JFXCheckBox.this,
                    "checkedColor",
                    DEFAULT_CHECKED_COLOR);
        }
        return this.checkedColor;
    }

    public Paint getCheckedColor() {
        return checkedColor == null ? DEFAULT_CHECKED_COLOR : checkedColor.get();
    }

    public void setCheckedColor(Paint color) {
        this.checkedColor.set(color);
    }

    /**
     * checkbox color property when not selected
     */
    private StyleableObjectProperty<Paint> unCheckedColor;

    private static final Color DEFAULT_UNCHECKED_COLOR = Color.valueOf("#5A5A5A");

    public StyleableObjectProperty<Paint> unCheckedColorProperty() {
        if (unCheckedColor == null) {
            unCheckedColor = new SimpleStyleableObjectProperty<>(StyleableProperties.UNCHECKED_COLOR,
                    JFXCheckBox.this,
                    "unCheckedColor",
                    DEFAULT_UNCHECKED_COLOR);
        }
        return this.unCheckedColor;
    }

    public Paint getUnCheckedColor() {
        return unCheckedColor == null ? DEFAULT_UNCHECKED_COLOR : unCheckedColor.get();
    }

    public void setUnCheckedColor(Paint color) {
        this.unCheckedColor.set(color);
    }

    private static final class StyleableProperties {
        private static final CssMetaData<JFXCheckBox, Paint> CHECKED_COLOR =
                new CssMetaData<>("-jfx-checked-color",
                        PaintConverter.getInstance(), DEFAULT_CHECKED_COLOR) {
                    @Override
                    public boolean isSettable(JFXCheckBox control) {
                        return control.checkedColor == null || !control.checkedColor.isBound();
                    }

                    @Override
                    public StyleableProperty<Paint> getStyleableProperty(JFXCheckBox control) {
                        return control.checkedColorProperty();
                    }
                };
        private static final CssMetaData<JFXCheckBox, Paint> UNCHECKED_COLOR =
                new CssMetaData<>("-jfx-unchecked-color",
                        PaintConverter.getInstance(), DEFAULT_UNCHECKED_COLOR) {
                    @Override
                    public boolean isSettable(JFXCheckBox control) {
                        return control.unCheckedColor == null || !control.unCheckedColor.isBound();
                    }

                    @Override
                    public StyleableProperty<Paint> getStyleableProperty(JFXCheckBox control) {
                        return control.unCheckedColorProperty();
                    }
                };
        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(CheckBox.getClassCssMetaData());
            Collections.addAll(styleables,
                    CHECKED_COLOR,
                    UNCHECKED_COLOR
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
