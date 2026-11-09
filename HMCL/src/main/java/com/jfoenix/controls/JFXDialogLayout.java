//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.jfoenix.controls;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class JFXDialogLayout extends StackPane {
    private final StackPane heading = new StackPane();
    private final StackPane body = new StackPane();
    private final FlowPane actions = new FlowPane() {
        protected double computeMinWidth(double height) {
            if (this.getContentBias() == Orientation.HORIZONTAL) {
                double maxPref = 0.0;
                for (Node child : this.getChildren()) {
                    if (child.isManaged()) {
                        maxPref = Math.max(maxPref, child.minWidth(-1.0));
                    }
                }

                Insets insets = this.getInsets();
                return insets.getLeft() + this.snapSizeX(maxPref) + insets.getRight();
            } else {
                return this.computePrefWidth(height);
            }
        }

        protected double computeMinHeight(double width) {
            if (this.getContentBias() == Orientation.VERTICAL) {
                double maxPref = 0.0;

                for (Node child : this.getChildren()) {
                    if (child.isManaged()) {
                        maxPref = Math.max(maxPref, child.minHeight(-1.0));
                    }
                }

                Insets insets = this.getInsets();
                return insets.getTop() + this.snapSizeY(maxPref) + insets.getBottom();
            } else {
                return this.computePrefHeight(width);
            }
        }
    };
    private static final String DEFAULT_STYLE_CLASS = "jfx-dialog-layout";

    public JFXDialogLayout() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        VBox layout = new VBox();

        this.heading.getStyleClass().add("jfx-layout-heading");
        this.heading.getStyleClass().add("title");

        this.body.getStyleClass().add("jfx-layout-body");
        this.body.prefHeightProperty().bind(this.prefHeightProperty());
        this.body.prefWidthProperty().bind(this.prefWidthProperty());

        this.actions.getStyleClass().add("jfx-layout-actions");

        layout.getChildren().setAll(this.heading, this.body, this.actions);

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

}
