// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.application.Platform;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies pixel queue behavior independently of control skins and input event dispatch.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class WheelScrollAnimationTest {
    /// Regular wheel input converges to a steady speed instead of replaying a fast start on every notch.
    @Test
    void regularInputMaintainsSteadySpeed() throws Exception {
        onFxThread(() -> {
            for (int interval : new int @Unmodifiable []{40, 60, 80, 100}) {
                Surface surface = new Surface();
                surface.maxY = 100_000;
                WheelScrollAnimation animation = new WheelScrollAnimation(surface);
                try {
                    long start = System.nanoTime();
                    double previous = 0;
                    double sum = 0;
                    double sumSquares = 0;
                    int samples = 0;
                    int inputs = 0;
                    for (int millis = 0; millis <= 2000; millis += 10) {
                        long now = start + millis * 1_000_000L;
                        if (millis % interval == 0) {
                            assertTrue(animation.offer(0, 40, now));
                            inputs++;
                        }
                        animation.handle(now);
                        double movement = surface.y - previous;
                        previous = surface.y;
                        if (millis >= 600) {
                            assertTrue(movement > 0);
                            sum += movement;
                            sumSquares += movement * movement;
                            samples++;
                        }
                    }
                    double mean = sum / samples;
                    double deviation = Math.sqrt(Math.max(0, sumSquares / samples - mean * mean));
                    assertTrue(deviation / mean < 0.12, "Speed variation at interval " + interval + " ms: " + deviation / mean);
                    animation.handle(start + 2_150_000_000L);
                    assertEquals(inputs * 40, surface.y, 0.0001);
                    assertFalse(animation.isRunning());
                } finally {
                    animation.cancel();
                }
            }
        });
    }

    /// Retargeting preserves velocity on both sides of an input arriving between regular animation pulses.
    @Test
    void additionalInputDoesNotJumpVelocity() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                assertTrue(animation.offer(0, 100, start));
                animation.handle(start + 49_900_000L);
                double before = surface.y;
                animation.handle(start + 50_000_000L);
                double previousStep = surface.y - before;
                assertTrue(previousStep > 0);
                before = surface.y;
                assertTrue(animation.offer(0, 40, start + 50_000_000L));
                animation.handle(start + 50_100_000L);
                assertEquals(previousStep, surface.y - before, previousStep * 0.01);
                animation.handle(start + 200_000_000L);
                assertEquals(140, surface.y, 0.0001);
            } finally {
                animation.cancel();
            }
        });
    }

    /// Input between pulses advances the same trajectory regardless of the rendering cadence.
    @Test
    void inputTimingIsIndependentOfPulseCadence() throws Exception {
        onFxThread(() -> {
            double expected = -1;
            for (int frameInterval : new int @Unmodifiable []{5, 16, 33}) {
                Surface surface = new Surface();
                WheelScrollAnimation animation = new WheelScrollAnimation(surface);
                try {
                    long start = System.nanoTime();
                    for (int millis = 0; millis <= 280; millis++) {
                        long now = start + millis * 1_000_000L;
                        if (millis % 70 == 0)
                            assertTrue(animation.offer(0, 40, now));
                        if (millis % frameInterval == 0 || millis == 280)
                            animation.handle(now);
                    }
                    if (expected < 0)
                        expected = surface.y;
                    else
                        assertEquals(expected, surface.y, 0.0001);
                    animation.handle(start + 430_000_000L);
                    assertEquals(200, surface.y, 0.0001);
                } finally {
                    animation.cancel();
                }
            }
        });
    }

    /// Reversing a moving axis clears its forward velocity while preserving the other axis's distance.
    @Test
    void reversalDiscardsExistingVelocity() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            surface.y = 500;
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                assertTrue(animation.offer(80, 100, start));
                animation.handle(start + 50_000_000L);
                double before = surface.y;
                animation.cancelReversal(0, -40);
                assertTrue(animation.offer(0, -40, start + 50_000_000L));
                animation.handle(start + 51_000_000L);
                assertTrue(surface.y < before);
                animation.handle(start + 200_000_000L);
                assertEquals(before - 40, surface.y, 0.0001);
                assertEquals(80, surface.x, 0.0001);
            } finally {
                animation.cancel();
            }
        });
    }

    /// Retargeting after partial playback preserves every accepted pixel across unequal frame intervals.
    @Test
    void retargetingPreservesDistance() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                assertTrue(animation.offer(0, 238, start));
                assertEquals(0, surface.y);
                animation.handle(start + 20_000_000L);
                assertTrue(surface.y > 0 && surface.y < 238);
                assertTrue(animation.offer(0, 238, start + 20_000_000L));
                animation.handle(start + 55_000_000L);
                assertTrue(animation.offer(0, 238, start + 55_000_000L));
                animation.handle(start + 70_000_000L);
                animation.handle(start + 205_000_000L);
                assertEquals(714, surface.y, 0.0001);
                assertFalse(animation.isRunning());
            } finally {
                animation.cancel();
            }
        });
    }

    /// An unapplied queue reserves boundary space, so another event can fall through to an ancestor.
    @Test
    void pendingMovementReservesBounds() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            surface.maxY = 10;
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                assertTrue(animation.offer(0, 80, start));
                assertFalse(animation.offer(0, 80, start));
                assertEquals(0, surface.y);
                animation.handle(start + 150_000_000L);
                assertEquals(10, surface.y, 0.0001);
                assertFalse(animation.isRunning());
            } finally {
                animation.cancel();
            }
        });
    }

    /// Losing space on one axis during playback leaves the other axis's accepted distance intact.
    @Test
    void shrinkingBoundsOnlyStopsBlockedAxis() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                assertTrue(animation.offer(80, 80, start));
                animation.handle(start + 20_000_000L);
                surface.maxY = surface.y;
                double stoppedY = surface.y;
                animation.handle(start + 50_000_000L);
                animation.handle(start + 150_000_000L);
                assertEquals(80, surface.x, 0.0001);
                assertEquals(stoppedY, surface.y, 0.0001);
                assertFalse(animation.isRunning());
            } finally {
                animation.cancel();
            }
        });
    }

    /// Reversing at a boundary cancels that axis even when no new movement can be accepted.
    @Test
    void reversalCancelsOnlyOpposingAxis() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                assertTrue(animation.offer(40, 80, start));
                animation.cancelReversal(0, -80);
                assertFalse(animation.offer(0, -80, start));
                animation.handle(start + 150_000_000L);
                assertEquals(40, surface.x, 0.0001);
                assertEquals(0, surface.y);
                assertFalse(animation.isRunning());
            } finally {
                animation.cancel();
            }
        });
    }

    /// Cancellation releases the old queue while allowing the same animation to accept fresh input.
    @Test
    void cancelledMovementDoesNotResume() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                assertTrue(animation.offer(40, 80, start));
                animation.cancel();
                animation.handle(start + 150_000_000L);
                assertEquals(0, surface.x);
                assertEquals(0, surface.y);
                assertFalse(animation.isRunning());
                assertTrue(animation.offer(0, 20, start + 150_000_000L));
                animation.handle(start + 300_000_000L);
                assertEquals(0, surface.x);
                assertEquals(20, surface.y, 0.0001);
                assertFalse(animation.isRunning());
            } finally {
                animation.cancel();
            }
        });
    }

    /// A synchronous cancellation cannot leave old pixels for the next wheel input or move the other axis.
    @Test
    void cancellationDuringFrameDiscardsRemainingMovement() throws Exception {
        onFxThread(() -> {
            for (boolean horizontal : new boolean @Unmodifiable []{true, false}) {
                Surface surface = new Surface();
                WheelScrollAnimation animation = new WheelScrollAnimation(surface);
                try {
                    long start = System.nanoTime();
                    surface.callbackHorizontal = horizontal;
                    surface.afterScroll = () -> {
                        animation.cancel();
                        surface.x = 0;
                        surface.y = 0;
                    };
                    assertTrue(animation.offer(100, 100, start));
                    animation.handle(start + 20_000_000L);
                    assertFalse(animation.isRunning());
                    assertEquals(0, surface.x);
                    assertEquals(0, surface.y);
                    assertTrue(animation.offer(10, 20, start + 20_000_000L));
                    animation.handle(start + 170_000_000L);
                    assertEquals(10, surface.x, 0.0001);
                    assertEquals(20, surface.y, 0.0001);
                } finally {
                    animation.cancel();
                }
            }
        });
    }

    /// The final frame of an old animation cannot stop or overwrite a replacement queued by a listener.
    @Test
    void replacementDuringFrameSurvivesOldCompletion() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                surface.callbackHorizontal = true;
                surface.afterScroll = () -> {
                    animation.cancel();
                    surface.x = 0;
                    surface.y = 0;
                    assertTrue(animation.offer(10, 20, start));
                };
                assertTrue(animation.offer(100, 100, start));
                animation.handle(start + 150_000_000L);
                assertTrue(animation.isRunning());
                assertEquals(0, surface.x);
                assertEquals(0, surface.y);
                animation.handle(start + 150_000_000L);
                assertEquals(10, surface.x, 0.0001);
                assertEquals(20, surface.y, 0.0001);
            } finally {
                animation.cancel();
            }
        });
    }

    /// Input accepted during a frame accumulates only the pixels that have not yet been applied.
    @Test
    void inputDuringFramePreservesAcceptedDistance() throws Exception {
        onFxThread(() -> {
            Surface surface = new Surface();
            WheelScrollAnimation animation = new WheelScrollAnimation(surface);
            try {
                long start = System.nanoTime();
                surface.afterScroll = () -> assertTrue(animation.offer(0, 20, start + 20_000_000L));
                assertTrue(animation.offer(0, 100, start));
                animation.handle(start + 20_000_000L);
                animation.handle(start + 170_000_000L);
                assertEquals(120, surface.y, 0.0001);
            } finally {
                animation.cancel();
            }
        });
    }

    /// Executes manual frames between toolkit pulses and propagates failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<Boolean> task = new FutureTask<>(action, true);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    /// Models a two-axis scrollable area without constructing any JavaFX controls.
    @NotNullByDefault
    private static final class Surface implements WheelScrollAnimation.Target {
        /// Applied horizontal position in pixels.
        private double x;

        /// Applied vertical position in pixels.
        private double y;

        /// Mutable vertical boundary used to simulate content resizing during playback.
        private double maxY = 1000;

        /// One-shot position listener invoked synchronously after movement.
        private @Nullable Runnable afterScroll;

        /// Axis whose movement triggers the position listener.
        private boolean callbackHorizontal;

        /// Bounds movement against each axis's remaining pixel range.
        @Override
        public double limit(boolean horizontal, double pixels) {
            double position = horizontal ? x : y;
            return MathUtils.clamp(pixels, -position, (horizontal ? 1000 : maxY) - position);
        }

        /// Applies exactly the distance allowed by the current bounds.
        @Override
        public double scroll(boolean horizontal, double pixels) {
            double distance = limit(horizontal, pixels);
            if (horizontal)
                x += distance;
            else
                y += distance;
            if (distance != 0 && horizontal == callbackHorizontal && afterScroll != null) {
                Runnable callback = afterScroll;
                afterScroll = null;
                callback.run();
            }
            return distance;
        }
    }
}
