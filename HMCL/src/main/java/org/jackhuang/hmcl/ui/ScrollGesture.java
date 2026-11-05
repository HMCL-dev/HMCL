// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.event.Event;
import javafx.event.EventDispatcher;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Tracks continuous input, shared nested inertia, and native movement before touch recognition.
@NotNullByDefault
final class ScrollGesture {
    /// Maximum native inertia tail, in nanoseconds; active gestures are never attenuated.
    private static final long INERTIA_DURATION = 200_000_000L;

    /// Locates ancestor gestures during capture and direct-event dispatch.
    private final Node node;

    /// Whether the platform has started a continuous gesture.
    private boolean gesture;

    /// Prevents synthesized mouse drags from repeating an active touchscreen scroll.
    private boolean directGesture;

    /// Horizontal native panning already included in the first direct scroll delta.
    double pannedX;

    /// Vertical native panning already included in the first direct scroll delta.
    double pannedY;

    /// Reusable displacement scope when this controller is the outermost participant.
    private final Panning panning = new Panning();

    /// Shared displacement for the currently dispatched direct event, or null outside dispatch.
    private @Nullable Panning eventPanning;

    /// Whether a press has cancelled the previous gesture's inertia.
    private boolean suppressInertia;

    /// Inertia timing shared with ancestor controllers for the current gesture.
    private Inertia inertia = new Inertia();

    /// Whether the current inertia state belongs to an ancestor controller.
    private boolean inheritedInertia;

    /// Shares native panning credit for the duration of each direct scroll event.
    ScrollGesture(Node node) {
        this.node = node;
        EventDispatcher dispatcher = Objects.requireNonNull(node.getEventDispatcher());
        node.setEventDispatcher((event, tail) -> {
            if (!(event instanceof ScrollEvent scroll) || scroll.getEventType() != ScrollEvent.SCROLL
                    || !scroll.isDirect() || scroll.isInertia())
                return dispatcher.dispatchEvent(event, tail);
            @Nullable Panning previous = eventPanning;
            @Nullable Panning shared = null;
            for (@Nullable Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
                @Nullable ScrollController controller = ScrollUtils.controller(parent);
                if (controller != null && controller.gesture.eventPanning != null) {
                    shared = controller.gesture.eventPanning;
                    break;
                }
            }
            if (shared == null) {
                shared = previous == null ? panning : new Panning();
                shared.x = 0;
                shared.y = 0;
            }
            shared.x += pannedX;
            shared.y += pannedY;
            eventPanning = shared;
            clearPanning();
            try {
                return dispatcher.dispatchEvent(event, tail);
            } finally {
                // Consumption by descendants must expire credit too; nested dispatch keeps its own scope.
                eventPanning = previous;
            }
        });
    }

    /// Observes gesture boundaries even when a descendant consumes every movement event.
    void observe(ScrollEvent event) {
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
        // Descendants may consume the event before this controller's bubbling handler runs.
        if (event.getEventType() == ScrollEvent.SCROLL && event.isDirect() && !event.isInertia())
            directGesture = true;
        if (event.getEventType() == ScrollEvent.SCROLL_STARTED
                || (event.getEventType() == ScrollEvent.SCROLL && !event.isInertia())) {
            suppressInertia = false;
            // Capture visits ancestors first, so all controllers inherit the same fresh tail.
            for (@Nullable Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
                @Nullable ScrollController controller = ScrollUtils.controller(parent);
                if (controller != null) {
                    inertia = controller.gesture.inertia;
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
    boolean isDuplicateDrag(Event event) {
        return directGesture && event instanceof MouseEvent mouse
                && mouse.getEventType() == MouseEvent.MOUSE_DRAGGED && mouse.isSynthesized();
    }

    /// Distinguishes continuous gestures from wheels, including unmarked fractional wheel input.
    boolean isPrecise(ScrollEvent event) {
        return gesture || event.isDirect() || event.getTouchCount() > 0 || event.isInertia();
    }

    /// Returns native movement already applied by this event's participating ancestors and descendants.
    double panning(boolean horizontal) {
        return eventPanning == null ? 0 : horizontal ? eventPanning.x : eventPanning.y;
    }

    /// Returns the shared inertia attenuation, or zero when the tail must be consumed without movement.
    double inertiaFactor(long now) {
        double remaining = inertia.start == 0 ? 1 : 1 - (double) (now - inertia.start) / INERTIA_DURATION;
        return suppressInertia || remaining <= 0 ? 0 : remaining * remaining;
    }

    /// Starts the common inertia clock only when a surface accepts movement.
    void acceptInertia(long now) {
        if (inertia.start == 0)
            inertia.start = now;
    }

    /// Discards recognition state and prevents old native inertia from resuming after a press.
    void interrupt() {
        clearPanning();
        suppressInertia = true;
        gesture = false;
        directGesture = false;
    }

    /// Discards native displacement that cannot belong to a later direct gesture.
    void clearPanning() {
        pannedX = 0;
        pannedY = 0;
    }

    /// Clears gesture state when the scrolling surface changes.
    void reset() {
        interrupt();
        inertia = new Inertia();
        inheritedInertia = false;
    }

    /// Shares the native inertia age across nested scrolling surfaces.
    @NotNullByDefault
    private static final class Inertia {
        /// Time of the first accepted inertia movement, or zero before the tail starts.
        private long start;
    }

    /// Native displacement shared by all controllers participating in one direct scroll event.
    @NotNullByDefault
    private static final class Panning {
        /// Horizontal content displacement already applied before gesture recognition.
        private double x;

        /// Vertical content displacement already applied before gesture recognition.
        private double y;
    }
}
