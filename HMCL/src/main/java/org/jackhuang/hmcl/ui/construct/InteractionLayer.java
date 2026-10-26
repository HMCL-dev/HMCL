// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.LongSupplier;

/// Draws hover, focus, press, and ripple feedback in a single canvas.
/// All construction, property changes, and operations must occur on the JavaFX application thread.
/// The owner supplies dimensions, input states, and lifecycle notifications; this layer installs
/// no input handlers and does not own content. Drawing is clipped to a rounded rectangle,
/// but pointer picking still uses the rectangular canvas bounds.
/// State opacities follow the
/// [AndroidX Material 3 state tokens](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/StateTokens.kt).
/// Animated presses are represented by waveforms over the existing hover or focus state;
/// presses without a waveform use a single static state layer instead.
@NotNullByDefault
public final class InteractionLayer extends Canvas {
    /// The hover state opacity from Material 3 StateTokens v0_210.
    private static final double HOVER_OPACITY = 0.08;
    /// The focus state opacity from Material 3 StateTokens v0_210.
    private static final double FOCUS_OPACITY = 0.10;
    /// The pressed waveform or static press opacity from Material 3 StateTokens v0_210.
    private static final double PRESSED_OPACITY = 0.10;
    /// The duration of state changes and ripple release, in nanoseconds.
    private static final long STATE_DURATION = (long) (Motion.SHORT4.toMillis() * 1_000_000);
    /// The duration of ripple expansion while held, in nanoseconds.
    private static final long EXPANSION_DURATION = (long) (Motion.LONG1.toMillis() * 1_000_000);
    /// Supplies monotonic timestamps for both input changes and animation pulses.
    private final LongSupplier clock;
    /// Whether this instance schedules real pulses; deterministic tests advance it explicitly.
    private final boolean automaticPulses;
    /// The current waveforms, including released waves that have not finished fading.
    private final ArrayList<Ripple> ripples = new ArrayList<>();
    /// The currently held waveform, or null for a static press or no press.
    private @Nullable Ripple activeRipple;
    /// The lazily allocated, reusable pulse source.
    private @Nullable AnimationTimer timer;
    /// Whether there is unfinished animation work.
    private boolean running;
    /// Whether the owner reports hover.
    private boolean hovered;
    /// Whether the owner reports visual focus.
    private boolean focusActive;
    /// Whether a press has started without a release or reset.
    private boolean pressActive;
    /// The currently displayed state opacity, excluding waveforms.
    private double stateOpacity;
    /// The opacity at the beginning of the current state transition.
    private double startOpacity;
    /// The desired state opacity.
    private double targetOpacity;
    /// The monotonic start time of the current state transition.
    private long stateStart;
    /// Whether the state opacity has not yet reached its target.
    private boolean transitioning;
    /// The paint applied to all feedback, including existing waveforms.
    private final ObjectProperty<@Nullable Paint> fill = new SimpleObjectProperty<>(this, "fill", Color.BLACK);
    /// The corner radius in local coordinates; invalid values are rendered as zero.
    private final DoubleProperty cornerRadius = new SimpleDoubleProperty(this, "cornerRadius", 3);
    /// Whether subsequent state changes and presses animate.
    private final BooleanProperty animationEnabled = new SimpleBooleanProperty(this, "animationEnabled", true);

    /// Creates an empty layer with black feedback, radius 3, and animation enabled.
    public InteractionLayer() {
        this(System::nanoTime, true);
    }

    /// Creates a layer with an injectable monotonic clock and optional automatic pulses.
    InteractionLayer(LongSupplier clock, boolean automaticPulses) {
        this.clock = clock;
        this.automaticPulses = automaticPulses;
        fill.addListener(observable -> draw());
        cornerRadius.addListener(observable -> draw());
        widthProperty().addListener(observable -> draw());
        heightProperty().addListener(observable -> draw());
        animationEnabled.addListener(observable -> {
            if (!isAnimationEnabled()) {
                ripples.clear();
                activeRipple = null;
                settle();
            }
        });
    }

    /// Returns the feedback paint property. Null suppresses drawing without changing input or state.
    public ObjectProperty<@Nullable Paint> fillProperty() {
        return fill;
    }

    /// Returns the paint used for both the state layer and waveforms, or null if hidden.
    public @Nullable Paint getFill() {
        return fill.get();
    }

    /// Sets the feedback paint; its intrinsic transparency multiplies the feedback opacity.
    public void setFill(@Nullable Paint value) {
        fill.set(value);
    }

    /// Returns the corner radius property, in local coordinates. Negative or non-finite values
    /// render as zero; positive values are clamped to half the shorter canvas dimension.
    public DoubleProperty cornerRadiusProperty() {
        return cornerRadius;
    }

    /// Returns the configured corner radius before rendering clamps it.
    public double getCornerRadius() {
        return cornerRadius.get();
    }

    /// Sets the corner radius in local coordinates; see [#cornerRadiusProperty()] for clamping.
    public void setCornerRadius(double value) {
        cornerRadius.set(value);
    }

    /// Returns the animation switch. Disabling it clears waveforms and immediately displays
    /// the current state, including a held press. Enabling it does not replay a held press;
    /// its static press feedback remains until release or reset.
    public BooleanProperty animationEnabledProperty() {
        return animationEnabled;
    }

    /// Returns whether new changes animate.
    public boolean isAnimationEnabled() {
        return animationEnabled.get();
    }

    /// Enables animation for subsequent changes, or immediately settles existing feedback.
    public void setAnimationEnabled(boolean value) {
        animationEnabled.set(value);
    }

    /// Reports hover. The idle hover opacity is 8%, below focus and static press priority.
    /// An animated press draws a waveform over this state without increasing the state opacity.
    public void setHovered(boolean value) {
        if (hovered != value) {
            hovered = value;
            changeState();
        }
    }

    /// Reports visual focus independently of the canvas's actual focus. Focus opacity is 10%,
    /// above hover priority. An animated press may draw a waveform over the focus state.
    public void setFocusActive(boolean value) {
        if (focusActive != value) {
            focusActive = value;
            changeState();
        }
    }

    /// Starts a press at finite local coordinates. With animation enabled, a waveform with
    /// up to 10% paint opacity overlays the hover or focus state, without a separate press layer.
    /// With animation disabled, a single 10% state layer replaces the hover or focus state.
    /// Repeated calls before release or reset have no effect. Coordinates may lie outside
    /// the canvas; drawing remains clipped.
    ///
    /// @throws IllegalArgumentException if either coordinate is not finite
    public void press(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Press coordinates must be finite");
        if (pressActive)
            return;
        pressActive = true;
        if (isAnimationEnabled()) {
            activeRipple = new Ripple(x, y, clock.getAsLong());
            ripples.add(activeRipple);
        }
        changeState();
    }

    /// Releases the current press. Its waveform expands and fades for 200 ms when animation
    /// is enabled. Repeated calls do nothing; previously released waveforms continue fading.
    public void release() {
        if (!pressActive)
            return;
        long now = clock.getAsLong();
        advance(now);
        pressActive = false;
        if (activeRipple != null) {
            activeRipple.releaseProgress = activeRipple.progress;
            activeRipple.released = true;
            activeRipple.start = now;
            activeRipple = null;
        }
        changeState();
    }

    /// Immediately clears all waveforms and the press, stops pulses, and displays the current
    /// hover or focus state. Repeated calls have no additional effect.
    public void reset() {
        pressActive = false;
        activeRipple = null;
        ripples.clear();
        settle();
    }

    /// Returns the persistent state opacity, leaving animated press feedback to its waveform.
    private double desiredOpacity() {
        // A press interrupted by disabling animation stays static even if animation is re-enabled.
        if (pressActive && activeRipple == null)
            return PRESSED_OPACITY;
        return focusActive ? FOCUS_OPACITY : hovered ? HOVER_OPACITY : 0;
    }

    /// Starts a transition from the currently displayed state rather than its previous target.
    private void changeState() {
        long now = clock.getAsLong();
        advance(now);
        if (!isAnimationEnabled()) {
            settle();
            return;
        }
        double target = desiredOpacity();
        if (target != targetOpacity) {
            startOpacity = stateOpacity;
            targetOpacity = target;
            stateStart = now;
            transitioning = startOpacity != targetOpacity;
        }
        advance(now);
    }

    /// Applies the exact static state and releases the pulse source.
    private void settle() {
        transitioning = false;
        stateOpacity = targetOpacity = desiredOpacity();
        setRunning(false);
        draw();
    }

    /// Advances all animations to a timestamp from the supplied clock, then redraws.
    /// Package access permits deterministic tests without exposing a public timing API.
    void advance(long now) {
        if (transitioning) {
            double progress = fraction(now - stateStart, STATE_DURATION);
            stateOpacity = progress == 0 ? startOpacity : progress == 1 ? targetOpacity
                    : Motion.STANDARD.interpolate(startOpacity, targetOpacity, progress);
            transitioning = progress < 1;
        }
        boolean moving = transitioning;
        for (int i = ripples.size() - 1; i >= 0; i--) {
            Ripple ripple = ripples.get(i);
            double progress = fraction(now - ripple.start, ripple.released ? STATE_DURATION : EXPANSION_DURATION);
            if (ripple.released && progress == 1) {
                ripples.remove(i);
                continue;
            }
            double eased = progress == 0 ? 0 : progress == 1 ? 1 : Motion.STANDARD.interpolate(0.0, 1.0, progress);
            ripple.progress = ripple.released ? ripple.releaseProgress + (1 - ripple.releaseProgress) * eased : eased;
            ripple.opacity = ripple.released ? PRESSED_OPACITY * (1 - eased) : PRESSED_OPACITY;
            moving |= progress < 1;
        }
        setRunning(moving);
        draw();
    }

    /// Clamps elapsed time to a unit interval, including exact animation endpoints.
    private static double fraction(long elapsed, long duration) {
        return Math.max(0, Math.min(1, (double) elapsed / duration));
    }

    /// Starts or stops the single reusable pulse source when unfinished work changes.
    private void setRunning(boolean value) {
        if (running == value)
            return;
        running = value;
        if (!automaticPulses)
            return;
        if (value) {
            if (timer == null) {
                timer = new AnimationTimer() {
                    /// Uses the same clock as input operations to avoid mixing time origins.
                    @Override
                    public void handle(long now) {
                        advance(clock.getAsLong());
                    }
                };
            }
            timer.start();
        } else if (timer != null) {
            timer.stop();
        }
    }

    /// Reports whether unfinished animation work requires another pulse.
    boolean isAnimating() {
        return running;
    }

    /// Returns the current state opacity, excluding waveforms.
    double stateOpacity() {
        return stateOpacity;
    }

    /// Returns the number of held and fading waveforms.
    int rippleCount() {
        return ripples.size();
    }

    /// Redraws the complete canvas without allocating scene-graph or clip nodes.
    private void draw() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0)
            return;
        GraphicsContext graphics = getGraphicsContext2D();
        graphics.clearRect(0, 0, width, height);
        @Nullable Paint paint = getFill();
        if (paint == null)
            return;
        double radius = getCornerRadius();
        radius = Double.isFinite(radius) ? Math.max(0, Math.min(radius, Math.min(width, height) / 2)) : 0;
        graphics.save();
        graphics.beginPath();
        graphics.moveTo(radius, 0);
        graphics.lineTo(width - radius, 0);
        graphics.quadraticCurveTo(width, 0, width, radius);
        graphics.lineTo(width, height - radius);
        graphics.quadraticCurveTo(width, height, width - radius, height);
        graphics.lineTo(radius, height);
        graphics.quadraticCurveTo(0, height, 0, height - radius);
        graphics.lineTo(0, radius);
        graphics.quadraticCurveTo(0, 0, radius, 0);
        graphics.closePath();
        graphics.clip();
        graphics.setFill(paint);
        graphics.setGlobalAlpha(stateOpacity);
        graphics.fillRect(0, 0, width, height);
        for (Ripple ripple : ripples) {
            double dx = Math.max(Math.abs(ripple.x), Math.abs(width - ripple.x));
            double dy = Math.max(Math.abs(ripple.y), Math.abs(height - ripple.y));
            double rippleRadius = Math.hypot(dx, dy) * ripple.progress;
            graphics.setGlobalAlpha(ripple.opacity);
            graphics.fillOval(ripple.x - rippleRadius, ripple.y - rippleRadius, rippleRadius * 2, rippleRadius * 2);
        }
        // JavaFX reuses its clipping scratch surface, including the previous draw's extra alpha.
        // Leave that surface at full alpha so the next clip is not multiplied by a state opacity.
        graphics.setGlobalAlpha(1);
        graphics.setFill(Color.TRANSPARENT);
        graphics.fillRect(0, 0, width, height);
        graphics.restore();
    }

    /// Stores a waveform without adding a node to the scene graph.
    @NotNullByDefault
    private static final class Ripple {
        /// The horizontal origin in local coordinates.
        private final double x;
        /// The vertical origin in local coordinates.
        private final double y;
        /// The start of the current expansion or release phase.
        private long start;
        /// Whether the release phase has begun.
        private boolean released;
        /// The fraction of the covering radius currently displayed.
        private double progress;
        /// The expansion fraction captured on release.
        private double releaseProgress;
        /// The additional paint opacity of this waveform.
        private double opacity = PRESSED_OPACITY;

        /// Creates an unexpanded held waveform at the supplied position and time.
        private Ripple(double x, double y, long start) {
            this.x = x;
            this.y = y;
            this.start = start;
        }
    }
}
