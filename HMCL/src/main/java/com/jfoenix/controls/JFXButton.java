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

import com.jfoenix.converters.ButtonTypeConverter;
import com.jfoenix.skins.JFXButtonSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.*;
import javafx.css.converter.BooleanConverter;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// JFXButton is the material design implementation of a button.
/// it contains ripple effect , the effect color is set according to text fill of the button 1st
/// or the text fill of graphic node (if it was set to Label) 2nd.
///
/// @author Shadi Shaheen
/// @version 1.0
/// @since 2016-03-09
public class JFXButton extends Button {

    public JFXButton() {
        initialize();
    }

    public JFXButton(String text) {
        super(text);
        initialize();
    }

    public JFXButton(String text, Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXButtonSkin(this);
    }

    /***************************************************************************
     *                                                                         *
     * Properties                                                              *
     *                                                                         *
     **************************************************************************/

    /// the ripple color property of JFXButton.
    private final ObjectProperty<Paint> ripplerFill = new SimpleObjectProperty<>(null);

    public final ObjectProperty<Paint> ripplerFillProperty() {
        return this.ripplerFill;
    }

    /// @return the ripple color
    public final Paint getRipplerFill() {
        return this.ripplerFillProperty().get();
    }

    /// set the ripple color
    ///
    /// @param ripplerFill the color of the ripple effect
    public final void setRipplerFill(final Paint ripplerFill) {
        this.ripplerFillProperty().set(ripplerFill);
    }


    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    /// Initialize the style class to 'jfx-button'.
    ///
    /// This is the selector class from which CSS can be used to style
    /// this control.
    private static final String DEFAULT_STYLE_CLASS = "jfx-button";

    public enum ButtonType {FLAT, RAISED}

    /// according to material design the button has two types:
    /// - flat : only shows the ripple effect upon clicking the button
    /// - raised : shows the ripple effect and change in depth upon clicking the button
    private final StyleableObjectProperty<ButtonType> buttonType = new SimpleStyleableObjectProperty<>(
            StyleableProperties.BUTTON_TYPE,
            JFXButton.this,
            "buttonType",
            ButtonType.FLAT);

    public ButtonType getButtonType() {
        return buttonType == null ? ButtonType.FLAT : buttonType.get();
    }

    public StyleableObjectProperty<ButtonType> buttonTypeProperty() {
        return this.buttonType;
    }

    public void setButtonType(ButtonType type) {
        this.buttonType.set(type);
    }

    /// Disable the visual indicator for focus
    private final StyleableBooleanProperty disableVisualFocus = new SimpleStyleableBooleanProperty(StyleableProperties.DISABLE_VISUAL_FOCUS,
            JFXButton.this,
            "disableVisualFocus",
            false);

    /// Setting this property disables this [JFXButton] from showing keyboard focus.
    ///
    /// @return A property that will disable visual focus if true and enable it if false.
    public final StyleableBooleanProperty disableVisualFocusProperty() {
        return this.disableVisualFocus;
    }

    /// Indicates whether or not this [JFXButton] will show focus when it receives keyboard focus.
    ///
    /// @return False if this [JFXButton] will show visual focus and true if it will not.
    public final Boolean isDisableVisualFocus() {
        return disableVisualFocus != null && this.disableVisualFocusProperty().get();
    }

    /**
     * Setting this to true will disable this {@link JFXButton} from showing focus when it receives keyboard focus.
     *
     * @param disabled True to disable visual focus and false to enable it.
     */
    public final void setDisableVisualFocus(final Boolean disabled) {
        this.disableVisualFocusProperty().set(disabled);
    }

    private static class StyleableProperties {
        private static final CssMetaData<JFXButton, ButtonType> BUTTON_TYPE =
                new CssMetaData<>("-jfx-button-type",
                        ButtonTypeConverter.getInstance(), ButtonType.FLAT) {
                    @Override
                    public boolean isSettable(JFXButton control) {
                        return control.buttonType == null || !control.buttonType.isBound();
                    }

                    @Override
                    public StyleableProperty<ButtonType> getStyleableProperty(JFXButton control) {
                        return control.buttonTypeProperty();
                    }
                };

        private static final CssMetaData<JFXButton, Boolean> DISABLE_VISUAL_FOCUS =
                new CssMetaData<>("-jfx-disable-visual-focus",
                        BooleanConverter.getInstance(), false) {
                    @Override
                    public boolean isSettable(JFXButton control) {
                        return control.disableVisualFocus == null || !control.disableVisualFocus.isBound();
                    }

                    @Override
                    public StyleableBooleanProperty getStyleableProperty(JFXButton control) {
                        return control.disableVisualFocusProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Button.getClassCssMetaData());
            Collections.addAll(styleables, BUTTON_TYPE, DISABLE_VISUAL_FOCUS);
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
