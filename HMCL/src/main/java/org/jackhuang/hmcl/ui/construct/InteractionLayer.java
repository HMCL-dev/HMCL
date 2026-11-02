// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.animation.AnimationTimer;
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

/// Draws uniform feedback behind the content of [RipplerContainer].
/// Press takes priority over focus and hover. Node opacity multiplies the paint's own alpha,
/// supporting colors, gradients, and image patterns without rebuilding the background per frame.
/// All animated state changes are linear at eight opacity percentage points per 100 ms.
/// Picking remains rectangular, including transparent corners and zero-opacity idle states.
/// All operations must occur on the JavaFX application thread.
@NotNullByDefault
final class InteractionLayer extends Region {
    /// The hover tint opacity.
    private static final double HOVER_OPACITY = 0.08;

    /// The focus tint opacity.
    private static final double FOCUS_OPACITY = 0.10;

    /// The press tint opacity.
    private static final double PRESSED_OPACITY = 0.16;

    /// The time per unit opacity, calibrated so idle-to-hover takes 100 ms.
    private static final double NANOS_PER_OPACITY = Motion.SHORT2.toMillis() * 1_000_000 / HOVER_OPACITY;

    /// Whether real pulses are scheduled; tests supply deterministic timestamps instead.
    private final boolean automaticPulses;

    /// The feedback paint; null hides drawing without affecting input.
    private @Nullable Paint fill = Color.BLACK;

    /// The feedback corners, with percentages resolved against this region.
    private CornerRadii cornerRadii = CornerRadii.EMPTY;

    /// Whether state changes animate, initially taken from the loaded settings.
    private boolean animationEnabled = AnimationUtils.isAnimationEnabled();

    /// The lazily allocated, reusable pulse source.
    private @Nullable AnimationTimer timer;

    /// Whether the owner reports hover.
    private boolean hovered;

    /// Whether the owner reports visual focus.
    private boolean focusActive;

    /// Whether a press is held.
    private boolean pressActive;

    /// The opacity at the start of the current transition.
    private double startOpacity;

    /// The target opacity of the current transition.
    private double targetOpacity;

    /// The monotonic start timestamp of the current transition.
    private long stateStart;

    /// The duration calculated from the remaining opacity distance, in nanoseconds.
    private long stateDuration;

    /// Whether the target opacity has not yet been reached.
    private boolean transitioning;

    /// The optional callback after a transition settles; reset does not invoke it.
    private @Nullable Runnable onStateSettled;

    /// Creates feedback using the loaded animation setting.
    InteractionLayer() {
        this(true);
    }

    /// Creates feedback with optional real pulses for deterministic tests.
    InteractionLayer(boolean automaticPulses) {
        this.automaticPulses = automaticPulses;
        setPickOnBounds(true);
        setOpacity(0);
        updateBackground();
    }

    /// Updates the paint without restarting the current transition.
    void setFill(@Nullable Paint value) {
        if (fill != value) {
            fill = value;
            updateBackground();
        }
    }

    /// Updates the corners without restarting the current transition.
    void setCornerRadii(CornerRadii value) {
        if (!cornerRadii.equals(value)) {
            cornerRadii = value;
            updateBackground();
        }
    }

    /// Rebuilds the background only when the paint or corner radii change.
    private void updateBackground() {
        setBackground(fill == null ? null : new Background(new BackgroundFill(fill, cornerRadii, Insets.EMPTY)));
    }

    /// Sets the settlement callback without invoking it.
    void setOnStateSettled(@Nullable Runnable listener) {
        onStateSettled = listener;
    }

    /// Returns whether the feedback is still transitioning.
    boolean isStateTransitioning() {
        return transitioning;
    }

    /// Notifies the owner after feedback has reached its current target.
    private void notifyStateSettled() {
        if (onStateSettled != null)
            onStateSettled.run();
    }

    /// Disabling animation immediately settles feedback; enabling it does not replay a transition.
    void setAnimationEnabled(boolean value) {
        if (animationEnabled == value)
            return;
        animationEnabled = value;
        if (!value) {
            settle();
            notifyStateSettled();
        }
    }

    /// Reports hover, below focus and press priority.
    void setHovered(boolean value) {
        setHovered(value, System.nanoTime());
    }

    /// Reports hover using the monotonic clock shared by all timed operations.
    void setHovered(boolean value, long now) {
        if (hovered != value) {
            hovered = value;
            changeState(now);
        }
    }

    /// Reports visual focus independently of the node's actual focus.
    void setFocusActive(boolean value) {
        setFocusActive(value, System.nanoTime());
    }

    /// Reports focus using the monotonic clock shared by all timed operations.
    void setFocusActive(boolean value, long now) {
        if (focusActive != value) {
            focusActive = value;
            changeState(now);
        }
    }

    /// Starts uniform press feedback at the shared linear rate; repeated presses have no effect.
    void press() {
        press(System.nanoTime());
    }

    /// Starts a press using the monotonic clock shared by all timed operations.
    void press(long now) {
        if (!pressActive) {
            pressActive = true;
            changeState(now);
        }
    }

    /// Restores hover, focus, or idle at the shared linear rate; repeated releases have no effect.
    void release() {
        release(System.nanoTime());
    }

    /// Releases a press using the monotonic clock shared by all timed operations.
    void release(long now) {
        if (pressActive) {
            pressActive = false;
            changeState(now);
        }
    }

    /// Cancels the press and immediately restores hover or focus without notifying settlement.
    void reset() {
        pressActive = false;
        settle();
    }

    /// Returns the opacity of the highest-priority active state.
    private double desiredOpacity() {
        return pressActive ? PRESSED_OPACITY : focusActive ? FOCUS_OPACITY : hovered ? HOVER_OPACITY : 0;
    }

    /// Samples the current fade and retargets it with a duration proportional to the remaining distance.
    private void changeState(long now) {
        updateAnimation(now);
        if (!animationEnabled) {
            settle();
            notifyStateSettled();
            return;
        }
        double target = desiredOpacity();
        if (target != targetOpacity) {
            startOpacity = getOpacity();
            stateDuration = Math.max(1, Math.round(Math.abs(target - startOpacity) * NANOS_PER_OPACITY));
            targetOpacity = target;
            stateStart = now;
            setTransitioning(startOpacity != targetOpacity);
        }
        if (!transitioning)
            notifyStateSettled();
    }

    /// Stops pulses and displays the current state immediately.
    private void settle() {
        setTransitioning(false);
        targetOpacity = desiredOpacity();
        setOpacity(targetOpacity);
    }

    /// Advances feedback to a monotonic timestamp, notifying settlement once on completion.
    void advance(long now) {
        boolean wasTransitioning = transitioning;
        updateAnimation(now);
        if (wasTransitioning && !transitioning)
            notifyStateSettled();
    }

    /// Samples the transition without notifying the owner during an input-state change.
    private void updateAnimation(long now) {
        if (!transitioning)
            return;
        double progress = Math.max(0, Math.min(1, (double) (now - stateStart) / stateDuration));
        setOpacity(progress == 0 ? startOpacity : progress == 1 ? targetOpacity
                : startOpacity + (targetOpacity - startOpacity) * progress);
        setTransitioning(progress < 1);
    }

    /// Keeps transition status and the reusable pulse source in sync.
    private void setTransitioning(boolean value) {
        if (transitioning == value)
            return;
        transitioning = value;
        if (!automaticPulses)
            return;
        if (value) {
            if (timer == null) {
                timer = new AnimationTimer() {
                    /// Samples the same monotonic clock used by input operations.
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
}
