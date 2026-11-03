// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.animation.AnimationTimer;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
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
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Scrolls gestures synchronously and smooths discrete wheel input without synthesizing flings.
@NotNullByDefault
final class ScrollUtils {
    /// Identifies the controller installed on a node, making installation idempotent.
    private static final Object CONTROLLER_KEY = new Object();

    /// Whether native skins need zero-distance input to animate overlay scrollbar visibility.
    private static final boolean TOUCH_SUPPORTED = Platform.isSupported(ConditionalFeature.INPUT_TOUCH);

    /// Material 3 short3 duration, in nanoseconds, for discrete wheel movement.
    private static final long WHEEL_DURATION = 150_000_000L;

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

    /// Preserves a skin's scroll lifecycle without applying its native displacement.
    private static ScrollEvent stationaryScroll(ScrollEvent event) {
        return new ScrollEvent(event.getSource(), event.getTarget(), event.getEventType(),
                event.getX(), event.getY(), event.getScreenX(), event.getScreenY(),
                event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown(),
                event.isDirect(), event.isInertia(), 0, 0, 0, 0,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, event.getTouchCount(), event.getPickResult());
    }

    /// Shares the native inertia age across nested scrolling surfaces.
    @NotNullByDefault
    private static final class Inertia {
        /// Time of the first accepted inertia movement, or zero before the tail starts.
        private long start;
    }

    /// Coordinates gesture lifecycles and a short, time-based wheel animation.
    @NotNullByDefault
    private abstract static class Controller extends AnimationTimer {
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

        /// Horizontal native panning already included in the first direct scroll delta.
        double pannedX;

        /// Vertical native panning already included in the first direct scroll delta.
        double pannedY;

        /// Horizontal panning eligible for deduction by the currently dispatched direct event only.
        private double eventPannedX;

        /// Vertical panning eligible for deduction by the currently dispatched direct event only.
        private double eventPannedY;

        /// Whether a press has cancelled the previous gesture's inertia.
        private boolean suppressInertia;

        /// Inertia timing shared with ancestor controllers for the current gesture.
        private Inertia inertia = new Inertia();

        /// Whether the current inertia state belongs to an ancestor controller.
        private boolean inheritedInertia;

        /// Distinguishes controller writes from external position changes.
        boolean updating;

        /// Installs interruption and lifecycle listeners without consuming input.
        Controller(Node node) {
            EventDispatcher dispatcher = Objects.requireNonNull(node.getEventDispatcher());
            node.setEventDispatcher((event, tail) -> {
                if (!(event instanceof ScrollEvent scroll) || scroll.getEventType() != ScrollEvent.SCROLL
                        || !scroll.isDirect() || scroll.isInertia())
                    return dispatcher.dispatchEvent(event, tail);
                double previousX = eventPannedX;
                double previousY = eventPannedY;
                eventPannedX = pannedX;
                eventPannedY = pannedY;
                clearPanning();
                try {
                    return dispatcher.dispatchEvent(event, tail);
                } finally {
                    // Consumption by descendants must expire credit too; nested dispatch keeps its own scope.
                    eventPannedX = previousX;
                    eventPannedY = previousY;
                }
            });
            node.addEventFilter(ScrollEvent.ANY, event -> observeScroll(node, event));
            node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> interrupt());
            node.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> clearPanning());
            node.addEventFilter(TouchEvent.TOUCH_PRESSED, event -> interrupt());
            node.addEventFilter(KeyEvent.KEY_PRESSED, event -> interrupt());
            node.sceneProperty().addListener(observable -> reset());
            node.visibleProperty().addListener(observable -> reset());
        }

        /// Observes gesture boundaries even when a descendant consumes every movement event.
        private void observeScroll(Node node, ScrollEvent event) {
            if (event.getEventType() == ScrollEvent.SCROLL_STARTED) {
                // Recognition can follow native mouse panning from the same touch press.
                double x = pannedX;
                double y = pannedY;
                reset();
                pannedX = x;
                pannedY = y;
                gesture = true;
            } else if (event.getEventType() == ScrollEvent.SCROLL_FINISHED) {
                gesture = false;
                clearPanning();
            }
            if (event.getEventType() == ScrollEvent.SCROLL) {
                // Descendants may consume the event before this controller's bubbling handler runs.
                if (event.isDirect() && !event.isInertia())
                    directGesture = true;
                if (wheelStart != 0) {
                    if (isPrecise(event)) {
                        stopWheel();
                    } else {
                        double dx = wheelDelta(event, true);
                        double dy = wheelDelta(event, false);
                        if (Double.isFinite(dx) && Double.isFinite(dy)) {
                            pendingX = cancelReversal(pendingX, dx);
                            pendingY = cancelReversal(pendingY, dy);
                            if (pendingX == 0 && pendingY == 0)
                                stopWheel();
                        }
                    }
                }
            }
            if (event.getEventType() == ScrollEvent.SCROLL_STARTED
                    || (event.getEventType() == ScrollEvent.SCROLL && !event.isInertia())) {
                suppressInertia = false;
                // Capture visits ancestors first, so all controllers inherit the same fresh tail.
                for (@Nullable Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
                    if (parent.hasProperties() && parent.getProperties().get(CONTROLLER_KEY) instanceof Controller controller) {
                        inertia = controller.inertia;
                        inheritedInertia = true;
                        return;
                    }
                }
                if (inheritedInertia || inertia.start != 0)
                    inertia = new Inertia();
                inheritedInertia = false;
            }
        }

        /// Identifies mouse drags that would repeat a direct touchscreen scroll.
        final boolean isDuplicateDrag(Event event) {
            return directGesture && event instanceof MouseEvent mouse
                    && mouse.getEventType() == MouseEvent.MOUSE_DRAGGED && mouse.isSynthesized();
        }

        /// Distinguishes continuous gestures from wheels, including unmarked fractional wheel input.
        private boolean isPrecise(ScrollEvent event) {
            return gesture || event.isDirect() || event.getTouchCount() > 0 || event.isInertia();
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
            if (event.getEventType() != ScrollEvent.SCROLL)
                return;

            long now = System.nanoTime();
            // Ordinary wheels can report NONE units or fractional text deltas after display scaling.
            // Only explicit gesture signals bypass animation; unmarked input uses the wheel fallback.
            boolean precise = isPrecise(event);
            double dx = precise ? -event.getDeltaX() : wheelDelta(event, true);
            double dy = precise ? -event.getDeltaY() : wheelDelta(event, false);
            if (!Double.isFinite(dx) || !Double.isFinite(dy))
                return;

            boolean panned = false;
            if (event.isDirect() && !event.isInertia()) {
                panned = eventPannedX != 0 || eventPannedY != 0;
                dx -= eventPannedX;
                dy -= eventPannedY;
                eventPannedX = 0;
                eventPannedY = 0;
            }
            clearPanning();

            if (event.isInertia()) {
                stopWheel();
                double remaining = inertia.start == 0 ? 1 : 1 - (double) (now - inertia.start) / INERTIA_DURATION;
                if (suppressInertia || remaining <= 0) {
                    // Do not let a native skin or ancestor restart a cancelled tail.
                    event.consume();
                    return;
                }
                dx *= remaining * remaining;
                dy *= remaining * remaining;
            }

            if (precise) {
                stopWheel();
                boolean moved = panned;
                moved |= move(true, dx) != 0;
                moved |= move(false, dy) != 0;
                if (moved) {
                    if (event.isInertia() && inertia.start == 0)
                        inertia.start = now;
                    event.consume();
                }
            } else {
                double x = limit(true, pendingX + dx);
                double y = limit(false, pendingY + dy);
                // Queued movement already reserves its space before the next pulse updates the position.
                if (x == pendingX && y == pendingY)
                    return;
                pendingX = x;
                pendingY = y;
                boolean running = wheelStart != 0;
                wheelStart = now;
                wheelProgress = 0;
                // Coalesce wheel input until the next pulse, especially for expensive virtual flows.
                if (!running)
                    start();
                event.consume();
            }
        }

        /// Drops queued movement opposing raw input, even when an edge prevents new displacement.
        private static double cancelReversal(double pending, double delta) {
            return delta != 0 && Math.signum(pending) != Math.signum(delta) ? 0 : pending;
        }

        /// Applies this frame's eased fraction, ending exactly at the requested displacement.
        @Override
        public final void handle(long now) {
            if (wheelStart == 0)
                return;
            double fraction = MathUtils.clamp((double) (now - wheelStart) / WHEEL_DURATION, 0, 1);
            // Preserve exact endpoints despite the shared curve's approximate solver.
            double progress = fraction == 0 || fraction == 1 ? fraction
                    : Motion.STANDARD_DECELERATE.interpolate(0.0, 1.0, fraction);
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
            clearPanning();
            suppressInertia = true;
            gesture = false;
            directGesture = false;
        }

        /// Discards native displacement that cannot belong to a later direct gesture.
        private void clearPanning() {
            pannedX = 0;
            pannedY = 0;
        }

        /// Clears gesture state when the scrolling surface changes.
        final void reset() {
            interrupt();
            inertia = new Inertia();
            inheritedInertia = false;
        }

        /// Cancels animation after an external position change, such as scrollbar dragging.
        final void positionChanged() {
            if (!updating)
                interrupt();
        }

        /// Releases the pulse timer and all unapplied wheel movement.
        private void stopWheel() {
            if (wheelStart == 0)
                return;
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

        /// Current skin viewport, whose bubbling dispatch controls native scrolling.
        private @Nullable Node viewport;

        /// Original viewport dispatcher restored when its skin is replaced.
        private @Nullable EventDispatcher viewportDispatcher;

        /// Installed wrapper, retained to avoid replacing a later application dispatcher.
        private @Nullable EventDispatcher viewportWrapper;

        /// Stops duplicate panning after child controls have handled synthesized drags.
        private final EventHandler<MouseEvent> dragHandler = event -> {
            if (isDuplicateDrag(event))
                event.consume();
        };

        /// Installs bubbling on content so nested controls receive input first.
        PaneController(ScrollPane pane) {
            super(pane);
            this.pane = pane;
            attach(null, pane.getContent());
            attachViewport();
            pane.skinProperty().addListener(observable -> attachViewport());
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
            pane.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
                if (!isDuplicateDrag(event))
                    return;
                // Empty viewport targets bypass the content's bubbling handler.
                @Nullable Node content = pane.getContent();
                @Nullable Node target = event.getTarget() instanceof Node n ? n : null;
                while (target != null && target != content && target != pane) {
                    if (target instanceof ScrollBar)
                        return;
                    target = target.getParent();
                }
                if (content == null || target != content)
                    event.consume();
            });
            pane.addEventHandler(ScrollEvent.SCROLL, event -> {
                // Viewport input was already handled; direct scrollbar input belongs to its skin.
                @Nullable Node target = event.getTarget() instanceof Node n ? n : null;
                while (target != null && target != pane) {
                    if (target == viewport || (event.isDirect() && target instanceof ScrollBar))
                        return;
                    target = target.getParent();
                }
                onScroll(event);
            });
        }

        /// Preserves native scrollbar visibility while unused input continues its original bubbling route.
        private void attachViewport() {
            reset();
            if (viewport != null && viewport.getEventDispatcher() == viewportWrapper)
                viewport.setEventDispatcher(viewportDispatcher);
            viewport = null;
            viewportDispatcher = null;
            viewportWrapper = null;
            for (Node child : pane.getChildrenUnmodifiable()) {
                if (!child.getStyleClass().contains("viewport"))
                    continue;
                EventDispatcher dispatcher = Objects.requireNonNull(child.getEventDispatcher());
                EventDispatcher wrapper = new ViewportDispatcher(dispatcher);
                viewport = child;
                viewportDispatcher = dispatcher;
                viewportWrapper = wrapper;
                child.setEventDispatcher(wrapper);
                break;
            }
        }

        /// Reuses a private tail adapter without inserting controller references into JavaFX's cached chain.
        @NotNullByDefault
        private final class ViewportDispatcher implements EventDispatcher, EventDispatchChain {
            /// Original viewport dispatcher, including native capture filters and bubbling handlers.
            private final EventDispatcher dispatcher;

            /// Active child chain, cleared as soon as dispatch completes.
            private @Nullable EventDispatchChain tail;

            /// Original unused event to resume bubbling above the viewport.
            private @Nullable ScrollEvent unused;

            /// Horizontal offset after child dispatch, or NaN if the child consumed the drag.
            private double panStartX;

            /// Vertical offset immediately before native viewport bubbling.
            private double panStartY;

            /// Wraps the viewport's native dispatch while preserving its capture phase.
            private ViewportDispatcher(EventDispatcher dispatcher) {
                this.dispatcher = dispatcher;
            }

            /// Coordinates scroll and native panning with isolated state for reentrant dispatch.
            @Override
            public @Nullable Event dispatchEvent(Event event, EventDispatchChain tail) {
                boolean panning = event instanceof MouseEvent mouse && mouse.isSynthesized()
                        && mouse.getEventType() == MouseEvent.MOUSE_DRAGGED && !isDuplicateDrag(mouse);
                if (!panning && (!(event instanceof ScrollEvent) || event.getEventType() != ScrollEvent.SCROLL))
                    return dispatcher.dispatchEvent(event, tail);
                if (this.tail != null)
                    return new ViewportDispatcher(dispatcher).dispatchEvent(event, tail);
                this.tail = tail;
                double previousX = pannedX;
                double previousY = pannedY;
                panStartX = Double.NaN;
                try {
                    @Nullable Event result = dispatcher.dispatchEvent(event, this);
                    if (panning) {
                        if (!Double.isNaN(panStartX)) {
                            // Position listeners interrupt animation during native panning; retain its credit.
                            pannedX = previousX + position(true) - panStartX;
                            pannedY = previousY + position(false) - panStartY;
                        }
                        return result;
                    }
                    return unused;
                } finally {
                    this.tail = null;
                    unused = null;
                }
            }

            /// Gives descendants priority, then measures native panning or replaces native scrolling.
            @Override
            public @Nullable Event dispatchEvent(Event event) {
                @Nullable Event result = Objects.requireNonNull(tail).dispatchEvent(event);
                if (result instanceof MouseEvent) {
                    panStartX = position(true);
                    panStartY = position(false);
                    return result;
                }
                if (!(result instanceof ScrollEvent scroll))
                    return result;
                onScroll(scroll);
                if (!scroll.isConsumed())
                    unused = scroll;
                return TOUCH_SUPPORTED ? stationaryScroll(scroll) : null;
            }

            /// Appends a caller-supplied dispatcher while keeping this adapter around the child chain.
            @Override
            public EventDispatchChain append(EventDispatcher dispatcher) {
                tail = Objects.requireNonNull(tail).append(dispatcher);
                return this;
            }

            /// Prepends a caller-supplied dispatcher while keeping this adapter around the child chain.
            @Override
            public EventDispatchChain prepend(EventDispatcher dispatcher) {
                tail = Objects.requireNonNull(tail).prepend(dispatcher);
                return this;
            }
        }

        /// Moves the handler to the current content, without retaining detached content nodes.
        private void attach(@Nullable Node oldContent, @Nullable Node newContent) {
            reset();
            if (oldContent != null)
                oldContent.removeEventHandler(MouseEvent.MOUSE_DRAGGED, dragHandler);
            if (newContent != null)
                newContent.addEventHandler(MouseEvent.MOUSE_DRAGGED, dragHandler);
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

        /// Measures the pane's current displacement in content pixels, clamped to its overflow.
        private double position(boolean horizontal) {
            double min = horizontal ? pane.getHmin() : pane.getVmin();
            double max = horizontal ? pane.getHmax() : pane.getVmax();
            double value = horizontal ? pane.getHvalue() : pane.getVvalue();
            double size = overflow(horizontal);
            return max > min ? MathUtils.clamp((value - min) / (max - min) * size, 0, size) : 0;
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
            double position = MathUtils.clamp((value.get() - min) / (max - min) * size, 0, size);
            return MathUtils.clamp(pixels, -position, size - position);
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
            value.set(MathUtils.clamp(value.get() + distance / overflow(horizontal) * (max - min), min, max));
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
            flow.setEventDispatcher(new FlowDispatcher(Objects.requireNonNull(flow.getEventDispatcher())));
            @Nullable EventHandler<? super ScrollEvent> nativeHandler = flow.getOnScroll();
            flow.setOnScroll(event -> {
                // The touch skin must reveal its bars before scrollPixels can move cells.
                // A zero-distance event preserves its visibility lifecycle without scrolling twice.
                if (TOUCH_SUPPORTED && nativeHandler != null && !event.isInertia()) {
                    nativeHandler.handle(stationaryScroll(event));
                }
                onScroll(event);
            });
            flow.positionProperty().addListener(observable -> positionChanged());
            flow.verticalProperty().addListener(observable -> reset());
            flow.cellCountProperty().addListener(observable -> reset());
        }

        /// Measures native capture-phase panning before descendants can change the flow themselves.
        @NotNullByDefault
        private final class FlowDispatcher implements EventDispatcher, EventDispatchChain {
            /// Native flow dispatcher, including its panning filters.
            private final EventDispatcher dispatcher;

            /// Active child chain, never inserted into JavaFX's reusable dispatch chain.
            private @Nullable EventDispatchChain tail;

            /// Whether native panning still needs to be measured for this dispatch.
            private boolean recording;

            /// Index of the first visible cell before native panning, or -1 for an empty flow.
            private int firstIndex;

            /// Layout position of the first visible cell before native panning.
            private double firstPosition;

            /// Breadth scrollbar value before native panning.
            private double breadthPosition;

            /// Horizontal panning credit preserved across native position-change notifications.
            private double previousX;

            /// Vertical panning credit preserved across native position-change notifications.
            private double previousY;

            /// Wraps native dispatch without changing ordinary mouse or wheel input.
            private FlowDispatcher(EventDispatcher dispatcher) {
                this.dispatcher = dispatcher;
            }

            /// Suppresses duplicate drags and measures early synthesized panning with reentrant isolation.
            @Override
            public @Nullable Event dispatchEvent(Event event, EventDispatchChain tail) {
                if (isDuplicateDrag(event)) {
                    tail.dispatchEvent(event);
                    return null;
                }
                if (!(event instanceof MouseEvent mouse) || !mouse.isSynthesized()
                        || mouse.getEventType() != MouseEvent.MOUSE_DRAGGED || !flow.isPannable())
                    return dispatcher.dispatchEvent(event, tail);
                if (this.tail != null)
                    return new FlowDispatcher(dispatcher).dispatchEvent(event, tail);
                @Nullable IndexedCell<?> first = flow.getFirstVisibleCell();
                firstIndex = first == null ? -1 : first.getIndex();
                firstPosition = first == null ? 0 : flow.isVertical() ? first.getLayoutY() : first.getLayoutX();
                @Nullable ScrollBar breadth = bar(flow.isVertical());
                breadthPosition = breadth == null ? 0 : breadth.getValue();
                previousX = pannedX;
                previousY = pannedY;
                this.tail = tail;
                recording = true;
                try {
                    @Nullable Event result = dispatcher.dispatchEvent(event, this);
                    recordPanning();
                    return result;
                } finally {
                    recording = false;
                    this.tail = null;
                }
            }

            /// Records capture-phase displacement before dispatching to cell controls.
            @Override
            public @Nullable Event dispatchEvent(Event event) {
                recordPanning();
                return Objects.requireNonNull(tail).dispatchEvent(event);
            }

            /// Preserves actual pixel movement even when panning recycles variable-size cells.
            private void recordPanning() {
                if (!recording)
                    return;
                recording = false;
                double length = 0;
                @Nullable IndexedCell<?> first = flow.getFirstVisibleCell();
                if (firstIndex >= 0 && first != null) {
                    int index = first.getIndex();
                    length = firstPosition - (flow.isVertical() ? first.getLayoutY() : first.getLayoutX());
                    if (flow.getFixedCellSize() > 0) {
                        length += (index - firstIndex) * flow.getFixedCellSize();
                    } else {
                        int direction = Integer.compare(index, firstIndex);
                        for (int i = Math.min(index, firstIndex); i < Math.max(index, firstIndex); i++) {
                            IndexedCell<?> cell = flow.getCell(i);
                            length += direction * (flow.isVertical() ? cell.getHeight() : cell.getWidth());
                        }
                    }
                }
                @Nullable ScrollBar breadth = bar(flow.isVertical());
                double cross = breadth == null ? 0 : breadth.getValue() - breadthPosition;
                pannedX = previousX + (flow.isVertical() ? cross : length);
                pannedY = previousY + (flow.isVertical() ? length : cross);
            }

            /// Appends a caller's dispatcher without adding this adapter to the shared chain.
            @Override
            public EventDispatchChain append(EventDispatcher dispatcher) {
                tail = Objects.requireNonNull(tail).append(dispatcher);
                return this;
            }

            /// Prepends a caller's dispatcher while retaining the capture-phase measurement boundary.
            @Override
            public EventDispatchChain prepend(EventDispatcher dispatcher) {
                tail = Objects.requireNonNull(tail).prepend(dispatcher);
                return this;
            }
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

        /// Bounds movement at visible end cells while allowing multi-page queues elsewhere.
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
                return MathUtils.clamp(pixels, bar.getMin() - bar.getValue(), max - bar.getValue());
            }
            if (flow.getCellCount() == 0 || flow.positionProperty().isBound()
                    || (pixels < 0 && flow.getPosition() <= 0)
                    || (pixels > 0 && flow.getPosition() >= 1))
                return 0;
            @Nullable IndexedCell<?> first = flow.getFirstVisibleCell();
            if (pixels < 0 && first != null && first.getIndex() == 0) {
                double start = horizontal ? first.getLayoutX() : first.getLayoutY();
                return Math.max(Math.min(0, start), pixels);
            }
            double size = viewportLength(horizontal);
            @Nullable IndexedCell<?> last = flow.getLastVisibleCell();
            if (pixels > 0 && last != null && last.getIndex() == flow.getCellCount() - 1) {
                double end = horizontal ? last.getLayoutX() + last.getWidth() : last.getLayoutY() + last.getHeight();
                return Math.min(Math.max(0, end - size), pixels);
            }
            return pixels;
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
                return -event.getDeltaY();
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
