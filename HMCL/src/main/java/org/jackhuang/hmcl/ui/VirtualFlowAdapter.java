// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Scrolls virtualized cells along their layout axis and preserves cross-axis pixel scrolling.
@NotNullByDefault
final class VirtualFlowAdapter extends ScrollAdapter {
    /// Flow providing pixel scrolling and normalized boundary positions.
    private final VirtualFlow<?> flow;

    /// Wraps native bubbling so cell controls and nested scroll panes retain priority.
    VirtualFlowAdapter(VirtualFlow<?> flow) {
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
            controller.onScroll(event);
        });
        flow.positionProperty().addListener(observable -> controller.positionChanged());
        flow.verticalProperty().addListener(observable -> controller.reset());
        flow.cellCountProperty().addListener(observable -> controller.reset());
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
            if (controller.gesture.isDuplicateDrag(event)) {
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
            previousX = controller.gesture.pannedX;
            previousY = controller.gesture.pannedY;
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
            controller.gesture.pannedX = previousX + (flow.isVertical() ? cross : length);
            controller.gesture.pannedY = previousY + (flow.isVertical() ? length : cross);
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

    /// Preserves horizontal-list trackpad mapping while direct touch follows the physical finger axes.
    @Override
    double gestureDelta(ScrollEvent event, boolean horizontal) {
        if (horizontal && !flow.isVertical() && !event.isDirect())
            return -(Math.abs(event.getDeltaX()) > Math.abs(event.getDeltaY()) ? event.getDeltaX() : event.getDeltaY());
        return super.gestureDelta(event, horizontal);
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
