// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Maps pixel distances onto a scroll pane's actual overflow and value ranges.
@NotNullByDefault
final class ScrollPaneAdapter extends ScrollAdapter {
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
        if (controller.gesture.isDuplicateDrag(event))
            event.consume();
    };

    /// Installs bubbling on content so nested controls receive input first.
    ScrollPaneAdapter(ScrollPane pane) {
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
                controller.reset();
        });
        pane.hvalueProperty().addListener(observable -> controller.positionChanged());
        pane.vvalueProperty().addListener(observable -> controller.positionChanged());
        pane.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!controller.gesture.isDuplicateDrag(event))
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
            controller.onScroll(event);
        });
    }

    /// Preserves native scrollbar visibility while unused input continues its original bubbling route.
    private void attachViewport() {
        controller.reset();
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
                    && mouse.getEventType() == MouseEvent.MOUSE_DRAGGED && !controller.gesture.isDuplicateDrag(mouse);
            if (!panning && (!(event instanceof ScrollEvent) || event.getEventType() != ScrollEvent.SCROLL))
                return dispatcher.dispatchEvent(event, tail);
            if (this.tail != null)
                return new ViewportDispatcher(dispatcher).dispatchEvent(event, tail);
            this.tail = tail;
            double previousX = controller.gesture.pannedX;
            double previousY = controller.gesture.pannedY;
            panStartX = Double.NaN;
            try {
                @Nullable Event result = dispatcher.dispatchEvent(event, this);
                if (panning) {
                    if (!Double.isNaN(panStartX)) {
                        // Position listeners interrupt animation during native panning; retain its credit.
                        controller.gesture.pannedX = previousX + position(true) - panStartX;
                        controller.gesture.pannedY = previousY + position(false) - panStartY;
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
            controller.onScroll(scroll);
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
        controller.reset();
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

    /// Uses shared wheel speed except for explicitly requested viewport-sized page scrolling.
    @Override
    double wheelDelta(ScrollEvent event, boolean horizontal) {
        if (!horizontal && event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.PAGES)
            return -event.getTextDeltaY() * pane.getViewportBounds().getHeight();
        return super.wheelDelta(event, horizontal);
    }
}
