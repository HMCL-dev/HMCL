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

import com.jfoenix.assets.JFoenixResources;
import com.jfoenix.controls.base.IFXLabelFloatControl;
import com.jfoenix.skins.JFXTextFieldSkin;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JFXPasswordField is the material design implementation of a password Field.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXPasswordField extends PasswordField implements IFXLabelFloatControl {

    /**
     * {@inheritDoc}
     */
    public JFXPasswordField() {
        initialize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXTextFieldSkin<>(this);
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    /***************************************************************************
     *                                                                         *
     * Properties                                                              *
     *                                                                         *
     **************************************************************************/

    /**
     * wrapper for validation properties / methods
     */
    private final ValidationControl validationControl = new ValidationControl(this);

    @Override
    public ValidatorBase getActiveValidator() {
        return validationControl.getActiveValidator();
    }

    @Override
    public ReadOnlyObjectProperty<ValidatorBase> activeValidatorProperty() {
        return validationControl.activeValidatorProperty();
    }

    @Override
    public ObservableList<ValidatorBase> getValidators() {
        return validationControl.getValidators();
    }

    @Override
    public void setValidators(ValidatorBase... validators) {
        validationControl.setValidators(validators);
    }

    @Override
    public boolean validate() {
        return validationControl.validate();
    }

    @Override
    public void resetValidation() {
        validationControl.resetValidation();
    }

    /***************************************************************************
     *                                                                         *
     * Styleable Properties                                                    *
     *                                                                         *
     **************************************************************************/

    /**
     * Initialize the style class to 'jfx-password-field'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "jfx-password-field";
    private static final String USER_AGENT_STYLESHEET = JFoenixResources.load("css/controls/jfx-password-field.css").toExternalForm();

    /**
     * set true to show a float the prompt text when focusing the field
     */
    private final StyleableBooleanProperty labelFloat = new SimpleStyleableBooleanProperty(StyleableProperties.LABEL_FLOAT,
        JFXPasswordField.this,
        "lableFloat",
        false);

    @Override
    public final StyleableBooleanProperty labelFloatProperty() {
        return this.labelFloat;
    }

    @Override
    public final boolean isLabelFloat() {
        return this.labelFloatProperty().get();
    }

    @Override
    public final void setLabelFloat(final boolean labelFloat) {
        this.labelFloatProperty().set(labelFloat);
    }

    /**
     * default color used when the field is unfocused
     */
    private final StyleableObjectProperty<Paint> unFocusColor = new SimpleStyleableObjectProperty<>(StyleableProperties.UNFOCUS_COLOR,
        JFXPasswordField.this,
        "unFocusColor",
        Color.rgb(77,
            77,
            77));

    @Override
    public Paint getUnFocusColor() {
        return unFocusColor == null ? Color.rgb(77, 77, 77) : unFocusColor.get();
    }

    @Override
    public StyleableObjectProperty<Paint> unFocusColorProperty() {
        return this.unFocusColor;
    }

    @Override
    public void setUnFocusColor(Paint color) {
        this.unFocusColor.set(color);
    }

    /**
     * default color used when the field is focused
     */
    private final StyleableObjectProperty<Paint> focusColor = new SimpleStyleableObjectProperty<>(StyleableProperties.FOCUS_COLOR,
        JFXPasswordField.this,
        "focusColor",
        Color.valueOf("#4059A9"));

    @Override
    public Paint getFocusColor() {
        return focusColor == null ? Color.valueOf("#4059A9") : focusColor.get();
    }

    @Override
    public StyleableObjectProperty<Paint> focusColorProperty() {
        return this.focusColor;
    }

    @Override
    public void setFocusColor(Paint color) {
        this.focusColor.set(color);
    }

    /**
     * disable animation on validation
     */
    private final StyleableBooleanProperty disableAnimation = new SimpleStyleableBooleanProperty(StyleableProperties.DISABLE_ANIMATION,
        JFXPasswordField.this,
        "disableAnimation",
        false);

    @Override
    public final StyleableBooleanProperty disableAnimationProperty() {
        return this.disableAnimation;
    }

    @Override
    public final Boolean isDisableAnimation() {
        return disableAnimation != null && this.disableAnimationProperty().get();
    }

    @Override
    public final void setDisableAnimation(final Boolean disabled) {
        this.disableAnimationProperty().set(disabled);
    }


    private static class StyleableProperties {
        private static final CssMetaData<JFXPasswordField, Paint> UNFOCUS_COLOR = new CssMetaData<JFXPasswordField, Paint>(
            "-jfx-unfocus-color",
            StyleConverter.getPaintConverter(),
            Color.valueOf("#A6A6A6")) {
            @Override
            public boolean isSettable(JFXPasswordField control) {
                return control.unFocusColor == null || !control.unFocusColor.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(JFXPasswordField control) {
                return control.unFocusColorProperty();
            }
        };
        private static final CssMetaData<JFXPasswordField, Paint> FOCUS_COLOR = new CssMetaData<JFXPasswordField, Paint>(
            "-jfx-focus-color",
            StyleConverter.getPaintConverter(),
            Color.valueOf("#3f51b5")) {
            @Override
            public boolean isSettable(JFXPasswordField control) {
                return !control.focusColor.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(JFXPasswordField control) {
                return control.focusColorProperty();
            }
        };

        private static final CssMetaData<JFXPasswordField, Boolean> LABEL_FLOAT = new CssMetaData<JFXPasswordField, Boolean>(
            "-jfx-label-float", StyleConverter.getBooleanConverter(), false) {
            @Override
            public boolean isSettable(JFXPasswordField control) {
                return !control.labelFloat.isBound();
            }

            @Override
            public StyleableBooleanProperty getStyleableProperty(JFXPasswordField control) {
                return control.labelFloatProperty();
            }
        };

        private static final CssMetaData<JFXPasswordField, Boolean> DISABLE_ANIMATION =
            new CssMetaData<JFXPasswordField, Boolean>("-fx-disable-animation", StyleConverter.getBooleanConverter(), false) {
                @Override
                public boolean isSettable(JFXPasswordField control) {
                    return !control.disableAnimation.isBound();
                }

                @Override
                public StyleableBooleanProperty getStyleableProperty(JFXPasswordField control) {
                    return control.disableAnimationProperty();
                }
            };


        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(
                PasswordField.getClassCssMetaData());
            Collections.addAll(styleables, UNFOCUS_COLOR, FOCUS_COLOR, LABEL_FLOAT, DISABLE_ANIMATION);
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
