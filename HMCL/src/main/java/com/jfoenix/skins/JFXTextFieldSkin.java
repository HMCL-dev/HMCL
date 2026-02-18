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

import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.controls.base.IFXLabelFloatControl;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;

import java.lang.reflect.Field;

/// # Material Design Text input control Skin, used for both JFXTextField/JFXPasswordField
///
/// @author Shadi Shaheen
/// @version 2.0
/// @since 2017-01-25
public class JFXTextFieldSkin<T extends TextField & IFXLabelFloatControl> extends TextFieldSkin {

    private boolean invalid = true;

    private Text promptText;
    private Pane textPane;
    private Node textNode;
    private ObservableDoubleValue textRight;
    private DoubleProperty textTranslateX;

    private ValidationPane<T> errorContainer;
    private PromptLinesWrapper<T> linesWrapper;

    public JFXTextFieldSkin(T textField) {
        super(textField);
        textPane = (Pane) this.getChildren().get(0);

        // get parent fields
        textNode = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textNode");
        textTranslateX = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textTranslateX");
        textRight = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textRight");

        linesWrapper = new PromptLinesWrapper<T>(
            textField,
            promptTextFillProperty(),
            textField.textProperty(),
            textField.promptTextProperty(),
            () -> promptText);

        linesWrapper.init(() -> createPromptNode(), textPane);

        ReflectionHelper.setFieldContent(TextFieldSkin.class, this, "usePromptText", linesWrapper.usePromptText);

        errorContainer = new ValidationPane<>(textField);

        getChildren().addAll(linesWrapper.line, linesWrapper.focusedLine, linesWrapper.promptContainer, errorContainer);

        registerChangeListener(textField.disableProperty(), obs -> linesWrapper.updateDisabled());
        registerChangeListener(textField.focusColorProperty(), obs -> linesWrapper.updateFocusColor());
        registerChangeListener(textField.unFocusColorProperty(), obs -> linesWrapper.updateUnfocusColor());
        registerChangeListener(textField.disableAnimationProperty(), obs -> errorContainer.updateClip());
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

        try {
            Field field = ReflectionHelper.getField(TextFieldSkin.class, "promptNode");
            Object oldValue = field.get(this);
            if (oldValue != null) {
                textPane.getChildren().remove(oldValue);
            }
            field.set(this, promptText);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
