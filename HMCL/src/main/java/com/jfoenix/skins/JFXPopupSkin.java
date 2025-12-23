/*
 * Copyright (c) 2016 JFoenix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.jfoenix.skins;

import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXPopup.PopupHPosition;
import com.jfoenix.controls.JFXPopup.PopupVPosition;
import com.jfoenix.effects.JFXDepthManager;
import com.jfoenix.transitions.CacheMomento;
import com.jfoenix.transitions.CachedTransition;
import javafx.animation.*;
import javafx.animation.Animation.Status;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/**
 * <h1>Material Design Popup Skin</h1>
 * TODO: REWORK
 *
 * @author Shadi Shaheen
 * @version 2.0
 * @since 2017-03-01
 */
public class JFXPopupSkin implements Skin<JFXPopup> {

    protected JFXPopup control;
    protected StackPane container = new StackPane();
    protected Region popupContent;
    protected Node root;

    private Animation animation;
    protected Scale scale;

    public JFXPopupSkin(JFXPopup control) {
        this.control = control;
        // set scale y to 0.01 instead of 0 to allow layout of the content,
        // otherwise it will cause exception in traverse engine, when focusing the 1st node
        scale = new Scale(1, 0.01, 0, 0);
        popupContent = control.getPopupContent();
        container.getStyleClass().add("jfx-popup-container");
        container.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        container.getChildren().add(popupContent);
        container.getTransforms().add(scale);
        container.setOpacity(0);
        root = JFXDepthManager.createMaterialNode(container, 4);
        animation = getAnimation();
    }


    public void reset(PopupVPosition vAlign, PopupHPosition hAlign, double offsetX, double offsetY) {
        // postion the popup according to its animation
        scale.setPivotX(hAlign == PopupHPosition.RIGHT ? container.getWidth() : 0);
        scale.setPivotY(vAlign == PopupVPosition.BOTTOM ? container.getHeight() : 0);
        root.setTranslateX(hAlign == PopupHPosition.RIGHT ? -container.getWidth() + offsetX : offsetX);
        root.setTranslateY(vAlign == PopupVPosition.BOTTOM ? -container.getHeight() + offsetY : offsetY);
    }

    public final void animate() {
        if (animation.getStatus() == Status.STOPPED) {
            animation.play();
        }
    }

    @Override
    public JFXPopup getSkinnable() {
        return control;
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void dispose() {
        animation.stop();
        animation = null;
        container = null;
        control = null;
        popupContent = null;
        root = null;
    }

    protected Animation getAnimation() {
        return new PopupTransition();
    }

    private final class PopupTransition extends CachedTransition {
        PopupTransition() {
            super(root, new Timeline(
                            new KeyFrame(
                                    Duration.ZERO,
                                    new KeyValue(popupContent.opacityProperty(), 0, Interpolator.EASE_BOTH),
                                    new KeyValue(scale.xProperty(), 0, Interpolator.EASE_BOTH),
                                    new KeyValue(scale.yProperty(), 0, Interpolator.EASE_BOTH)
                            ),
                            new KeyFrame(Duration.millis(700),
                                    new KeyValue(scale.xProperty(), 1, Interpolator.EASE_BOTH),
                                    new KeyValue(popupContent.opacityProperty(), 0, Interpolator.EASE_BOTH)
                            ),
                            new KeyFrame(Duration.millis(1000),
                                    new KeyValue(popupContent.opacityProperty(), 1, Interpolator.EASE_BOTH),
                                    new KeyValue(scale.yProperty(), 1, Interpolator.EASE_BOTH)
                            )
                    )
                    , new CacheMomento(popupContent));
            setCycleDuration(Duration.seconds(.4));
            setDelay(Duration.seconds(0));
        }

        @Override
        protected void starting() {
            container.setOpacity(1);
            super.starting();
        }
    }

    public void init() {
        animation.stop();
        container.setOpacity(0);
        scale.setX(1);
        scale.setY(0.1);
    }
}