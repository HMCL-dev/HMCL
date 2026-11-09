//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.jfoenix.controls;

import com.jfoenix.converters.ButtonTypeConverter;
import com.jfoenix.skins.JFXButtonSkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;

public class JFXButton extends Button {
    private static final String DEFAULT_STYLE_CLASS = "jfx-button";

    private List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

    public JFXButton() {
        this.initialize();
    }

    public JFXButton(String text) {
        super(text);
        this.initialize();
    }

    public JFXButton(String text, Node graphic) {
        super(text, graphic);
        this.initialize();
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    protected Skin<?> createDefaultSkin() {
        return new JFXButtonSkin(this);
    }

    private final ObjectProperty<Paint> ripplerFill = new SimpleObjectProperty<>(this, "ripplerFill", null);

    public final ObjectProperty<Paint> ripplerFillProperty() {
        return this.ripplerFill;
    }

    public final Paint getRipplerFill() {
        return this.ripplerFillProperty().get();
    }

    public final void setRipplerFill(Paint ripplerFill) {
        this.ripplerFillProperty().set(ripplerFill);
    }

    private final StyleableObjectProperty<ButtonType> buttonType = new SimpleStyleableObjectProperty<>(
            JFXButton.StyleableProperties.BUTTON_TYPE, this, "buttonType", JFXButton.ButtonType.FLAT);

    public ButtonType getButtonType() {
        return this.buttonType == null ? JFXButton.ButtonType.FLAT : this.buttonType.get();
    }

    public StyleableObjectProperty<ButtonType> buttonTypeProperty() {
        return this.buttonType;
    }

    public void setButtonType(ButtonType type) {
        this.buttonType.set(type);
    }

    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        if (this.STYLEABLES == null) {
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            styleables.addAll(getClassCssMetaData());
            styleables.addAll(Labeled.getClassCssMetaData());
            this.STYLEABLES = List.copyOf(styleables);
        }

        return this.STYLEABLES;
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return JFXButton.StyleableProperties.CHILD_STYLEABLES;
    }

    protected void layoutChildren() {
        super.layoutChildren();
        this.setNeedsLayout(false);
    }

    public enum ButtonType {
        FLAT,
        RAISED;
    }

    private static final class StyleableProperties {
        private static final CssMetaData<JFXButton, ButtonType> BUTTON_TYPE;
        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            BUTTON_TYPE = new CssMetaData<>("-jfx-button-type", ButtonTypeConverter.getInstance(), JFXButton.ButtonType.FLAT) {
                public boolean isSettable(JFXButton control) {
                    return control.buttonType == null || !control.buttonType.isBound();
                }

                public StyleableProperty<ButtonType> getStyleableProperty(JFXButton control) {
                    return control.buttonTypeProperty();
                }
            };
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, BUTTON_TYPE);
            CHILD_STYLEABLES = List.copyOf(styleables);
        }
    }
}
