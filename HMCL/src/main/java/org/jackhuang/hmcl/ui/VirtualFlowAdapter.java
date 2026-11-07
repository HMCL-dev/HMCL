// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.event.WeakEventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollToEvent;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyEvent;
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

    /// Owning control whose navigation events interrupt queued wheel movement.
    private @Nullable Control owner;

    /// Cancels pending movement before native keyboard or scroll-to navigation.
    private final EventHandler<Event> navigationHandler = event -> controller.interrupt();

    /// Visible edge anchoring the measured offscreen interval, or -1 when invalid.
    private int measuredEdge = -1;

    /// Direction of the measured interval.
    private int measuredDirection;

    /// First index beyond the measured interval.
    private int nextMeasure;

    /// Combined length of measured offscreen cells; no cell references are retained.
    private double measuredLength;

    /// Invalidates measurements if data changes synchronously during cell updates.
    private long measurementRevision;

    /// Distinguishes layout requests caused by measurement from external layout changes.
    private boolean measuring;

    /// Observes layout changes while ignoring requests caused by the reusable measurement cell.
    private final ChangeListener<Boolean> layoutListener = (observable, oldValue, value) -> {
        if (!measuring)
            invalidateMeasurements();
    };

    /// Invalidates cached sizes after data, factory, or geometry changes.
    private final InvalidationListener measurementListener = observable -> {
        if (observable instanceof ObservableValue<?> value)
            value.getValue();
        invalidateMeasurements();
    };

    /// Prevents externally retained item lists from retaining a detached control.
    private final InvalidationListener weakMeasurementListener = new WeakInvalidationListener(measurementListener);

    /// Owner's replaceable items or tree root property.
    private @Nullable ObservableValue<?> dataSource;

    /// Flat item list currently observed for updates, permutations, and replacements.
    private @Nullable ObservableList<?> items;

    /// Tree root currently observed for descendant changes.
    private @Nullable TreeItem<?> root;

    /// Rebinds data listeners when the owner replaces its items or root.
    private final InvalidationListener dataSourceListener = observable -> bindData();

    /// Invalidates tree measurements when descendants change or expand.
    private final EventHandler<TreeItem.TreeModificationEvent<Object>> treeListener = event -> invalidateMeasurements();

    /// Prevents externally retained tree models from retaining a detached control.
    private final EventHandler<TreeItem.TreeModificationEvent<Object>> weakTreeListener = new WeakEventHandler<>(treeListener);

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
        // Native layout revises the estimated scroll position without moving the viewport.
        // Input filters handle interruptions without treating those estimates as external scrolling.
        flow.verticalProperty().addListener(observable -> controller.reset());
        flow.cellCountProperty().addListener(observable -> controller.reset());
        flow.cellCountProperty().addListener(measurementListener);
        flow.verticalProperty().addListener(measurementListener);
        flow.fixedCellSizeProperty().addListener(measurementListener);
        flow.cellFactoryProperty().addListener(measurementListener);
        flow.layoutBoundsProperty().addListener(measurementListener);
        flow.positionProperty().addListener(measurementListener);
        flow.needsLayoutProperty().addListener(layoutListener);
        flow.parentProperty().addListener(observable -> bindOwner());
        flow.sceneProperty().addListener(observable -> bindOwner());
        bindOwner();
    }

    /// Follows the owning control and releases its handlers when the flow is detached.
    private void bindOwner() {
        @Nullable Node parent = flow.getParent();
        while (parent != null && !(parent instanceof Control))
            parent = parent.getParent();
        if (owner == parent)
            return;
        if (owner != null) {
            owner.removeEventFilter(KeyEvent.KEY_PRESSED, navigationHandler);
            owner.removeEventFilter(ScrollToEvent.ANY, navigationHandler);
            owner.needsLayoutProperty().removeListener(layoutListener);
            owner.getProperties().removeListener(measurementListener);
        }
        if (dataSource != null)
            dataSource.removeListener(dataSourceListener);
        owner = (Control) parent;
        if (owner != null) {
            owner.addEventFilter(KeyEvent.KEY_PRESSED, navigationHandler);
            owner.addEventFilter(ScrollToEvent.ANY, navigationHandler);
            owner.needsLayoutProperty().addListener(layoutListener);
            // Native refresh operations use the control's properties to invalidate cells.
            owner.getProperties().addListener(measurementListener);
        }
        dataSource = owner instanceof ListView<?> list ? list.itemsProperty()
                : owner instanceof TableView<?> table ? table.itemsProperty()
                : owner instanceof TreeView<?> tree ? tree.rootProperty()
                : owner instanceof TreeTableView<?> treeTable ? treeTable.rootProperty() : null;
        if (dataSource != null)
            dataSource.addListener(dataSourceListener);
        bindData();
    }

    /// Releases old data listeners and observes the current model without retaining replaced items.
    private void bindData() {
        if (items != null)
            items.removeListener(weakMeasurementListener);
        if (root != null)
            root.removeEventHandler(TreeItem.treeNotificationEvent(), weakTreeListener);
        @Nullable Object data = dataSource == null ? null : dataSource.getValue();
        items = data instanceof ObservableList<?> list ? list : null;
        root = data instanceof TreeItem<?> tree ? tree : null;
        if (items != null)
            items.addListener(weakMeasurementListener);
        if (root != null)
            root.addEventHandler(TreeItem.treeNotificationEvent(), weakTreeListener);
        invalidateMeasurements();
    }

    /// Discards the measured interval without retaining cells or allocating per-item state.
    private void invalidateMeasurements() {
        measuredEdge = -1;
        measurementRevision++;
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

    /// Bounds movement against the full fixed-size extent or the variable-size cells along its path.
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
        if (pixels == 0 || flow.getCellCount() == 0 || flow.positionProperty().isBound()
                || (pixels < 0 && flow.getPosition() <= 0)
                || (pixels > 0 && flow.getPosition() >= 1))
            return 0;
        @Nullable IndexedCell<?> first = flow.getFirstVisibleCell();
        double size = viewportLength(horizontal);
        double cellSize = flow.getFixedCellSize();
        if (first != null && cellSize > 0) {
            // Queued movement can reach an edge before its end cell becomes visible.
            double extent = Math.max(0, flow.getCellCount() * cellSize - size);
            double start = horizontal ? first.getLayoutX() : first.getLayoutY();
            double position = MathUtils.clamp(first.getIndex() * cellSize - start, 0, extent);
            return MathUtils.clamp(pixels, -position, extent - position);
        }
        @Nullable IndexedCell<?> edge = pixels < 0 ? first : flow.getLastVisibleCell();
        if (edge == null)
            return pixels;
        double start = horizontal ? edge.getLayoutX() : edge.getLayoutY();
        double length = horizontal ? edge.getWidth() : edge.getHeight();
        double available = Math.max(0, pixels < 0 ? -start : start + length - size);
        int direction = pixels < 0 ? -1 : 1;
        int edgeIndex = edge.getIndex();
        boolean reuse = measuredEdge == edgeIndex && measuredDirection == direction;
        double measured = reuse ? measuredLength : 0;
        int index = reuse ? nextMeasure : edgeIndex + direction;
        long revision = measurementRevision;
        boolean previous = measuring;
        measuring = true;
        try {
            // Extend the previous interval instead of remeasuring the whole queued distance.
            while (available + measured < Math.abs(pixels) && index >= 0 && index < flow.getCellCount()) {
                IndexedCell<?> cell = flow.getCell(index);
                measured += horizontal ? cell.getWidth() : cell.getHeight();
                index += direction;
            }
        } finally {
            measuring = previous;
        }
        if (revision == measurementRevision) {
            measuredEdge = edgeIndex;
            measuredDirection = direction;
            nextMeasure = index;
            measuredLength = measured;
        }
        return direction * Math.min(Math.abs(pixels), available + measured);
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

    /// Uses shared wheel speed, retaining native horizontal-axis mapping and viewport-sized pages.
    @Override
    double wheelDelta(ScrollEvent event, boolean horizontal) {
        if (!horizontal && flow.isVertical() && event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.PAGES)
            return -event.getTextDeltaY() * flow.getHeight();
        if (horizontal && !flow.isVertical())
            return super.wheelDelta(event, Math.abs(event.getDeltaX()) > Math.abs(event.getDeltaY()));
        return super.wheelDelta(event, horizontal);
    }
}
