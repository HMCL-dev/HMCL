// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;

public class MenuUpDownButton extends Control {

    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);

    private final StringProperty text = new SimpleStringProperty(this, "text", "");

    private final ObservableList<Node> content = FXCollections.observableArrayList();

    public MenuUpDownButton() {
        getStyleClass().add("menu-up-down-button");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new MenuUpDownButtonSkin(this);
    }

    public boolean isExpanded() {
        return expanded.get();
    }

    public void setExpanded(boolean expanded) {
        this.expanded.set(expanded);
    }

    public BooleanProperty expandedProperty() {
        return expanded;
    }

    public String getText() {
        return text.get();
    }

    public void setText(String text) {
        this.text.set(text);
    }

    public StringProperty textProperty() {
        return text;
    }

    public ObservableList<Node> getContent() {
        return content;
    }

    private static class MenuUpDownButtonSkin extends SkinBase<MenuUpDownButton> {

        private final Map<Node, InvalidationListener> contentLayoutListeners = new IdentityHashMap<>();

        private final Map<Node, WeakInvalidationListener> weakContentLayoutListeners = new IdentityHashMap<>();

        private boolean heightUpdatePending;
        
        private final VBox root = new VBox();

        private final JFXButton button = new JFXButton();
        private final VBox contentContainer = new VBox();
        private final VBox contentBox = new VBox();

        private Animation expandAnimation;

        private final HBox headerContent = new HBox(8);
        private final Label label = new Label();

        private final Node arrow = SVG.KEYBOARD_ARROW_DOWN.createIcon(16);

        private final ListChangeListener<Node> contentListener = change -> {
            updateContent();
            updateObservedContentNodes();
            updateExpandedContentHeight();
        };

        private final ChangeListener<Boolean> expandedListener = (observable, oldValue, newValue) -> updateExpanded(newValue);

        protected MenuUpDownButtonSkin(MenuUpDownButton control) {
            super(control);

            label.textProperty().bind(control.textProperty());

            headerContent.setAlignment(Pos.CENTER);
            headerContent.getChildren().setAll(label, arrow);

            button.setGraphic(headerContent);
            button.setOnAction(event -> control.setExpanded(!control.isExpanded()));

            contentBox.getChildren().setAll(control.getContent());

            contentContainer.getChildren().setAll(contentBox);
            contentContainer.getStyleClass().add("menu-up-down-content");

            FXUtils.setLimitHeight(contentContainer, 0);
            FXUtils.setOverflowHidden(contentContainer);

            contentContainer.setVisible(false);
            contentContainer.setManaged(false);

            root.getChildren().setAll(button, contentContainer);
            getChildren().setAll(root);

            control.getContent().addListener(contentListener);
            control.expandedProperty().addListener(expandedListener);

            if (control.isExpanded()) {
                updateExpanded(true);
            } else {
                setContentHeight(0);
                arrow.setRotate(0);
                contentContainer.setVisible(false);
                contentContainer.setManaged(false);
            }
        }

        private void updateContent() {
            if (getSkinnable().isExpanded()) {
                Platform.runLater(() -> {
                    if (getSkinnable() != null
                        && getSkinnable().isExpanded()
                        && (
                            expandAnimation == null
                            || expandAnimation.getStatus() != Animation.Status.RUNNING
                        )
                    ) {
                        setContentHeight(computeContentHeight());
                    }
                });
            }
        }

        private void updateExpanded(boolean expanded) {
            if (expandAnimation != null) {
                double currentHeight = contentContainer.getHeight();

                expandAnimation.stop();

                setContentHeight(currentHeight);
            }

            if (expanded) {
                contentContainer.setVisible(true);
                contentContainer.setManaged(true);
            }

            Platform.runLater(() -> {
                if (getSkinnable() == null || getSkinnable().isExpanded() != expanded) return;

                if (expanded) {
                    var scene = getSkinnable().getScene();

                    if (scene != null) {
                        var sceneRoot = scene.getRoot();
                        sceneRoot.applyCss();
                        sceneRoot.layout();
                    }

                    root.layout();
                    contentContainer.layout();
                    contentBox.layout();
                }

                double targetRotate = expanded ? -180 : 0;
                double targetHeight = expanded ? computeContentHeight() : 0;

                double currentRotate = arrow.getRotate();

                if (!AnimationUtils.isAnimationEnabled()) {
                    setContentHeight(targetHeight);
                    arrow.setRotate(targetRotate);

                    if (!expanded) {
                        contentContainer.setVisible(false);
                        contentContainer.setManaged(false);
                    }

                    return;
                }

                Duration duration = Motion.LONG2.multiply(Math.abs(currentRotate - targetRotate) / 180.0);

                Interpolator interpolator = Motion.EASE_IN_OUT_CUBIC_EMPHASIZED;

                expandAnimation = new Timeline(
                    new KeyFrame(
                        duration,
                        new KeyValue(
                            contentContainer.minHeightProperty(),
                            targetHeight,
                            interpolator
                        ),
                        new KeyValue(
                            contentContainer.prefHeightProperty(),
                            targetHeight,
                            interpolator
                        ),
                        new KeyValue(
                            contentContainer.maxHeightProperty(),
                            targetHeight,
                            interpolator
                        ),
                        new KeyValue(
                            arrow.rotateProperty(),
                            targetRotate,
                            interpolator
                        )
                    )
                );

                expandAnimation.setOnFinished(event -> {
                    if (!expanded) {
                        contentContainer.setVisible(false);
                        contentContainer.setManaged(false);
                    } else {
                        setContentHeight(computeContentHeight());
                    }
                });

                expandAnimation.play();
            });
        }

        private double computeContentHeight() {
            contentBox.applyCss();

            double width = contentContainer.getWidth();

            if (width <= 0) width = root.getWidth();

            return contentBox.prefHeight(width);
        }

        private void setContentHeight(double height) {
            contentContainer.setMinHeight(height);
            contentContainer.setPrefHeight(height);
            contentContainer.setMaxHeight(height);
        }

        private void updateObservedContentNodes() {
            contentLayoutListeners.entrySet().removeIf(entry -> {
                Node node = entry.getKey();

                if (getSkinnable().getContent().contains(node)) return false;

                WeakInvalidationListener weakListener =
                        weakContentLayoutListeners.remove(node);

                if (weakListener != null) {
                    node.layoutBoundsProperty().removeListener(weakListener);

                    if (node instanceof Parent parent) {
                        parent.needsLayoutProperty().removeListener(weakListener);
                    }
                }

                return true;
            });

            for (Node node : getSkinnable().getContent()) {
                if (contentLayoutListeners.containsKey(node)) continue;

                InvalidationListener listener = observable -> updateExpandedContentHeight();

                WeakInvalidationListener weakListener = new WeakInvalidationListener(listener);

                node.layoutBoundsProperty().addListener(weakListener);

                if (node instanceof Parent parent) parent.needsLayoutProperty().addListener(weakListener);

                contentLayoutListeners.put(node, listener);
                weakContentLayoutListeners.put(node, weakListener);
            }
        }

        private void updateExpandedContentHeight() {
            if (
                !getSkinnable().isExpanded()
                || heightUpdatePending
                || (
                    expandAnimation != null
                    && expandAnimation.getStatus() == Animation.Status.RUNNING
                )
            ) {
                return;
            }

            heightUpdatePending = true;

            Platform.runLater(() -> {
                heightUpdatePending = false;

                if (getSkinnable() == null || !getSkinnable().isExpanded()) return;

                if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) return;

                double targetHeight = computeContentHeight();

                if (targetHeight < 0) return;

                if (Math.abs(targetHeight - contentContainer.getPrefHeight()) > 0.5) setContentHeight(targetHeight);
            });
        }

        @Override
        public void dispose() {
            MenuUpDownButton control = getSkinnable();

            if (expandAnimation != null) {
                expandAnimation.stop();
                expandAnimation = null;
            }

            if (control != null) {
                control.getContent().removeListener(contentListener);
                control.expandedProperty().removeListener(expandedListener);
            }

            for (Map.Entry<Node, WeakInvalidationListener> entry : weakContentLayoutListeners.entrySet()) {
                Node node = entry.getKey();
                WeakInvalidationListener listener = entry.getValue();

                node.layoutBoundsProperty().removeListener(listener);

                if (node instanceof Parent parent) parent.needsLayoutProperty().removeListener(listener);
            }

            contentLayoutListeners.clear();
            weakContentLayoutListeners.clear();

            label.textProperty().unbind();

            super.dispose();
        }
    }
}
