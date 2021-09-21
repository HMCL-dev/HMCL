/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

/**
 * @author huangyuhui
 */
class ComponentListCell extends StackPane {
    private final Node content;
    private Animation expandAnimation;
    private Rectangle clipRect;
    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);

    ComponentListCell(Node content) {
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

            getStyleClass().add("no-padding");

            VBox groupNode = new VBox();

            Node expandIcon = SVG.expand(Theme.blackFillBinding(), 20, 20);
            JFXButton expandButton = new JFXButton();
            expandButton.setGraphic(expandIcon);
            expandButton.getStyleClass().add("options-list-item-expand-button");

            VBox labelVBox = new VBox();
            labelVBox.setMouseTransparent(true);
            labelVBox.setAlignment(Pos.CENTER_LEFT);

            boolean overrideHeaderLeft = false;
            if (list instanceof ComponentSublist) {
                Node leftNode = ((ComponentSublist) list).getHeaderLeft();
                if (leftNode != null) {
                    labelVBox.getChildren().setAll(leftNode);
                    overrideHeaderLeft = true;
                }
            }

            if (!overrideHeaderLeft) {
                Label label = new Label();
                label.textProperty().bind(list.titleProperty());
                labelVBox.getChildren().add(label);

                if (list.isHasSubtitle()) {
                    Label subtitleLabel = new Label();
                    subtitleLabel.textProperty().bind(list.subtitleProperty());
                    subtitleLabel.getStyleClass().add("subtitle-label");
                    labelVBox.getChildren().add(subtitleLabel);
                }
            }

            HBox header = new HBox();
            header.setSpacing(16);
            header.getChildren().add(labelVBox);
            header.setPadding(new Insets(10, 16, 10, 16));
            header.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(labelVBox, Priority.ALWAYS);
            if (list instanceof ComponentSublist) {
                Node rightNode = ((ComponentSublist) list).getHeaderRight();
                if (rightNode != null)
                    header.getChildren().add(rightNode);
            }
            header.getChildren().add(expandButton);

            RipplerContainer headerRippler = new RipplerContainer(header);
            groupNode.getChildren().add(headerRippler);

            VBox container = new VBox();
            container.setPadding(new Insets(8, 16, 10, 16));
            FXUtils.setLimitHeight(container, 0);
            FXUtils.setOverflowHidden(container);
            container.getChildren().setAll(content);
            groupNode.getChildren().add(container);

            Runnable onExpand = () -> {
                if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) {
                    expandAnimation.stop();
                }

                setExpanded(!isExpanded());

                if (isExpanded()) {
                    list.onExpand();
                    list.layout();
                }

                Platform.runLater(() -> {
                    double newAnimatedHeight = (content.prefHeight(-1) + 8 + 10) * (isExpanded() ? 1 : -1);
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
            };

            headerRippler.setOnMouseClicked(e -> onExpand.run());
            expandButton.setOnMouseClicked(e -> onExpand.run());

            expandedProperty().addListener((a, b, newValue) ->
                    expandIcon.setRotate(newValue ? 180 : 0));

            getChildren().setAll(groupNode);
        } else {
            getStyleClass().remove("no-padding");
            getChildren().setAll(content);
        }
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
