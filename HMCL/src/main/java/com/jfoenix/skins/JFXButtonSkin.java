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

import com.jfoenix.adapters.skins.ButtonSkinAdapter;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXButton.ButtonType;
import com.jfoenix.controls.JFXRippler;
import com.jfoenix.effects.JFXDepthManager;
import com.jfoenix.transitions.CachedTransition;
import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * <h1>Material Design Button Skin</h1>
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXButtonSkin extends ButtonSkinAdapter {

    private Transition clickedAnimation;
    private final JFXRippler buttonRippler;
    private Runnable releaseManualRippler = null;
    private boolean invalid = true;
    private boolean mousePressed = false;

    public JFXButtonSkin(JFXButton button) {
        super(button);

        buttonRippler = new JFXRippler(getSkinnable()) {
            @Override
            protected Node getMask() {
                StackPane mask = new StackPane();
                mask.shapeProperty().bind(getSkinnable().shapeProperty());
                JFXNodeUtils.updateBackground(getSkinnable().getBackground(), mask);
                mask.resize(getWidth() - snappedRightInset() - snappedLeftInset(),
                    getHeight() - snappedBottomInset() - snappedTopInset());
                return mask;
            }

            @Override
            protected void positionControl(Node control) {
                // do nothing as the controls is not inside the ripple
            }
        };

        // add listeners to the button and bind properties
        button.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> playClickAnimation(1));
//        button.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> playClickAnimation(-1));
        button.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> mousePressed = true);
        button.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> mousePressed = false);
        button.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> mousePressed = false);

        button.ripplerFillProperty().addListener((o, oldVal, newVal) -> buttonRippler.setRipplerFill(newVal));

        button.armedProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                if (!mousePressed) {
                    releaseManualRippler = buttonRippler.createManualRipple();
                    playClickAnimation(1);
                }
            } else {
                if (releaseManualRippler != null) {
                    releaseManualRippler.run();
                    releaseManualRippler = null;
                }
                playClickAnimation(-1);
            }
        });

        // show focused state
        button.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (!button.disableVisualFocusProperty().get()) {
                if (newVal) {
                    if (!getSkinnable().isPressed()) {
                        buttonRippler.setOverlayVisible(true);
                    }
                } else {
                    buttonRippler.setOverlayVisible(false);
                }
            }
        });

        button.buttonTypeProperty().addListener((o, oldVal, newVal) -> updateButtonType(newVal));

        updateButtonType(button.getButtonType());

        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        if (buttonRippler != null) {
            getChildren().add(0, buttonRippler);
        }
        for (int i = 1; i < getChildren().size(); i++) {
            final Node child = getChildren().get(i);
            if (child instanceof Text) {
                child.setMouseTransparent(true);
            }
        }
    }

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        if (invalid) {
            if (((JFXButton) getSkinnable()).getRipplerFill() == null) {
                // change rippler fill according to the last LabeledText/Label child
                for (int i = getChildren().size() - 1; i >= 1; i--) {
                    if (getChildren().get(i) instanceof Text) {
                        buttonRippler.setRipplerFill(((Text) getChildren().get(i)).getFill());
                        ((Text) getChildren().get(i)).fillProperty()
                            .addListener((o, oldVal, newVal) -> buttonRippler.setRipplerFill(
                                newVal));
                        break;
                    } else if (getChildren().get(i) instanceof Label) {
                        buttonRippler.setRipplerFill(((Label) getChildren().get(i)).getTextFill());
                        ((Label) getChildren().get(i)).textFillProperty()
                            .addListener((o, oldVal, newVal) -> buttonRippler.setRipplerFill(
                                newVal));
                        break;
                    }
                }
            } else {
                buttonRippler.setRipplerFill(((JFXButton) getSkinnable()).getRipplerFill());
            }
            invalid = false;
        }
        buttonRippler.resizeRelocate(
            getSkinnable().getLayoutBounds().getMinX(),
            getSkinnable().getLayoutBounds().getMinY(),
            getSkinnable().getWidth(), getSkinnable().getHeight());
        layoutLabelInArea(x, y, w, h);
    }


    private void updateButtonType(ButtonType type) {
        switch (type) {
            case RAISED:
                JFXDepthManager.setDepth(getSkinnable(), 2);
                clickedAnimation = new ButtonClickTransition(getSkinnable(), (DropShadow) getSkinnable().getEffect());
                /*
                 * disable action when clicking on the button shadow
                 */
                getSkinnable().setPickOnBounds(false);
                break;
            default:
                getSkinnable().setEffect(null);
                getSkinnable().setPickOnBounds(true);
                break;
        }
    }

    private void playClickAnimation(double rate) {
        if (clickedAnimation != null) {
            if (!clickedAnimation.getCurrentTime().equals(clickedAnimation.getCycleDuration()) || rate != 1) {
                clickedAnimation.setRate(rate);
                clickedAnimation.play();
            }
        }
    }

    private static class ButtonClickTransition extends CachedTransition {
        ButtonClickTransition(Node node, DropShadow shadowEffect) {
            super(node, new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(shadowEffect.radiusProperty(),
                            JFXDepthManager.getShadowAt(2).radiusProperty().get(),
                            Interpolator.EASE_BOTH),
                        new KeyValue(shadowEffect.spreadProperty(),
                            JFXDepthManager.getShadowAt(2).spreadProperty().get(),
                            Interpolator.EASE_BOTH),
                        new KeyValue(shadowEffect.offsetXProperty(),
                            JFXDepthManager.getShadowAt(2).offsetXProperty().get(),
                            Interpolator.EASE_BOTH),
                        new KeyValue(shadowEffect.offsetYProperty(),
                            JFXDepthManager.getShadowAt(2).offsetYProperty().get(),
                            Interpolator.EASE_BOTH)
                    ),
                    new KeyFrame(Duration.millis(1000),
                        new KeyValue(shadowEffect.radiusProperty(),
                            JFXDepthManager.getShadowAt(5).radiusProperty().get(),
                            Interpolator.EASE_BOTH),
                        new KeyValue(shadowEffect.spreadProperty(),
                            JFXDepthManager.getShadowAt(5).spreadProperty().get(),
                            Interpolator.EASE_BOTH),
                        new KeyValue(shadowEffect.offsetXProperty(),
                            JFXDepthManager.getShadowAt(5).offsetXProperty().get(),
                            Interpolator.EASE_BOTH),
                        new KeyValue(shadowEffect.offsetYProperty(),
                            JFXDepthManager.getShadowAt(5).offsetYProperty().get(),
                            Interpolator.EASE_BOTH)
                    )
                )
            );
            // reduce the number to increase the shifting , increase number to reduce shifting
            setCycleDuration(Duration.seconds(0.2));
            setDelay(Duration.seconds(0));
        }
    }
}
