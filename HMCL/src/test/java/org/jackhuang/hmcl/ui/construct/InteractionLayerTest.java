// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.SnapshotParameters;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.ImagePattern;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.jackhuang.hmcl.ui.construct.FeedbackTestSupport.runFx;
import static org.jackhuang.hmcl.ui.construct.FeedbackTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies deterministic waveform timing, compositing, interruption, and static feedback.
@NotNullByDefault
final class InteractionLayerTest {
    /// Initializes the cached animation default with detached settings, without accessing user files.
    @BeforeAll
    static void initializeAnimationDefaults() {
        fx(() -> {
            Field field = SettingsManager.class.getDeclaredField("launcherSettings");
            field.setAccessible(true);
            @Nullable Object previousSettings = field.get(null);
            try {
                LauncherSettings settings = new LauncherSettings();
                settings.animationDisabledProperty().set(true);
                field.set(null, settings);
                AnimationUtils.init();
            } finally {
                field.set(null, previousSettings);
            }
            return true;
        });
    }

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

    /// Hover fades are linear over 100 ms, remain continuous when reversed, and stop at exact endpoints.
    @Test
    void stateTransitionsRemainContinuousAndStop() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(false);
            layer.setAnimationEnabled(true);
            layer.setHovered(true, clock.get());
            advance(layer, clock, 80);
            double entered = layer.stateOpacity();
            assertEquals(0.064, entered, 1e-12);
            layer.setHovered(false, clock.get());
            assertEquals(entered, layer.stateOpacity());
            advance(layer, clock, 80);
            double exited = layer.stateOpacity();
            assertEquals(0.0128, exited, 1e-12);
            layer.setHovered(true, clock.get());
            assertEquals(exited, layer.stateOpacity());
            advance(layer, clock, 50);
            assertEquals((exited + 0.08) / 2, layer.stateOpacity(), 1e-12);
            assertTrue(layer.isAnimating());
            advance(layer, clock, 50);
            assertEquals(0.08, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            layer.setHovered(false, clock.get());
            advance(layer, clock, 50);
            assertEquals(0.04, layer.stateOpacity(), 1e-12);
            assertTrue(layer.isAnimating());
            advance(layer, clock, 50);
            assertEquals(0, layer.stateOpacity());
            assertFalse(layer.isAnimating());

            layer.setFocusActive(true, clock.get());
            advance(layer, clock, 100);
            assertTrue(layer.stateOpacity() > 0 && layer.stateOpacity() < 0.10);
            assertTrue(layer.isAnimating());
            advance(layer, clock, 100);
            assertEquals(0.10, layer.stateOpacity());
            assertFalse(layer.isAnimating());
        });
    }

    /// Released waves coexist with new presses, while reset removes every phase.
    @Test
    void overlappingWavesFinishAndHeldWavesStopPulsing() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(false);
            layer.setAnimationEnabled(true);
            layer.press(10, 10, clock.get());
            layer.press(20, 20, clock.get());
            assertEquals(1, layer.rippleCount());
            advance(layer, clock, 450);
            assertFalse(layer.isAnimating());
            assertEquals(0, layer.stateOpacity());
            layer.release(clock.get());
            assertTrue(layer.isAnimating());
            advance(layer, clock, 50);
            layer.press(30, 30, clock.get());
            assertEquals(2, layer.rippleCount());
            advance(layer, clock, 150);
            assertEquals(1, layer.rippleCount());
            layer.release(clock.get());
            advance(layer, clock, 200);
            assertEquals(0, layer.rippleCount());
            assertEquals(0, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            layer.press(1, 1, clock.get());
            layer.release(clock.get());
            layer.press(2, 2, clock.get());
            layer.setHovered(true, clock.get());
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
            InteractionLayer layer = new InteractionLayer(false);
            layer.setAnimationEnabled(true);
            layer.setHovered(true, clock.get());
            layer.press(10, 10, clock.get());
            advance(layer, clock, 60);
            layer.setAnimationEnabled(false);
            assertEquals(0.10, layer.stateOpacity());
            assertEquals(0, layer.rippleCount());
            assertFalse(layer.isAnimating());
            layer.setAnimationEnabled(true);
            assertFalse(layer.isAnimating());
            assertEquals(0.10, layer.stateOpacity());
            layer.press(10, 10, clock.get());
            assertEquals(0, layer.rippleCount());
            layer.setHovered(false, clock.get());
            assertEquals(0.10, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            layer.setHovered(true, clock.get());
            layer.setAnimationEnabled(false);
            layer.release(clock.get());
            assertEquals(0.08, layer.stateOpacity());
            layer.setAnimationEnabled(true);
            layer.press(10, 10, clock.get());
            assertEquals(1, layer.rippleCount());
            layer.reset();
        });
    }

    /// Snapshots verify clipping, resizing, live paints, and intrinsic paint transparency.
    @Test
    void renderingUsesOneClipAndPreservesPaintAlpha() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(false);
            layer.resize(100, 50);
            layer.setCornerRadius(10);
            layer.setAnimationEnabled(false);
            layer.setHovered(true, clock.get());
            layer.setFill(Color.color(1, 0, 0, 0.5));
            WritableImage hover = snapshot(layer);
            assertEquals(0.04, hover.getPixelReader().getColor(50, 25).getOpacity(), 0.005);
            assertEquals(0, hover.getPixelReader().getColor(0, 0).getOpacity());
            layer.press(50, 25, clock.get());
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
            layer.press(50, 25, clock.get());
            advance(layer, clock, 450);
            layer.resize(200, 100);
            assertNull(layer.getClip());
            assertEquals(1, layer.rippleCount());
            assertFalse(layer.isAnimating());
            assertEquals(0.172, snapshot(layer).getPixelReader().getColor(190, 90).getOpacity(), 0.005);
            layer.resize(0, 0);
            layer.reset();
            assertFalse(layer.isAnimating());
        });
    }

    /// Animated presses add only a waveform, while reduced motion replaces persistent states.
    @Test
    void animatedPressDoesNotDuplicateItsStateLayer() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(false);
            layer.setAnimationEnabled(true);
            layer.resize(100, 50);
            layer.press(50, 25, clock.get());
            advance(layer, clock, 450);
            assertEquals(0, layer.stateOpacity());
            assertEquals(0.10, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);

            layer.setHovered(true, clock.get());
            advance(layer, clock, 200);
            assertEquals(0.08, layer.stateOpacity());
            assertEquals(0.172, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);
            layer.setFocusActive(true, clock.get());
            advance(layer, clock, 200);
            assertEquals(0.10, layer.stateOpacity());
            assertEquals(0.19, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);

            layer.setAnimationEnabled(false);
            assertEquals(0, layer.rippleCount());
            assertEquals(0.10, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);
            layer.release(clock.get());
            assertEquals(0.10, layer.stateOpacity());
            layer.setFocusActive(false, clock.get());
            assertEquals(0.08, layer.stateOpacity());
            layer.reset();
        });
    }

    /// Idle state feedback has no children and repeated resets preserve the same background.
    @Test
    void settledBackgroundAvoidsRedundantUpdates() {
        runFx(() -> {
            InteractionLayer layer = new InteractionLayer();
            layer.resize(100, 50);
            layer.setAnimationEnabled(false);
            layer.setHovered(true);
            Background background = Objects.requireNonNull(layer.getBackground());
            for (int i = 0; i < 20; i++) {
                layer.reset();
                layer.resize(100 + i, 50);
                assertSame(background, layer.getBackground());
                assertTrue(layer.getChildrenUnmodifiable().isEmpty());
                assertNull(layer.getClip());
            }
            layer.setHovered(false);
            assertNull(layer.getBackground());
        });
    }

    /// Overlapping waves share one clip and leave no attached circles after reset or completion.
    @Test
    void completedWaveformsDetachWithoutAffectingPicking() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(false);
            layer.setAnimationEnabled(true);
            layer.resize(100, 50);
            layer.setCornerRadius(12);
            for (int i = 0; i < 8; i++) {
                layer.press(i, 25, clock.get());
                layer.release(clock.get());
            }
            Group group = (Group) layer.getChildrenUnmodifiable().getFirst();
            assertEquals(8, group.getChildren().size());
            assertNotNull(group.getClip());
            assertNull(layer.getClip());
            assertTrue(layer.contains(0, 0));
            @Unmodifiable List<Node> previous = List.copyOf(group.getChildren());
            layer.reset();
            assertTrue(layer.getChildrenUnmodifiable().isEmpty());
            assertTrue(group.getChildren().isEmpty());
            assertNull(group.getClip());
            assertTrue(previous.stream().allMatch(node -> node.getParent() == null));
            for (int i = 0; i < 3; i++) {
                layer.press(20, 25, clock.get());
                layer.release(clock.get());
            }
            assertSame(group, layer.getChildrenUnmodifiable().getFirst());
            assertTrue(group.getChildren().stream().noneMatch(previous::contains));
            @Unmodifiable List<Node> released = List.copyOf(group.getChildren());
            advance(layer, clock, 200);
            assertTrue(layer.getChildrenUnmodifiable().isEmpty());
            assertTrue(group.getChildren().isEmpty());
            assertNull(group.getClip());
            assertTrue(released.stream().allMatch(node -> node.getParent() == null));
            assertFalse(layer.isAnimating());
        });
    }

    /// Gradient and image paints multiply their intrinsic alpha without fading child waveforms.
    @Test
    void nonColorPaintsPreserveStateAndWaveformAlpha() {
        runFx(() -> {
            WritableImage image = new WritableImage(1, 1);
            Color translucent = Color.color(1, 0, 0, 0.5);
            image.getPixelWriter().setColor(0, 0, translucent);
            @Unmodifiable List<Paint> paints = List.of(
                    new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                            new Stop(0, translucent), new Stop(1, translucent)),
                    new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                            new Stop(0, translucent), new Stop(1, translucent)),
                    new ImagePattern(image));
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(false);
            layer.resize(100, 50);
            layer.setCornerRadius(10);
            layer.setAnimationEnabled(false);
            layer.setHovered(true, clock.get());
            for (Paint paint : paints) {
                layer.setFill(paint);
                assertEquals(0.04, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);
                assertEquals(0, snapshot(layer).getPixelReader().getColor(0, 0).getOpacity());
                layer.setAnimationEnabled(true);
                layer.press(50, 25, clock.get());
                advance(layer, clock, 450);
                assertEquals(0.088, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);
                assertEquals(0, snapshot(layer).getPixelReader().getColor(0, 0).getOpacity());
                layer.setAnimationEnabled(false);
                assertEquals(0.05, snapshot(layer).getPixelReader().getColor(50, 25).getOpacity(), 0.005);
                layer.reset();
            }
            layer.setFill(Color.BLUE);
            assertTrue(layer.getChildrenUnmodifiable().isEmpty());
            layer.setFill(null);
            assertNull(layer.getBackground());
        });
    }

    /// Resizing changes a waveform's covering radius without changing its expansion progress.
    @Test
    void resizingPreservesWaveformProgressAndClipsItsBounds() {
        runFx(() -> {
            AtomicLong clock = new AtomicLong();
            InteractionLayer layer = new InteractionLayer(false);
            layer.setAnimationEnabled(true);
            layer.resize(100, 50);
            layer.setCornerRadius(10);
            layer.press(20, 25, clock.get());
            advance(layer, clock, 100);
            Group group = (Group) layer.getChildrenUnmodifiable().getFirst();
            Circle circle = (Circle) group.getChildren().getFirst();
            double progress = circle.getScaleX();
            double radius = circle.getRadius();
            layer.resize(200, 100);
            assertEquals(progress, circle.getScaleX());
            assertTrue(circle.getRadius() > radius);
            advance(layer, clock, 350);
            WritableImage pixels = snapshot(layer);
            assertEquals(200, pixels.getWidth());
            assertEquals(100, pixels.getHeight());
            assertEquals(0.10, pixels.getPixelReader().getColor(190, 90).getOpacity(), 0.005);
            assertEquals(0, pixels.getPixelReader().getColor(0, 0).getOpacity());
            layer.reset();
        });
    }

    /// Captures feedback pixels against transparent surroundings.
    private static WritableImage snapshot(InteractionLayer layer) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return layer.snapshot(parameters, null);
    }
}
