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

package com.jfoenix.validation;

import com.jfoenix.utils.JFXUtilities;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.animation.Animation.Status;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * this class has been deprecated and will be removed in later versions of JFoenix,
 * we are moving validations into each control that implements the interface
 * {@Link IFXValidatableControl}. Validation will be applied through the control itself
 * similar to {@link com.jfoenix.controls.JFXTextField}, it's straight forward and
 * simpler than using the ValidationFacade.
 */
@Deprecated
public class ValidationFacade extends VBox {

    /**
     * Initialize the style class to 'validation-facade'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "validation-facade";

    private Label errorLabel;
    private StackPane errorIcon;
    private HBox errorContainer;

    private double oldErrorLabelHeight = -1;
    private double initYlayout = -1;
    private double initHeight = -1;
    private boolean errorShown = false;
    private double currentFieldHeight = -1;
    private double errorLabelInitHeight = 0;

    private boolean heightChanged = false;
    private boolean disableAnimation = false;

    private Timeline hideErrorAnimation;

    public ValidationFacade() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setPadding(new Insets(0, 0, 0, 0));
        setSpacing(0);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);

        StackPane errorLabelContainer = new StackPane();
        errorLabelContainer.getChildren().add(errorLabel);
        StackPane.setAlignment(errorLabel, Pos.CENTER_LEFT);

        errorIcon = new StackPane();
        errorContainer = new HBox();
        errorContainer.setAlignment(Pos.TOP_LEFT);
        errorContainer.getChildren().add(errorLabelContainer);
        errorContainer.getChildren().add(errorIcon);

        HBox.setHgrow(errorLabelContainer, Priority.ALWAYS);
        errorLabelContainer.setMaxWidth(Double.MAX_VALUE);

        errorIcon.setTranslateY(5);
        errorContainer.setSpacing(10);
        errorContainer.setVisible(false);
        errorContainer.setOpacity(0);

        // add listeners to show error label
        errorLabel.heightProperty().addListener((o, oldVal, newVal) -> {
            if (errorShown) {
                if (oldErrorLabelHeight == -1) {
                    oldErrorLabelHeight = errorLabelInitHeight = oldVal.doubleValue();
                }
                heightChanged = true;
                double newHeight = getHeight() - oldErrorLabelHeight + newVal.doubleValue();
                // show the error
                Timeline errorAnimation = new Timeline(new KeyFrame(Duration.ZERO,
                    new KeyValue(minHeightProperty(),
                        currentFieldHeight,
                        Interpolator.EASE_BOTH)),
                    new KeyFrame(Duration.millis(160),
                        // text pane animation
                        new KeyValue(translateYProperty(),
                            (initYlayout + getMaxHeight() / 2) - newHeight / 2,
                            Interpolator.EASE_BOTH),
                        // animate the height change effect
                        new KeyValue(minHeightProperty(),
                            newHeight,
                            Interpolator.EASE_BOTH)));
                errorAnimation.play();
                // show the error label when finished
                errorAnimation.setOnFinished(finish -> new Timeline(new KeyFrame(Duration.millis(160),
                    new KeyValue(errorContainer.opacityProperty(),
                        1,
                        Interpolator.EASE_BOTH))).play());
                currentFieldHeight = newHeight;
                oldErrorLabelHeight = newVal.doubleValue();
            }
        });
        errorContainer.visibleProperty().addListener((o, oldVal, newVal) -> {
            // show the error label if it's not shown
            new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(errorContainer.opacityProperty(),
                    1,
                    Interpolator.EASE_BOTH))).play();
        });

        activeValidatorProperty().addListener((o, oldVal, newVal) -> {
            if (!isDisableAnimation()) {
                if (hideErrorAnimation != null && hideErrorAnimation.getStatus() == Status.RUNNING) {
                    hideErrorAnimation.stop();
                }
                if (newVal != null) {
                    hideErrorAnimation = new Timeline(new KeyFrame(Duration.millis(160),
                        new KeyValue(errorContainer.opacityProperty(),
                            0,
                            Interpolator.EASE_BOTH)));
                    hideErrorAnimation.setOnFinished(finish -> {
                        JFXUtilities.runInFX(() -> showError(newVal));
                    });
                    hideErrorAnimation.play();
                } else {
                    JFXUtilities.runInFX(this::hideError);
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

    /***************************************************************************
     * * Properties * *
     **************************************************************************/

    /**
     * holds the current active validator on the text field in case of
     * validation error
     */
    private ReadOnlyObjectWrapper<ValidatorBase> activeValidator = new ReadOnlyObjectWrapper<>();

    public ValidatorBase getActiveValidator() {
        return activeValidator == null ? null : activeValidator.get();
    }

    public ReadOnlyObjectProperty<ValidatorBase> activeValidatorProperty() {
        return this.activeValidator.getReadOnlyProperty();
    }

    /**
     * list of validators that will validate the text value upon calling {
     * {@link #validate()}
     */
    private ObservableList<ValidatorBase> validators = FXCollections.observableArrayList();

    public ObservableList<ValidatorBase> getValidators() {
        return validators;
    }

    public void setValidators(ValidatorBase... validators) {
        this.validators.addAll(validators);
    }

    /**
     * validates the text value using the list of validators provided by the
     * user {{@link #setValidators(ValidatorBase...)}
     *
     * @return true if the value is valid else false
     */
    public static boolean validate(Control control) {
        ValidationFacade facade = (ValidationFacade) control.getParent();
        for (ValidatorBase validator : facade.validators) {
            validator.setSrcControl(facade.controlProperty.get());
            validator.validate();
            if (validator.getHasErrors()) {
                facade.activeValidator.set(validator);
                control.pseudoClassStateChanged(PSEUDO_CLASS_ERROR, true);
                return false;
            }
        }
        control.pseudoClassStateChanged(PSEUDO_CLASS_ERROR, false);
        facade.activeValidator.set(null);
        return true;
    }

    public static void reset(Control control) {
        ValidationFacade facade = (ValidationFacade) control.getParent();
        control.pseudoClassStateChanged(PSEUDO_CLASS_ERROR, false);
        facade.activeValidator.set(null);
    }

    private ObjectProperty<Control> controlProperty = new SimpleObjectProperty<>();

    public Control getControl() {
        return controlProperty.get();
    }

    public void setControl(Control control) {
        maxWidthProperty().bind(control.maxWidthProperty());
        prefWidthProperty().bind(control.prefWidthProperty());
        prefHeightProperty().bind(control.prefHeightProperty());

        errorContainer.setMaxWidth(control.getMaxWidth() > -1 ? control.getMaxWidth() : control.getPrefWidth());
        errorContainer.prefWidthProperty().bind(control.widthProperty());
        errorContainer.prefHeightProperty().bind(control.heightProperty());

        getChildren().clear();
        getChildren().add(control);
        getChildren().add(errorContainer);
        this.controlProperty.set(control);
    }

    private void showError(ValidatorBase validator) {

        // set text in error label
        errorLabel.setText(validator.getMessage());
        // show error icon
        Node awsomeIcon = validator.getIcon();
        errorIcon.getChildren().clear();
        if (awsomeIcon != null) {
            errorIcon.getChildren().add(awsomeIcon);
            StackPane.setAlignment(awsomeIcon, Pos.TOP_RIGHT);
        }
        // init only once, to fix the text pane from resizing
        if (initYlayout == -1) {
            initYlayout = getBoundsInParent().getMinY();
            initHeight = getHeight();
            currentFieldHeight = initHeight;
        }
        errorContainer.setVisible(true);

        errorShown = true;

    }

    private void hideError() {
        if (heightChanged) {
            new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(translateYProperty(), 0, Interpolator.EASE_BOTH))).play();
            // reset the height of text field
            new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(minHeightProperty(), initHeight, Interpolator.EASE_BOTH))).play();
            heightChanged = false;
        }
        // clear error label text
        errorLabel.setText(null);
        oldErrorLabelHeight = errorLabelInitHeight;
        // clear error icon
        errorIcon.getChildren().clear();
        // reset the height of the text field
        currentFieldHeight = initHeight;
        // hide error container
        errorContainer.setVisible(false);

        errorShown = false;
    }

    public boolean isDisableAnimation() {
        return disableAnimation;
    }

    public void setDisableAnimation(boolean disableAnimation) {
        this.disableAnimation = disableAnimation;
    }

    /**
     * this style class will be activated when a validation error occurs
     */
    private static final PseudoClass PSEUDO_CLASS_ERROR = PseudoClass.getPseudoClass("error");

}
