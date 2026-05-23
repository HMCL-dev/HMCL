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
import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.util.StringUtils;

/// @author Glavo
final class ComponentSublistWrapper extends VBox implements NoPaddingComponent {
    private VBox container;

    private Animation expandAnimation;
    private boolean expanded = false;

    ComponentSublistWrapper(ComponentSublist sublist) {
        this.getStyleClass().add("options-sublist-wrapper");

        Node expandIcon = SVG.KEYBOARD_ARROW_DOWN.createIcon(20);
        expandIcon.getStyleClass().add("expand-icon");
        expandIcon.setMouseTransparent(true);

        HeaderButton header = new HeaderButton();
        header.getStyleClass().add("options-sublist-header");
        header.titleProperty().bind(sublist.titleProperty());
        header.subtitleProperty().bind(sublist.subtitleProperty());
        header.leadingProperty().bind(sublist.leadingProperty());
        header.trailingTextProperty().bind(sublist.descriptionProperty());
        header.setTrailingIcon(expandIcon);
        header.setOnAction(event -> {
            if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) {
                expandAnimation.stop();
            }

            boolean expanded = !this.expanded;
            this.expanded = expanded;
            if (expanded) {
                sublist.doLazyInit();

                if (container == null) {
                    this.container = new VBox();
                    this.container.getStyleClass().add("container");
                    FXUtils.setLimitHeight(container, 0);

                    Rectangle rectangle = FXUtils.setOverflowHidden(container);
                    rectangle.getStyleClass().add("overflow-hidden");

                    var last = PseudoClass.getPseudoClass("last");

                    InvalidationListener updateArc = o -> {
                        if (ComponentSublistWrapper.this.getPseudoClassStates().contains(last)) {
                            rectangle.setArcHeight(4);
                            rectangle.setArcWidth(4);
                        } else {
                            rectangle.setArcHeight(0);
                            rectangle.setArcWidth(0);
                        }
                    };
                    updateArc.invalidated(null);
                    ComponentSublistWrapper.this.getPseudoClassStates().addListener(updateArc);

                    container.getChildren().setAll(sublist);
                    ComponentSublistWrapper.this.getChildren().add(container);

                    this.applyCss();
                }

                this.layout();
            }

            Platform.runLater(() -> {
                double targetRotate = expanded ? -180 : 0;
                if (!expanded && container != null) {
                    double currentHeight = container.getHeight() > 0 ? container.getHeight() : computeContentHeight(sublist);
                    container.setMinHeight(currentHeight);
                    container.setMaxHeight(currentHeight);
                }

                if (AnimationUtils.isAnimationEnabled()) {
                    double currentRotate = expandIcon.getRotate();
                    Duration duration = Motion.LONG2.multiply(Math.abs(currentRotate - targetRotate) / 180.0);
                    Interpolator interpolator = Motion.EASE_IN_OUT_CUBIC_EMPHASIZED;

                    expandAnimation = new Timeline(
                            new KeyFrame(duration,
                                    new KeyValue(container.minHeightProperty(), expanded ? computeContentHeight(sublist) : 0, interpolator),
                                    new KeyValue(container.maxHeightProperty(), expanded ? computeContentHeight(sublist) : 0, interpolator),
                                    new KeyValue(expandIcon.rotateProperty(), targetRotate, interpolator))
                    );
                    expandAnimation.setOnFinished(e -> {
                        if (this.expanded) {
                            useComputedContentHeight();
                        }
                    });

                    expandAnimation.play();
                } else {
                    if (expanded) {
                        useComputedContentHeight();
                    } else {
                        container.setMinHeight(0);
                        container.setMaxHeight(0);
                    }
                    expandIcon.setRotate(targetRotate);
                }
            });
        });

        Node headerLeft = sublist.getHeaderLeft();
        if (headerLeft != null) {
            header.setTitleContent(headerLeft);
        }

        InvalidationListener updateTitleTrailing = observable -> {
            Node titleTrailing = sublist.getTitleTrailing();
            String tip = sublist.getTip();

            if (titleTrailing == null && StringUtils.isBlank(tip)) {
                header.setTitleTrailing(null);
                return;
            }

            HBox box = new HBox(4);
            if (titleTrailing != null) {
                box.getChildren().add(titleTrailing);
            }
            if (!StringUtils.isBlank(tip)) {
                var tipContainer = new StackPane(SVG.INFO.createIcon(16));
                FXUtils.installFastTooltip(tipContainer, tip);
                box.getChildren().add(tipContainer);
            }
            header.setTitleTrailing(box);
        };
        sublist.tipProperty().addListener(updateTitleTrailing);
        sublist.titleTrailingProperty().addListener(updateTitleTrailing);
        updateTitleTrailing.invalidated(null);

        InvalidationListener updateTrailing = observable -> header.setExtraTrailing(sublist.getTrailing());
        sublist.trailingProperty().addListener(updateTrailing);
        updateTrailing.invalidated(null);

        sublist.getContent().addListener((InvalidationListener) observable -> updateExpandedContentHeight(sublist));

        this.getChildren().add(header);
    }

    /// Uses the sublist's computed height while expanded so dynamic content can resize naturally.
    private void updateExpandedContentHeight(ComponentSublist sublist) {
        if (!expanded || container == null) {
            return;
        }

        Platform.runLater(() -> {
            if (!expanded || container == null) {
                return;
            }

            if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) {
                expandAnimation.stop();
            }

            useComputedContentHeight();
        });
    }

    /// Returns the preferred height for the current sublist content.
    private double computeContentHeight(ComponentSublist sublist) {
        return sublist.prefHeight(sublist.getWidth());
    }

    /// Clears fixed height constraints on the expanded content container.
    private void useComputedContentHeight() {
        if (container == null) {
            return;
        }

        container.setMinHeight(Region.USE_COMPUTED_SIZE);
        container.setMaxHeight(Region.USE_COMPUTED_SIZE);
    }

    private static final class HeaderButton extends LineButton {
        private static final int EXTRA_TRAILING_INDEX = IDX_TRAILING + 1;

        @Override
        protected int getTrailingIconIndex() {
            return IDX_TRAILING + 2;
        }

        private void setExtraTrailing(Node node) {
            setNode(EXTRA_TRAILING_INDEX, node);
        }

        private void setTitleContent(Node node) {
            setNode(IDX_TITLE, node);
        }
    }
}
