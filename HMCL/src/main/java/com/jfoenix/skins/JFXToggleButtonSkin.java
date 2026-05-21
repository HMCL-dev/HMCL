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

import com.jfoenix.controls.JFXToggleButton;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNullByDefault;

/// The Material Design 3 switch skin used by [JFXToggleButton].
@NotNullByDefault
public class JFXToggleButtonSkin extends SkinBase<JFXToggleButton> {
    /// The unscaled switch track width.
    private static final double TRACK_WIDTH = 52.0;

    /// The unscaled switch track height.
    private static final double TRACK_HEIGHT = 32.0;

    /// The unscaled minimum circular state layer size around the thumb.
    private static final double STATE_LAYER_SIZE = 40.0;

    /// The unscaled off-state thumb center within the track.
    private static final double OFF_THUMB_CENTER_X = 16.0;

    /// The unscaled on-state thumb center within the track.
    private static final double ON_THUMB_CENTER_X = 36.0;

    /// The unscaled off-state thumb size.
    private static final double OFF_THUMB_SIZE = 16.0;

    /// The unscaled on-state thumb size.
    private static final double ON_THUMB_SIZE = 24.0;

    /// The legacy size value that represents the default M3 switch scale.
    private static final double DEFAULT_SIZE = 8.0;

    /// The animation duration for thumb movement.
    private static final Duration SELECTION_DURATION = Duration.millis(150.0);

    /// The animation duration for state layer opacity changes.
    private static final Duration STATE_LAYER_DURATION = Duration.millis(100.0);

    /// The animation duration for ripple expansion.
    private static final Duration RIPPLE_EXPANSION_DURATION = Duration.millis(250.0);

    /// The animation duration for ripple fade-out.
    private static final Duration RIPPLE_FADE_DURATION = Duration.millis(150.0);

    /// The root layout container.
    private final HBox container = new HBox();

    /// The switch touch target slot.
    private final StackPane indicatorSlot = new StackPane();

    /// The visual switch track.
    private final StackPane box = new StackPane();

    /// The visual switch thumb.
    private final StackPane thumb = new StackPane();

    /// The persistent interaction state layer.
    private final Region stateLayer = new Region();

    /// The animated press ripple.
    private final Region ripple = new Region();

    /// The label that mirrors the skinnable control's labeled content.
    private final Label label = new Label();

    /// The animated thumb position from off to on.
    private final DoubleProperty thumbPosition = new SimpleDoubleProperty(this, "thumbPosition");

    /// The thumb position animation.
    private final Timeline selectionAnimation = new Timeline();

    /// The state layer opacity animation.
    private final Timeline stateLayerAnimation = new Timeline();

    /// The ripple animation.
    private final Timeline rippleAnimation = new Timeline();

    /// Requests layout after thumb position changes.
    private final InvalidationListener thumbPositionListener = observable -> getSkinnable().requestLayout();

    /// Applies size token changes to the switch layout.
    private final InvalidationListener metricsInvalidation = observable -> updateMetrics();

    /// Applies track shape token changes to the switch track.
    private final InvalidationListener trackShapeInvalidation = observable -> updateTrackStyle();

    /// Animates the thumb after selection changes.
    private final ChangeListener<Boolean> selectedListener =
            (observable, oldValue, newValue) -> animateThumbPosition(newValue);

    /// Updates state layer opacity when an interaction state changes.
    private final ChangeListener<Boolean> interactionStateListener =
            (observable, oldValue, newValue) -> updateStateLayerOpacity();

    /// Handles primary mouse presses.
    private final EventHandler<MouseEvent> mousePressedHandler = this::handleMousePressed;

    /// Handles primary mouse releases.
    private final EventHandler<MouseEvent> mouseReleasedHandler = this::handleMouseReleased;

    /// Handles pointer entry while a mouse press is active.
    private final EventHandler<MouseEvent> mouseEnteredHandler = this::handleMouseEntered;

    /// Handles pointer exit while a mouse press is active.
    private final EventHandler<MouseEvent> mouseExitedHandler = this::handleMouseExited;

    /// Handles keyboard activation presses.
    private final EventHandler<KeyEvent> keyPressedHandler = this::handleKeyPressed;

    /// Handles keyboard activation releases.
    private final EventHandler<KeyEvent> keyReleasedHandler = this::handleKeyReleased;

    /// Whether the current interaction was started by a primary mouse press.
    private boolean mousePressed;

    /// Whether the space key currently owns the armed state.
    private boolean spaceKeyPressed;

    /// Creates a switch skin.
    public JFXToggleButtonSkin(JFXToggleButton control) {
        super(control);
        configureNodes(control);
        bindLabel(control);
        installListeners(control);
        installInteractionHandlers(control);
        thumbPosition.set(control.isSelected() ? 1.0 : 0.0);
        thumbPosition.addListener(thumbPositionListener);
        updateMetrics();
        updateStateLayerOpacity();
    }

    /// Removes listeners and stops animations before the skin is disposed.
    @Override
    public void dispose() {
        JFXToggleButton control = getSkinnable();
        resetInteractionState();
        selectionAnimation.stop();
        stateLayerAnimation.stop();
        rippleAnimation.stop();
        thumbPosition.removeListener(thumbPositionListener);
        uninstallListeners(control);
        uninstallInteractionHandlers(control);
        unbindLabel();
        super.dispose();
    }

    /// Computes the minimum width from the internal container.
    @Override
    protected double computeMinWidth(
            double height,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return leftInset + container.minWidth(height) + rightInset;
    }

    /// Computes the minimum height from the internal container.
    @Override
    protected double computeMinHeight(
            double width,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return topInset + container.minHeight(width) + bottomInset;
    }

    /// Computes the preferred width from the internal container.
    @Override
    protected double computePrefWidth(
            double height,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return leftInset + container.prefWidth(height) + rightInset;
    }

    /// Computes the preferred height from the internal container.
    @Override
    protected double computePrefHeight(
            double width,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return topInset + container.prefHeight(width) + bottomInset;
    }

    /// Lays out the internal container and switch thumb.
    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        container.resizeRelocate(x, y, width, height);
        layoutThumb();
    }

    /// Configures static node classes and hierarchy.
    private void configureNodes(JFXToggleButton control) {
        container.getStyleClass().add("m3-selection-container");
        indicatorSlot.getStyleClass().add("m3-selection-indicator");
        box.getStyleClass().addAll("box", "m3-switch-track");
        thumb.getStyleClass().addAll("thumb", "m3-switch-thumb");
        stateLayer.getStyleClass().add("m3-state-layer");
        ripple.getStyleClass().add("m3-ripple");
        label.getStyleClass().add("m3-selection-label");

        container.setAlignment(Pos.CENTER_LEFT);
        indicatorSlot.setAlignment(Pos.CENTER);
        stateLayer.setManaged(false);
        ripple.setManaged(false);
        thumb.setManaged(false);
        stateLayer.setMouseTransparent(true);
        ripple.setMouseTransparent(true);
        stateLayer.setOpacity(0.0);
        ripple.setOpacity(0.0);

        indicatorSlot.getChildren().addAll(box, stateLayer, ripple, thumb);
        container.getChildren().addAll(indicatorSlot, label);
        getChildren().add(container);

    }

    /// Installs property listeners for layout, state, and interaction updates.
    private void installListeners(JFXToggleButton control) {
        control.sizeProperty().addListener(metricsInvalidation);
        control.touchTargetSizeProperty().addListener(metricsInvalidation);
        control.trackShapeProperty().addListener(trackShapeInvalidation);
        control.selectedProperty().addListener(selectedListener);
        control.hoverProperty().addListener(interactionStateListener);
        control.focusedProperty().addListener(interactionStateListener);
        control.armedProperty().addListener(interactionStateListener);
        control.pressedProperty().addListener(interactionStateListener);
        control.disabledProperty().addListener(interactionStateListener);
    }

    /// Uninstalls property listeners installed by this skin.
    private void uninstallListeners(JFXToggleButton control) {
        control.sizeProperty().removeListener(metricsInvalidation);
        control.touchTargetSizeProperty().removeListener(metricsInvalidation);
        control.trackShapeProperty().removeListener(trackShapeInvalidation);
        control.selectedProperty().removeListener(selectedListener);
        control.hoverProperty().removeListener(interactionStateListener);
        control.focusedProperty().removeListener(interactionStateListener);
        control.armedProperty().removeListener(interactionStateListener);
        control.pressedProperty().removeListener(interactionStateListener);
        control.disabledProperty().removeListener(interactionStateListener);
    }

    /// Installs mouse and keyboard behavior handlers.
    private void installInteractionHandlers(JFXToggleButton control) {
        control.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        control.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        control.addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEnteredHandler);
        control.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        control.addEventHandler(KeyEvent.KEY_PRESSED, keyPressedHandler);
        control.addEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
    }

    /// Uninstalls mouse and keyboard behavior handlers.
    private void uninstallInteractionHandlers(JFXToggleButton control) {
        control.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        control.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        control.removeEventHandler(MouseEvent.MOUSE_ENTERED, mouseEnteredHandler);
        control.removeEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        control.removeEventHandler(KeyEvent.KEY_PRESSED, keyPressedHandler);
        control.removeEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
    }

    /// Binds label content and presentation properties to the skinnable control.
    private void bindLabel(JFXToggleButton control) {
        label.textProperty().bind(control.textProperty());
        label.graphicProperty().bind(control.graphicProperty());
        label.textFillProperty().bind(control.textFillProperty());
        label.fontProperty().bind(control.fontProperty());
        label.contentDisplayProperty().bind(control.contentDisplayProperty());
        label.graphicTextGapProperty().bind(control.graphicTextGapProperty());
        label.alignmentProperty().bind(control.alignmentProperty());
        label.textAlignmentProperty().bind(control.textAlignmentProperty());
        label.textOverrunProperty().bind(control.textOverrunProperty());
        label.ellipsisStringProperty().bind(control.ellipsisStringProperty());
        label.wrapTextProperty().bind(control.wrapTextProperty());
        label.underlineProperty().bind(control.underlineProperty());
        label.mnemonicParsingProperty().bind(control.mnemonicParsingProperty());
    }

    /// Unbinds mirrored label properties from the skinnable control.
    private void unbindLabel() {
        label.textProperty().unbind();
        label.graphicProperty().unbind();
        label.textFillProperty().unbind();
        label.fontProperty().unbind();
        label.contentDisplayProperty().unbind();
        label.graphicTextGapProperty().unbind();
        label.alignmentProperty().unbind();
        label.textAlignmentProperty().unbind();
        label.textOverrunProperty().unbind();
        label.ellipsisStringProperty().unbind();
        label.wrapTextProperty().unbind();
        label.underlineProperty().unbind();
        label.mnemonicParsingProperty().unbind();
    }

    /// Applies size-related control tokens to the skin nodes.
    private void updateMetrics() {
        double scale = scale();
        double trackWidth = TRACK_WIDTH * scale;
        double trackHeight = TRACK_HEIGHT * scale;
        double touchTargetHeight = Math.max(getSkinnable().getTouchTargetSize(), trackHeight);
        setFixedSize(indicatorSlot, trackWidth, touchTargetHeight);
        setFixedSize(box, trackWidth, trackHeight);
        updateTrackStyle();
        getSkinnable().requestLayout();
    }

    /// Applies the switch track shape token to the visual track.
    private void updateTrackStyle() {
        String shape = formatPixels(getSkinnable().getTrackShape());
        box.setStyle("-fx-background-radius: " + shape + "; -fx-border-radius: " + shape + ";");
    }

    /// Animates the thumb to the selected or unselected position.
    private void animateThumbPosition(boolean selected) {
        double target = selected ? 1.0 : 0.0;
        selectionAnimation.stop();
        if (getSkinnable().isDisableAnimation()) {
            thumbPosition.set(target);
            return;
        }

        selectionAnimation.getKeyFrames().setAll(new KeyFrame(
                SELECTION_DURATION,
                new KeyValue(thumbPosition, target, Interpolator.EASE_BOTH)
        ));
        selectionAnimation.playFromStart();
    }

    /// Lays out the thumb and its interaction layers from the animated position value.
    private void layoutThumb() {
        double scale = scale();
        double position = thumbPosition.get();
        double thumbSize = scaled(OFF_THUMB_SIZE + (ON_THUMB_SIZE - OFF_THUMB_SIZE) * position, scale);
        double thumbCenterX = scaled(OFF_THUMB_CENTER_X + (ON_THUMB_CENTER_X - OFF_THUMB_CENTER_X) * position, scale);
        double thumbX = thumbCenterX - thumbSize / 2.0;
        double touchTargetHeight = Math.max(getSkinnable().getTouchTargetSize(), scaled(TRACK_HEIGHT, scale));
        double thumbY = (touchTargetHeight - thumbSize) / 2.0;
        double stateLayerSize = Math.max(scaled(STATE_LAYER_SIZE, scale), getSkinnable().getTouchTargetSize());
        double stateLayerX = thumbCenterX - stateLayerSize / 2.0;
        double stateLayerY = (touchTargetHeight - stateLayerSize) / 2.0;
        double radius = stateLayerSize / 2.0;

        layoutStateRegion(stateLayer, stateLayerX, stateLayerY, stateLayerSize, radius);
        layoutStateRegion(ripple, stateLayerX, stateLayerY, stateLayerSize, radius);
        thumb.resizeRelocate(thumbX, thumbY, thumbSize, thumbSize);
    }

    /// Applies a circular size and radius to a state feedback region.
    private void layoutStateRegion(Region region, double x, double y, double size, double radius) {
        region.resizeRelocate(x, y, size, size);
        region.setStyle("-fx-background-radius: " + formatPixels(radius) + ";");
    }

    /// Updates the persistent state layer opacity from current interaction state.
    private void updateStateLayerOpacity() {
        JFXToggleButton control = getSkinnable();
        double targetOpacity;
        if (control.isDisabled()) {
            targetOpacity = 0.0;
        } else if (control.isPressed() || control.isArmed()) {
            targetOpacity = 0.12;
        } else if (control.isFocused() && !control.isDisableVisualFocus()) {
            targetOpacity = 0.10;
        } else if (control.isHover()) {
            targetOpacity = 0.08;
        } else {
            targetOpacity = 0.0;
        }

        animateOpacity(stateLayer, stateLayerAnimation, targetOpacity, STATE_LAYER_DURATION);
    }

    /// Arms the control on primary mouse press.
    private void handleMousePressed(MouseEvent event) {
        JFXToggleButton control = getSkinnable();
        if (control.isDisabled() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        mousePressed = true;
        if (control.isFocusTraversable()) {
            control.requestFocus();
        }
        playRipple(event);
        control.arm();
        event.consume();
    }

    /// Fires the control when a primary mouse press is released inside the control.
    private void handleMouseReleased(MouseEvent event) {
        JFXToggleButton control = getSkinnable();
        if (!mousePressed || event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        boolean shouldFire = control.isArmed() && control.contains(event.getX(), event.getY());
        mousePressed = false;
        releaseRipple();
        control.disarm();
        if (shouldFire) {
            control.fire();
        }
        event.consume();
    }

    /// Re-arms the control when a pressed pointer re-enters the control.
    private void handleMouseEntered(MouseEvent event) {
        JFXToggleButton control = getSkinnable();
        if (mousePressed && !control.isDisabled()) {
            control.arm();
            event.consume();
        }
    }

    /// Disarms the control when a pressed pointer exits the control.
    private void handleMouseExited(MouseEvent event) {
        JFXToggleButton control = getSkinnable();
        if (mousePressed && !control.isDisabled()) {
            control.disarm();
            event.consume();
        }
    }

    /// Handles keyboard activation for enter and space.
    private void handleKeyPressed(KeyEvent event) {
        JFXToggleButton control = getSkinnable();
        if (control.isDisabled()) {
            return;
        }

        if (event.getCode() == KeyCode.SPACE) {
            if (!spaceKeyPressed) {
                spaceKeyPressed = true;
                playCenteredRipple();
                control.arm();
            }
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
            playCenteredRipple();
            releaseRipple();
            control.fire();
            event.consume();
        }
    }

    /// Fires the control when a space key activation is released.
    private void handleKeyReleased(KeyEvent event) {
        JFXToggleButton control = getSkinnable();
        if (event.getCode() != KeyCode.SPACE || !spaceKeyPressed) {
            return;
        }

        boolean shouldFire = control.isArmed() && !control.isDisabled();
        spaceKeyPressed = false;
        releaseRipple();
        control.disarm();
        if (shouldFire) {
            control.fire();
        }
        event.consume();
    }

    /// Clears armed state and transient feedback.
    private void resetInteractionState() {
        mousePressed = false;
        spaceKeyPressed = false;
        rippleAnimation.stop();
        ripple.setOpacity(0.0);
        getSkinnable().disarm();
    }

    /// Plays the press ripple from a mouse event.
    private void playRipple(MouseEvent event) {
        Point2D point = indicatorSlot.sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.setTranslateX((point.getX() - (ripple.getLayoutX() + ripple.getWidth() / 2.0)) * 0.12);
        ripple.setTranslateY((point.getY() - (ripple.getLayoutY() + ripple.getHeight() / 2.0)) * 0.12);
        playRippleAnimation();
    }

    /// Plays the press ripple from the current thumb center.
    private void playCenteredRipple() {
        ripple.setTranslateX(0.0);
        ripple.setTranslateY(0.0);
        playRippleAnimation();
    }

    /// Starts the ripple expansion animation.
    private void playRippleAnimation() {
        rippleAnimation.stop();
        ripple.setScaleX(0.45);
        ripple.setScaleY(0.45);
        ripple.setOpacity(0.18);
        if (getSkinnable().isDisableAnimation()) {
            ripple.setScaleX(1.0);
            ripple.setScaleY(1.0);
            return;
        }

        rippleAnimation.getKeyFrames().setAll(new KeyFrame(
                RIPPLE_EXPANSION_DURATION,
                new KeyValue(ripple.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(ripple.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
        ));
        rippleAnimation.playFromStart();
    }

    /// Releases the active ripple and fades it out.
    private void releaseRipple() {
        rippleAnimation.stop();
        animateOpacity(ripple, rippleAnimation, 0.0, RIPPLE_FADE_DURATION);
    }

    /// Animates a region opacity or applies the target immediately when animations are disabled.
    private void animateOpacity(Region region, Timeline timeline, double targetOpacity, Duration duration) {
        timeline.stop();
        if (getSkinnable().isDisableAnimation()) {
            region.setOpacity(targetOpacity);
            return;
        }

        timeline.getKeyFrames().setAll(new KeyFrame(
                duration,
                new KeyValue(region.opacityProperty(), targetOpacity, Interpolator.EASE_BOTH)
        ));
        timeline.playFromStart();
    }

    /// Applies a fixed size to a region.
    private static void setFixedSize(Region region, double width, double height) {
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
    }

    /// Returns the current legacy-size scale factor.
    private double scale() {
        return getSkinnable().getSize() / DEFAULT_SIZE;
    }

    /// Scales a base pixel value.
    private static double scaled(double value, double scale) {
        return value * scale;
    }

    /// Formats a CSS pixel value.
    private static String formatPixels(double value) {
        if (Math.rint(value) == value) {
            return (long) value + "px";
        }
        return value + "px";
    }
}
