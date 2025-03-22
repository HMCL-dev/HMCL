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

import com.jfoenix.adapters.skins.TextFieldSkinAdapter;
import com.jfoenix.controls.base.IFXLabelFloatControl;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;

/**
 * <h1>Material Design Text input control Skin, used for both JFXTextField/JFXPasswordField</h1>
 *
 * @author Shadi Shaheen
 * @version 2.0
 * @since 2017-01-25
 */
public class JFXTextFieldSkin<T extends TextField & IFXLabelFloatControl> extends TextFieldSkinAdapter {

    private boolean invalid = true;

    private Text promptText;
    private final Pane textPane;
    private final Node textNode;
    private final ObservableDoubleValue textRight;
    private final DoubleProperty textTranslateX;

    private final ValidationPane<T> errorContainer;
    private final PromptLinesWrapper<T> linesWrapper;

    public JFXTextFieldSkin(T textField) {
        super(textField);
        textPane = (Pane) this.getChildren().get(0);

        // get parent fields
        textNode = __getTextNode();
        textTranslateX = __getTextTranslateX();
        textRight = __getTextRightHandle();

        linesWrapper = new PromptLinesWrapper<>(
                textField,
                __promptTextFillProperty(),
                textField.textProperty(),
                textField.promptTextProperty(),
                () -> promptText
        );

        linesWrapper.init(this::createPromptNode, textPane);

        __setUsePromptText(linesWrapper.usePromptText);

        errorContainer = new ValidationPane<>(textField);

        getChildren().addAll(linesWrapper.line, linesWrapper.focusedLine, linesWrapper.promptContainer, errorContainer);

        __registerChangeListener(textField.disableProperty(), "DISABLE_ANIMATION");
        __registerChangeListener(textField.focusColorProperty(), "FOCUS_COLOR");
        __registerChangeListener(textField.unFocusColorProperty(), "UNFOCUS_COLOR");
        __registerChangeListener(textField.disableAnimationProperty(), "DISABLE_ANIMATION");
    }

    protected void __handleControlPropertyChanged(String key) {
        if ("LEADING_GRAPHIC".equals(key)) {
            // fixme: updateGraphic(((JFXTextField) getSkinnable()).getLeadingGraphic(), "leading");
        } else if ("TRAILING_GRAPHIC".equals(key)) {
            // fixme: updateGraphic(((JFXTextField) getSkinnable()).getTrailingGraphic(), "trailing");
        } else if ("DISABLE_NODE".equals(key)) {
            linesWrapper.updateDisabled();
        } else if ("FOCUS_COLOR".equals(key)) {
            linesWrapper.updateFocusColor();
        } else if ("UNFOCUS_COLOR".equals(key)) {
            linesWrapper.updateUnfocusColor();
        } else if ("DISABLE_ANIMATION".equals(key)) {
            // remove error clip if animation is disabled
            errorContainer.updateClip();
        }
    }

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        super.layoutChildren(x, y, w, h);

        final double height = getSkinnable().getHeight();
        linesWrapper.layoutLines(x, y, w, h, height, Math.floor(h));
        errorContainer.layoutPane(x, height + linesWrapper.focusedLine.getHeight(), w, h);

        if (getSkinnable().getWidth() > 0) {
            updateTextPos();
        }

        linesWrapper.updateLabelFloatLayout();

        if (invalid) {
            invalid = false;
            // update validation container
            errorContainer.invalid(w);
            // focus
            linesWrapper.invalid();
        }
    }

    private void updateTextPos() {
        double textWidth = textNode.getLayoutBounds().getWidth();
        final double promptWidth = promptText == null ? 0 : promptText.getLayoutBounds().getWidth();
        switch (getSkinnable().getAlignment().getHpos()) {
            case CENTER:
                linesWrapper.promptTextScale.setPivotX(promptWidth / 2);
                double midPoint = textRight.get() / 2;
                double newX = midPoint - textWidth / 2;
                if (newX + textWidth <= textRight.get()) {
                    textTranslateX.set(newX);
                }
                break;
            case LEFT:
                linesWrapper.promptTextScale.setPivotX(0);
                break;
            case RIGHT:
                linesWrapper.promptTextScale.setPivotX(promptWidth);
                break;
        }

    }

    private void createPromptNode() {
        if (promptText != null || !linesWrapper.usePromptText.get()) {
            return;
        }
        promptText = new Text();
        promptText.setManaged(false);
        promptText.getStyleClass().add("text");
        promptText.visibleProperty().bind(linesWrapper.usePromptText);
        promptText.fontProperty().bind(getSkinnable().fontProperty());
        promptText.textProperty().bind(getSkinnable().promptTextProperty());
        promptText.fillProperty().bind(linesWrapper.animatedPromptTextFill);
        promptText.setLayoutX(1);
        promptText.getTransforms().add(linesWrapper.promptTextScale);
        linesWrapper.promptContainer.getChildren().add(promptText);
        if (getSkinnable().isFocused() && ((IFXLabelFloatControl) getSkinnable()).isLabelFloat()) {
            promptText.setTranslateY(-Math.floor(textPane.getHeight()));
            linesWrapper.promptTextScale.setX(0.85);
            linesWrapper.promptTextScale.setY(0.85);
        }

        Text oldValue = __getPromptNode();
        if (oldValue != null) {
            textPane.getChildren().remove(oldValue);
        }
        __setPromptNode(promptText);
    }
}
