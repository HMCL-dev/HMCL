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

package com.jfoenix.controls;

import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/// JFXSnackbarLayout default layout for snackbar content
///
/// @author Shadi Shaheen
/// @version 1.0
/// @since 2018-11-16
public class JFXSnackbarLayout extends BorderPane {

    private final Label toast;
    private JFXButton action;
    private final StackPane actionContainer;

    public JFXSnackbarLayout(String message) {
        this(message, null, null);
    }

    public JFXSnackbarLayout(String message, String actionText, EventHandler<ActionEvent> actionHandler) {
        initialize();

        toast = new Label();
        toast.setMinWidth(Control.USE_PREF_SIZE);
        toast.getStyleClass().add("jfx-snackbar-toast");
        toast.setWrapText(true);
        toast.setText(message);
        StackPane toastContainer = new StackPane(toast);
        toastContainer.setPadding(new Insets(20));
        actionContainer = new StackPane();
        actionContainer.setPadding(new Insets(0, 10, 0, 0));

        toast.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> {
            if (getPrefWidth() == -1) {
                return getPrefWidth();
            }
            double actionWidth = actionContainer.isVisible() ? actionContainer.getWidth() : 0.0;
            return prefWidthProperty().get() - actionWidth;
        }, prefWidthProperty(), actionContainer.widthProperty(), actionContainer.visibleProperty()));

        setLeft(toastContainer);
        setRight(actionContainer);

        if (actionText != null) {
            action = new JFXButton();
            action.setText(actionText);
            action.setOnAction(actionHandler);
            action.setMinWidth(Control.USE_PREF_SIZE);
            action.setButtonType(JFXButton.ButtonType.FLAT);
            action.getStyleClass().add("jfx-snackbar-action");
            // actions will be added upon showing the snackbar if needed
            actionContainer.getChildren().add(action);

            if (!actionText.isEmpty()) {
                action.setVisible(true);
                actionContainer.setVisible(true);
                actionContainer.setManaged(true);
                // to force updating the layout bounds
                action.setText("");
                action.setText(actionText);
                action.setOnAction(actionHandler);
            } else {
                actionContainer.setVisible(false);
                actionContainer.setManaged(false);
                action.setVisible(false);
            }
        }
    }

    private static final String DEFAULT_STYLE_CLASS = "jfx-snackbar-layout";

    public String getToast() {
        return toast.getText();
    }

    public void setToast(String toast) {
        this.toast.setText(toast);
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }
}

