// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.animation.AnimationTimer;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.NotNullByDefault;

/// Coalesces wheel distances into a bounded pixel queue, applied once per animation pulse.
@NotNullByDefault
final class WheelScrollAnimation extends AnimationTimer {
    /// Material 3 short3 duration, in nanoseconds, for discrete wheel movement.
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

    /// Unapplied horizontal wheel distance in pixels.
    private double pendingX;

    /// Unapplied vertical wheel distance in pixels.
    private double pendingY;

    /// Beginning of the current wheel interpolation, or zero while stopped.
    private long wheelStart;

    /// Eased fraction already applied to the current wheel interpolation.
    private double wheelProgress;

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
        double x = target.limit(true, pendingX + dx);
        double y = target.limit(false, pendingY + dy);
        if (x == pendingX && y == pendingY)
            return false;
        pendingX = x;
        pendingY = y;
        boolean running = wheelStart != 0;
        wheelStart = now;
        wheelProgress = 0;
        if (!running)
            start();
        return true;
    }

    /// Cancels opposing queued pixels before a descendant can consume the new input.
    void cancelReversal(double dx, double dy) {
        pendingX = withoutReversal(pendingX, dx);
        pendingY = withoutReversal(pendingY, dy);
        if (pendingX == 0 && pendingY == 0)
            cancel();
    }

    /// Drops queued movement opposing raw input, even when an edge prevents new displacement.
    private static double withoutReversal(double pending, double delta) {
        return delta != 0 && Math.signum(pending) != Math.signum(delta) ? 0 : pending;
    }

    /// Applies this frame's eased fraction, ending exactly at the requested displacement.
    @Override
    public void handle(long now) {
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
            cancel();
    }

    /// Removes applied distance and discards any remainder that hits a boundary.
    private double advance(boolean horizontal, double pending, double fraction) {
        double requested = pending * fraction;
        double actual = target.scroll(horizontal, requested);
        if (Math.abs(actual - requested) > 0.01)
            return 0;
        return target.limit(horizontal, pending - requested);
    }

    /// Releases the pulse timer and all unapplied wheel movement.
    void cancel() {
        if (wheelStart == 0)
            return;
        stop();
        pendingX = 0;
        pendingY = 0;
        wheelStart = 0;
        wheelProgress = 0;
    }
}
