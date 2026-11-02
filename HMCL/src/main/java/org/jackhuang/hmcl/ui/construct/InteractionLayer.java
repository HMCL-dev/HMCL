// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Draws hover, focus, and press feedback as transitions over the entire background.
/// All construction, property changes, and operations must occur on the JavaFX application thread.
/// The owner supplies dimensions, input states, and lifecycle notifications; this layer installs
/// no input handlers and does not own content. Backgrounds follow the configured corner radii;
/// the layer retains rectangular pointer picking, including transparent corners.
/// Solid state colors use the region background; other paints use a lazily created region
/// so their intrinsic transparency is multiplied by the state opacity.
/// Press feedback takes priority over focus and hover, using a stronger tint for visibility.
@NotNullByDefault
public final class InteractionLayer extends Region {
    /// The hover state opacity from Material 3 StateTokens v0_210.
    private static final double HOVER_OPACITY = 0.08;

    /// The focus state opacity from Material 3 StateTokens v0_210.
    private static final double FOCUS_OPACITY = 0.10;

    /// The press opacity, distinct from both hover and focus feedback.
    private static final double PRESSED_OPACITY = 0.16;

    /// The duration of transitions within the hover opacity range, in nanoseconds.
    private static final long HOVER_DURATION = (long) (Motion.SHORT2.toMillis() * 1_000_000);

    /// The duration of focus changes and press release, in nanoseconds.
    private static final long STATE_DURATION = (long) (Motion.SHORT4.toMillis() * 1_000_000);

    /// The duration of entering the pressed state, in nanoseconds.
    private static final long PRESS_DURATION = (long) (Motion.SHORT2.toMillis() * 1_000_000);

    /// Whether this instance schedules real pulses; deterministic tests advance it explicitly.
    private final boolean automaticPulses;

    /// The lazily created state region for paints that cannot encode an additional alpha.
    private @Nullable Region paintBackground;

    /// The paint last applied to the persistent state background.
    private @Nullable Paint backgroundPaint;

    /// The opacity last applied to the persistent state background.
    private double backgroundOpacity;

    /// The corner radii last applied to the persistent state background.
    private CornerRadii backgroundRadii = CornerRadii.EMPTY;

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

    /// The currently displayed state opacity.
    private double stateOpacity;

    /// The opacity at the beginning of the current state transition.
    private double startOpacity;

    /// The desired state opacity.
    private double targetOpacity;

    /// The monotonic start time of the current state transition.
    private long stateStart;

    /// The duration captured when the current transition starts.
    private long stateDuration;

    /// Whether the state opacity has not yet reached its target.
    private boolean transitioning;

    /// The optional owner notification for a completed or unnecessary state transition.
    private @Nullable Runnable onStateSettled;

    /// Sets an owner notification delivered after publishing a settled state.
    /// Reset does not notify; disabling animation does. Assigning a listener does not invoke it.
    void setOnStateSettled(@Nullable Runnable listener) {
        onStateSettled = listener;
    }

    /// Returns whether the background is still transitioning.
    boolean isStateTransitioning() {
        return transitioning;
    }

    /// Notifies the owner after state visuals have reached their current target.
    private void notifyStateSettled() {
        if (onStateSettled != null)
            onStateSettled.run();
    }

    /// Creates an empty layer with black feedback, square corners, and the animation default from
    /// [AnimationUtils#isAnimationEnabled()]. Settings must have been loaded before construction.
    public InteractionLayer() {
        this(true);
    }

    /// Creates a layer with optional automatic pulses; manual timing uses the timestamped operations.
    InteractionLayer(boolean automaticPulses) {
        this.automaticPulses = automaticPulses;
        setPickOnBounds(true);
        fill.addListener(observable -> updateVisuals());
        animationEnabled.addListener(observable -> {
            if (!isAnimationEnabled()) {
                settle();
                notifyStateSettled();
            }
        });
    }

    /// The paint applied to all feedback.
    private final ObjectProperty<@Nullable Paint> fill = new SimpleObjectProperty<>(this, "fill", Color.BLACK);

    /// Returns the feedback paint property. Null suppresses drawing without changing input or state.
    public ObjectProperty<@Nullable Paint> fillProperty() {
        return fill;
    }

    /// Returns the feedback paint, or null if hidden.
    public @Nullable Paint getFill() {
        return fill.get();
    }

    /// Sets the feedback paint; its intrinsic transparency multiplies the feedback opacity.
    public void setFill(@Nullable Paint value) {
        fill.set(value);
    }

    /// The corner radii of the feedback background.
    private final ObjectProperty<@Nullable CornerRadii> cornerRadii =
            new SimpleObjectProperty<>(this, "cornerRadii", CornerRadii.EMPTY) {
                /// Applies changed radii to the feedback background.
                @Override
                protected void invalidated() {
                    updateVisuals();
                }
            };

    /// Returns the corner radii property. Null means square corners. Percentage radii are relative
    /// to this layer's dimensions; JavaFX background rendering normalizes overlapping corners.
    public ObjectProperty<@Nullable CornerRadii> cornerRadiiProperty() {
        return cornerRadii;
    }

    /// Returns the configured corner radii, or null for square corners.
    public @Nullable CornerRadii getCornerRadii() {
        return cornerRadii.get();
    }

    /// Sets the feedback radii without restarting animation.
    public void setCornerRadii(@Nullable CornerRadii value) {
        cornerRadii.set(value);
    }

    /// Whether subsequent state changes and presses animate.
    private final BooleanProperty animationEnabled = new SimpleBooleanProperty(this, "animationEnabled", AnimationUtils.isAnimationEnabled());

    /// Returns the animation switch. Disabling it immediately displays the current state,
    /// including a held press. Enabling it does not replay a settled transition.
    /// Initially uses [AnimationUtils#isAnimationEnabled()]; global settings are not observed.
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

    /// Reports hover. The idle hover opacity is 8%, below focus and press priority.
    public void setHovered(boolean value) {
        setHovered(value, System.nanoTime());
    }

    /// Reports hover at a monotonic timestamp in nanoseconds, shared with other timed operations.
    void setHovered(boolean value, long now) {
        if (hovered != value) {
            hovered = value;
            changeState(now);
        }
    }

    /// Reports visual focus independently of the layer's actual focus. Focus opacity is 10%,
    /// above hover priority and below press priority.
    public void setFocusActive(boolean value) {
        setFocusActive(value, System.nanoTime());
    }

    /// Reports visual focus at a monotonic timestamp in nanoseconds, shared with other timed operations.
    void setFocusActive(boolean value, long now) {
        if (focusActive != value) {
            focusActive = value;
            changeState(now);
        }
    }

    /// Starts a press, transitioning the entire background to 16% paint opacity in 100 ms.
    /// With animation disabled, the pressed state appears immediately.
    /// Repeated calls before release or reset have no effect. Coordinates are retained for
    /// API compatibility and validated, but do not affect the uniform feedback.
    ///
    /// @throws IllegalArgumentException if either coordinate is not finite
    public void press(double x, double y) {
        press(x, y, System.nanoTime());
    }

    /// Starts a press at a monotonic timestamp in nanoseconds; otherwise follows [#press(double, double)].
    void press(double x, double y, long now) {
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Press coordinates must be finite");
        if (pressActive)
            return;
        pressActive = true;
        changeState(now);
    }

    /// Releases the current press, returning to hover, focus, or idle feedback over 200 ms.
    /// Repeated calls do nothing; animation-disabled changes appear immediately.
    public void release() {
        release(System.nanoTime());
    }

    /// Releases the press at a monotonic timestamp in nanoseconds, shared with other timed operations.
    void release(long now) {
        if (!pressActive)
            return;
        updateAnimations(now);
        pressActive = false;
        changeState(now);
    }

    /// Immediately clears the press, stops pulses, and displays the current
    /// hover or focus state. Repeated calls have no additional effect.
    public void reset() {
        pressActive = false;
        settle();
    }

    /// Returns the opacity of the highest-priority active state.
    private double desiredOpacity() {
        if (pressActive)
            return PRESSED_OPACITY;
        return focusActive ? FOCUS_OPACITY : hovered ? HOVER_OPACITY : 0;
    }

    /// Starts a transition from the currently displayed state rather than its previous target.
    private void changeState(long now) {
        updateAnimations(now);
        if (!isAnimationEnabled()) {
            settle();
            notifyStateSettled();
            return;
        }
        double target = desiredOpacity();
        if (target != targetOpacity) {
            startOpacity = stateOpacity;
            stateDuration = pressActive ? PRESS_DURATION
                    : targetOpacity == PRESSED_OPACITY ? STATE_DURATION
                    : startOpacity <= HOVER_OPACITY && target <= HOVER_OPACITY ? HOVER_DURATION : STATE_DURATION;
            targetOpacity = target;
            stateStart = now;
            transitioning = startOpacity != targetOpacity;
        }
        if (transitioning)
            setRunning(true);
        updateVisuals();
        if (!transitioning)
            notifyStateSettled();
    }

    /// Applies the exact static state and releases the pulse source.
    private void settle() {
        transitioning = false;
        stateOpacity = targetOpacity = desiredOpacity();
        setRunning(false);
        updateVisuals();
    }

    /// Advances all animations to a monotonic timestamp in nanoseconds, then updates their nodes.
    /// The timestamp must use the same time origin as input operations.
    /// Package access permits deterministic tests without exposing a public timing API.
    void advance(long now) {
        boolean wasTransitioning = transitioning;
        updateAnimations(now);
        updateVisuals();
        if (wasTransitioning && !transitioning)
            notifyStateSettled();
    }

    /// Advances logical animation state without publishing intermediate visual changes.
    private void updateAnimations(long now) {
        if (transitioning) {
            // Hover-only fades use a uniform opacity change in both directions.
            boolean hoverOnly = startOpacity <= HOVER_OPACITY && targetOpacity <= HOVER_OPACITY;
            double progress = fraction(now - stateStart, stateDuration);
            Interpolator interpolator = hoverOnly ? Interpolator.LINEAR : Motion.STANDARD;
            stateOpacity = progress == 0 ? startOpacity : progress == 1 ? targetOpacity
                    : interpolator.interpolate(startOpacity, targetOpacity, progress);
            transitioning = progress < 1;
        }
        setRunning(transitioning);
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
                    /// Samples the system monotonic clock used by input operations.
                    @Override
                    public void handle(long now) {
                        advance(System.nanoTime());
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

    /// Returns the current feedback opacity.
    double stateOpacity() {
        return stateOpacity;
    }

    /// Updates both dimensions before adjusting the feedback background.
    @Override
    public void resize(double width, double height) {
        if (width == getWidth() && height == getHeight())
            return;
        super.resize(width, height);
        if (paintBackground != null)
            paintBackground.resize(getWidth(), getHeight());
        updateVisuals();
    }

    /// Publishes the current feedback paint, opacity, and corner radii.
    private void updateVisuals() {
        @Nullable Paint paint = getFill();
        @Nullable CornerRadii radii = getCornerRadii();
        updateBackground(paint, radii == null ? CornerRadii.EMPTY : radii);
    }

    /// Updates the persistent background only when its paint, opacity, or corner radii change.
    private void updateBackground(@Nullable Paint paint, CornerRadii radii) {
        boolean geometryOrPaintChanged = paint != backgroundPaint || !radii.equals(backgroundRadii);
        if (!geometryOrPaintChanged && stateOpacity == backgroundOpacity)
            return;
        backgroundPaint = paint;
        backgroundOpacity = stateOpacity;
        backgroundRadii = radii;
        if (paintBackground != null && (paint instanceof Color || paint == null || stateOpacity == 0)) {
            getChildren().remove(paintBackground);
            paintBackground.setBackground(null);
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
                paintBackground = new Region();
                paintBackground.setManaged(false);
                paintBackground.setMouseTransparent(true);
            }
            paintBackground.resize(getWidth(), getHeight());
            if (geometryOrPaintChanged || paintBackground.getBackground() == null)
                paintBackground.setBackground(new Background(new BackgroundFill(paint, radii, Insets.EMPTY)));
            paintBackground.setOpacity(stateOpacity);
            if (paintBackground.getParent() == null)
                getChildren().add(0, paintBackground);
        }
    }
}
