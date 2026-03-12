/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;

/// @author Glavo
final class ComponentSublistWrapper extends VBox implements NoPaddingComponent {
    private VBox container;

    private Animation expandAnimation;
    private boolean expanded = false;

    ComponentSublistWrapper(ComponentSublist sublist) {
        boolean noPadding = !sublist.hasComponentPadding();

        this.getStyleClass().add("options-sublist-wrapper");

        Node expandIcon = SVG.KEYBOARD_ARROW_DOWN.createIcon(20);
        expandIcon.getStyleClass().add("expand-icon");
        expandIcon.setMouseTransparent(true);

        VBox labelVBox = new VBox();
        labelVBox.setMouseTransparent(true);
        labelVBox.setAlignment(Pos.CENTER_LEFT);

        Node leftNode = sublist.getHeaderLeft();
        if (leftNode == null) {
            Label label = new Label();
            label.textProperty().bind(sublist.titleProperty());
            label.getStyleClass().add("title-label");
            labelVBox.getChildren().add(label);

            if (sublist.isHasSubtitle()) {
                Label subtitleLabel = new Label();
                subtitleLabel.textProperty().bind(sublist.subtitleProperty());
                subtitleLabel.getStyleClass().add("subtitle-label");
                subtitleLabel.textFillProperty().bind(Themes.colorSchemeProperty().getOnSurfaceVariant());
                labelVBox.getChildren().add(subtitleLabel);
            }
        } else {
            labelVBox.getChildren().setAll(leftNode);
        }

        HBox header = new HBox();
        header.setSpacing(12);
        header.getChildren().add(labelVBox);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(labelVBox, Priority.ALWAYS);
        Node rightNode = sublist.getHeaderRight();
        if (rightNode != null)
            header.getChildren().add(rightNode);
        header.getChildren().add(expandIcon);

        RipplerContainer headerRippler = new RipplerContainer(header);
        this.getChildren().add(headerRippler);

        headerRippler.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY)
                return;

            event.consume();

            if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) {
                expandAnimation.stop();
            }

            boolean expanded = !this.expanded;
            this.expanded = expanded;
            if (expanded) {
                sublist.doLazyInit();

                if (container == null) {
                    this.container = new VBox();

                    if (!noPadding) {
                        container.setPadding(new Insets(8, 16, 10, 16));
                    }
                    FXUtils.setLimitHeight(container, 0);
                    FXUtils.setOverflowHidden(container);
                    container.getChildren().setAll(sublist);
                    ComponentSublistWrapper.this.getChildren().add(container);

                    this.applyCss();
                }

                this.layout();
            }

            Platform.runLater(() -> {
                // FIXME: ComponentSubList without padding must have a 4 pixel padding for displaying a border radius.
                double contentHeight = expanded ? (sublist.prefHeight(sublist.getWidth()) + (noPadding ? 4 : 8 + 10)) : 0;
                double targetRotate = expanded ? -180 : 0;

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

                    expandAnimation.play();
                } else {
                    container.setMinHeight(contentHeight);
                    container.setMaxHeight(contentHeight);
                    expandIcon.setRotate(targetRotate);
                }
            });
        });
    }
}
