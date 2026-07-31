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
import javafx.animation.Transition;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.Nullable;

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

    /// The property key for the smooth scroll state installed on a scroll pane.
    private static final Object SCROLL_PANE_STATE_KEY = new Object();

    /// The pixel distance used for one platform-reported text line or character.
    private static final double DEFAULT_LINE_SCROLL_PIXELS = 40.0;

    /// The minimum meaningful difference between two scroll values.
    private static final double EPSILON = 0.000001;

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

    /// Adds smooth scrolling to a scroll pane using its platform-reported scroll distances.
    ///
    /// @param scrollPane the scroll pane to configure
    /// @param speed      the multiplier applied to platform scroll distances
    public static void addSmoothScrolling(ScrollPane scrollPane, double speed) {
        addSmoothScrolling(scrollPane, speed, 1.0);
    }

    /// Adds smooth scrolling to a scroll pane with an optional adjustment for small pixel deltas.
    ///
    /// This overload is retained for callers that explicitly configured the former trackpad adjustment. Text-line
    /// and page scroll units are never adjusted.
    ///
    /// @param scrollPane         the scroll pane to configure
    /// @param speed              the multiplier applied to platform scroll distances
    /// @param trackPadAdjustment the divisor applied to small pixel-unit deltas
    public static void addSmoothScrolling(ScrollPane scrollPane, double speed, double trackPadAdjustment) {
        @Nullable Object installedState = scrollPane.getProperties().get(SCROLL_PANE_STATE_KEY);
        if (installedState instanceof ScrollPaneSmoothScrollState state) {
            state.configure(speed, trackPadAdjustment);
        } else {
            scrollPane.getProperties().put(
                    SCROLL_PANE_STATE_KEY,
                    new ScrollPaneSmoothScrollState(scrollPane, speed, trackPadAdjustment)
            );
        }
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

    /// Maintains the accumulated scroll target and transition for one scroll pane.
    private static final class ScrollPaneSmoothScrollState {
        /// The scroll pane receiving smooth scrolling.
        private final ScrollPane scrollPane;

        /// The reusable transition for both axes.
        private final ScrollPaneTransition animation;

        /// The event filter installed on the scroll pane.
        private final EventHandler<ScrollEvent> scrollHandler = this::handleScroll;

        /// Stops pending wheel motion before mouse-driven interaction.
        private final EventHandler<MouseEvent> mouseHandler = event -> stopAnimation();

        /// The accumulated horizontal target.
        private double targetHValue;

        /// The accumulated vertical target.
        private double targetVValue;

        /// The horizontal pixel span represented by the current target.
        private double targetHScrollablePixels;

        /// The vertical pixel span represented by the current target.
        private double targetVScrollablePixels;

        /// The configured scroll distance multiplier.
        private double speed;

        /// The configured divisor for small pixel-unit deltas.
        private double trackPadAdjustment;

        /// Creates and installs smooth scrolling for a scroll pane.
        ///
        /// @param scrollPane         the scroll pane to configure
        /// @param speed              the scroll distance multiplier
        /// @param trackPadAdjustment the small-delta divisor
        private ScrollPaneSmoothScrollState(
                ScrollPane scrollPane,
                double speed,
                double trackPadAdjustment
        ) {
            this.scrollPane = scrollPane;
            this.animation = new ScrollPaneTransition(scrollPane);
            this.targetHValue = scrollPane.getHvalue();
            this.targetVValue = scrollPane.getVvalue();
            configure(speed, trackPadAdjustment);
            scrollPane.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
            scrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseHandler);
        }

        /// Updates scroll distance settings without installing another event filter.
        ///
        /// @param speed              the scroll distance multiplier
        /// @param trackPadAdjustment the small-delta divisor
        private void configure(double speed, double trackPadAdjustment) {
            this.speed = speed;
            this.trackPadAdjustment = trackPadAdjustment;
        }

        /// Stops pending smooth movement.
        private void stopAnimation() {
            animation.stop();
        }

        /// Handles one indirect wheel or trackpad event.
        ///
        /// @param event the scroll event delivered to the pane
        private void handleScroll(ScrollEvent event) {
            if (event.isDirect() || !isEventTargetForScrollPane(scrollPane, event.getTarget())) {
                return;
            }

            double viewportWidth = scrollPane.getViewportBounds().getWidth();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double adjustment = scrollAdjustment(event);
            double horizontalDelta = scrollDeltaX(event) * adjustment;
            double verticalDelta = scrollDeltaY(event, viewportHeight) * adjustment;
            double horizontalScrollablePixels = Math.max(0.0, contentWidth(scrollPane) - viewportWidth);
            double verticalScrollablePixels = Math.max(0.0, contentHeight(scrollPane) - viewportHeight);
            boolean canScrollHorizontally = canScroll(
                    scrollPane.getHmin(),
                    scrollPane.getHmax(),
                    horizontalScrollablePixels
            );
            boolean canScrollVertically = canScroll(
                    scrollPane.getVmin(),
                    scrollPane.getVmax(),
                    verticalScrollablePixels
            );

            if (animation.getStatus() == Status.STOPPED) {
                targetHValue = scrollPane.getHvalue();
                targetVValue = scrollPane.getVvalue();
            } else {
                targetHValue = retargetScrollValue(
                        targetHValue,
                        targetHScrollablePixels,
                        horizontalScrollablePixels,
                        scrollPane.getHmin(),
                        scrollPane.getHmax()
                );
                targetVValue = retargetScrollValue(
                        targetVValue,
                        targetVScrollablePixels,
                        verticalScrollablePixels,
                        scrollPane.getVmin(),
                        scrollPane.getVmax()
                );
            }
            targetHScrollablePixels = horizontalScrollablePixels;
            targetVScrollablePixels = verticalScrollablePixels;

            if (event.isShiftDown() && canScrollHorizontally && close(horizontalDelta, 0.0)) {
                horizontalDelta = verticalDelta;
                verticalDelta = 0.0;
            } else if (!canScrollVertically
                    && canScrollHorizontally
                    && close(horizontalDelta, 0.0)
                    && !close(verticalDelta, 0.0)) {
                horizontalDelta = verticalDelta;
                verticalDelta = 0.0;
            }

            double nextHValue = targetHValue;
            double nextVValue = targetVValue;
            if (canScrollHorizontally && !close(horizontalDelta, 0.0)) {
                nextHValue = scrollTargetValue(
                        targetHValue,
                        horizontalDelta,
                        scrollPane.getHmin(),
                        scrollPane.getHmax(),
                        horizontalScrollablePixels
                );
            }
            if (canScrollVertically && !close(verticalDelta, 0.0)) {
                nextVValue = scrollTargetValue(
                        targetVValue,
                        verticalDelta,
                        scrollPane.getVmin(),
                        scrollPane.getVmax(),
                        verticalScrollablePixels
                );
            }

            if (close(nextHValue, targetHValue) && close(nextVValue, targetVValue)) {
                return;
            }

            targetHValue = nextHValue;
            targetVValue = nextVValue;
            animateToTarget();
            event.consume();
        }

        /// Returns the multiplier for this event's platform unit type.
        ///
        /// @param event the event being handled
        /// @return the configured speed, optionally divided for a small pixel delta
        private double scrollAdjustment(ScrollEvent event) {
            boolean usesPixelUnits = event.getTextDeltaXUnits() == ScrollEvent.HorizontalTextScrollUnits.NONE
                    && event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.NONE;
            if (usesPixelUnits && isTrackPad(event, determineScrollDirection(event))) {
                return speed / trackPadAdjustment;
            }
            return speed;
        }

        /// Starts a transition or applies the target immediately when no window can render it.
        private void animateToTarget() {
            if (!canAnimate(scrollPane)) {
                animation.stop();
                scrollPane.setHvalue(targetHValue);
                scrollPane.setVvalue(targetVValue);
                return;
            }

            animation.configure(
                    scrollPane.getHvalue(),
                    targetHValue,
                    scrollPane.getVvalue(),
                    targetVValue
            );
            animation.playFromStart();
        }
    }

    /// Interpolates both scroll axes for one scroll pane.
    private static final class ScrollPaneTransition extends Transition {
        /// The scroll pane whose values are updated.
        private final ScrollPane scrollPane;

        /// The horizontal value at the start of the current transition.
        private double startHValue;

        /// The horizontal target of the current transition.
        private double targetHValue;

        /// The vertical value at the start of the current transition.
        private double startVValue;

        /// The vertical target of the current transition.
        private double targetVValue;

        /// Creates a reusable transition for a scroll pane.
        ///
        /// @param scrollPane the pane whose values are updated
        private ScrollPaneTransition(ScrollPane scrollPane) {
            this.scrollPane = scrollPane;
            setCycleDuration(Motion.LONG2);
            setInterpolator(Motion.STANDARD_DECELERATE);
        }

        /// Reconfigures this transition for the latest accumulated targets.
        ///
        /// @param startHValue  the current horizontal value
        /// @param targetHValue the horizontal target
        /// @param startVValue  the current vertical value
        /// @param targetVValue the vertical target
        private void configure(
                double startHValue,
                double targetHValue,
                double startVValue,
                double targetVValue
        ) {
            stop();
            this.startHValue = startHValue;
            this.targetHValue = targetHValue;
            this.startVValue = startVValue;
            this.targetVValue = targetVValue;
        }

        /// Applies an eased animation fraction to both axes.
        ///
        /// @param fraction the eased fraction
        @Override
        protected void interpolate(double fraction) {
            scrollPane.setHvalue(ScrollUtils.interpolate(startHValue, targetHValue, fraction));
            scrollPane.setVvalue(ScrollUtils.interpolate(startVValue, targetVValue, fraction));
        }
    }

    /// Returns whether the event target belongs to this pane rather than to a nested scroll owner.
    ///
    /// @param scrollPane the pane that installed the event filter
    /// @param target     the original event target
    /// @return `true` when this pane owns the event
    static boolean isEventTargetForScrollPane(ScrollPane scrollPane, EventTarget target) {
        if (!(target instanceof Node node)) {
            return true;
        }

        @Nullable Node current = node;
        while (current != null && current != scrollPane) {
            if (current instanceof ScrollPane
                    || current instanceof TextArea
                    || current instanceof VirtualFlow<?>
                    || current instanceof ListView<?>
                    || current instanceof TreeView<?>
                    || current instanceof TableView<?>
                    || current instanceof TreeTableView<?>) {
                return false;
            }
            current = current.getParent();
        }
        return current == scrollPane;
    }

    /// Returns whether a node has a visible window that can advance an animation.
    ///
    /// @param node the animation owner
    /// @return `true` when a showing window is available
    private static boolean canAnimate(Node node) {
        @Nullable Scene scene = node.getScene();
        if (scene == null) {
            return false;
        }
        @Nullable Window window = scene.getWindow();
        return window != null && window.isShowing();
    }

    /// Returns the current width used by a scroll pane's content.
    ///
    /// @param scrollPane the pane whose content is measured
    /// @return the content width, or `0` for an empty pane
    private static double contentWidth(ScrollPane scrollPane) {
        @Nullable Node content = scrollPane.getContent();
        if (content == null) {
            return 0.0;
        }

        Bounds bounds = content.getBoundsInLocal();
        double width = bounds.getWidth();
        if (content instanceof Region region) {
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            width = Math.max(width, region.prefWidth(viewportHeight > 0.0 ? viewportHeight : -1.0));
        }
        return width;
    }

    /// Returns the current height used by a scroll pane's content.
    ///
    /// @param scrollPane the pane whose content is measured
    /// @return the content height, or `0` for an empty pane
    private static double contentHeight(ScrollPane scrollPane) {
        @Nullable Node content = scrollPane.getContent();
        if (content == null) {
            return 0.0;
        }

        Bounds bounds = content.getBoundsInLocal();
        double height = bounds.getHeight();
        if (content instanceof Region region) {
            double viewportWidth = scrollPane.getViewportBounds().getWidth();
            height = Math.max(height, region.prefHeight(viewportWidth > 0.0 ? viewportWidth : -1.0));
        }
        return height;
    }

    /// Converts an event's horizontal amount to pixels.
    ///
    /// @param event the scroll event to convert
    /// @return the signed horizontal distance in pixels
    static double scrollDeltaX(ScrollEvent event) {
        return switch (event.getTextDeltaXUnits()) {
            case CHARACTERS -> event.getTextDeltaX() * DEFAULT_LINE_SCROLL_PIXELS;
            case NONE -> event.getDeltaX();
        };
    }

    /// Converts an event's vertical amount to pixels.
    ///
    /// @param event          the scroll event to convert
    /// @param viewportHeight the viewport height used for page units
    /// @return the signed vertical distance in pixels
    static double scrollDeltaY(ScrollEvent event, double viewportHeight) {
        return switch (event.getTextDeltaYUnits()) {
            case LINES -> event.getTextDeltaY() * DEFAULT_LINE_SCROLL_PIXELS;
            case PAGES -> event.getTextDeltaY() * viewportHeight;
            case NONE -> event.getDeltaY();
        };
    }

    /// Computes a normalized target after applying a pixel delta.
    ///
    /// @param currentValue     the current normalized target
    /// @param scrollDelta      the signed pixel delta
    /// @param minValue         the minimum normalized value
    /// @param maxValue         the maximum normalized value
    /// @param scrollablePixels the pixel span represented by the normalized range
    /// @return the updated, clamped normalized target
    static double scrollTargetValue(
            double currentValue,
            double scrollDelta,
            double minValue,
            double maxValue,
            double scrollablePixels
    ) {
        if (!canScroll(minValue, maxValue, scrollablePixels)) {
            return currentValue;
        }

        double currentPixels = pixelsForValue(currentValue, minValue, maxValue, scrollablePixels);
        double targetPixels = clamp(currentPixels - scrollDelta, 0.0, scrollablePixels);
        return valueForPixels(targetPixels, minValue, maxValue, scrollablePixels);
    }

    /// Preserves an in-flight target's pixel offset after the content span changes.
    ///
    /// @param currentValue             the current normalized target
    /// @param previousScrollablePixels the previous scrollable pixel span
    /// @param currentScrollablePixels  the current scrollable pixel span
    /// @param minValue                 the minimum normalized value
    /// @param maxValue                 the maximum normalized value
    /// @return the target mapped to the current pixel span
    static double retargetScrollValue(
            double currentValue,
            double previousScrollablePixels,
            double currentScrollablePixels,
            double minValue,
            double maxValue
    ) {
        if (previousScrollablePixels <= EPSILON
                || currentScrollablePixels <= EPSILON
                || close(minValue, maxValue)) {
            return currentValue;
        }

        double targetPixels = pixelsForValue(currentValue, minValue, maxValue, previousScrollablePixels);
        return valueForPixels(
                clamp(targetPixels, 0.0, currentScrollablePixels),
                minValue,
                maxValue,
                currentScrollablePixels
        );
    }

    /// Returns whether an axis can represent meaningful movement.
    private static boolean canScroll(double minValue, double maxValue, double scrollablePixels) {
        return scrollablePixels > EPSILON && !close(minValue, maxValue);
    }

    /// Converts a normalized value to a pixel offset.
    private static double pixelsForValue(
            double value,
            double minValue,
            double maxValue,
            double scrollablePixels
    ) {
        double clampedValue = clamp(value, minValue, maxValue);
        return (clampedValue - minValue) / (maxValue - minValue) * scrollablePixels;
    }

    /// Converts a pixel offset to a normalized value.
    private static double valueForPixels(
            double pixels,
            double minValue,
            double maxValue,
            double scrollablePixels
    ) {
        return minValue + pixels / scrollablePixels * (maxValue - minValue);
    }

    /// Clamps a value to an inclusive range.
    private static double clamp(double value, double minValue, double maxValue) {
        if (value <= minValue) {
            return minValue;
        }
        return Math.min(value, maxValue);
    }

    /// Returns whether two scroll values are effectively equal.
    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    /// Interpolates linearly between two values.
    private static double interpolate(double start, double end, double fraction) {
        return start + (end - start) * fraction;
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
