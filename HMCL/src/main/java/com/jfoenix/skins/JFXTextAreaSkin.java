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

import com.jfoenix.adapters.skins.TextAreaSkinAdapter;
import com.jfoenix.controls.JFXTextArea;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.Collections;

/**
 * <h1>Material Design TextArea Skin</h1>
 *
 * @author Shadi Shaheen
 * @version 2.0
 * @since 2017-01-25
 */
public class JFXTextAreaSkin extends TextAreaSkinAdapter {

    private boolean invalid = true;

    private final ScrollPane scrollPane;
    private Text promptText;

    private final ValidationPane<JFXTextArea> errorContainer;
    private final PromptLinesWrapper<JFXTextArea> linesWrapper;

    public JFXTextAreaSkin(JFXTextArea textArea) {
        super(textArea);
        // init text area properties
        scrollPane = (ScrollPane) getChildren().get(0);
        textArea.setWrapText(true);

        linesWrapper = new PromptLinesWrapper<>(
                textArea,
                promptTextFillProperty(),
                textArea.textProperty(),
                textArea.promptTextProperty(),
                () -> promptText);

        linesWrapper.init(this::createPromptNode, scrollPane);
        errorContainer = new ValidationPane<>(textArea);
        getChildren().addAll(linesWrapper.line, linesWrapper.focusedLine, linesWrapper.promptContainer, errorContainer);

        __registerChangeListener(textArea.disableProperty(), "DISABLE_NODE");
        __registerChangeListener(textArea.focusColorProperty(), "FOCUS_COLOR");
        __registerChangeListener(textArea.unFocusColorProperty(), "UNFOCUS_COLOR");
        __registerChangeListener(textArea.disableAnimationProperty(), "DISABLE_ANIMATION");
    }

    @Override
    protected void __handleControlPropertyChanged(String key) {
        if ("DISABLE_NODE".equals(key)) {
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
        linesWrapper.layoutLines(x, y, w, h, height, promptText == null ? 0 : promptText.getLayoutBounds().getHeight() + 3);
        errorContainer.layoutPane(x, height + linesWrapper.focusedLine.getHeight(), w, h);
        linesWrapper.updateLabelFloatLayout();

        if (invalid) {
            invalid = false;
            // set the default background of text area viewport to white
            Region viewPort = (Region) scrollPane.getChildrenUnmodifiable().get(0);
            viewPort.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT,
                    CornerRadii.EMPTY,
                    Insets.EMPTY)));
            // reapply css of scroll pane in case set by the user
            viewPort.applyCss();
            errorContainer.invalid(w);
            // focus
            linesWrapper.invalid();
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
        promptText.setTranslateX(1);
        promptText.getTransforms().add(linesWrapper.promptTextScale);
        linesWrapper.promptContainer.getChildren().add(promptText);
        if (getSkinnable().isFocused() && ((JFXTextArea) getSkinnable()).isLabelFloat()) {
            promptText.setTranslateY(-Math.floor(scrollPane.getHeight()));
            linesWrapper.promptTextScale.setX(0.85);
            linesWrapper.promptTextScale.setY(0.85);
        }

        Text oldValue = __getPromptNode();
        if (oldValue != null) {
            removeHighlight(Collections.singletonList(((Node) oldValue)));
        }

        __setPromptNode(promptText);
    }
}
