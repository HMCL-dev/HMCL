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
import com.jfoenix.skins.JFXTextAreaSkin;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.scene.control.Skin;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JFXTextArea is the material design implementation of a text area.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXTextArea extends TextArea implements IFXLabelFloatControl {

    /**
     * {@inheritDoc}
     */
    public JFXTextArea() {
        initialize();
    }

    /**
     * {@inheritDoc}
     */
    public JFXTextArea(String text) {
        super(text);
        initialize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXTextAreaSkin(this);
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
    ValidationControl validationControl = new ValidationControl(this);

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
     * Initialize the style class to 'jfx-text-field'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "jfx-text-area";
    private static final String USER_AGENT_STYLESHEET = JFoenixResources.load("css/controls/jfx-text-area.css").toExternalForm();
    /**
     * set true to show a float the prompt text when focusing the field
     */
    private final StyleableBooleanProperty labelFloat = new SimpleStyleableBooleanProperty(StyleableProperties.LABEL_FLOAT,
        JFXTextArea.this, "lableFloat", false);

    public final StyleableBooleanProperty labelFloatProperty() {
        return this.labelFloat;
    }

    public final boolean isLabelFloat() {
        return this.labelFloatProperty().get();
    }

    public final void setLabelFloat(final boolean labelFloat) {
        this.labelFloatProperty().set(labelFloat);
    }

    /**
     * default color used when the text area is unfocused
     */
    private final StyleableObjectProperty<Paint> unFocusColor = new SimpleStyleableObjectProperty<>(StyleableProperties.UNFOCUS_COLOR,
        JFXTextArea.this, "unFocusColor", Color.rgb(77, 77, 77));

    public Paint getUnFocusColor() {
        return unFocusColor == null ? Color.rgb(77, 77, 77) : unFocusColor.get();
    }

    public StyleableObjectProperty<Paint> unFocusColorProperty() {
        return this.unFocusColor;
    }

    public void setUnFocusColor(Paint color) {
        this.unFocusColor.set(color);
    }

    /**
     * default color used when the text area is focused
     */
    private final StyleableObjectProperty<Paint> focusColor = new SimpleStyleableObjectProperty<>(StyleableProperties.FOCUS_COLOR,
        JFXTextArea.this,
        "focusColor",
        Color.valueOf("#4059A9"));

    public Paint getFocusColor() {
        return focusColor == null ? Color.valueOf("#4059A9") : focusColor.get();
    }

    public StyleableObjectProperty<Paint> focusColorProperty() {
        return this.focusColor;
    }

    public void setFocusColor(Paint color) {
        this.focusColor.set(color);
    }

    /**
     * disable animation on validation
     */
    private final StyleableBooleanProperty disableAnimation = new SimpleStyleableBooleanProperty(StyleableProperties.DISABLE_ANIMATION,
        JFXTextArea.this,
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

    private static class StyleableProperties {
        private static final CssMetaData<JFXTextArea, Paint> UNFOCUS_COLOR = new CssMetaData<JFXTextArea, Paint>(
            "-jfx-unfocus-color",
            StyleConverter.getPaintConverter(),
            Color.rgb(77, 77, 77)) {
            @Override
            public boolean isSettable(JFXTextArea control) {
                return !control.unFocusColor.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(JFXTextArea control) {
                return control.unFocusColorProperty();
            }
        };
        private static final CssMetaData<JFXTextArea, Paint> FOCUS_COLOR = new CssMetaData<JFXTextArea, Paint>(
            "-jfx-focus-color",
            StyleConverter.getPaintConverter(),
            Color.valueOf("#4059A9")) {
            @Override
            public boolean isSettable(JFXTextArea control) {
                return !control.focusColor.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(JFXTextArea control) {
                return control.focusColorProperty();
            }
        };
        private static final CssMetaData<JFXTextArea, Boolean> LABEL_FLOAT = new CssMetaData<JFXTextArea, Boolean>(
            "-jfx-label-float",
            StyleConverter.getBooleanConverter(),
            false) {
            @Override
            public boolean isSettable(JFXTextArea control) {
                return !control.labelFloat.isBound();
            }

            @Override
            public StyleableBooleanProperty getStyleableProperty(JFXTextArea control) {
                return control.labelFloatProperty();
            }
        };

        private static final CssMetaData<JFXTextArea, Boolean> DISABLE_ANIMATION =
            new CssMetaData<JFXTextArea, Boolean>("-jfx-disable-animation",
                StyleConverter.getBooleanConverter(), false) {
                @Override
                public boolean isSettable(JFXTextArea control) {
                    return !control.disableAnimation.isBound();
                }

                @Override
                public StyleableBooleanProperty getStyleableProperty(JFXTextArea control) {
                    return control.disableAnimationProperty();
                }
            };

        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(
                TextArea.getClassCssMetaData());
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
