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

import com.jfoenix.controls.base.IFXStaticControl;
import com.jfoenix.controls.base.IFXValidatableControl;
import com.jfoenix.utils.JFXUtilities;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.animation.*;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/**
 * this class used to create validation ui for all {@link IFXValidatableControl}
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2018-07-19
 */
public class ValidationPane<T extends Region & IFXValidatableControl & IFXStaticControl> extends HBox {

    private final Label errorLabel = new Label();
    private final StackPane errorIcon = new StackPane();

    private final Rectangle errorContainerClip = new Rectangle();
    private final Scale errorClipScale = new Scale(1, 0, 0, 0);
    private final Timeline errorHideTransition = new Timeline(new KeyFrame(Duration.millis(80),
        new KeyValue(opacityProperty(), 0, Interpolator.LINEAR)));
    private final Timeline errorShowTransition = new Timeline(new KeyFrame(Duration.millis(80),
        new KeyValue(opacityProperty(), 1, Interpolator.EASE_OUT)));
    private final Timeline scale1 = new Timeline();
    private final Timeline scaleLess1 = new Timeline();

    private final T control;

    public ValidationPane(T control) {
        this.control = control;
        setManaged(false);

        errorLabel.getStyleClass().add("error-label");

        final StackPane labelContainer = new StackPane(errorLabel);
        labelContainer.getStyleClass().add("error-label-container");
        labelContainer.setAlignment(Pos.TOP_LEFT);
        getChildren().setAll(labelContainer, errorIcon);
        HBox.setHgrow(labelContainer, Priority.ALWAYS);

        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(4, 0, 0, 0));
        setVisible(false);
        setOpacity(0);

        errorContainerClip.getTransforms().add(errorClipScale);
        setClip(control.isDisableAnimation() ? null : errorContainerClip);


        control.activeValidatorProperty().addListener((ObservableValue<? extends ValidatorBase> o, ValidatorBase oldVal, ValidatorBase newVal) -> {
            if (!control.isDisableAnimation()) {
                if (newVal != null) {
                    errorHideTransition.setOnFinished(finish -> {
                        showError(newVal);
                        final double w = control.getWidth();
                        double errorContainerHeight = computeErrorHeight(computeErrorWidth(w));
                        if (errorLabel.isWrapText()) {
                            // animate opacity + scale
                            if (errorContainerHeight < getHeight()) {
                                // update animation frames
                                scaleLess1.getKeyFrames().setAll(createSmallerScaleFrame(errorContainerHeight));
                                scaleLess1.setOnFinished(event -> {
                                    updateErrorContainerSize(w, errorContainerHeight);
                                    errorClipScale.setY(1);
                                });
                                SequentialTransition transition = new SequentialTransition(scaleLess1,
                                    errorShowTransition);
                                transition.play();
                            } else {
                                errorClipScale.setY(oldVal == null ? 0 : getHeight() / errorContainerHeight);
                                updateErrorContainerSize(w, errorContainerHeight);
                                // update animation frames
                                scale1.getKeyFrames().setAll(createScaleToOneFrames());
                                // play animation
                                ParallelTransition parallelTransition = new ParallelTransition();
                                parallelTransition.getChildren().addAll(scale1, errorShowTransition);
                                parallelTransition.play();
                            }
                        } else {
                            // animate opacity only
                            errorClipScale.setY(1);
                            updateErrorContainerSize(w, errorContainerHeight);
                            ParallelTransition parallelTransition = new ParallelTransition(errorShowTransition);
                            parallelTransition.play();
                        }
                    });
                    errorHideTransition.play();
                } else {
                    errorHideTransition.setOnFinished(null);
                    if (errorLabel.isWrapText()) {
                        // animate scale only
                        scaleLess1.getKeyFrames().setAll(new KeyFrame(Duration.millis(100),
                            new KeyValue(errorClipScale.yProperty(), 0, Interpolator.EASE_BOTH)));
                        scaleLess1.setOnFinished(event -> {
                            hideError();
                            errorClipScale.setY(0);
                        });
                        SequentialTransition transition = new SequentialTransition(scaleLess1);
                        transition.play();
                    } else {
                        errorClipScale.setY(0);
                    }
                    // animate opacity only
                    errorHideTransition.play();
                }
            } else {
                if (newVal != null) {
                    JFXUtilities.runInFXAndWait(() -> showError(newVal));
                } else {
                    JFXUtilities.runInFXAndWait(this::hideError);
                }
            }
        });
    }

    public void layoutPane(final double x, final double y, final double w, final double h) {
        relocate(x, y);
        // resize error container if animation is disabled
        if (control.isDisableAnimation() || isErrorVisible()) {
            resize(w, computeErrorHeight(computeErrorWidth(w)));
            errorContainerClip.setWidth(w);
        }
    }

    public void invalid(double w) {
        final ValidatorBase activeValidator = control.getActiveValidator();
        if (activeValidator != null) {
            showError(activeValidator);
            final double errorContainerWidth = w - errorIcon.prefWidth(-1);
            setOpacity(1);
            resize(w, computeErrorHeight(errorContainerWidth));
            errorContainerClip.setWidth(w);
            errorContainerClip.setHeight(getHeight());
            errorClipScale.setY(1);
        }
    }

    public void updateClip() {
        setClip(control.isDisableAnimation() ? null : errorContainerClip);
    }

    private boolean isErrorVisible() {
        return isVisible()
               && errorShowTransition.getStatus().equals(Animation.Status.STOPPED)
               && errorHideTransition.getStatus().equals(Animation.Status.STOPPED);
    }

    private double computeErrorWidth(double w) {
        return w - errorIcon.prefWidth(-1);
    }

    private double computeErrorHeight(double errorContainerWidth) {
        return errorLabel.prefHeight(errorContainerWidth)
               + snappedBottomInset()
               + snappedTopInset();
    }

    /**
     * update the size of error container and its clip
     */
    private void updateErrorContainerSize(double w, double errorContainerHeight) {
        errorContainerClip.setWidth(w);
        errorContainerClip.setHeight(errorContainerHeight);
        resize(w, errorContainerHeight);
    }

    /**
     * creates error animation frames when moving from large -> small error container
     */
    private KeyFrame createSmallerScaleFrame(double errorContainerHeight) {
        return new KeyFrame(Duration.millis(100),
            new KeyValue(errorClipScale.yProperty(),
                errorContainerHeight / getHeight(),
                Interpolator.EASE_BOTH));
    }

    /**
     * creates error animation frames when moving from small -> large error container
     */
    private KeyFrame createScaleToOneFrames() {
        return new KeyFrame(Duration.millis(100), new
            KeyValue(errorClipScale.yProperty(), 1, Interpolator.EASE_BOTH));
    }


    private void showError(ValidatorBase validator) {
        // set text in error label
        errorLabel.setText(validator.getMessage());
        // show error icon
        Node icon = validator.getIcon();
        errorIcon.getChildren().clear();
        if (icon != null) {
            errorIcon.getChildren().setAll(icon);
            StackPane.setAlignment(icon, Pos.CENTER_RIGHT);
        }
        setVisible(true);
    }

    private void hideError() {
        // clear error label text
        errorLabel.setText(null);
        // clear error icon
        errorIcon.getChildren().clear();
        // reset the height of the text field
        // hide error container
        setVisible(false);
    }

}
