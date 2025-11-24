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

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;

/**
 * @author huangyuhui
 */
final class ComponentListCell extends StackPane {
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
        if (content instanceof ComponentList list) {
            content.getStyleClass().remove("options-list");
            content.getStyleClass().add("options-sublist");

            getStyleClass().add("no-padding");

            VBox groupNode = new VBox();

            Node expandIcon = SVG.KEYBOARD_ARROW_DOWN.createIcon(Theme.blackFill(), 20);
            expandIcon.setMouseTransparent(true);
            HBox.setMargin(expandIcon, new Insets(0, 8, 0, 8));

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
            header.getChildren().add(expandIcon);

            RipplerContainer headerRippler = new RipplerContainer(header);
            groupNode.getChildren().add(headerRippler);

            VBox container = new VBox();
            boolean hasPadding = !(list instanceof ComponentSublist subList) || subList.hasComponentPadding();
            if (hasPadding) {
                container.setPadding(new Insets(8, 16, 10, 16));
            }
            FXUtils.setLimitHeight(container, 0);
            FXUtils.setOverflowHidden(container);
            container.getChildren().setAll(content);
            groupNode.getChildren().add(container);

            headerRippler.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() != MouseButton.PRIMARY)
                    return;

                event.consume();

                if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) {
                    expandAnimation.stop();
                }

                boolean expanded = !isExpanded();
                setExpanded(expanded);
                if (expanded) {
                    list.doLazyInit();
                    list.layout();
                }

                Platform.runLater(() -> {
                    // FIXME: ComponentSubList without padding must have a 4 pixel padding for displaying a border radius.
                    double newAnimatedHeight = (list.prefHeight(list.getWidth()) + (hasPadding ? 8 + 10 : 4)) * (expanded ? 1 : -1);
                    double newHeight = expanded ? getHeight() + newAnimatedHeight : prefHeight(list.getWidth());
                    double contentHeight = expanded ? newAnimatedHeight : 0;
                    double targetRotate = expanded ? -180 : 0;

                    if (expanded) {
                        updateClip(newHeight);
                    }

                    if (AnimationUtils.isAnimationEnabled()) {
                        double currentRotate = expandIcon.getRotate();
                        Duration duration = Motion.LONG2.multiply(Math.abs(currentRotate - targetRotate) / 180.0);
                        Interpolator interpolator = Motion.EASE_IN_OUT_CUBIC_EMPHASIZED;

                        expandAnimation = new Timeline(
                                new KeyFrame(duration,
                                        new KeyValue(container.minHeightProperty(), contentHeight, interpolator),
                                        new KeyValue(container.maxHeightProperty(), contentHeight, interpolator),
                                        new KeyValue(expandIcon.rotateProperty(), targetRotate, interpolator))
                        );

                        if (!expanded) {
                            expandAnimation.setOnFinished(e2 -> updateClip(newHeight));
                        }

                        expandAnimation.play();
                    } else {
                        container.setMinHeight(contentHeight);
                        container.setMaxHeight(contentHeight);
                        expandIcon.setRotate(targetRotate);

                        if (!expanded) {
                            updateClip(newHeight);
                        }
                    }
                });
            });

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
