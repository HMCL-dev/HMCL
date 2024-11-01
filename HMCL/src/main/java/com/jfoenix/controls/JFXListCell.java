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

import com.jfoenix.svg.SVGGlyph;
import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.Animation.Status;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

import java.util.Set;

/**
 * material design implementation of ListCell
 * <p>
 * By default JFXListCell will try to create a graphic node for the cell,
 * to override it you need to set graphic to null in {@link #updateItem(Object, boolean)} method.
 * <p>
 * NOTE: passive nodes (Labels and Shapes) will be set to mouse transparent in order to
 * show the ripple effect upon clicking , to change this behavior you can override the
 * method {{@link #makeChildrenTransparent()}
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
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
    private Timeline expandAnimation;
    private Timeline gapAnimation;
    private double animatedHeight = 0;
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

        // refresh sublist style class
        if (this.getGraphic() != null && this.getGraphic().getStyleClass().contains("sublist-container")) {
            this.getStyleClass().add("sublist-item");
        } else {
            this.getStyleClass().remove("sublist-item");
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
            if (item instanceof Node) {
                setText(null);
                Node currentNode = getGraphic();
                Node newNode = (Node) item;
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

                    // SUBLIST ITEM : build the Cell node as sublist the sublist
                    else if (newNode instanceof JFXListView<?>) {
                        // add the sublist to the parent and style the cell as sublist item
                        ((JFXListView<?>) getListView()).addSublist((JFXListView<?>) newNode, this.getIndex());
                        this.getStyleClass().add("sublist-item");

                        if (this.getPadding() != null) {
                            this.setPadding(new Insets(this.getPadding().getTop(),
                                0,
                                this.getPadding().getBottom(),
                                0));
                        }

                        // First build the group item used to expand / hide the sublist
                        StackPane groupNode = new StackPane();
                        groupNode.getStyleClass().add("sublist-header");
                        SVGGlyph dropIcon = new SVGGlyph(0,
                            "ANGLE_RIGHT",
                            "M340 548.571q0 7.429-5.714 13.143l-266.286 266.286q-5.714 5.714-13.143 5.714t-13.143-5.714l-28.571-28.571q-5.714-5.714-5.714-13.143t5.714-13.143l224.571-224.571-224.571-224.571q-5.714-5.714-5.714-13.143t5.714-13.143l28.571-28.571q5.714-5.714 13.143-5.714t13.143 5.714l266.286 266.286q5.714 5.714 5.714 13.143z",
                            Color.BLACK);
                        dropIcon.setStyle(
                            "-fx-min-width:0.4em;-fx-max-width:0.4em;-fx-min-height:0.6em;-fx-max-height:0.6em;");
                        dropIcon.getStyleClass().add("drop-icon");
                        //  alignment of the group node can be changed using the following css selector
                        //  .jfx-list-view .sublist-header{ }
                        groupNode.getChildren().setAll(((JFXListView<?>) newNode).getGroupnode(), dropIcon);
                        // the margin is needed when rotating the angle
                        StackPane.setMargin(dropIcon, new Insets(0, 19, 0, 0));
                        StackPane.setAlignment(dropIcon, Pos.CENTER_RIGHT);

                        // Second build the sublist container
                        StackPane sublistContainer = new StackPane();
                        sublistContainer.setMinHeight(0);
                        sublistContainer.setMaxHeight(0);
                        sublistContainer.getChildren().setAll(newNode);
                        sublistContainer.setTranslateY(this.snappedBottomInset());
                        sublistContainer.setOpacity(0);
                        StackPane.setMargin(newNode, new Insets(-1, -1, 0, -1));

                        // Third, create container of group title and the sublist
                        VBox contentHolder = new VBox();
                        contentHolder.getChildren().setAll(groupNode, sublistContainer);
                        contentHolder.getStyleClass().add("sublist-container");
                        VBox.setVgrow(groupNode, Priority.ALWAYS);
                        cellContent = contentHolder;
                        cellRippler.ripplerPane.addEventHandler(MouseEvent.ANY, Event::consume);
                        contentHolder.addEventHandler(MouseEvent.ANY, e -> {
                            if (!e.isConsumed()) {
                                cellRippler.ripplerPane.fireEvent(e);
                                e.consume();
                            }
                        });
                        cellRippler.ripplerPane.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                            if (!e.isConsumed()) {
                                e.consume();
                                contentHolder.fireEvent(e);
                            }
                        });
                        // cache rippler clip in subnodes
                        cellRippler.rippler.cacheRippleClip(true);

                        this.setOnMouseClicked(Event::consume);
                        // Finally, add sublist animation
                        contentHolder.setOnMouseClicked((click) -> {
                            click.consume();
                            // stop the animation or change the list height
                            if (expandAnimation != null && expandAnimation.getStatus() == Status.RUNNING) {
                                expandAnimation.stop();
                            }

                            // invert the expand property
                            expandedProperty.set(!expandedProperty.get());

                            double newAnimatedHeight = newNode.prefHeight(-1) * (expandedProperty.get() ? 1 : -1);
                            double newHeight = expandedProperty.get() ? this.getHeight() + newAnimatedHeight : this.prefHeight(
                                -1);
                            // animate showing/hiding the sublist
                            double contentHeight = expandedProperty.get() ? newAnimatedHeight : 0;

                            if (expandedProperty.get()) {
                                updateClipHeight(newHeight);
                                getListView().setPrefHeight(getListView().getHeight() + newAnimatedHeight + animatedHeight);
                            }
                            // update the animated height
                            animatedHeight = newAnimatedHeight;

                            int opacity = expandedProperty.get() ? 1 : 0;
                            expandAnimation = new Timeline(new KeyFrame(Duration.millis(320),
                                new KeyValue(sublistContainer.minHeightProperty(),
                                    contentHeight,
                                    Interpolator.EASE_BOTH),
                                new KeyValue(sublistContainer.maxHeightProperty(),
                                    contentHeight,
                                    Interpolator.EASE_BOTH),
                                new KeyValue(sublistContainer.opacityProperty(),
                                    opacity,
                                    Interpolator.EASE_BOTH)));

                            if (!expandedProperty.get()) {
                                expandAnimation.setOnFinished((finish) -> {
                                    updateClipHeight(newHeight);
                                    getListView().setPrefHeight(getListView().getHeight() + newAnimatedHeight);
                                    animatedHeight = 0;
                                });
                            }
                            expandAnimation.play();
                        });

                        // animate arrow
                        expandedProperty.addListener((o, oldVal, newVal) -> {
                            if (newVal) {
                                new Timeline(new KeyFrame(Duration.millis(160),
                                    new KeyValue(dropIcon.rotateProperty(),
                                        90,
                                        Interpolator.EASE_BOTH))).play();
                            } else {
                                new Timeline(new KeyFrame(Duration.millis(160),
                                    new KeyValue(dropIcon.rotateProperty(),
                                        0,
                                        Interpolator.EASE_BOTH))).play();
                            }
                        });
                    }
                    ((Region) cellContent).setMaxHeight(cellContent.prefHeight(-1));
                    setGraphic(cellContent);
                }
            } else {
                setText(item == null ? "null" : item.toString());
                setGraphic(null);
            }
            boolean isJFXListView = getListView() instanceof JFXListView;
            // show cell tooltip if its toggled in JFXListView
            if (isJFXListView && ((JFXListView<?>) getListView()).isShowTooltip()) {
                if (item instanceof Label) {
                    setTooltip(new Tooltip(((Label) item).getText()));
                } else if (getText() != null) {
                    setTooltip(new Tooltip(getText()));
                }
            }
        }
    }


    private void updateClipHeight(double newHeight) {
        clip.setHeight(newHeight - getGap());
    }


    /***************************************************************************
     *                                                                         *
     * Properties                                                              *
     *                                                                         *
     **************************************************************************/

    // indicate whether the sub list is expanded or not
    @Deprecated
    private final BooleanProperty expandedProperty = new SimpleBooleanProperty(false);

    @Deprecated
    public BooleanProperty expandedProperty() {
        return expandedProperty;
    }

    @Deprecated
    public void setExpanded(boolean expand) {
        expandedProperty.set(expand);
    }

    @Deprecated
    public boolean isExpanded() {
        return expandedProperty.get();
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
        return (getListView() instanceof JFXListView) ? (((JFXListView<?>) getListView()).isExpanded() ? ((JFXListView<?>) getListView())
            .currentVerticalGapProperty()
            .get() : 0) : 0;
    }
}
