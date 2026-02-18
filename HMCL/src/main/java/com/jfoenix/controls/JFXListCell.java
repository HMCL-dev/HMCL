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

import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

import java.util.Set;

/// material design implementation of ListCell
///
/// By default, JFXListCell will try to create a graphic node for the cell,
/// to override it you need to set graphic to null in [#updateItem(Object, boolean)] method.
///
/// NOTE: passive nodes (Labels and Shapes) will be set to mouse transparent in order to
/// show the ripple effect upon clicking , to change this behavior you can override the
/// method {[#makeChildrenTransparent()]
///
/// @author Shadi Shaheen
/// @version 1.0
/// @since 2016-03-09
public class JFXListCell<T> extends ListCell<T> {

    protected JFXRippler cellRippler = new JFXRippler(this) {
        @Override
        protected Node getMask() {
            Region clip = new Region();
            JFXNodeUtils.updateBackground(JFXListCell.this.getBackground(), clip);
            double width = control.getLayoutBounds().getWidth();
            double height = control.getLayoutBounds().getHeight();
            clip.resize(width, height);
            return clip;
        }

        @Override
        protected void positionControl(Node control) {
            // do nothing
        }
    };

    protected Node cellContent;
    private Rectangle clip;

    //	private Timeline animateGap;
    private Timeline gapAnimation;
    private boolean playExpandAnimation = false;
    private boolean selectionChanged = false;

    /**
     * {@inheritDoc}
     */
    public JFXListCell() {
        initialize();
        initListeners();
    }

    /**
     * init listeners to update the vertical gap / selection animation
     */
    private void initListeners() {
        listViewProperty().addListener((listObj, oldList, newList) -> {
            if (newList != null) {
                if (getListView() instanceof JFXListView) {
                    ((JFXListView<?>) newList).currentVerticalGapProperty().addListener((o, oldVal, newVal) -> {
                        cellRippler.rippler.setClip(null);
                        if (newVal.doubleValue() != 0) {
                            playExpandAnimation = true;
                            getListView().requestLayout();
                        } else {
                            // fake expand state
                            double gap = clip.getY() * 2;
                            gapAnimation = new Timeline(
                                new KeyFrame(Duration.millis(240),
                                    new KeyValue(this.translateYProperty(),
                                        -gap / 2 - (gap * (getIndex())),
                                        Interpolator.EASE_BOTH)
                                ));
                            gapAnimation.play();
                            gapAnimation.setOnFinished((finish) -> {
                                requestLayout();
                                Platform.runLater(() -> getListView().requestLayout());
                            });
                        }
                    });

                    selectedProperty().addListener((o, oldVal, newVal) -> {
                        if (newVal) {
                            selectionChanged = true;
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        cellRippler.resizeRelocate(0, 0, getWidth(), getHeight());
        double gap = getGap();

        if (clip == null) {
            clip = new Rectangle(0, gap / 2, getWidth(), getHeight() - gap);
            setClip(clip);
        } else {
            if (gap != 0) {
                if (playExpandAnimation || selectionChanged) {
                    // fake list collapse state
                    if (playExpandAnimation) {
                        this.setTranslateY(-gap / 2 + (-gap * (getIndex())));
                        clip.setY(gap / 2);
                        clip.setHeight(getHeight() - gap);
                        gapAnimation = new Timeline(new KeyFrame(Duration.millis(240),
                            new KeyValue(this.translateYProperty(),
                                0,
                                Interpolator.EASE_BOTH)));
                        playExpandAnimation = false;
                    } else if (selectionChanged) {
                        clip.setY(0);
                        clip.setHeight(getHeight());
                        gapAnimation = new Timeline(
                            new KeyFrame(Duration.millis(240),
                                new KeyValue(clip.yProperty(), gap / 2, Interpolator.EASE_BOTH),
                                new KeyValue(clip.heightProperty(), getHeight() - gap, Interpolator.EASE_BOTH)
                            ));
                    }
                    playExpandAnimation = false;
                    selectionChanged = false;
                    gapAnimation.play();
                } else {
                    if (gapAnimation != null) {
                        gapAnimation.stop();
                    }
                    this.setTranslateY(0);
                    clip.setY(gap / 2);
                    clip.setHeight(getHeight() - gap);
                }
            } else {
                this.setTranslateY(0);
                clip.setY(0);
                clip.setHeight(getHeight());
            }
            clip.setX(0);
            clip.setWidth(getWidth());
        }
        if (!getChildren().contains(cellRippler)) {
            makeChildrenTransparent();
            getChildren().add(0, cellRippler);
            cellRippler.rippler.clear();
        }
    }

    /**
     * this method is used to set some nodes in cell content as mouse transparent nodes
     * so clicking on them will trigger the ripple effect.
     */
    protected void makeChildrenTransparent() {
        for (Node child : getChildren()) {
            if (child instanceof Label) {
                Set<Node> texts = child.lookupAll("Text");
                for (Node text : texts) {
                    text.setMouseTransparent(true);
                }
            } else if (child instanceof Shape) {
                child.setMouseTransparent(true);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
            // remove empty (Trailing cells)
            setMouseTransparent(true);
            setStyle("-fx-background-color:TRANSPARENT;");
        } else {
            setMouseTransparent(false);
            setStyle(null);
            if (item instanceof Node newNode) {
                setText(null);
                Node currentNode = getGraphic();
                if (currentNode == null || !currentNode.equals(newNode)) {
                    cellContent = newNode;
                    cellRippler.rippler.cacheRippleClip(false);
                    // build the Cell node
                    // RIPPLER ITEM : in case if the list item has its own rippler bind the list rippler and item rippler properties
                    if (newNode instanceof JFXRippler) {
                        // build cell container from exisiting rippler
                        cellRippler.ripplerFillProperty().bind(((JFXRippler) newNode).ripplerFillProperty());
                        cellRippler.maskTypeProperty().bind(((JFXRippler) newNode).maskTypeProperty());
                        cellRippler.positionProperty().bind(((JFXRippler) newNode).positionProperty());
                        cellContent = ((JFXRippler) newNode).getControl();
                    }
                    ((Region) cellContent).setMaxHeight(cellContent.prefHeight(-1));
                    setGraphic(cellContent);
                }
            } else {
                setText(item == null ? "null" : item.toString());
                setGraphic(null);
            }
            // show cell tooltip if it's toggled in JFXListView
            if (getListView() instanceof JFXListView<?> listView && listView.isShowTooltip()) {
                if (item instanceof Label label) {
                    setTooltip(new Tooltip(label.getText()));
                } else if (getText() != null) {
                    setTooltip(new Tooltip(getText()));
                }
            }
        }
    }

    // Stylesheet Handling                                                     *

    /**
     * Initialize the style class to 'jfx-list-cell'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "jfx-list-cell";

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
        this.setPadding(new Insets(8, 12, 8, 12));
    }

    @Override
    protected double computePrefHeight(double width) {
        double gap = getGap();
        return super.computePrefHeight(width) + gap;
    }

    private double getGap() {
        return (getListView() instanceof JFXListView<?> listView)
                ? (listView.isExpanded() ? listView.currentVerticalGapProperty().get() : 0)
                : 0;
    }
}
