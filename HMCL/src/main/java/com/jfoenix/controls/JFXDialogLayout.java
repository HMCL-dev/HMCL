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

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

// Old
public class JFXDialogLayout extends StackPane {
    private final StackPane heading = new StackPane();
    private final StackPane body = new StackPane();
    private final FlowPane actions = new FlowPane() {
        protected double computeMinWidth(double height) {
            if (this.getContentBias() == Orientation.HORIZONTAL) {
                double maxPref = 0.0;
                List<Node> children = this.getChildren();
                int i = 0;

                for (int size = children.size(); i < size; ++i) {
                    Node child = children.get(i);
                    if (child.isManaged()) {
                        maxPref = Math.max(maxPref, child.minWidth(-1.0));
                    }
                }

                Insets insets = this.getInsets();
                return insets.getLeft() + this.snapSize(maxPref) + insets.getRight();
            } else {
                return this.computePrefWidth(height);
            }
        }

        protected double computeMinHeight(double width) {
            if (this.getContentBias() == Orientation.VERTICAL) {
                double maxPref = 0.0;
                List<Node> children = this.getChildren();
                int i = 0;

                for (int size = children.size(); i < size; ++i) {
                    Node child = children.get(i);
                    if (child.isManaged()) {
                        maxPref = Math.max(maxPref, child.minHeight(-1.0));
                    }
                }

                Insets insets = this.getInsets();
                return insets.getTop() + this.snapSize(maxPref) + insets.getBottom();
            } else {
                return this.computePrefHeight(width);
            }
        }
    };
    private static final String DEFAULT_STYLE_CLASS = "jfx-dialog-layout";

    public JFXDialogLayout() {
        this.initialize();
        VBox layout = new VBox();
        layout.getChildren().add(this.heading);
        this.heading.getStyleClass().add("jfx-layout-heading");
        this.heading.getStyleClass().add("title");
        layout.getChildren().add(this.body);
        this.body.getStyleClass().add("jfx-layout-body");
        this.body.prefHeightProperty().bind(this.prefHeightProperty());
        this.body.prefWidthProperty().bind(this.prefWidthProperty());
        layout.getChildren().add(this.actions);
        this.actions.getStyleClass().add("jfx-layout-actions");
        this.getChildren().add(layout);
    }

    public ObservableList<Node> getHeading() {
        return this.heading.getChildren();
    }

    public void setHeading(Node... titleContent) {
        this.heading.getChildren().setAll(titleContent);
    }

    public ObservableList<Node> getBody() {
        return this.body.getChildren();
    }

    public void setBody(Node... body) {
        this.body.getChildren().setAll(body);
    }

    public ObservableList<Node> getActions() {
        return this.actions.getChildren();
    }

    public void setActions(Node... actions) {
        this.actions.getChildren().setAll(actions);
    }

    public void setActions(List<? extends Node> actions) {
        this.actions.getChildren().setAll(actions);
    }

    private void initialize() {
        this.getStyleClass().add("jfx-dialog-layout");
        this.setPadding(new Insets(24.0, 24.0, 16.0, 24.0));
        this.setStyle("-fx-text-fill: rgba(0, 0, 0, 0.87);");
        this.heading.setStyle("-fx-font-weight: BOLD;-fx-alignment: center-left;");
        this.heading.setPadding(new Insets(5.0, 0.0, 5.0, 0.0));
        this.body.setStyle("-fx-pref-width: 400px;-fx-wrap-text: true;");
        this.actions.setStyle("-fx-alignment: center-right ;");
        this.actions.setPadding(new Insets(10.0, 0.0, 0.0, 0.0));
    }
}
