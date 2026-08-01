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

import javafx.animation.Animation.Status;
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
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for ScrollPanes.
 */
final class ScrollUtils {
    private static final double DEFAULT_SPEED = 1.0;

    /// The property key for the smooth scroll state installed on a scroll pane.
    private static final Object SCROLL_PANE_STATE_KEY = new Object();

    /// The property key for the smooth scroll state installed on a virtual flow.
    private static final Object VIRTUAL_FLOW_STATE_KEY = new Object();

    /// The pixel distance used for one platform-reported text line or character.
    private static final double DEFAULT_LINE_SCROLL_PIXELS = 40.0;

    /// Restores the approximate distance of the former friction chain for pixel-unit input.
    private static final double PIXEL_SCROLL_MULTIPLIER = 4.0;

    /// The minimum meaningful difference between two scroll values.
    private static final double EPSILON = 0.000001;

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
        addSmoothScrolling(virtualFlow, speed, 1.0);
    }

    /// Adds smooth pixel scrolling to a vertical virtual flow.
    ///
    /// Repeated calls update the configuration without installing duplicate event filters.
    ///
    /// @param virtualFlow        the virtual flow used by a list-like control
    /// @param speed              the multiplier applied to platform scroll distances
    /// @param trackPadAdjustment the divisor applied to small pixel-unit deltas
    public static void addSmoothScrolling(VirtualFlow<?> virtualFlow, double speed, double trackPadAdjustment) {
        if (!virtualFlow.isVertical()) {
            return;
        }

        @Nullable Object installedState = virtualFlow.getProperties().get(VIRTUAL_FLOW_STATE_KEY);
        if (installedState instanceof VirtualFlowSmoothScrollState state) {
            state.configure(speed, trackPadAdjustment);
        } else {
            virtualFlow.getProperties().put(
                    VIRTUAL_FLOW_STATE_KEY,
                    new VirtualFlowSmoothScrollState(virtualFlow, speed, trackPadAdjustment)
            );
        }
    }

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
            double horizontalDelta = scrollDeltaX(event) * horizontalScrollScale(event);
            double verticalDelta = scrollDeltaY(event, viewportHeight) * verticalScrollScale(event);
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

        /// Returns the multiplier for this event's horizontal unit type.
        ///
        /// @param event the event being handled
        /// @return the configured scale for the horizontal delta
        private double horizontalScrollScale(ScrollEvent event) {
            return event.getTextDeltaXUnits() == ScrollEvent.HorizontalTextScrollUnits.NONE
                    ? pixelScrollScale(event, speed, trackPadAdjustment)
                    : speed;
        }

        /// Returns the multiplier for this event's vertical unit type.
        ///
        /// @param event the event being handled
        /// @return the configured scale for the vertical delta
        private double verticalScrollScale(ScrollEvent event) {
            return event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.NONE
                    ? pixelScrollScale(event, speed, trackPadAdjustment)
                    : speed;
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

    /// Maintains accumulated pixel movement for one vertical virtual flow.
    private static final class VirtualFlowSmoothScrollState {
        /// The virtual flow receiving smooth scrolling.
        private final VirtualFlow<?> virtualFlow;

        /// The reusable pixel-distance transition.
        private final VirtualFlowTransition animation;

        /// The event filter installed on the virtual flow.
        private final EventHandler<ScrollEvent> scrollHandler = this::handleScroll;

        /// Stops pending wheel motion before mouse-driven interaction.
        private final EventHandler<MouseEvent> mouseHandler = event -> stopAnimation();

        /// The configured scroll distance multiplier.
        private double speed;

        /// The configured divisor for small pixel-unit deltas.
        private double trackPadAdjustment;

        /// Creates and installs smooth scrolling for a virtual flow.
        ///
        /// @param virtualFlow        the flow to configure
        /// @param speed              the scroll distance multiplier
        /// @param trackPadAdjustment the small-delta divisor
        private VirtualFlowSmoothScrollState(
                VirtualFlow<?> virtualFlow,
                double speed,
                double trackPadAdjustment
        ) {
            this.virtualFlow = virtualFlow;
            this.animation = new VirtualFlowTransition(virtualFlow);
            configure(speed, trackPadAdjustment);
            virtualFlow.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
            virtualFlow.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseHandler);
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

        /// Handles one indirect vertical wheel or trackpad event.
        ///
        /// @param event the scroll event delivered to the flow
        private void handleScroll(ScrollEvent event) {
            if (event.isDirect()
                    || !virtualFlow.isVertical()
                    || !isEventTargetForVirtualFlow(virtualFlow, event.getTarget())) {
                return;
            }

            double viewportHeight = virtualFlow.getViewportLength();
            if (viewportHeight <= 0.0) {
                viewportHeight = virtualFlow.getHeight();
            }
            double scale = event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.NONE
                    ? pixelScrollScale(event, speed, trackPadAdjustment)
                    : speed;
            double delta = scrollDeltaY(event, viewportHeight) * scale;
            if (close(delta, 0.0)) {
                return;
            }

            double remainingDistance = animation.getStatus() == Status.STOPPED
                    ? 0.0
                    : animation.remainingDistance();
            double targetDistance = remainingDistance - delta;
            if (close(targetDistance, 0.0)) {
                animation.stop();
                event.consume();
                return;
            }

            double position = virtualFlow.getPosition();
            if ((position <= EPSILON && targetDistance < 0.0)
                    || (position >= 1.0 - EPSILON && targetDistance > 0.0)) {
                animation.stop();
                return;
            }

            if (!canAnimate(virtualFlow)) {
                animation.stop();
                if (!close(virtualFlow.scrollPixels(targetDistance), 0.0)) {
                    event.consume();
                }
                return;
            }

            animation.configure(targetDistance);
            animation.playFromStart();
            event.consume();
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
            setCycleDuration(Motion.MEDIUM2);
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

    /// Applies an eased pixel distance incrementally to one virtual flow.
    private static final class VirtualFlowTransition extends Transition {
        /// The virtual flow moved by this transition.
        private final VirtualFlow<?> virtualFlow;

        /// The total pixel distance requested for the current transition.
        private double targetDistance;

        /// The interpolated distance already submitted to the virtual flow.
        private double appliedDistance;

        /// Creates a reusable transition for a virtual flow.
        ///
        /// @param virtualFlow the flow moved by this transition
        private VirtualFlowTransition(VirtualFlow<?> virtualFlow) {
            this.virtualFlow = virtualFlow;
            setCycleDuration(Motion.MEDIUM2);
            setInterpolator(Motion.STANDARD_DECELERATE);
        }

        /// Reconfigures this transition for the remaining accumulated distance.
        ///
        /// @param targetDistance the signed pixel distance to move
        private void configure(double targetDistance) {
            stop();
            this.targetDistance = targetDistance;
            this.appliedDistance = 0.0;
        }

        /// Returns the distance that has not yet been submitted to the virtual flow.
        ///
        /// @return the signed remaining distance in pixels
        private double remainingDistance() {
            return targetDistance - appliedDistance;
        }

        /// Applies the newly interpolated distance and stops when the flow reaches a boundary.
        ///
        /// @param fraction the eased animation fraction
        @Override
        protected void interpolate(double fraction) {
            double nextDistance = targetDistance * fraction;
            double requestedDistance = nextDistance - appliedDistance;
            double actualDistance = virtualFlow.scrollPixels(requestedDistance);
            appliedDistance = nextDistance;
            if (!close(actualDistance, requestedDistance)) {
                stop();
            }
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

    /// Returns whether the event target belongs to this flow rather than to a nested scroll owner.
    ///
    /// @param virtualFlow the flow that installed the event filter
    /// @param target      the original event target
    /// @return `true` when this flow owns the event
    private static boolean isEventTargetForVirtualFlow(VirtualFlow<?> virtualFlow, EventTarget target) {
        if (!(target instanceof Node node)) {
            return true;
        }

        @Nullable Node current = node;
        while (current != null && current != virtualFlow) {
            if (current instanceof ScrollPane || current instanceof VirtualFlow<?>) {
                return false;
            }
            current = current.getParent();
        }
        return current == virtualFlow;
    }

    /// Returns the scale applied to a platform pixel-unit scroll delta.
    ///
    /// @param event              the event being handled
    /// @param speed              the configured distance multiplier
    /// @param trackPadAdjustment the configured small-delta divisor
    /// @return the calibrated pixel scale
    static double pixelScrollScale(ScrollEvent event, double speed, double trackPadAdjustment) {
        double scale = speed * PIXEL_SCROLL_MULTIPLIER;
        double dominantDelta = Math.max(Math.abs(event.getDeltaX()), Math.abs(event.getDeltaY()));
        return dominantDelta > EPSILON && dominantDelta < 10.0 ? scale / trackPadAdjustment : scale;
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

    private ScrollUtils() {
    }
}
