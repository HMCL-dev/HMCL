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

import com.jfoenix.adapters.skins.SliderSkinAdapter;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXSlider.IndicatorPosition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * <h1>Material Design Slider Skin</h1>
 * <p>
 * rework of JFXSliderSkin by extending Java SliderSkin
 * this solves padding and resizing issues
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXSliderSkin extends SliderSkinAdapter {

    private static final PseudoClass MIN_VALUE = PseudoClass.getPseudoClass("min");
    private static final PseudoClass MAX_VALUE = PseudoClass.getPseudoClass("max");

    private final Pane mouseHandlerPane = new Pane();

    private final Text sliderValue;
    private final StackPane coloredTrack;
    private final StackPane thumb;
    private final StackPane track;
    private final StackPane animatedThumb;

    private Timeline timeline;

    private double indicatorRotation;
    private double horizontalRotation;
    private double shifting;
    private boolean isValid = false;

    public JFXSliderSkin(JFXSlider slider) {
        super(slider);

        track = (StackPane) getSkinnable().lookup(".track");
        thumb = (StackPane) getSkinnable().lookup(".thumb");

        coloredTrack = new StackPane();
        coloredTrack.getStyleClass().add("colored-track");
        coloredTrack.setMouseTransparent(true);

        sliderValue = new Text();
        sliderValue.getStyleClass().setAll("slider-value");

        animatedThumb = new StackPane();
        animatedThumb.getStyleClass().add("animated-thumb");
        animatedThumb.getChildren().add(sliderValue);
        animatedThumb.setMouseTransparent(true);
        animatedThumb.setScaleX(0);
        animatedThumb.setScaleY(0);

        getChildren().add(getChildren().indexOf(thumb), coloredTrack);
        getChildren().add(getChildren().indexOf(thumb), animatedThumb);
        getChildren().add(0, mouseHandlerPane);
        registerChangeListener(slider.valueFactoryProperty(), obs->refreshSliderValueBinding());

        initListeners();
    }

    private void refreshSliderValueBinding() {
        sliderValue.textProperty().unbind();
        if (((JFXSlider) getSkinnable()).getValueFactory() != null) {
            sliderValue.textProperty()
                .bind(((JFXSlider) getSkinnable()).getValueFactory().call((JFXSlider) getSkinnable()));
        } else {
            sliderValue.textProperty().bind(Bindings.createStringBinding(() -> {
                if (getSkinnable().getLabelFormatter() != null) {
                    return getSkinnable().getLabelFormatter().toString(getSkinnable().getValue());
                } else {
                    return String.valueOf(Math.round(getSkinnable().getValue()));
                }
            }, getSkinnable().valueProperty()));
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);

        if (!isValid) {
            initializeVariables();
            initAnimation(getSkinnable().getOrientation());
            isValid = true;
        }

        double prefWidth = animatedThumb.prefWidth(-1);
        animatedThumb.resize(prefWidth, animatedThumb.prefHeight(prefWidth));

        boolean horizontal = getSkinnable().getOrientation() == Orientation.HORIZONTAL;
        double width, height, layoutX, layoutY;
        if (horizontal) {
            width = thumb.getLayoutX() - snappedLeftInset();
            height = track.getHeight();
            layoutX = track.getLayoutX();
            layoutY = track.getLayoutY();
            animatedThumb.setLayoutX(thumb.getLayoutX() + thumb.getWidth() / 2 - animatedThumb.getWidth() / 2);
        } else {
            height = track.getLayoutBounds().getMaxY() + track.getLayoutY() - thumb.getLayoutY() - snappedBottomInset();
            width = track.getWidth();
            layoutX = track.getLayoutX();
            layoutY = thumb.getLayoutY();
            animatedThumb.setLayoutY(thumb.getLayoutY() + thumb.getHeight() / 2 - animatedThumb.getHeight() / 2);
        }

        coloredTrack.resizeRelocate(layoutX, layoutY, width, height);
        mouseHandlerPane.resizeRelocate(x, y, w, h);
    }

    private void initializeVariables() {
        shifting = 30 + thumb.getWidth();
        if (getSkinnable().getOrientation() != Orientation.HORIZONTAL) {
            horizontalRotation = -90;
        }
        if (((JFXSlider) getSkinnable()).getIndicatorPosition() != IndicatorPosition.LEFT) {
            indicatorRotation = 180;
            shifting = -shifting;
        }
        final double rotationAngle = 45;
        sliderValue.setRotate(rotationAngle + indicatorRotation + 3 * horizontalRotation);
        animatedThumb.setRotate(-rotationAngle + indicatorRotation + horizontalRotation);
    }

    private void initListeners() {
        // delegate slider mouse events to track node
        mouseHandlerPane.setOnMousePressed(this::delegateToTrack);
        mouseHandlerPane.setOnMouseReleased(this::delegateToTrack);
        mouseHandlerPane.setOnMouseDragged(this::delegateToTrack);

        // animate value node
        track.addEventHandler(MouseEvent.MOUSE_PRESSED, (event) -> {
            timeline.setRate(1);
            timeline.play();
        });
        track.addEventHandler(MouseEvent.MOUSE_RELEASED, (event) -> {
            timeline.setRate(-1);
            timeline.play();
        });
        thumb.addEventHandler(MouseEvent.MOUSE_PRESSED, (event) -> {
            timeline.setRate(1);
            timeline.play();
        });
        thumb.addEventHandler(MouseEvent.MOUSE_RELEASED, (event) -> {
            timeline.setRate(-1);
            timeline.play();
        });

        refreshSliderValueBinding();
        updateValueStyleClass();

        getSkinnable().valueProperty().addListener(observable -> updateValueStyleClass());
        getSkinnable().orientationProperty().addListener(observable -> initAnimation(getSkinnable().getOrientation()));
    }

    private void delegateToTrack(MouseEvent event) {
        if (!event.isConsumed()) {
            event.consume();
            track.fireEvent(event);
        }
    }

    private void updateValueStyleClass() {
        getSkinnable().pseudoClassStateChanged(MIN_VALUE, getSkinnable().getMin() == getSkinnable().getValue());
        getSkinnable().pseudoClassStateChanged(MAX_VALUE, getSkinnable().getMax() == getSkinnable().getValue());
    }

    private void initAnimation(Orientation orientation) {
        double thumbPos, thumbNewPos;
        DoubleProperty layoutProperty;

        if (orientation == Orientation.HORIZONTAL) {
            if (((JFXSlider) getSkinnable()).getIndicatorPosition() == IndicatorPosition.RIGHT) {
                thumbPos = thumb.getLayoutY() - thumb.getHeight();
                thumbNewPos = thumbPos - shifting;
            } else {
                double height = animatedThumb.prefHeight(animatedThumb.prefWidth(-1));
                thumbPos = thumb.getLayoutY() - height / 2;
                thumbNewPos = thumb.getLayoutY() - height - thumb.getHeight();
            }
            layoutProperty = animatedThumb.translateYProperty();
        } else {
            if (((JFXSlider) getSkinnable()).getIndicatorPosition() == IndicatorPosition.RIGHT) {
                thumbPos = thumb.getLayoutX() - thumb.getWidth();
                thumbNewPos = thumbPos - shifting;
            } else {
                double width = animatedThumb.prefWidth(-1);
                thumbPos = thumb.getLayoutX() - width / 2;
                thumbNewPos = thumb.getLayoutX() - width - thumb.getWidth();
            }
            layoutProperty = animatedThumb.translateXProperty();
        }

        clearAnimation();

        timeline = new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(animatedThumb.scaleXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(animatedThumb.scaleYProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(layoutProperty, thumbPos, Interpolator.EASE_BOTH)),
            new KeyFrame(
                Duration.seconds(0.2),
                new KeyValue(animatedThumb.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                new KeyValue(animatedThumb.scaleYProperty(), 1, Interpolator.EASE_BOTH),
                new KeyValue(layoutProperty, thumbNewPos, Interpolator.EASE_BOTH)));
    }

    @Override
    public void dispose() {
        super.dispose();
        clearAnimation();
    }

    private void clearAnimation() {
        if (timeline != null) {
            timeline.stop();
            timeline.getKeyFrames().clear();
            timeline = null;
        }
    }
}
