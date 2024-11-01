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

import com.jfoenix.controls.base.IFXLabelFloatControl;
import com.jfoenix.transitions.JFXAnimationTimer;
import com.jfoenix.transitions.JFXKeyFrame;
import com.jfoenix.transitions.JFXKeyValue;
import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.function.Supplier;

/**
 * this class used to create label-float/focus-lines for all {@link IFXLabelFloatControl}
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2018-07-19
 */
public class PromptLinesWrapper<T extends Control & IFXLabelFloatControl> {

    private final Supplier<Text> promptTextSupplier;
    private final T control;

    public StackPane line = new StackPane();
    public StackPane focusedLine = new StackPane();
    public StackPane promptContainer = new StackPane();

    private JFXAnimationTimer focusTimer;
    private JFXAnimationTimer unfocusTimer;

    private final double initScale = 0.05;
    public final Scale promptTextScale = new Scale(1, 1, 0, 0);
    private final Scale scale = new Scale(initScale, 1);
    public final Rectangle clip = new Rectangle();

    public ObjectProperty<Paint> animatedPromptTextFill;
    public BooleanBinding usePromptText;
    private final ObjectProperty<Paint> promptTextFill;
    private final ObservableValue<?> valueProperty;
    private final ObservableValue<String> promptTextProperty;

    private boolean animating = false;
    private double contentHeight = 0;

    public PromptLinesWrapper(T control, ObjectProperty<Paint> promptTextFill,
                              ObservableValue<?> valueProperty,
                              ObservableValue<String> promptTextProperty,
                              Supplier<Text> promptTextSupplier) {
        this.control = control;
        this.promptTextSupplier = promptTextSupplier;
        this.promptTextFill = promptTextFill;
        this.valueProperty = valueProperty;
        this.promptTextProperty = promptTextProperty;
    }

    public void init(Runnable createPromptNodeRunnable, Node... cachedNodes) {
        animatedPromptTextFill = new SimpleObjectProperty<>(promptTextFill.get());
        usePromptText = Bindings.createBooleanBinding(this::usePromptText,
            valueProperty,
            promptTextProperty,
            control.labelFloatProperty(),
            promptTextFill);

        // draw lines
        line.setManaged(false);
        line.getStyleClass().add("input-line");
        line.setBackground(new Background(
            new BackgroundFill(control.getUnFocusColor(), CornerRadii.EMPTY, Insets.EMPTY)));

        // focused line
        focusedLine.setManaged(false);
        focusedLine.getStyleClass().add("input-focused-line");
        focusedLine.setBackground(new Background(
            new BackgroundFill(control.getFocusColor(), CornerRadii.EMPTY, Insets.EMPTY)));
        focusedLine.setOpacity(0);
        focusedLine.getTransforms().add(scale);


        if (usePromptText.get()) {
            createPromptNodeRunnable.run();
        }
        usePromptText.addListener(observable -> {
            createPromptNodeRunnable.run();
            control.requestLayout();
        });

        final Supplier<WritableValue<Number>> promptTargetSupplier =
            () -> promptTextSupplier.get() == null ? null : promptTextSupplier.get().translateYProperty();

        focusTimer = new JFXAnimationTimer(
            new JFXKeyFrame(Duration.millis(1),
                JFXKeyValue.builder()
                    .setTarget(focusedLine.opacityProperty())
                    .setEndValue(1)
                    .setInterpolator(Interpolator.EASE_BOTH)
                    .setAnimateCondition(control::isFocused).build()),

            new JFXKeyFrame(Duration.millis(160),
                JFXKeyValue.builder()
                    .setTarget(scale.xProperty())
                    .setEndValue(1)
                    .setInterpolator(Interpolator.EASE_BOTH)
                    .setAnimateCondition(control::isFocused).build(),
                JFXKeyValue.builder()
                    .setTarget(animatedPromptTextFill)
                    .setEndValueSupplier(control::getFocusColor)
                    .setInterpolator(Interpolator.EASE_BOTH)
                    .setAnimateCondition(() -> control.isFocused() && control.isLabelFloat()).build(),
                JFXKeyValue.builder()
                    .setTargetSupplier(promptTargetSupplier)
                    .setEndValueSupplier(() -> -contentHeight)
                    .setAnimateCondition(control::isLabelFloat)
                    .setInterpolator(Interpolator.EASE_BOTH).build(),
                JFXKeyValue.builder()
                    .setTarget(promptTextScale.xProperty())
                    .setEndValue(0.85)
                    .setAnimateCondition(control::isLabelFloat)
                    .setInterpolator(Interpolator.EASE_BOTH).build(),
                JFXKeyValue.builder()
                    .setTarget(promptTextScale.yProperty())
                    .setEndValue(0.85)
                    .setAnimateCondition(control::isLabelFloat)
                    .setInterpolator(Interpolator.EASE_BOTH).build())
        );

        unfocusTimer = new JFXAnimationTimer(
            new JFXKeyFrame(Duration.millis(160),
                JFXKeyValue.builder()
                    .setTargetSupplier(promptTargetSupplier)
                    .setEndValue(0)
                    .setInterpolator(Interpolator.EASE_BOTH).build(),
                JFXKeyValue.builder()
                    .setTarget(promptTextScale.xProperty())
                    .setEndValue(1)
                    .setInterpolator(Interpolator.EASE_BOTH).build(),
                JFXKeyValue.builder()
                    .setTarget(promptTextScale.yProperty())
                    .setEndValue(1)
                    .setInterpolator(Interpolator.EASE_BOTH).build())
        );

        promptContainer.getStyleClass().add("prompt-container");
        promptContainer.setManaged(false);
        promptContainer.setMouseTransparent(true);

        // clip prompt container
        clip.setSmooth(false);
        clip.setX(0);
        clip.widthProperty().bind(promptContainer.widthProperty());
        promptContainer.setClip(clip);

        focusTimer.setOnFinished(() -> animating = false);
        unfocusTimer.setOnFinished(() -> animating = false);
        focusTimer.setCacheNodes(cachedNodes);
        unfocusTimer.setCacheNodes(cachedNodes);

        // handle animation on focus gained/lost event
        control.focusedProperty().addListener(observable -> {
            if (control.isFocused()) {
                focus();
            } else {
                unFocus();
            }
        });

        promptTextFill.addListener(observable -> {
            if (!control.isLabelFloat() || !control.isFocused()) {
                animatedPromptTextFill.set(promptTextFill.get());
            }
        });

        updateDisabled();
    }

    private Object getControlValue() {
        Object text = valueProperty.getValue();
        text = validateComboBox(text);
        return text;
    }

    private Object validateComboBox(Object text) {
        if (control instanceof ComboBox && ((ComboBox<?>) control).isEditable()) {
            final String editorText = ((ComboBox<?>) control).getEditor().getText();
            text = editorText == null || editorText.isEmpty() ? null : text;
        }
        return text;
    }

    private void focus() {
        unfocusTimer.stop();
        animating = true;
        runTimer(focusTimer, true);
    }

    private void unFocus() {
        focusTimer.stop();
        scale.setX(initScale);
        focusedLine.setOpacity(0);
        if (control.isLabelFloat()) {
            animatedPromptTextFill.set(promptTextFill.get());
            Object text = getControlValue();
            if (text == null || text.toString().isEmpty()) {
                animating = true;
                runTimer(unfocusTimer, true);
            }
        }
    }

    public void updateFocusColor() {
        Paint paint = control.getFocusColor();
        focusedLine.setBackground(paint == null ? Background.EMPTY
            : new Background(new BackgroundFill(paint, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    public void updateUnfocusColor() {
        Paint paint = control.getUnFocusColor();
        line.setBackground(paint == null ? Background.EMPTY
            : new Background(new BackgroundFill(paint, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    private void updateLabelFloat(boolean animation) {
        if (control.isLabelFloat()) {
            if (control.isFocused()) {
                animateFloatingLabel(true, animation);
            } else {
                Object text = getControlValue();
                animateFloatingLabel(!(text == null || text.toString().isEmpty()), animation);
            }
        }
    }

    /**
     * this method is called when the text property is changed when the
     * field is not focused (changed in code)
     *
     * @param up direction of the prompt label
     */
    private void animateFloatingLabel(boolean up, boolean animation) {
        if (promptTextSupplier.get() == null) {
            return;
        }
        if (up) {
            if (promptTextSupplier.get().getTranslateY() != -contentHeight) {
                unfocusTimer.stop();
                runTimer(focusTimer, animation);
            }
        } else {
            if (promptTextSupplier.get().getTranslateY() != 0) {
                focusTimer.stop();
                runTimer(unfocusTimer, animation);
            }
        }
    }

    private void runTimer(JFXAnimationTimer timer, boolean animation) {
        if (animation) {
            if (!timer.isRunning()) {
                timer.start();
            }
        } else {
            timer.applyEndValues();
        }
    }

    private boolean usePromptText() {
        Object txt = getControlValue();
        String promptTxt = promptTextProperty.getValue();
        boolean isLabelFloat = control.isLabelFloat();
        return isLabelFloat || (promptTxt != null
                                && (txt == null || txt.toString().isEmpty())
                                && !promptTxt.isEmpty()
                                && !promptTextFill.get().equals(Color.TRANSPARENT));
    }

    public void layoutLines(double x, double y, double w, double h, double controlHeight, double translateY) {
        this.contentHeight = translateY;
        clip.setY(-contentHeight);
        clip.setHeight(controlHeight + contentHeight);
        focusedLine.resizeRelocate(x, controlHeight, w, focusedLine.prefHeight(-1));
        line.resizeRelocate(x, controlHeight, w, line.prefHeight(-1));
        promptContainer.resizeRelocate(x, y, w, h);
        scale.setPivotX(w / 2);
    }

    public void updateLabelFloatLayout() {
        if (!animating) {
            updateLabelFloat(false);
        } else if (unfocusTimer.isRunning()) {
            // handle the case when changing the control value when losing focus
            unfocusTimer.stop();
            updateLabelFloat(true);
        }
    }

    public void invalid() {
        if (control.isFocused()) {
            focus();
        }
    }

    public void updateDisabled() {
        final boolean disabled = control.isDisable();
        line.setBorder(!disabled ? Border.EMPTY :
            new Border(new BorderStroke(control.getUnFocusColor(),
                BorderStrokeStyle.DASHED, CornerRadii.EMPTY, new BorderWidths(1))));
        line.setBackground(new Background(
            new BackgroundFill(disabled ? Color.TRANSPARENT : control.getUnFocusColor(), CornerRadii.EMPTY, Insets.EMPTY)));
    }
}
