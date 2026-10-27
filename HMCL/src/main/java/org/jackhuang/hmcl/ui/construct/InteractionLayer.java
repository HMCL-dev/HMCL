// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.LongSupplier;

/// Draws hover, focus, and static press backgrounds with transient circle waveforms.
/// All construction, property changes, and operations must occur on the JavaFX application thread.
/// The owner supplies dimensions, input states, and lifecycle notifications; this layer installs
/// no input handlers and does not own content. Waveforms share a rounded rectangular clip;
/// the layer itself retains rectangular pointer picking, including transparent corners.
/// Solid state colors use the region background; other paints use a lazily created rectangle
/// so their intrinsic transparency is multiplied by the state opacity.
/// State opacities follow the
/// [AndroidX Material 3 state tokens](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/StateTokens.kt).
/// Animated presses are represented by waveforms over the existing hover or focus state;
/// presses without a waveform use a single static state layer instead.
@NotNullByDefault
public final class InteractionLayer extends Region {
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
    /// The lazily created waveform group; it is attached only while waveforms exist.
    private @Nullable Group waveformGroup;
    /// The shared waveform clip, detached when no waveforms remain.
    private @Nullable Rectangle waveformClip;
    /// The lazily created state rectangle for paints that cannot encode an additional alpha.
    private @Nullable Rectangle paintBackground;
    /// The paint last applied to the persistent state background.
    private @Nullable Paint backgroundPaint;
    /// The opacity last applied to the persistent state background.
    private double backgroundOpacity;
    /// The radius last applied to the persistent state background.
    private double backgroundRadius;
    /// The immutable corner radii reused while only opacity changes.
    private CornerRadii backgroundRadii = CornerRadii.EMPTY;
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
        setPickOnBounds(true);
        fill.addListener(observable -> updateVisuals());
        cornerRadius.addListener(observable -> updateGeometry());
        animationEnabled.addListener(observable -> {
            if (!isAnimationEnabled()) {
                clearRipples();
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
    /// render as zero; positive values are clamped to half the shorter layer dimension.
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

    /// Reports visual focus independently of the layer's actual focus. Focus opacity is 10%,
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
    /// the layer; drawing remains clipped.
    ///
    /// @throws IllegalArgumentException if either coordinate is not finite
    public void press(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Press coordinates must be finite");
        if (pressActive)
            return;
        pressActive = true;
        if (isAnimationEnabled()) {
            Circle circle = new Circle();
            circle.setManaged(false);
            circle.setMouseTransparent(true);
            activeRipple = new Ripple(x, y, clock.getAsLong(), circle);
            ripples.add(activeRipple);
            if (waveformGroup == null) {
                waveformGroup = new Group();
                waveformGroup.setManaged(false);
                waveformGroup.setMouseTransparent(true);
                waveformClip = new Rectangle();
            }
            if (waveformGroup.getParent() == null) {
                getChildren().add(waveformGroup);
                waveformGroup.setClip(waveformClip);
            }
            waveformGroup.getChildren().add(circle);
            updateGeometry();
        }
        changeState();
    }

    /// Releases the current press. Its waveform expands and fades for 200 ms when animation
    /// is enabled. Repeated calls do nothing; previously released waveforms continue fading.
    public void release() {
        if (!pressActive)
            return;
        long now = clock.getAsLong();
        updateAnimations(now);
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
        clearRipples();
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
        updateAnimations(now);
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
        if (transitioning)
            setRunning(true);
        updateVisuals();
    }

    /// Applies the exact static state and releases the pulse source.
    private void settle() {
        transitioning = false;
        stateOpacity = targetOpacity = desiredOpacity();
        setRunning(false);
        updateVisuals();
    }

    /// Advances all animations to a timestamp from the supplied clock, then updates their nodes.
    /// Package access permits deterministic tests without exposing a public timing API.
    void advance(long now) {
        updateAnimations(now);
        updateVisuals();
    }

    /// Advances logical animation state without publishing intermediate visual changes.
    private void updateAnimations(long now) {
        if (transitioning) {
            double progress = fraction(now - stateStart, STATE_DURATION);
            // Hover-only fades use a uniform opacity change in both directions.
            Interpolator interpolator = startOpacity <= HOVER_OPACITY && targetOpacity <= HOVER_OPACITY
                    ? Interpolator.LINEAR : Motion.STANDARD;
            stateOpacity = progress == 0 ? startOpacity : progress == 1 ? targetOpacity
                    : interpolator.interpolate(startOpacity, targetOpacity, progress);
            transitioning = progress < 1;
        }
        boolean moving = transitioning;
        for (int i = ripples.size() - 1; i >= 0; i--) {
            Ripple ripple = ripples.get(i);
            double progress = fraction(now - ripple.start, ripple.released ? STATE_DURATION : EXPANSION_DURATION);
            if (ripple.released && progress == 1) {
                ripples.remove(i);
                removeWaveform(ripple.circle);
                continue;
            }
            double eased = progress == 0 ? 0 : progress == 1 ? 1 : Motion.STANDARD.interpolate(0.0, 1.0, progress);
            ripple.progress = ripple.released ? ripple.releaseProgress + (1 - ripple.releaseProgress) * eased : eased;
            ripple.opacity = ripple.released ? PRESSED_OPACITY * (1 - eased) : PRESSED_OPACITY;
            moving |= progress < 1;
        }
        setRunning(moving);
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

    /// Updates both dimensions before adjusting backgrounds, clipping, and waveform radii.
    @Override
    public void resize(double width, double height) {
        if (width == getWidth() && height == getHeight())
            return;
        super.resize(width, height);
        updateGeometry();
    }

    /// Returns the effective uniform radius within the current dimensions.
    private double effectiveRadius() {
        double radius = getCornerRadius();
        return Double.isFinite(radius) ? Math.max(0, Math.min(radius, Math.min(getWidth(), getHeight()) / 2)) : 0;
    }

    /// Updates size-dependent geometry without restarting an existing animation.
    private void updateGeometry() {
        double radius = effectiveRadius();
        if (waveformClip != null && !ripples.isEmpty()) {
            waveformClip.setWidth(getWidth());
            waveformClip.setHeight(getHeight());
            waveformClip.setArcWidth(radius * 2);
            waveformClip.setArcHeight(radius * 2);
        }
        if (paintBackground != null) {
            paintBackground.setWidth(getWidth());
            paintBackground.setHeight(getHeight());
            paintBackground.setArcWidth(radius * 2);
            paintBackground.setArcHeight(radius * 2);
        }
        for (int i = 0; i < ripples.size(); i++) {
            Ripple ripple = ripples.get(i);
            double dx = Math.max(Math.abs(ripple.x), Math.abs(getWidth() - ripple.x));
            double dy = Math.max(Math.abs(ripple.y), Math.abs(getHeight() - ripple.y));
            ripple.circle.setRadius(Math.hypot(dx, dy));
        }
        updateVisuals();
    }

    /// Publishes changed state and waveform properties without replaying drawing commands.
    private void updateVisuals() {
        @Nullable Paint paint = getFill();
        updateBackground(paint, effectiveRadius());
        if (waveformGroup != null)
            waveformGroup.setVisible(paint != null && getWidth() > 0 && getHeight() > 0);
        for (int i = 0; i < ripples.size(); i++) {
            Ripple ripple = ripples.get(i);
            Circle circle = ripple.circle;
            circle.setFill(paint);
            circle.setCenterX(ripple.x);
            circle.setCenterY(ripple.y);
            circle.setScaleX(ripple.progress);
            circle.setScaleY(ripple.progress);
            circle.setOpacity(ripple.opacity);
        }
    }

    /// Updates the persistent background only when its paint, opacity, or radius changes.
    private void updateBackground(@Nullable Paint paint, double radius) {
        if (paint == backgroundPaint && stateOpacity == backgroundOpacity && radius == backgroundRadius)
            return;
        backgroundPaint = paint;
        backgroundOpacity = stateOpacity;
        if (radius != backgroundRadius)
            backgroundRadii = radius == 0 ? CornerRadii.EMPTY : new CornerRadii(radius);
        backgroundRadius = radius;
        if (paintBackground != null && (paint instanceof Color || paint == null || stateOpacity == 0)) {
            getChildren().remove(paintBackground);
            paintBackground.setFill(null);
        }
        if (paint == null || stateOpacity == 0) {
            setBackground(null);
        } else if (paint instanceof Color color) {
            Color tinted = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity() * stateOpacity);
            setBackground(new Background(new BackgroundFill(tinted, backgroundRadii, Insets.EMPTY)));
        } else {
            // A node opacity also supports ImagePattern without rewriting its source pixels.
            setBackground(null);
            if (paintBackground == null) {
                paintBackground = new Rectangle();
                paintBackground.setManaged(false);
                paintBackground.setMouseTransparent(true);
            }
            paintBackground.setWidth(getWidth());
            paintBackground.setHeight(getHeight());
            paintBackground.setArcWidth(radius * 2);
            paintBackground.setArcHeight(radius * 2);
            paintBackground.setFill(paint);
            paintBackground.setOpacity(stateOpacity);
            if (paintBackground.getParent() == null)
                getChildren().add(0, paintBackground);
        }
    }

    /// Detaches all waves and releases their animation state.
    private void clearRipples() {
        for (int i = ripples.size() - 1; i >= 0; i--)
            removeWaveform(ripples.remove(i).circle);
    }

    /// Removes one waveform node and detaches the shared clip when its group becomes empty.
    private void removeWaveform(Circle circle) {
        if (waveformGroup != null) {
            waveformGroup.getChildren().remove(circle);
            if (waveformGroup.getChildren().isEmpty()) {
                getChildren().remove(waveformGroup);
                waveformGroup.setClip(null);
            }
        }
        circle.setFill(null);
    }

    /// Stores animation progress alongside its waveform node until completion or cancellation.
    @NotNullByDefault
    private static final class Ripple {
        /// The horizontal origin in local coordinates.
        private final double x;
        /// The vertical origin in local coordinates.
        private final double y;
        /// The unmanaged circle displaying this waveform.
        private final Circle circle;
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
        private Ripple(double x, double y, long start, Circle circle) {
            this.x = x;
            this.y = y;
            this.start = start;
            this.circle = circle;
        }
    }
}
