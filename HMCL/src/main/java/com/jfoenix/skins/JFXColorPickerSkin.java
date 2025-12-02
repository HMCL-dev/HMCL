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

package com.jfoenix.skins;

import com.jfoenix.controls.JFXClippedPane;
import com.jfoenix.controls.JFXColorPicker;
import com.jfoenix.controls.JFXRippler;
import com.jfoenix.effects.JFXDepthManager;
import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.*;
import javafx.css.converter.BooleanConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.ComboBoxPopupControl;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Shadi Shaheen
 */
public final class JFXColorPickerSkin extends JFXGenericPickerSkin<Color> {

    private final Label displayNode;
    private final JFXClippedPane colorBox;
    private JFXColorPalette popupContent;
    StyleableBooleanProperty colorLabelVisible = new SimpleStyleableBooleanProperty(StyleableProperties.COLOR_LABEL_VISIBLE,
            JFXColorPickerSkin.this,
            "colorLabelVisible",
            true);

    private final ObjectProperty<Color> textFill = new SimpleObjectProperty<>(Color.BLACK);
    private final ObjectProperty<Background> colorBoxBackground = new SimpleObjectProperty<>(null);

    public JFXColorPickerSkin(final ColorPicker colorPicker) {
        super(colorPicker);

        // create displayNode
        displayNode = new Label("");
        displayNode.getStyleClass().add("color-label");
        displayNode.setMouseTransparent(true);
        displayNode.textFillProperty().bind(textFill);

        // label graphic
        colorBox = new JFXClippedPane(displayNode);
        colorBox.getStyleClass().add("color-box");
        colorBox.setManaged(false);
        colorBox.backgroundProperty().bind(colorBoxBackground);
        initColor();
        final JFXRippler rippler = new JFXRippler(colorBox, JFXRippler.RipplerMask.FIT);
        rippler.ripplerFillProperty().bind(textFill);
        getChildren().setAll(rippler);
        JFXDepthManager.setDepth(getSkinnable(), 1);
        getSkinnable().setPickOnBounds(false);

        colorPicker.focusedProperty().addListener(observable -> {
            if (colorPicker.isFocused()) {
                if (!getSkinnable().isPressed()) {
                    rippler.setOverlayVisible(true);
                }
            } else {
                rippler.setOverlayVisible(false);
            }
        });

        // add listeners
        registerChangeListener(colorPicker.valueProperty(), obs -> updateColor());

        colorLabelVisible.addListener(invalidate -> {
            if (colorLabelVisible.get()) {
                displayNode.setText(JFXNodeUtils.colorToHex(getSkinnable().getValue()));
            } else {
                displayNode.setText("");
            }
        });
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        double width = 100;
        String displayNodeText = displayNode.getText();
        displayNode.setText("#DDDDDD");
        width = Math.max(width, super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset));
        displayNode.setText(displayNodeText);
        return width + rightInset + leftInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (colorBox == null) {
            reflectUpdateDisplayArea();
        }
        return topInset + colorBox.prefHeight(width) + bottomInset;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        double hInsets = snappedLeftInset() + snappedRightInset();
        double vInsets = snappedTopInset() + snappedBottomInset();
        double width = w + hInsets;
        double height = h + vInsets;
        colorBox.resizeRelocate(0, 0, width, height);
    }

    @Override
    protected Node getPopupContent() {
        if (popupContent == null) {
            popupContent = new JFXColorPalette((JFXColorPicker) getSkinnable());
        }
        return popupContent;
    }

    @Override
    public void show() {
        super.show();
        final ColorPicker colorPicker = (ColorPicker) getSkinnable();
        popupContent.updateSelection(colorPicker.getValue());
    }

    @Override
    public Node getDisplayNode() {
        return displayNode;
    }

    private void updateColor() {
        final ColorPicker colorPicker = (ColorPicker) getSkinnable();
        Color color = colorPicker.getValue();
        Color circleColor = color == null ? Color.WHITE : color;
        // update picker box color
        if (((JFXColorPicker) getSkinnable()).isDisableAnimation()) {
            JFXNodeUtils.updateBackground(colorBox.getBackground(), colorBox, circleColor);
        } else {
            Circle colorCircle = new Circle();
            colorCircle.setFill(circleColor);
            colorCircle.setManaged(false);
            colorCircle.setLayoutX(colorBox.getWidth() / 4);
            colorCircle.setLayoutY(colorBox.getHeight() / 2);
            colorBox.getChildren().add(colorCircle);
            Timeline animateColor = new Timeline(new KeyFrame(Duration.millis(240),
                    new KeyValue(colorCircle.radiusProperty(),
                            200,
                            Interpolator.EASE_BOTH)));
            animateColor.setOnFinished((finish) -> {
                colorBoxBackground.set(new Background(new BackgroundFill(colorCircle.getFill(), new CornerRadii(3), Insets.EMPTY)));
                colorBox.getChildren().remove(colorCircle);
            });
            animateColor.play();
        }
        // update label color
        textFill.set(circleColor.grayscale().getRed() < 0.5 ? Color.valueOf(
                "rgba(255, 255, 255, 0.87)") : Color.valueOf("rgba(0, 0, 0, 0.87)"));
        if (colorLabelVisible.get()) {
            displayNode.setText(JFXNodeUtils.colorToHex(circleColor));
        } else {
            displayNode.setText("");
        }
    }

    private void initColor() {
        final ColorPicker colorPicker = (ColorPicker) getSkinnable();
        Color color = colorPicker.getValue();
        Color circleColor = color == null ? Color.WHITE : color;
        // update picker box color
        colorBoxBackground.set(new Background(new BackgroundFill(circleColor, new CornerRadii(3), Insets.EMPTY)));
        // update label color
        textFill.set(circleColor.grayscale().getRed() < 0.5 ? Color.valueOf(
                "rgba(255, 255, 255, 0.87)") : Color.valueOf("rgba(0, 0, 0, 0.87)"));
        if (colorLabelVisible.get()) {
            displayNode.setText(JFXNodeUtils.colorToHex(circleColor));
        } else {
            displayNode.setText("");
        }
    }

    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    private static final class StyleableProperties {
        private static final CssMetaData<ColorPicker, Boolean> COLOR_LABEL_VISIBLE =
                new CssMetaData<ColorPicker, Boolean>("-fx-color-label-visible",
                        BooleanConverter.getInstance(), Boolean.TRUE) {

                    @Override
                    public boolean isSettable(ColorPicker n) {
                        final JFXColorPickerSkin skin = (JFXColorPickerSkin) n.getSkin();
                        return skin.colorLabelVisible == null || !skin.colorLabelVisible.isBound();
                    }

                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(ColorPicker n) {
                        final JFXColorPickerSkin skin = (JFXColorPickerSkin) n.getSkin();
                        return skin.colorLabelVisible;
                    }
                };
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ComboBoxPopupControl.getClassCssMetaData());
            styleables.add(COLOR_LABEL_VISIBLE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    protected TextField getEditor() {
        return null;
    }

    protected javafx.util.StringConverter<Color> getConverter() {
        return null;
    }
}
