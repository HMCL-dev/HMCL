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
import com.jfoenix.skins.JFXCheckBoxSkin;
import javafx.css.*;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JFXCheckBox is the material design implementation of a checkbox.
 * it shows ripple effect and a custom selection animation.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
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

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }


    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    /**
     * Initialize the style class to 'jfx-check-box'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "jfx-check-box";
    private static final String USER_AGENT_STYLESHEET = JFoenixResources.load("css/controls/jfx-check-box.css").toExternalForm();
    /**
     * checkbox color property when selected
     */
    private final StyleableObjectProperty<Paint> checkedColor = new SimpleStyleableObjectProperty<>(StyleableProperties.CHECKED_COLOR,
        JFXCheckBox.this,
        "checkedColor",
        Color.valueOf(
            "#0F9D58"));

    public Paint getCheckedColor() {
        return checkedColor == null ? Color.valueOf("#0F9D58") : checkedColor.get();
    }

    public StyleableObjectProperty<Paint> checkedColorProperty() {
        return this.checkedColor;
    }

    public void setCheckedColor(Paint color) {
        this.checkedColor.set(color);
    }

    /**
     * checkbox color property when not selected
     */
    private final StyleableObjectProperty<Paint> unCheckedColor = new SimpleStyleableObjectProperty<>(StyleableProperties.UNCHECKED_COLOR,
        JFXCheckBox.this,
        "unCheckedColor",
        Color.valueOf(
            "#5A5A5A"));

    public Paint getUnCheckedColor() {
        return unCheckedColor == null ? Color.valueOf("#5A5A5A") : unCheckedColor.get();
    }

    public StyleableObjectProperty<Paint> unCheckedColorProperty() {
        return this.unCheckedColor;
    }

    public void setUnCheckedColor(Paint color) {
        this.unCheckedColor.set(color);
    }

    /**
     * Disable the visual indicator for focus.
     */
    private final StyleableBooleanProperty disableVisualFocus = new SimpleStyleableBooleanProperty(StyleableProperties.DISABLE_VISUAL_FOCUS,
        JFXCheckBox.this,
        "disableVisualFocus",
        false);

    /**
     * Setting this property disables this {@link JFXCheckBox} from showing keyboard focus.
     * @return A property that will disable visual focus if true and enable it if false.
     */
    public final StyleableBooleanProperty disableVisualFocusProperty() {
        return this.disableVisualFocus;
    }

    /**
     * Indicates whether or not this {@link JFXCheckBox} will show focus when it receives keyboard focus.
     * @return False if this {@link JFXCheckBox} will show visual focus and true if it will not.
     */
    public final Boolean isDisableVisualFocus() {
        return disableVisualFocus != null && this.disableVisualFocusProperty().get();
    }

    /**
     * Setting this to true will disable this {@link JFXCheckBox} from showing focus when it receives keyboard focus.
     * @param disabled True to disable visual focus and false to enable it.
     */
    public final void setDisableVisualFocus(final Boolean disabled) {
        this.disableVisualFocusProperty().set(disabled);
    }

    private static class StyleableProperties {
        private static final CssMetaData<JFXCheckBox, Paint> CHECKED_COLOR =
            new CssMetaData<JFXCheckBox, Paint>("-jfx-checked-color",
                StyleConverter.getPaintConverter(), Color.valueOf("#0F9D58")) {
                @Override
                public boolean isSettable(JFXCheckBox control) {
                    return !control.checkedColor.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(JFXCheckBox control) {
                    return control.checkedColorProperty();
                }
            };
        private static final CssMetaData<JFXCheckBox, Paint> UNCHECKED_COLOR =
            new CssMetaData<JFXCheckBox, Paint>("-jfx-unchecked-color",
                StyleConverter.getPaintConverter(), Color.valueOf("#5A5A5A")) {
                @Override
                public boolean isSettable(JFXCheckBox control) {
                    return !control.unCheckedColor.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(JFXCheckBox control) {
                    return control.unCheckedColorProperty();
                }
            };
        private static final CssMetaData<JFXCheckBox, Boolean> DISABLE_VISUAL_FOCUS =
            new CssMetaData<JFXCheckBox, Boolean>("-jfx-disable-visual-focus",
                StyleConverter.getBooleanConverter(), false) {
                @Override
                public boolean isSettable(JFXCheckBox control) {
                    return !control.disableVisualFocus.isBound();
                }

                @Override
                public StyleableBooleanProperty getStyleableProperty(JFXCheckBox control) {
                    return control.disableVisualFocusProperty();
                }
            };
        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                new ArrayList<>(CheckBox.getClassCssMetaData());
            Collections.addAll(styleables,
                CHECKED_COLOR,
                UNCHECKED_COLOR,
                DISABLE_VISUAL_FOCUS
            );
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
