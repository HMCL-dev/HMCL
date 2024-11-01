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

import com.jfoenix.assets.JFoenixResources;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Default dialog layout according to material design guidelines.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXDialogLayout extends VBox {
    private final StackPane heading = new StackPane();
    private final StackPane body = new StackPane();
    private final FlowPane actions = new FlowPane();

    /**
     * creates empty dialog layout
     */
    public JFXDialogLayout() {
        initialize();
        heading.getStyleClass().addAll("jfx-layout-heading", "title");
        body.getStyleClass().add("jfx-layout-body");
        VBox.setVgrow(body, Priority.ALWAYS);
        actions.getStyleClass().add("jfx-layout-actions");
        getChildren().setAll(heading, body, actions);
    }

    /***************************************************************************
     *                                                                         *
     * Setters / Getters                                                       *
     *                                                                         *
     **************************************************************************/

    public ObservableList<Node> getHeading() {
        return heading.getChildren();
    }

    /**
     * set header node
     */
    public void setHeading(Node... titleContent) {
        this.heading.getChildren().setAll(titleContent);
    }

    public ObservableList<Node> getBody() {
        return body.getChildren();
    }

    /**
     * set body node
     */
    public void setBody(Node... body) {
        this.body.getChildren().setAll(body);
    }

    public ObservableList<Node> getActions() {
        return actions.getChildren();
    }

    /**
     * set actions of the dialog (Accept, Cancel,...)
     */
    public void setActions(Node... actions) {
        this.actions.getChildren().setAll(actions);
    }

    public void setActions(List<? extends Node> actions) {
        this.actions.getChildren().setAll(actions);
    }

    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/
    /**
     * Initialize the style class to 'jfx-dialog-layout'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "jfx-dialog-layout";

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return JFoenixResources.load("css/controls/jfx-dialog-layout.css").toExternalForm();
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }
}
