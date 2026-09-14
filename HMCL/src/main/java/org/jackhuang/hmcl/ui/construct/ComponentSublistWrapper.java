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
import javafx.beans.WeakInvalidationListener;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.IdentityHashMap;
import java.util.Map;

/// @author Glavo
final class ComponentSublistWrapper extends VBox implements NoPaddingComponent {
    private VBox container;
    private final Map<Node, InvalidationListener> contentLayoutListeners = new IdentityHashMap<>();
    private final Map<Node, WeakInvalidationListener> weakContentLayoutListeners = new IdentityHashMap<>();

    private Animation expandAnimation;
    private boolean expanded = false;
    private boolean heightTransitionPending = false;

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
            heightTransitionPending = true;
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
                    setContentHeight(currentHeight);
                }

                if (AnimationUtils.isAnimationEnabled()) {
                    double currentRotate = expandIcon.getRotate();
                    Duration duration = Motion.LONG2.multiply(Math.abs(currentRotate - targetRotate) / 180.0);
                    Interpolator interpolator = Motion.EASE_IN_OUT_CUBIC_EMPHASIZED;
                    double targetHeight = expanded ? computeContentHeight(sublist) : 0;

                    expandAnimation = new Timeline(
                            new KeyFrame(duration,
                                    new KeyValue(container.minHeightProperty(), targetHeight, interpolator),
                                    new KeyValue(container.prefHeightProperty(), targetHeight, interpolator),
                                    new KeyValue(container.maxHeightProperty(), targetHeight, interpolator),
                                    new KeyValue(expandIcon.rotateProperty(), targetRotate, interpolator))
                    );
                    expandAnimation.setOnFinished(e -> {
                        heightTransitionPending = false;
                        if (this.expanded) {
                            setContentHeight(computeContentHeight(sublist));
                        }
                    });

                    expandAnimation.play();
                } else {
                    if (expanded) {
                        setContentHeight(computeContentHeight(sublist));
                    } else {
                        setContentHeight(0);
                    }
                    expandIcon.setRotate(targetRotate);
                    heightTransitionPending = false;
                }
            });
        });

        FXUtils.onChangeAndOperate(sublist.largeTitleProperty(), header::setLargeTitle);

        InvalidationListener updateTitleTrailing = observable -> {
            Node titleTrailing = sublist.getTitleTrailing();
            String tip = sublist.getTip();

            if (titleTrailing == null && StringUtils.isBlank(tip)) {
                header.setTitleTrailing(null);
                return;
            }

            HBox box = new HBox(4);
            box.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> event.consume());
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

        InvalidationListener updateContentHeight = observable -> {
            updateObservedContentNodes(sublist);
            updateExpandedContentHeight(sublist);
        };
        sublist.getContent().addListener(updateContentHeight);
        updateObservedContentNodes(sublist);

        this.getChildren().add(header);
    }

    /// Keeps dynamic child layout changes reflected in the expanded container height.
    private void updateObservedContentNodes(ComponentSublist sublist) {
        contentLayoutListeners.entrySet().removeIf(entry -> {
            Node node = entry.getKey();
            if (sublist.getContent().contains(node)) {
                return false;
            }

            WeakInvalidationListener weakListener = weakContentLayoutListeners.remove(node);
            if (weakListener != null) {
                node.layoutBoundsProperty().removeListener(weakListener);
                if (node instanceof Parent parent) {
                    parent.needsLayoutProperty().removeListener(weakListener);
                }
            }
            return true;
        });

        for (Node node : sublist.getContent()) {
            if (contentLayoutListeners.containsKey(node)) {
                continue;
            }

            InvalidationListener listener = observable -> updateExpandedContentHeight(sublist);
            WeakInvalidationListener weakListener = new WeakInvalidationListener(listener);
            node.layoutBoundsProperty().addListener(weakListener);
            if (node instanceof Parent parent) {
                parent.needsLayoutProperty().addListener(weakListener);
            }
            contentLayoutListeners.put(node, listener);
            weakContentLayoutListeners.put(node, weakListener);
        }
    }

    /// Uses the sublist's computed height while expanded so dynamic content can resize naturally.
    private void updateExpandedContentHeight(ComponentSublist sublist) {
        if (!expanded || container == null || heightTransitionPending) {
            return;
        }

        Platform.runLater(() -> {
            if (!expanded || container == null || heightTransitionPending) {
                return;
            }

            if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) {
                return;
            }

            setContentHeight(computeContentHeight(sublist));
        });
    }

    /// Returns the preferred height for the current sublist content.
    private double computeContentHeight(ComponentSublist sublist) {
        sublist.applyCss();

        double width = sublist.getWidth();
        if (width <= 0 && container != null) {
            width = container.getWidth();
        }
        if (width <= 0) {
            width = getWidth();
        }
        return sublist.prefHeight(width);
    }

    /// Applies the same fixed height to all height constraints used during expand/collapse animation.
    private void setContentHeight(double height) {
        if (container == null) {
            return;
        }

        container.setMinHeight(height);
        container.setPrefHeight(height);
        container.setMaxHeight(height);
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
