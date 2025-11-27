//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jfoenix.controls;

import com.jfoenix.skins.JFXCheckBoxSkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

public class JFXCheckBox extends CheckBox {
    private static final String DEFAULT_STYLE_CLASS = "jfx-check-box";
    private final StyleableObjectProperty<Paint> checkedColor;
    private final StyleableObjectProperty<Paint> unCheckedColor;
    private List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

    public JFXCheckBox(String text) {
        super(text);
        this.checkedColor = new SimpleStyleableObjectProperty<>(JFXCheckBox.StyleableProperties.CHECKED_COLOR, this, "checkedColor", Color.valueOf("#0F9D58"));
        this.unCheckedColor = new SimpleStyleableObjectProperty<>(JFXCheckBox.StyleableProperties.UNCHECKED_COLOR, this, "unCheckedColor", Color.valueOf("#5A5A5A"));
        this.initialize();
    }

    public JFXCheckBox() {
        this.checkedColor = new SimpleStyleableObjectProperty<>(JFXCheckBox.StyleableProperties.CHECKED_COLOR, this, "checkedColor", Color.valueOf("#0F9D58"));
        this.unCheckedColor = new SimpleStyleableObjectProperty<>(JFXCheckBox.StyleableProperties.UNCHECKED_COLOR, this, "unCheckedColor", Color.valueOf("#5A5A5A"));
        this.initialize();
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    protected Skin<?> createDefaultSkin() {
        return new JFXCheckBoxSkin(this);
    }

    public Paint getCheckedColor() {
        return this.checkedColor == null ? Color.valueOf("#0F9D58") : this.checkedColor.get();
    }

    public StyleableObjectProperty<Paint> checkedColorProperty() {
        return this.checkedColor;
    }

    public void setCheckedColor(Paint color) {
        this.checkedColor.set(color);
    }

    public Paint getUnCheckedColor() {
        return this.unCheckedColor == null ? Color.valueOf("#5A5A5A") : this.unCheckedColor.get();
    }

    public StyleableObjectProperty<Paint> unCheckedColorProperty() {
        return this.unCheckedColor;
    }

    public void setUnCheckedColor(Paint color) {
        this.unCheckedColor.set(color);
    }

    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        if (this.STYLEABLES == null) {
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            styleables.addAll(getClassCssMetaData());
            styleables.addAll(Labeled.getClassCssMetaData());
            this.STYLEABLES = Collections.unmodifiableList(styleables);
        }

        return this.STYLEABLES;
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return JFXCheckBox.StyleableProperties.CHILD_STYLEABLES;
    }

    protected void layoutChildren() {
        super.layoutChildren();
        this.setNeedsLayout(false);
    }

    private static class StyleableProperties {
        private static final CssMetaData<JFXCheckBox, Paint> CHECKED_COLOR = new CssMetaData<JFXCheckBox, Paint>("-jfx-checked-color", StyleConverter.getPaintConverter(), Color.valueOf("#0F9D58")) {
            public boolean isSettable(JFXCheckBox control) {
                return control.checkedColor == null || !control.checkedColor.isBound();
            }

            public StyleableProperty<Paint> getStyleableProperty(JFXCheckBox control) {
                return control.checkedColorProperty();
            }
        };
        private static final CssMetaData<JFXCheckBox, Paint> UNCHECKED_COLOR = new CssMetaData<JFXCheckBox, Paint>("-jfx-unchecked-color", StyleConverter.getPaintConverter(), Color.valueOf("#5A5A5A")) {
            public boolean isSettable(JFXCheckBox control) {
                return control.unCheckedColor == null || !control.unCheckedColor.isBound();
            }

            public StyleableProperty<Paint> getStyleableProperty(JFXCheckBox control) {
                return control.unCheckedColorProperty();
            }
        };
        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, CHECKED_COLOR, UNCHECKED_COLOR);
            CHILD_STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
