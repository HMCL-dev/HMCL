// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.NumberBinding;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Skin;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Floating scrollbar skin selected by root.css, supporting pointer drags and direct touch positioning.
@NotNullByDefault
@SuppressWarnings("unused")
public class FloatScrollBarSkin implements Skin<ScrollBar> {
    /// Owning scrollbar, cleared on disposal.
    private @Nullable ScrollBar scrollBar;
    /// Skin root, cleared on disposal.
    private @Nullable Region group;
    /// Available scrollbar track excluding its end padding.
    private final Rectangle track = new Rectangle();
    /// Thumb whose position follows the scrollbar value.
    private final Rectangle thumb = new Rectangle();

    /// Combined pointer feedback state, retained until disposal.
    private @Nullable BooleanBinding hoverOrPressed;
    /// Current feedback transition, or null when no transition is retained.
    private @Nullable Animation thumbHoverAnimation;

    /// Removable touch handler installed on the control so thumb, track, and background targets work.
    private final EventHandler<ScrollEvent> directScrollHandler = this::handleDirectScroll;

    /// Creates track geometry, pointer handlers, and hover feedback for the scrollbar.
    public FloatScrollBarSkin(final ScrollBar scrollBar) {
        this.scrollBar = scrollBar;
        scrollBar.setPrefHeight(1e-18);
        scrollBar.setPrefWidth(1e-18);

        this.group = new Region() {
            /// Pointer position at the start of a mouse drag, or null before pressing.
            @Nullable Point2D dragStart;
            /// Normalized thumb position captured when a mouse drag begins.
            double preDragThumbPos;

            /// Width of the scrollbar's value range.
            final NumberBinding range = Bindings.subtract(scrollBar.maxProperty(), scrollBar.minProperty());
            /// Current normalized scrollbar value.
            final NumberBinding position = Bindings.divide(Bindings.subtract(scrollBar.valueProperty(), scrollBar.minProperty()), range);

            {
                // Children are added unmanaged because for some reason the height of the bar keeps changing
                // if they're managed in certain situations... not sure about the cause.
                getChildren().addAll(track, thumb);

                track.setManaged(false);
                track.getStyleClass().add("track");

                thumb.setManaged(false);
                thumb.getStyleClass().add("thumb");

                scrollBar.orientationProperty().addListener(obs -> setup());

                setup();

                thumb.setOnMousePressed(me -> {
                    if (me.isSynthesized()) {
                        // touch-screen events handled by Scroll handler
                        me.consume();
                        return;
                    }
                    /*
                     ** if max isn't greater than min then there is nothing to do here
                     */
                    if (getSkinnable().getMax() > getSkinnable().getMin()) {
                        dragStart = thumb.localToParent(me.getX(), me.getY());
                        double clampedValue = MathUtils.clamp(getSkinnable().getValue(), getSkinnable().getMin(), getSkinnable().getMax());
                        preDragThumbPos = (clampedValue - getSkinnable().getMin()) / (getSkinnable().getMax() - getSkinnable().getMin());
                        me.consume();
                    }
                });


                thumb.setOnMouseDragged(me -> {
                    if (me.isSynthesized()) {
                        // touch-screen events handled by Scroll handler
                        me.consume();
                        return;
                    }
                    /*
                     ** if max isn't greater than min then there is nothing to do here
                     */
                    if (getSkinnable().getMax() > getSkinnable().getMin()) {
                        /*
                         ** if the tracklength isn't greater then do nothing....
                         */
                        if (trackLength() > thumbLength()) {
                            Point2D cur = thumb.localToParent(me.getX(), me.getY());
                            if (dragStart == null) {
                                // we're getting dragged without getting a mouse press
                                dragStart = thumb.localToParent(me.getX(), me.getY());
                            }
                            double dragPos = getSkinnable().getOrientation() == Orientation.VERTICAL ? cur.getY() - dragStart.getY() : cur.getX() - dragStart.getX();
                            double position = preDragThumbPos + dragPos / (trackLength() - thumbLength());
                            if (!getSkinnable().isFocused() && getSkinnable().isFocusTraversable())
                                getSkinnable().requestFocus();
                            double newValue = (position * (getSkinnable().getMax() - getSkinnable().getMin())) + getSkinnable().getMin();
                            if (!Double.isNaN(newValue)) {
                                getSkinnable().setValue(MathUtils.clamp(newValue, getSkinnable().getMin(), getSkinnable().getMax()));
                            }
                        }

                        me.consume();
                    }
                });
            }

            /// Returns track length along the scrolling axis.
            private double trackLength() {
                return getSkinnable().getOrientation() == Orientation.VERTICAL ? track.getHeight() : track.getWidth();
            }

            /// Returns thumb length along the scrolling axis.
            private double thumbLength() {
                return getSkinnable().getOrientation() == Orientation.VERTICAL ? thumb.getHeight() : thumb.getWidth();
            }

            /// Rebinds track and thumb geometry after an orientation change.
            private void setup() {
                track.widthProperty().unbind();
                track.heightProperty().unbind();

                double offset = 4;

                if (scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    track.relocate(offset, -6.0);
                    NumberBinding trackWidth = Bindings.max(0, Bindings.subtract(scrollBar.widthProperty(), offset * 2));
                    track.widthProperty().bind(trackWidth);
                    track.setHeight(6.0);
                } else {
                    track.relocate(-6.0, offset);
                    track.setWidth(6.0);
                    NumberBinding trackHeight = Bindings.max(0, Bindings.subtract(scrollBar.heightProperty(), offset * 2));
                    track.heightProperty().bind(trackHeight);
                }

                thumb.xProperty().unbind();
                thumb.yProperty().unbind();
                thumb.widthProperty().unbind();
                thumb.heightProperty().unbind();

                if (scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    thumb.relocate(0, -6.0);
                    thumb.setHeight(6.0);

                    NumberBinding trackWidth = Bindings.max(0, Bindings.subtract(scrollBar.widthProperty(), offset * 2));

                    thumb.widthProperty().bind(Bindings.min(trackWidth,
                            Bindings.max(20, scrollBar.visibleAmountProperty().divide(range).multiply(trackWidth))));

                    thumb.xProperty().bind(
                            Bindings.add(offset,
                                    Bindings.multiply(
                                            Bindings.max(0, Bindings.subtract(trackWidth, thumb.widthProperty())),
                                            position)
                            )
                    );
                } else {
                    thumb.relocate(-6.0, 0);
                    thumb.setWidth(6.0);

                    NumberBinding trackHeight = Bindings.max(0, Bindings.subtract(scrollBar.heightProperty(), offset * 2));

                    thumb.heightProperty().bind(Bindings.min(trackHeight,
                            Bindings.max(20, scrollBar.visibleAmountProperty().divide(range).multiply(trackHeight))));

                    thumb.yProperty().bind(
                            Bindings.add(offset,
                                    Bindings.multiply(
                                            Bindings.max(0, Bindings.subtract(trackHeight, thumb.heightProperty())),
                                            position)
                            )
                    );
                }
            }

            /// Allows horizontal tracks to fill their owner while keeping vertical tracks narrow.
            @Override
            protected double computeMaxWidth(double height) {
                if (scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    return Double.MAX_VALUE;
                }

                return 6.0;
            }

            /// Allows vertical tracks to fill their owner while keeping horizontal tracks narrow.
            @Override
            protected double computeMaxHeight(double width) {
                if (scrollBar.getOrientation() == Orientation.VERTICAL) {
                    return Double.MAX_VALUE;
                }

                return 6.0;
            }
        };

        scrollBar.addEventHandler(ScrollEvent.SCROLL, directScrollHandler);
        this.hoverOrPressed = thumb.hoverProperty().or(thumb.pressedProperty());
        FXUtils.onChangeAndOperate(hoverOrPressed, newValue -> {
            if (thumbHoverAnimation != null) {
                thumbHoverAnimation.stop();
                thumbHoverAnimation = null;
            }

            double targetOpacity = newValue ? 1.0 : 0.5;
            double currentOpacity = thumb.getOpacity();

            double opacityAdjustment = targetOpacity - currentOpacity;
            if (Math.abs(opacityAdjustment) < 0.001) {
                if (opacityAdjustment != 0)
                    thumb.setOpacity(targetOpacity);

                return;
            }

            if (AnimationUtils.isAnimationEnabled() && JFXNodeUtils.isTreeVisible(thumb)) {
                thumbHoverAnimation = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(thumb.opacityProperty(), currentOpacity)),
                        new KeyFrame(Motion.SHORT2, new KeyValue(thumb.opacityProperty(), targetOpacity))
                );
                thumbHoverAnimation.play();
            } else {
                thumb.setOpacity(targetOpacity);
            }
        });
    }

    /// Maps direct touch coordinates onto the value range without interpreting wheel input as dragging.
    private void handleDirectScroll(ScrollEvent event) {
        @Nullable ScrollBar bar = scrollBar;
        if (!event.isDirect() || bar == null || bar.isDisabled() || bar.valueProperty().isBound()
                || !(bar.getMax() > bar.getMin()))
            return;
        boolean vertical = bar.getOrientation() == Orientation.VERTICAL;
        double length = vertical ? track.getHeight() : track.getWidth();
        double thumbLength = vertical ? thumb.getHeight() : thumb.getWidth();
        if (!(length > thumbLength))
            return;
        @Nullable Point2D point = track.sceneToLocal(event.getSceneX(), event.getSceneY());
        if (point == null)
            return;
        double fraction = (vertical ? point.getY() : point.getX()) / length;
        if (!Double.isFinite(fraction))
            return;
        bar.setValue(bar.getMin() + MathUtils.clamp(fraction, 0, 1) * (bar.getMax() - bar.getMin()));
        event.consume();
    }

    /// Removes touch handling and stops feedback animation before releasing the skin's owner.
    @Override
    public void dispose() {
        if (scrollBar != null)
            scrollBar.removeEventHandler(ScrollEvent.SCROLL, directScrollHandler);
        if (thumbHoverAnimation != null) {
            thumbHoverAnimation.stop();
            thumbHoverAnimation = null;
        }
        hoverOrPressed = null;
        scrollBar = null;
        group = null;
    }

    /// Returns the skin root, or null after disposal.
    @Override
    public @Nullable Node getNode() {
        return group;
    }

    /// Returns the owning scrollbar, or null after disposal.
    @Override
    public @Nullable ScrollBar getSkinnable() {
        return scrollBar;
    }
}
