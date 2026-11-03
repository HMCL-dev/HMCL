// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import org.jetbrains.annotations.NotNullByDefault;

/// Routes input to immediate gesture movement or queued wheel animation.
@NotNullByDefault
final class ScrollController implements WheelScrollAnimation.Target {
    /// Supplies control-specific geometry, movement, and input-axis mapping.
    private final ScrollAdapter adapter;

    /// Owns gesture lifetimes and nested movement accounting.
    final ScrollGesture gesture;

    /// Owns unapplied wheel movement and pulse interpolation.
    final WheelScrollAnimation wheel;

    /// Distinguishes controller writes from external position changes.
    private boolean updating;

    /// Observes capture without consuming input, leaving acceptance to the adapter's bubbling hooks.
    ScrollController(Node node, ScrollAdapter adapter) {
        this.adapter = adapter;
        gesture = new ScrollGesture(node);
        wheel = new WheelScrollAnimation(this);
        node.addEventFilter(ScrollEvent.ANY, this::observeScroll);
        node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> interrupt());
        node.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> gesture.clearPanning());
        node.addEventFilter(TouchEvent.TOUCH_PRESSED, event -> interrupt());
        node.addEventFilter(KeyEvent.KEY_PRESSED, event -> interrupt());
        node.sceneProperty().addListener(observable -> reset());
        node.visibleProperty().addListener(observable -> reset());
    }

    /// Cancels stale animation even when a nested control consumes the new movement.
    private void observeScroll(ScrollEvent event) {
        gesture.observe(event);
        if (event.getEventType() == ScrollEvent.SCROLL_STARTED) {
            wheel.cancel();
        } else if (event.getEventType() == ScrollEvent.SCROLL && wheel.isRunning()) {
            if (gesture.isPrecise(event)) {
                wheel.cancel();
            } else {
                double dx = adapter.wheelDelta(event, true);
                double dy = adapter.wheelDelta(event, false);
                if (Double.isFinite(dx) && Double.isFinite(dy))
                    wheel.cancelReversal(dx, dy);
            }
        }
    }

    /// Consumes accepted movement; unused input keeps its original bubbling route to ancestors.
    void onScroll(ScrollEvent event) {
        if (event.isConsumed() || event.getEventType() != ScrollEvent.SCROLL)
            return;
        long now = System.nanoTime();
        // Unmarked fractional input still uses the wheel fallback; explicit gestures bypass animation.
        boolean precise = gesture.isPrecise(event);
        double dx = precise ? adapter.gestureDelta(event, true) : adapter.wheelDelta(event, true);
        double dy = precise ? adapter.gestureDelta(event, false) : adapter.wheelDelta(event, false);
        if (!Double.isFinite(dx) || !Double.isFinite(dy))
            return;

        if (!precise) {
            gesture.clearPanning();
            if (wheel.offer(dx, dy, now))
                event.consume();
            return;
        }

        wheel.cancel();
        boolean panned = false;
        if (event.isDirect() && !event.isInertia()) {
            double x = gesture.panning(true);
            double y = gesture.panning(false);
            panned = x != 0 || y != 0;
            dx -= x;
            dy -= y;
        }
        gesture.clearPanning();
        if (event.isInertia()) {
            double factor = gesture.inertiaFactor(now);
            if (factor == 0) {
                event.consume();
                return;
            }
            dx *= factor;
            dy *= factor;
        }

        // Shared credit remains available when this surface cannot apply the remaining movement.
        boolean moved = panned && dx == 0 && dy == 0;
        moved |= scroll(true, dx) != 0;
        moved |= scroll(false, dy) != 0;
        if (moved) {
            if (event.isInertia())
                gesture.acceptInertia(now);
            event.consume();
        }
    }

    /// Supplies current bounds to the animation without changing the control's position.
    @Override
    public double limit(boolean horizontal, double pixels) {
        return adapter.limit(horizontal, pixels);
    }

    /// Guards synchronous position notifications against cancelling the controller's own movement.
    @Override
    public double scroll(boolean horizontal, double pixels) {
        if (pixels == 0)
            return 0;
        boolean previous = updating;
        updating = true;
        try {
            return adapter.scroll(horizontal, pixels);
        } finally {
            updating = previous;
        }
    }

    /// Cancels queued movement and prevents old native inertia from resuming after a press.
    void interrupt() {
        wheel.cancel();
        gesture.interrupt();
    }

    /// Clears animation and gesture state when the scrolling surface changes.
    void reset() {
        wheel.cancel();
        gesture.reset();
    }

    /// Cancels animation after an external position change, such as scrollbar dragging.
    void positionChanged() {
        if (!updating)
            interrupt();
    }
}
