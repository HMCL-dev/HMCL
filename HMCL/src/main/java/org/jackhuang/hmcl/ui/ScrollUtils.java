// Copy from https://github.com/palexdev/MaterialFX/blob/c8038ce2090f5cddf923a19d79cc601db86a4d17/materialfx/src/main/java/io/github/palexdev/materialfx/utils/ScrollUtils.java

/*
 * Copyright (C) 2022 Parisi Alessandro
 * This file is part of MaterialFX (https://github.com/palexdev/MaterialFX).
 *
 * MaterialFX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MaterialFX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with MaterialFX.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jackhuang.hmcl.ui;

import javafx.animation.Animation;
import javafx.animation.Animation.Status;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;
import org.jackhuang.hmcl.util.Holder;

/**
 * Utility class for ScrollPanes.
 */
final class ScrollUtils {

    public enum ScrollDirection {
        UP(-1), RIGHT(-1), DOWN(1), LEFT(1);

        final int intDirection;

        ScrollDirection(int intDirection) {
            this.intDirection = intDirection;
        }

        public int intDirection() {
            return intDirection;
        }
    }

    private static final double DEFAULT_SPEED = 1.0;
    private static final double DEFAULT_TRACK_PAD_ADJUSTMENT = 7.0;

    private static final double CUTOFF_DELTA = 0.01;

    /**
     * Determines if the given ScrollEvent comes from a trackpad.
     * <p></p>
     * Although this method works in most cases, it is not very accurate.
     * Since in JavaFX there's no way to tell if a ScrollEvent comes from a trackpad or a mouse
     * we use this trick: I noticed that a mouse scroll has a delta of 32 (don't know if it changes depending on the device or OS)
     * and trackpad scrolls have a way smaller delta. So depending on the scroll direction we check if the delta is lesser than 10
     * (trackpad event) or greater(mouse event).
     *
     * @see ScrollEvent#getDeltaX()
     * @see ScrollEvent#getDeltaY()
     */
    public static boolean isTrackPad(ScrollEvent event, ScrollDirection scrollDirection) {
        return switch (scrollDirection) {
            case UP, DOWN -> Math.abs(event.getDeltaY()) < 10;
            case LEFT, RIGHT -> Math.abs(event.getDeltaX()) < 10;
        };
    }

    /**
     * Determines the scroll direction of the given ScrollEvent.
     * <p></p>
     * Although this method works fine, it is not very accurate.
     * In JavaFX there's no concept of scroll direction, if you try to scroll with a trackpad
     * you'll notice that you can scroll in both directions at the same time, both deltaX and deltaY won't be 0.
     * <p></p>
     * For this method to work we assume that this behavior is not possible.
     * <p></p>
     * If deltaY is 0 we return LEFT or RIGHT depending on deltaX (respectively if lesser or greater than 0).
     * <p>
     * Else we return DOWN or UP depending on deltaY (respectively if lesser or greater than 0).
     *
     * @see ScrollEvent#getDeltaX()
     * @see ScrollEvent#getDeltaY()
     */
    public static ScrollDirection determineScrollDirection(ScrollEvent event) {
        double deltaX = event.getDeltaX();
        double deltaY = event.getDeltaY();

        if (deltaY == 0.0) {
            return deltaX < 0 ? ScrollDirection.LEFT : ScrollDirection.RIGHT;
        } else {
            return deltaY < 0 ? ScrollDirection.DOWN : ScrollDirection.UP;
        }
    }

    //================================================================================
    // ScrollPanes
    //================================================================================

    /**
     * Adds a smooth scrolling effect to the given scroll pane,
     * calls {@link #addSmoothScrolling(ScrollPane, double)} with a
     * default speed value of 1.
     */
    public static void addSmoothScrolling(ScrollPane scrollPane) {
        addSmoothScrolling(scrollPane, DEFAULT_SPEED);
    }

    /**
     * Adds a smooth scrolling effect to the given scroll pane with the given scroll speed.
     * Calls {@link #addSmoothScrolling(ScrollPane, double, double)}
     * with a default trackPadAdjustment of 7.
     */
    public static void addSmoothScrolling(ScrollPane scrollPane, double speed) {
        addSmoothScrolling(scrollPane, speed, DEFAULT_TRACK_PAD_ADJUSTMENT);
    }

    /**
     * Adds a smooth scrolling effect to the given scroll pane with the given
     * scroll speed and the given trackPadAdjustment.
     * <p></p>
     * The trackPadAdjustment is a value used to slow down the scrolling if a trackpad is used.
     * This is kind of a workaround and it's not perfect, but at least it's way better than before.
     * The default value is 7, tested up to 10, further values can cause scrolling misbehavior.
     */
    public static void addSmoothScrolling(ScrollPane scrollPane, double speed, double trackPadAdjustment) {
        smoothScroll(scrollPane, speed, trackPadAdjustment);
    }

    /// @author Glavo
    public static void addSmoothScrolling(VirtualFlow<?> virtualFlow) {
        addSmoothScrolling(virtualFlow, DEFAULT_SPEED);
    }

    /// @author Glavo
    public static void addSmoothScrolling(VirtualFlow<?> virtualFlow, double speed) {
        addSmoothScrolling(virtualFlow, speed, DEFAULT_TRACK_PAD_ADJUSTMENT);
    }

    /// @author Glavo
    public static void addSmoothScrolling(VirtualFlow<?> virtualFlow, double speed, double trackPadAdjustment) {
        smoothScroll(virtualFlow, speed, trackPadAdjustment);
    }

    private static final double[] FRICTIONS = {0.99, 0.1, 0.05, 0.04, 0.03, 0.02, 0.01, 0.04, 0.01, 0.008, 0.008, 0.008, 0.008, 0.0006, 0.0005, 0.00003, 0.00001};
    private static final Duration DURATION = Duration.millis(3);

    private static void smoothScroll(ScrollPane scrollPane, double speed, double trackPadAdjustment) {
        final double[] derivatives = new double[FRICTIONS.length];

        Timeline timeline = new Timeline();
        Holder<ScrollDirection> scrollDirectionHolder = new Holder<>();
        final EventHandler<MouseEvent> mouseHandler = event -> timeline.stop();
        final EventHandler<ScrollEvent> scrollHandler = event -> {
            if (event.getEventType() == ScrollEvent.SCROLL) {
                ScrollDirection scrollDirection = determineScrollDirection(event);
                scrollDirectionHolder.value = scrollDirection;

                double currentSpeed = isTrackPad(event, scrollDirection) ? speed / trackPadAdjustment : speed;

                derivatives[0] += scrollDirection.intDirection * currentSpeed;
                if (timeline.getStatus() == Status.STOPPED) {
                    timeline.play();
                }
                event.consume();
            }
        };
        if (scrollPane.getContent().getParent() != null) {
            scrollPane.getContent().getParent().addEventFilter(MouseEvent.MOUSE_PRESSED, mouseHandler);
            scrollPane.getContent().getParent().addEventHandler(ScrollEvent.ANY, scrollHandler);
        }
        scrollPane.getContent().parentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeEventFilter(MouseEvent.MOUSE_PRESSED, mouseHandler);
                oldValue.removeEventHandler(ScrollEvent.ANY, scrollHandler);
            }
            if (newValue != null) {
                newValue.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseHandler);
                newValue.addEventHandler(ScrollEvent.ANY, scrollHandler);
            }
        });

        timeline.getKeyFrames().add(new KeyFrame(DURATION, event -> {
            for (int i = 0; i < derivatives.length; i++) {
                derivatives[i] *= FRICTIONS[i];
            }
            for (int i = 1; i < derivatives.length; i++) {
                derivatives[i] += derivatives[i - 1];
            }

            double dy = derivatives[derivatives.length - 1];
            double size;
            switch (scrollDirectionHolder.value) {
                case LEFT:
                case RIGHT:
                    size = scrollPane.getContent().getLayoutBounds().getWidth();
                    scrollPane.setHvalue(Math.min(Math.max(scrollPane.getHvalue() + dy / size, 0), 1));
                    break;
                case UP:
                case DOWN:
                    size = scrollPane.getContent().getLayoutBounds().getHeight();
                    scrollPane.setVvalue(Math.min(Math.max(scrollPane.getVvalue() + dy / size, 0), 1));
                    break;
            }

            if (Math.abs(dy) < CUTOFF_DELTA) {
                timeline.stop();
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    /// @author Glavo
    private static void smoothScroll(VirtualFlow<?> virtualFlow, double speed, double trackPadAdjustment) {
        if (!virtualFlow.isVertical())
            return;

        final double[] derivatives = new double[FRICTIONS.length];

        Timeline timeline = new Timeline();
        final EventHandler<MouseEvent> mouseHandler = event -> timeline.stop();
        final EventHandler<ScrollEvent> scrollHandler = event -> {
            if (event.getEventType() == ScrollEvent.SCROLL) {
                ScrollDirection scrollDirection = determineScrollDirection(event);
                if (scrollDirection == ScrollDirection.LEFT || scrollDirection == ScrollDirection.RIGHT) {
                    return;
                }
                double currentSpeed = isTrackPad(event, scrollDirection) ? speed / trackPadAdjustment : speed;

                derivatives[0] += scrollDirection.intDirection * currentSpeed;
                if (timeline.getStatus() == Status.STOPPED) {
                    timeline.play();
                }
                event.consume();
            }
        };
        virtualFlow.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseHandler);
        virtualFlow.addEventFilter(ScrollEvent.ANY, scrollHandler);

        timeline.getKeyFrames().add(new KeyFrame(DURATION, event -> {
            for (int i = 0; i < derivatives.length; i++) {
                derivatives[i] *= FRICTIONS[i];
            }
            for (int i = 1; i < derivatives.length; i++) {
                derivatives[i] += derivatives[i - 1];
            }

            double dy = derivatives[derivatives.length - 1];
            virtualFlow.scrollPixels(dy);

            if (Math.abs(dy) < CUTOFF_DELTA) {
                timeline.stop();
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    private ScrollUtils() {
    }
}
