// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.animation.AnimationTimer;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.NotNullByDefault;

/// Coalesces bounded wheel distances into velocity-continuous motion applied once per animation pulse.
@NotNullByDefault
final class WheelScrollAnimation extends AnimationTimer {
    /// Settling time after the last accepted wheel input, in nanoseconds.
    private static final long WHEEL_DURATION = 150_000_000L;

    /// Supplies pixel bounds and movement without exposing nodes, gestures, or skins.
    @NotNullByDefault
    interface Target {
        /// Bounds a requested displacement against the current position.
        double limit(boolean horizontal, double pixels);

        /// Moves along one axis and returns the displacement actually applied.
        double scroll(boolean horizontal, double pixels);
    }

    /// Receives each frame's movement and supplies current bounds.
    private final Target target;

    /// Horizontal queue and its current motion segment.
    private final Axis xAxis = new Axis();

    /// Vertical queue and its current motion segment.
    private final Axis yAxis = new Axis();

    /// Beginning of the current wheel interpolation, or zero while stopped.
    private long wheelStart;

    /// Latest time evaluated by an input or pulse; reentrant calls cannot move the trajectory backwards.
    private long lastTime;

    /// Invalidates an in-progress frame when synchronous callbacks change the animation's queue or lifetime.
    private long revision;

    /// Creates an idle animation for a pixel-based scrolling target.
    WheelScrollAnimation(Target target) {
        this.target = target;
    }

    /// Whether unapplied wheel movement is waiting for animation pulses.
    boolean isRunning() {
        return wheelStart != 0;
    }

    /// Queues additional pixels, returning false when pending movement already reserves all available space.
    boolean offer(double dx, double dy, long now) {
        long version = revision;
        double x = target.limit(true, xAxis.pending + dx);
        if (revision != version)
            return false;
        double y = target.limit(false, yAxis.pending + dy);
        if (revision != version)
            return false;
        if (x == xAxis.pending && y == yAxis.pending)
            return false;
        revision++;
        boolean running = wheelStart != 0;
        long time = running ? Math.max(now, lastTime) : now;
        double fraction = running ? fraction(time) : 0;
        xAxis.retarget(x, fraction);
        yAxis.retarget(y, fraction);
        wheelStart = time;
        lastTime = time;
        if (!running)
            start();
        return true;
    }

    /// Cancels opposing queued pixels before a descendant can consume the new input.
    void cancelReversal(double dx, double dy) {
        boolean changed = xAxis.cancelReversal(dx);
        changed |= yAxis.cancelReversal(dy);
        if (changed)
            revision++;
        if (xAxis.pending == 0 && yAxis.pending == 0)
            cancel();
    }

    /// Returns progress through the current segment with exact endpoints.
    private double fraction(long now) {
        return MathUtils.clamp((double) (now - wheelStart) / WHEEL_DURATION, 0, 1);
    }

    /// Applies the trajectory's accumulated movement, ending exactly at the accepted displacement.
    @Override
    public void handle(long now) {
        if (wheelStart == 0)
            return;
        long version = ++revision;
        lastTime = Math.max(now, lastTime);
        double fraction = fraction(lastTime);
        xAxis.advance(fraction);
        yAxis.advance(fraction);
        if (!apply(true, version) || !apply(false, version))
            return;
        if (fraction >= 1 || (xAxis.pending == 0 && yAxis.pending == 0))
            cancel();
    }

    /// Applies one axis without overwriting queue changes made by synchronous target callbacks.
    private boolean apply(boolean horizontal, long version) {
        Axis axis = horizontal ? xAxis : yAxis;
        double requested = axis.scheduled;
        // Listeners accepting new input must see only the distance left after this movement.
        axis.scheduled = 0;
        axis.pending -= requested;
        double actual = target.scroll(horizontal, requested);
        if (revision != version)
            return false;
        double remaining = Math.abs(actual - requested) > 0.01 ? 0 : target.limit(horizontal, axis.pending);
        if (revision != version)
            return false;
        axis.restrict(remaining);
        return true;
    }

    /// Releases the pulse timer and all unapplied wheel movement.
    void cancel() {
        revision++;
        if (wheelStart == 0)
            return;
        stop();
        xAxis.clear();
        yAxis.clear();
        wheelStart = 0;
        lastTime = 0;
    }

    /// A monotone cubic Hermite segment preserving its entry velocity and ending at rest.
    @NotNullByDefault
    private static final class Axis {
        /// Total accepted distance not yet applied to the target, including scheduled movement.
        private double pending;

        /// Trajectory movement awaiting the next rendering pulse.
        private double scheduled;

        /// Signed distance covered by the current segment.
        private double distance;

        /// Entry velocity multiplied by the segment duration, in pixels.
        private double initialVelocity;

        /// Segment displacement already included in scheduled or rendered movement.
        private double position;

        /// Updates the target while carrying forward both velocity and movement between pulses.
        private void retarget(double pending, double fraction) {
            double velocity = 6 * distance * fraction * (1 - fraction)
                    + initialVelocity * (1 - fraction) * (1 - 3 * fraction);
            advance(fraction);
            this.pending = pending;
            scheduled = MathUtils.clamp(scheduled, Math.min(0, pending), Math.max(0, pending));
            distance = pending - scheduled;
            // With a zero exit velocity, this tangent bound prevents overshoot and reversal.
            initialVelocity = MathUtils.clamp(velocity, Math.min(0, 3 * distance), Math.max(0, 3 * distance));
            position = 0;
        }

        /// Evaluates motion without scrolling the control, so frequent input still coalesces per pulse.
        private void advance(double fraction) {
            double next = fraction == 1 ? distance
                    : distance * fraction * fraction * (3 - 2 * fraction)
                    + initialVelocity * fraction * (1 - fraction) * (1 - fraction);
            scheduled += next - position;
            position = next;
        }

        /// Rescales remaining motion if a changing boundary reduces the accepted queue.
        private void restrict(double remaining) {
            if (remaining == 0) {
                clear();
            } else if (remaining != pending) {
                double scale = remaining / pending;
                distance *= scale;
                initialVelocity *= scale;
                position *= scale;
                pending = remaining;
            }
        }

        /// Discards both forward motion and its velocity when raw input reverses this axis.
        private boolean cancelReversal(double delta) {
            if (pending == 0 || delta == 0 || Math.signum(pending) == Math.signum(delta))
                return false;
            clear();
            return true;
        }

        /// Releases all movement and velocity associated with this axis.
        private void clear() {
            pending = 0;
            scheduled = 0;
            distance = 0;
            initialVelocity = 0;
            position = 0;
        }
    }
}
