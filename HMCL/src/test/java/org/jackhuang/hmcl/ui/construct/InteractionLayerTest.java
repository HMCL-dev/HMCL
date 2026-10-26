// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.jackhuang.hmcl.ui.construct.FeedbackTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies deterministic waveform timing, compositing, interruption, and static feedback.
@NotNullByDefault
final class InteractionLayerTest {
    /// Advances a deterministic layer by the specified number of milliseconds.
    private static void advance(InteractionLayer layer, AtomicLong clock, long millis) {
        layer.advance(clock.addAndGet(millis * 1_000_000));
    }

    /// Disabled animation changes states synchronously without waveforms or pulse work.
    @Test
    void staticStatesUsePriorityAndIdempotentPresses() {
        runFx(() -> {
            InteractionLayer layer = new InteractionLayer();
            layer.setAnimationEnabled(false);
            layer.setHovered(true);
            assertEquals(0.08, layer.stateOpacity());
            layer.setFocusActive(true);
            assertEquals(0.10, layer.stateOpacity());
            layer.press(10, 10);
            layer.press(20, 20);
            assertEquals(0.10, layer.stateOpacity());
            assertEquals(0, layer.rippleCount());
            assertFalse(layer.isAnimating());
            layer.release();
            layer.release();
            assertEquals(0.10, layer.stateOpacity());
            layer.setFocusActive(false);
            assertEquals(0.08, layer.stateOpacity());
            layer.setHovered(false);
            assertEquals(0, layer.stateOpacity());
            assertThrows(IllegalArgumentException.class, () -> layer.press(Double.NaN, 0));
        });
    }

    /// Reversing a partial fade preserves its value and reaches exact endpoints.
    @Test
    void stateTransitionsRemainContinuousAndStop() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(clock::get, false);
            layer.setHovered(true);
            advance(layer, clock, 80);
            double entered = layer.stateOpacity();
            assertTrue(entered > 0 && entered < 0.08);
            layer.setHovered(false);
            assertEquals(entered, layer.stateOpacity());
            advance(layer, clock, 80);
            double exited = layer.stateOpacity();
            assertTrue(exited > 0 && exited < entered);
            layer.setHovered(true);
            assertEquals(exited, layer.stateOpacity());
            advance(layer, clock, 200);
            assertEquals(0.08, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            layer.setHovered(false);
            advance(layer, clock, 200);
            assertEquals(0, layer.stateOpacity());
            assertFalse(layer.isAnimating());
        });
    }

    /// Released waves coexist with new presses, while reset removes every phase.
    @Test
    void overlappingWavesFinishAndHeldWavesStopPulsing() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(clock::get, false);
            layer.press(10, 10);
            layer.press(20, 20);
            assertEquals(1, layer.rippleCount());
            advance(layer, clock, 450);
            assertFalse(layer.isAnimating());
            assertEquals(0, layer.stateOpacity());
            layer.release();
            assertTrue(layer.isAnimating());
            advance(layer, clock, 50);
            layer.press(30, 30);
            assertEquals(2, layer.rippleCount());
            advance(layer, clock, 150);
            assertEquals(1, layer.rippleCount());
            layer.release();
            advance(layer, clock, 200);
            assertEquals(0, layer.rippleCount());
            assertEquals(0, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            layer.press(1, 1);
            layer.release();
            layer.press(2, 2);
            layer.setHovered(true);
            layer.reset();
            layer.reset();
            assertEquals(0, layer.rippleCount());
            assertEquals(0.08, layer.stateOpacity());
            assertFalse(layer.isAnimating());
        });
    }

    /// Changing the animation switch settles a held press without replaying it on re-enable.
    @Test
    void disablingAnimationSettlesAnActivePress() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(clock::get, false);
            layer.setHovered(true);
            layer.press(10, 10);
            advance(layer, clock, 60);
            layer.setAnimationEnabled(false);
            assertEquals(0.10, layer.stateOpacity());
            assertEquals(0, layer.rippleCount());
            assertFalse(layer.isAnimating());
            layer.setAnimationEnabled(true);
            assertFalse(layer.isAnimating());
            assertEquals(0.10, layer.stateOpacity());
            layer.press(10, 10);
            assertEquals(0, layer.rippleCount());
            layer.setHovered(false);
            assertEquals(0.10, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            layer.setHovered(true);
            layer.setAnimationEnabled(false);
            layer.release();
            assertEquals(0.08, layer.stateOpacity());
            layer.setAnimationEnabled(true);
            layer.press(10, 10);
            assertEquals(1, layer.rippleCount());
            layer.reset();
        });
    }

    /// Canvas snapshots verify clipping, resizing, live paints, and intrinsic paint transparency.
    @Test
    void renderingUsesOneClipAndPreservesPaintAlpha() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(clock::get, false);
            layer.setWidth(100);
            layer.setHeight(50);
            layer.setCornerRadius(10);
            layer.setAnimationEnabled(false);
            layer.setHovered(true);
            layer.setFill(Color.color(1, 0, 0, 0.5));
            WritableImage hover = snapshot(layer);
            assertEquals(0.04, hover.getPixelReader().getColor(50, 25).getOpacity(), 0.005);
            assertEquals(0, hover.getPixelReader().getColor(0, 0).getOpacity());
            layer.press(50, 25);
            assertEquals(0.05, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);
            layer.setFill(Color.BLUE);
            assertEquals(1, snapshot(layer).getPixelReader().getColor(50, 25).getBlue());
            layer.setFill(null);
            assertEquals(0, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity());
            layer.setFill(Color.TRANSPARENT);
            assertEquals(0, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity());
            layer.reset();
            layer.setFill(Color.BLACK);
            layer.setAnimationEnabled(true);
            layer.press(50, 25);
            advance(layer, clock, 450);
            layer.setWidth(200);
            layer.setHeight(100);
            assertNull(layer.getClip());
            assertEquals(1, layer.rippleCount());
            assertFalse(layer.isAnimating());
            assertEquals(0.172, snapshot(layer).getPixelReader().getColor(190, 90).getOpacity(), 0.005);
            layer.setWidth(0);
            layer.setHeight(0);
            layer.reset();
            assertFalse(layer.isAnimating());
        });
    }

    /// Animated presses add only a waveform, while reduced motion replaces persistent states.
    @Test
    void animatedPressDoesNotDuplicateItsStateLayer() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(clock::get, false);
            layer.setWidth(100);
            layer.setHeight(50);
            layer.press(50, 25);
            advance(layer, clock, 450);
            assertEquals(0, layer.stateOpacity());
            assertEquals(0.10, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);

            layer.setHovered(true);
            advance(layer, clock, 200);
            assertEquals(0.08, layer.stateOpacity());
            assertEquals(0.172, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);
            layer.setFocusActive(true);
            advance(layer, clock, 200);
            assertEquals(0.10, layer.stateOpacity());
            assertEquals(0.19, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);

            layer.setAnimationEnabled(false);
            assertEquals(0, layer.rippleCount());
            assertEquals(0.10, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);
            layer.release();
            assertEquals(0.10, layer.stateOpacity());
            layer.setFocusActive(false);
            assertEquals(0.08, layer.stateOpacity());
            layer.reset();
        });
    }

    /// Captures canvas pixels against transparent surroundings.
    private static WritableImage snapshot(InteractionLayer layer) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return layer.snapshot(parameters, null);
    }
}
