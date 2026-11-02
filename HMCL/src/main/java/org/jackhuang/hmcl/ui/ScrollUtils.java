// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Scrolls gestures synchronously and smooths discrete wheel input without synthesizing flings.
@NotNullByDefault
final class ScrollUtils {
    /// Identifies the controller installed on a node, making installation idempotent.
    private static final Object CONTROLLER_KEY = new Object();

    /// Material 3 short3 duration, in nanoseconds, for discrete wheel movement.
    private static final long WHEEL_DURATION = 150_000_000L;

    /// Material 3 standard decelerate easing for an immediately responsive wheel step.
    private static final Interpolator WHEEL_EASING = Interpolator.SPLINE(0, 0, 0, 1);

    /// Maximum native inertia tail, in nanoseconds; active gestures are never attenuated.
    private static final long INERTIA_DURATION = 200_000_000L;

    /// Prevents construction of this utility class.
    private ScrollUtils() {
    }

    /// Installs smooth wheel scrolling and direct pixel-based gestures on the FX thread.
    public static void addSmoothScrolling(ScrollPane scrollPane) {
        if (!scrollPane.getProperties().containsKey(CONTROLLER_KEY))
            scrollPane.getProperties().put(CONTROLLER_KEY, new PaneController(scrollPane));
    }

    /// Installs scrolling on either flow orientation, preserving native cross-axis scrolling.
    public static void addSmoothScrolling(VirtualFlow<?> virtualFlow) {
        if (!virtualFlow.getProperties().containsKey(CONTROLLER_KEY))
            virtualFlow.getProperties().put(CONTROLLER_KEY, new FlowController(virtualFlow));
    }

    /// Coordinates gesture lifecycles and a short, time-based wheel animation.
    @NotNullByDefault
    private abstract static class Controller extends AnimationTimer implements EventDispatcher {
        /// Original dispatcher, including skin filters that must not duplicate touchscreen movement.
        private final EventDispatcher dispatcher;

        /// Unapplied horizontal wheel distance in pixels.
        private double pendingX;

        /// Unapplied vertical wheel distance in pixels.
        private double pendingY;

        /// Beginning of the current wheel interpolation, or zero while stopped.
        private long wheelStart;

        /// Eased fraction already applied to the current wheel interpolation.
        private double wheelProgress;

        /// Whether the platform has started a continuous gesture.
        private boolean gesture;

        /// Prevents synthesized mouse drags from repeating an active touchscreen scroll.
        private boolean directGesture;

        /// Whether a press has cancelled the previous gesture's inertia.
        private boolean suppressInertia;

        /// First inertia event's time, or zero before a tail begins.
        private long inertiaStart;

        /// Distinguishes controller writes from external position changes.
        boolean updating;

        /// Installs interruption and lifecycle listeners without consuming input.
        Controller(Node node) {
            dispatcher = Objects.requireNonNull(node.getEventDispatcher());
            node.setEventDispatcher(this);
            node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> interrupt());
            node.addEventFilter(TouchEvent.TOUCH_PRESSED, event -> interrupt());
            node.addEventFilter(KeyEvent.KEY_PRESSED, event -> interrupt());
            node.sceneProperty().addListener(observable -> reset());
            node.visibleProperty().addListener(observable -> reset());
        }

        /// Suppresses duplicate touch-generated drags before any native panning filter runs.
        @Override
        public final @Nullable Event dispatchEvent(Event event, EventDispatchChain tail) {
            if (directGesture && event instanceof MouseEvent mouse
                    && mouse.getEventType() == MouseEvent.MOUSE_DRAGGED && mouse.isSynthesized()) {
                event.consume();
                return event;
            }
            return dispatcher.dispatchEvent(event, tail);
        }

        /// Returns the scrollable pixel distance available in the requested direction.
        abstract double limit(boolean horizontal, double pixels);

        /// Applies a bounded pixel displacement and returns the distance actually moved.
        abstract double scroll(boolean horizontal, double pixels);

        /// Returns a wheel displacement in pixels, respecting page scroll settings.
        abstract double wheelDelta(ScrollEvent event, boolean horizontal);

        /// Handles gestures before the native skin, consuming only accepted movement or inertia.
        final void onScroll(ScrollEvent event) {
            if (event.isConsumed())
                return;
            if (event.getEventType() == ScrollEvent.SCROLL_STARTED) {
                reset();
                gesture = true;
                return;
            }
            if (event.getEventType() == ScrollEvent.SCROLL_FINISHED) {
                gesture = false;
                return;
            }
            if (event.getEventType() != ScrollEvent.SCROLL)
                return;

            long now = System.nanoTime();
            if (event.isDirect() && !event.isInertia())
                directGesture = true;
            // Ordinary wheels can report NONE units or fractional text deltas after display scaling.
            // Only explicit gesture signals bypass animation; unmarked input uses the wheel fallback.
            boolean precise = gesture || event.isDirect() || event.getTouchCount() > 0 || event.isInertia();
            double dx = precise ? -event.getDeltaX() : wheelDelta(event, true);
            double dy = precise ? -event.getDeltaY() : wheelDelta(event, false);
            if (!Double.isFinite(dx) || !Double.isFinite(dy))
                return;

            if (event.isInertia()) {
                stopWheel();
                if (inertiaStart == 0)
                    inertiaStart = now;
                double remaining = 1 - (double) (now - inertiaStart) / INERTIA_DURATION;
                if (suppressInertia || remaining <= 0) {
                    // Do not let a native skin or ancestor restart a cancelled tail.
                    event.consume();
                    return;
                }
                dx *= remaining * remaining;
                dy *= remaining * remaining;
            } else {
                suppressInertia = false;
                inertiaStart = 0;
            }

            if (precise) {
                stopWheel();
                boolean moved = move(true, dx) != 0;
                moved |= move(false, dy) != 0;
                if (moved || event.isInertia())
                    event.consume();
            } else {
                if (wheelStart != 0)
                    handle(now);
                double x = limit(true, dx);
                double y = limit(false, dy);
                if (x == 0 && y == 0)
                    return;
                pendingX = accumulate(pendingX, x, true);
                pendingY = accumulate(pendingY, y, false);
                wheelStart = now;
                wheelProgress = 0;
                start();
                event.consume();
            }
        }

        /// Drops old momentum on reversal and keeps queued movement within the available range.
        private double accumulate(double pending, double delta, boolean horizontal) {
            if (delta != 0 && Math.signum(pending) != Math.signum(delta))
                pending = 0;
            return limit(horizontal, pending + delta);
        }

        /// Applies this frame's eased fraction, ending exactly at the requested displacement.
        @Override
        public final void handle(long now) {
            if (wheelStart == 0)
                return;
            double fraction = Math.max(0, Math.min(1, (double) (now - wheelStart) / WHEEL_DURATION));
            double progress = WHEEL_EASING.interpolate(0.0, 1.0, fraction);
            double step = (progress - wheelProgress) / (1 - wheelProgress);
            pendingX = advance(true, pendingX, step);
            pendingY = advance(false, pendingY, step);
            wheelProgress = progress;
            if (fraction >= 1 || (pendingX == 0 && pendingY == 0))
                stopWheel();
        }

        /// Removes applied distance and discards any remainder that hits a boundary.
        private double advance(boolean horizontal, double pending, double fraction) {
            double requested = pending * fraction;
            double actual = move(horizontal, requested);
            if (Math.abs(actual - requested) > 0.01)
                return 0;
            return limit(horizontal, pending - requested);
        }

        /// Marks position changes so that external-scroll listeners do not cancel our own updates.
        private double move(boolean horizontal, double pixels) {
            if (pixels == 0)
                return 0;
            updating = true;
            try {
                return scroll(horizontal, pixels);
            } finally {
                updating = false;
            }
        }

        /// Cancels queued movement and prevents old native inertia from resuming after a press.
        private void interrupt() {
            stopWheel();
            suppressInertia = true;
            gesture = false;
            directGesture = false;
        }

        /// Clears gesture state when the scrolling surface changes.
        final void reset() {
            interrupt();
            inertiaStart = 0;
        }

        /// Cancels animation after an external position change, such as scrollbar dragging.
        final void positionChanged() {
            if (!updating)
                interrupt();
        }

        /// Releases the pulse timer and all unapplied wheel movement.
        private void stopWheel() {
            stop();
            pendingX = 0;
            pendingY = 0;
            wheelStart = 0;
            wheelProgress = 0;
        }
    }

    /// Maps pixel distances onto a scroll pane's actual overflow and value ranges.
    @NotNullByDefault
    private static final class PaneController extends Controller {
        /// Pane whose content and viewport determine the scroll range.
        private final ScrollPane pane;

        /// Stable handler used when content is replaced or removed.
        private final EventHandler<ScrollEvent> scrollHandler = this::handleScroll;

        /// Installs bubbling on content so nested controls receive input first.
        PaneController(ScrollPane pane) {
            super(pane);
            this.pane = pane;
            attach(null, pane.getContent());
            pane.contentProperty().addListener((ObservableValue<? extends Node> observable,
                                               @Nullable Node oldContent, @Nullable Node newContent) -> attach(oldContent, newContent));
            pane.viewportBoundsProperty().addListener((ObservableValue<? extends Bounds> observable,
                                                       @Nullable Bounds oldBounds, @Nullable Bounds newBounds) -> {
                // Layout also replaces these bounds when only the content offset changes.
                if (oldBounds == null || newBounds == null
                        || oldBounds.getWidth() != newBounds.getWidth()
                        || oldBounds.getHeight() != newBounds.getHeight())
                    reset();
            });
            pane.hvalueProperty().addListener(observable -> positionChanged());
            pane.vvalueProperty().addListener(observable -> positionChanged());
            pane.addEventFilter(ScrollEvent.ANY, event -> {
                // Empty viewport and scrollbar targets do not pass through the content node.
                @Nullable Node content = pane.getContent();
                @Nullable Node target = event.getTarget() instanceof Node n ? n : null;
                while (target != null && target != content && target != pane)
                    target = target.getParent();
                if (content == null || target != content)
                    handleScroll(event);
            });
        }

        /// Passes unused input above the skin, which otherwise overscrolls on touch-enabled systems.
        private void handleScroll(ScrollEvent event) {
            onScroll(event);
            if (event.getEventType() == ScrollEvent.SCROLL && !event.isConsumed()) {
                @Nullable Node parent = pane.getParent();
                ScrollEvent forwarded = event.copyFor(parent, parent);
                event.consume();
                if (parent != null)
                    parent.fireEvent(forwarded);
            }
        }

        /// Moves the handler to the current content, without retaining detached content nodes.
        private void attach(@Nullable Node oldContent, @Nullable Node newContent) {
            reset();
            if (oldContent != null)
                oldContent.removeEventHandler(ScrollEvent.ANY, scrollHandler);
            if (newContent != null)
                newContent.addEventHandler(ScrollEvent.ANY, scrollHandler);
        }

        /// Returns overflow measured in the same layout coordinates as gesture pixel deltas.
        private double overflow(boolean horizontal) {
            @Nullable Node content = pane.getContent();
            if (content == null)
                return 0;
            return Math.max(0, horizontal
                    ? content.getLayoutBounds().getWidth() - pane.getViewportBounds().getWidth()
                    : content.getLayoutBounds().getHeight() - pane.getViewportBounds().getHeight());
        }

        /// Clamps to the distance before an edge, including non-default scroll value ranges.
        @Override
        double limit(boolean horizontal, double pixels) {
            DoubleProperty value = horizontal ? pane.hvalueProperty() : pane.vvalueProperty();
            double min = horizontal ? pane.getHmin() : pane.getVmin();
            double max = horizontal ? pane.getHmax() : pane.getVmax();
            double size = overflow(horizontal);
            if (value.isBound() || !(max > min) || !(size > 0))
                return 0;
            double position = Math.max(0, Math.min(size, (value.get() - min) / (max - min) * size));
            return Math.max(-position, Math.min(size - position, pixels));
        }

        /// Scrolls both axes independently without assuming normalized values or overflowing content.
        @Override
        double scroll(boolean horizontal, double pixels) {
            double distance = limit(horizontal, pixels);
            if (distance == 0)
                return 0;
            double min = horizontal ? pane.getHmin() : pane.getVmin();
            double max = horizontal ? pane.getHmax() : pane.getVmax();
            DoubleProperty value = horizontal ? pane.hvalueProperty() : pane.vvalueProperty();
            value.set(Math.max(min, Math.min(max, value.get() + distance / overflow(horizontal) * (max - min))));
            return distance;
        }

        /// Uses platform pixel deltas except for explicitly requested page scrolling.
        @Override
        double wheelDelta(ScrollEvent event, boolean horizontal) {
            if (!horizontal && event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.PAGES)
                return -event.getTextDeltaY() * pane.getViewportBounds().getHeight();
            return horizontal ? -event.getDeltaX() : -event.getDeltaY();
        }
    }

    /// Scrolls virtualized cells along their layout axis and preserves cross-axis pixel scrolling.
    @NotNullByDefault
    private static final class FlowController extends Controller {
        /// Flow providing pixel scrolling and normalized boundary positions.
        private final VirtualFlow<?> flow;

        /// Wraps native bubbling so cell controls and nested scroll panes retain priority.
        FlowController(VirtualFlow<?> flow) {
            super(flow);
            this.flow = flow;
            @Nullable EventHandler<? super ScrollEvent> nativeHandler = flow.getOnScroll();
            flow.setOnScroll(event -> {
                // The touch skin must reveal its bars before scrollPixels can move cells.
                // A zero-distance event preserves its visibility lifecycle without scrolling twice.
                if (nativeHandler != null && !event.isInertia()) {
                    nativeHandler.handle(new ScrollEvent(ScrollEvent.SCROLL,
                            event.getX(), event.getY(), event.getScreenX(), event.getScreenY(),
                            event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown(),
                            event.isDirect(), false, 0, 0, 0, 0,
                            ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                            ScrollEvent.VerticalTextScrollUnits.NONE, 0, event.getTouchCount(), event.getPickResult()));
                }
                onScroll(event);
            });
            flow.addEventHandler(ScrollEvent.SCROLL_STARTED, this::onScroll);
            flow.addEventHandler(ScrollEvent.SCROLL_FINISHED, this::onScroll);
            flow.positionProperty().addListener(observable -> positionChanged());
            flow.verticalProperty().addListener(observable -> reset());
            flow.cellCountProperty().addListener(observable -> reset());
        }

        /// Finds the flow's own scrollbar on the requested axis.
        private @Nullable ScrollBar bar(boolean horizontal) {
            Orientation orientation = horizontal ? Orientation.HORIZONTAL : Orientation.VERTICAL;
            for (Node child : flow.getChildrenUnmodifiable()) {
                if (child instanceof ScrollBar bar && bar.getOrientation() == orientation)
                    return bar;
            }
            return null;
        }

        /// Measures the clipping surface, including platforms with overlay scrollbars.
        private double viewportLength(boolean horizontal) {
            for (Node child : flow.getChildrenUnmodifiable()) {
                if (child.getStyleClass().contains("clipped-container"))
                    return horizontal ? child.getLayoutBounds().getWidth() : child.getLayoutBounds().getHeight();
            }
            return horizontal ? flow.getWidth() : flow.getHeight();
        }

        /// Bounds queued motion to one viewport and rejects movement beyond either end.
        @Override
        double limit(boolean horizontal, double pixels) {
            if (horizontal == flow.isVertical()) {
                @Nullable ScrollBar bar = bar(horizontal);
                @Nullable IndexedCell<?> cell = flow.getFirstVisibleCell();
                if (bar == null || cell == null || bar.valueProperty().isBound())
                    return 0;
                double extent = (horizontal ? cell.getWidth() : cell.getHeight()) - viewportLength(horizontal);
                double max = bar.getMin() + Math.max(0, extent);
                if (bar.maxProperty().isBound())
                    max = Math.min(max, bar.getMax());
                return Math.max(bar.getMin() - bar.getValue(), Math.min(max - bar.getValue(), pixels));
            }
            if (flow.getCellCount() == 0 || flow.positionProperty().isBound()
                    || (pixels < 0 && flow.getPosition() <= 0)
                    || (pixels > 0 && flow.getPosition() >= 1))
                return 0;
            double size = viewportLength(horizontal);
            @Nullable IndexedCell<?> last = flow.getLastVisibleCell();
            if (pixels > 0 && last != null && last.getIndex() == flow.getCellCount() - 1) {
                double end = horizontal ? last.getLayoutX() + last.getWidth() : last.getLayoutY() + last.getHeight();
                if (end <= size)
                    return 0;
            }
            size = Math.max(0, size);
            return Math.max(-size, Math.min(size, pixels));
        }

        /// Applies synchronous gesture pixels through the flow or its breadth scrollbar.
        @Override
        double scroll(boolean horizontal, double pixels) {
            if (horizontal == flow.isVertical()) {
                @Nullable ScrollBar bar = bar(horizontal);
                double distance = limit(horizontal, pixels);
                if (bar != null && distance != 0) {
                    // Hidden touch bars have not necessarily received their range from layout yet.
                    if (bar.getValue() + distance > bar.getMax() && !bar.maxProperty().isBound())
                        bar.setMax(bar.getValue() + distance);
                    bar.setValue(bar.getValue() + distance);
                }
                return distance;
            }
            if (limit(horizontal, pixels) == 0)
                return 0;
            @Nullable ScrollBar bar = bar(horizontal);
            // The native reveal animation starts on the next pulse; preserve the first touch delta too.
            if (bar != null && !bar.isVisible() && !bar.visibleProperty().isBound())
                bar.setVisible(true);
            return flow.scrollPixels(pixels);
        }

        /// Respects system line/page settings for wheels without scaling touch gestures by cell size.
        @Override
        double wheelDelta(ScrollEvent event, boolean horizontal) {
            if (horizontal) {
                if (flow.isVertical())
                    return -event.getDeltaX();
                return -(Math.abs(event.getDeltaX()) > Math.abs(event.getDeltaY()) ? event.getDeltaX() : event.getDeltaY());
            }
            if (!flow.isVertical())
                return 0;
            return switch (event.getTextDeltaYUnits()) {
                case NONE -> -event.getDeltaY();
                case PAGES -> -event.getTextDeltaY() * flow.getHeight();
                case LINES -> {
                    @Nullable IndexedCell<?> cell = flow.getFirstVisibleCell();
                    double line = flow.getFixedCellSize() > 0 ? flow.getFixedCellSize()
                            : cell == null ? 16 : cell.getHeight();
                    yield -event.getTextDeltaY() * Math.min(line, flow.getHeight() / 8);
                }
            };
        }
    }
}
