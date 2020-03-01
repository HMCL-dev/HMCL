/**
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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.util.Lang;

public class FloatScrollBarSkin implements Skin<ScrollBar> {
    private ScrollBar scrollBar;
    private Region group;
    private Rectangle track = new Rectangle();
    private Rectangle thumb = new Rectangle();

    public FloatScrollBarSkin(final ScrollBar scrollBar) {
        this.scrollBar = scrollBar;
        scrollBar.setPrefHeight(1e-18);
        scrollBar.setPrefWidth(1e-18);

        this.group = new Region() {
            Point2D dragStart;
            double preDragThumbPos;

            NumberBinding range = Bindings.subtract(scrollBar.maxProperty(), scrollBar.minProperty());
            NumberBinding position = Bindings.divide(Bindings.subtract(scrollBar.valueProperty(), scrollBar.minProperty()), range);

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
                        double clampedValue = Lang.clamp(getSkinnable().getMin(), getSkinnable().getValue(), getSkinnable().getMax());
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
                            double dragPos = getSkinnable().getOrientation() == Orientation.VERTICAL ? cur.getY() - dragStart.getY(): cur.getX() - dragStart.getX();
                            double position = preDragThumbPos + dragPos / (trackLength() - thumbLength());
                            if (!getSkinnable().isFocused() && getSkinnable().isFocusTraversable()) getSkinnable().requestFocus();
                            double newValue = (position * (getSkinnable().getMax() - getSkinnable().getMin())) + getSkinnable().getMin();
                            if (!Double.isNaN(newValue)) {
                                getSkinnable().setValue(Lang.clamp(getSkinnable().getMin(), newValue, getSkinnable().getMax()));
                            }
                        }

                        me.consume();
                    }
                });
            }

            private double trackLength() {
                return getSkinnable().getOrientation() == Orientation.VERTICAL ? track.getHeight() : track.getWidth();
            }

            private double thumbLength() {
                return getSkinnable().getOrientation() == Orientation.VERTICAL ? thumb.getHeight() : thumb.getWidth();
            }

            private double boundedSize(double min, double value, double max) {
                return Math.min(Math.max(value, min), Math.max(min, max));
            }

            private void setup() {
                track.widthProperty().unbind();
                track.heightProperty().unbind();

                if (scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    track.relocate(0, -5);
                    track.widthProperty().bind(scrollBar.widthProperty());
                    track.setHeight(5);
                } else {
                    track.relocate(-5, 0);
                    track.setWidth(5);
                    track.heightProperty().bind(scrollBar.heightProperty());
                }

                thumb.xProperty().unbind();
                thumb.yProperty().unbind();
                thumb.widthProperty().unbind();
                thumb.heightProperty().unbind();

                if (scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    thumb.relocate(0, -5);
                    thumb.widthProperty().bind(Bindings.max(5, scrollBar.visibleAmountProperty().divide(range).multiply(scrollBar.widthProperty())));
                    thumb.setHeight(5);
                    thumb.xProperty().bind(Bindings.subtract(scrollBar.widthProperty(), thumb.widthProperty()).multiply(position));
                } else {
                    thumb.relocate(-5, 0);
                    thumb.setWidth(5);
                    thumb.heightProperty().bind(Bindings.max(5, scrollBar.visibleAmountProperty().divide(range).multiply(scrollBar.heightProperty())));
                    thumb.yProperty().bind(Bindings.subtract(scrollBar.heightProperty(), thumb.heightProperty()).multiply(position));
                }
            }

            @Override
            protected double computeMaxWidth(double height) {
                if (scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    return Double.MAX_VALUE;
                }

                return 5;
            }

            @Override
            protected double computeMaxHeight(double width) {
                if (scrollBar.getOrientation() == Orientation.VERTICAL) {
                    return Double.MAX_VALUE;
                }

                return 5;
            }
        };
    }

    @Override
    public void dispose() {
        scrollBar = null;
        group = null;
    }

    @Override
    public Node getNode() {
        return group;
    }

    @Override
    public ScrollBar getSkinnable() {
        return scrollBar;
    }
}
