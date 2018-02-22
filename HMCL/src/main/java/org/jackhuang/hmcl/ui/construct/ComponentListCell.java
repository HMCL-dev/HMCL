/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

/**
 * @author huangyuhui
 */
public class ComponentListCell extends StackPane {
    private final Node content;
    private Animation expandAnimation;
    private Rectangle clipRect;
    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);

    public ComponentListCell(Node content) {
        this.content = content;

        updateLayout();
    }

    private void updateClip(double newHeight) {
        if (clipRect != null)
            clipRect.setHeight(newHeight);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        if (clipRect == null)
            clipRect = new Rectangle(0, 0, getWidth(), getHeight());
        else {
            clipRect.setX(0);
            clipRect.setY(0);
            clipRect.setHeight(getHeight());
            clipRect.setWidth(getWidth());
        }
    }

    private void updateLayout() {
        if (content instanceof ComponentList) {
            ComponentList list = (ComponentList) content;
            content.getStyleClass().remove("options-list");
            content.getStyleClass().add("options-sublist");

            StackPane groupNode = new StackPane();
            groupNode.getStyleClass().add("options-list-item-header");

            Node expandIcon = SVG.expand("black", 10, 10);
            JFXButton expandButton = new JFXButton();
            expandButton.setGraphic(expandIcon);
            expandButton.getStyleClass().add("options-list-item-expand-button");
            StackPane.setAlignment(expandButton, Pos.CENTER_RIGHT);

            VBox labelVBox = new VBox();
            Label label = new Label();
            label.textProperty().bind(list.titleProperty());
            label.setMouseTransparent(true);
            labelVBox.getChildren().add(label);

            if (list.isHasSubtitle()) {
                Label subtitleLabel = new Label();
                subtitleLabel.textProperty().bind(list.subtitleProperty());
                subtitleLabel.setMouseTransparent(true);
                subtitleLabel.getStyleClass().add("subtitle-label");
                labelVBox.getChildren().add(subtitleLabel);
            }

            StackPane.setAlignment(labelVBox, Pos.CENTER_LEFT);
            groupNode.getChildren().setAll(labelVBox, expandButton);

            VBox container = new VBox();
            container.setStyle("-fx-padding: 8 0 0 0;");
            FXUtils.setLimitHeight(container, 0);
            Rectangle clipRect = new Rectangle();
            clipRect.widthProperty().bind(container.widthProperty());
            clipRect.heightProperty().bind(container.heightProperty());
            container.setClip(clipRect);
            container.getChildren().setAll(content);

            VBox holder = new VBox();
            holder.getChildren().setAll(groupNode, container);
            holder.getStyleClass().add("options-list-item-container");

            expandButton.setOnMouseClicked(e -> {
                if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) {
                    expandAnimation.stop();
                }

                setExpanded(!isExpanded());

                double newAnimatedHeight = content.prefHeight(-1) * (isExpanded() ? 1 : -1);
                double newHeight = isExpanded() ? getHeight() + newAnimatedHeight : prefHeight(-1);
                double contentHeight = isExpanded() ? newAnimatedHeight : 0;

                if (isExpanded()) {
                    updateClip(newHeight);
                }

                expandAnimation = new Timeline(new KeyFrame(new Duration(320.0),
                        new KeyValue(container.minHeightProperty(), contentHeight, FXUtils.SINE),
                        new KeyValue(container.maxHeightProperty(), contentHeight, FXUtils.SINE)
                ));

                if (!isExpanded()) {
                    expandAnimation.setOnFinished(e2 -> updateClip(newHeight));
                }

                expandAnimation.play();
            });

            expandedProperty().addListener((a, b, newValue) ->
                    expandIcon.setRotate(newValue ? 180 : 0));

            getChildren().setAll(holder);
        } else
            getChildren().setAll(content);
    }

    public boolean isExpanded() {
        return expanded.get();
    }

    public BooleanProperty expandedProperty() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded.set(expanded);
    }
}
