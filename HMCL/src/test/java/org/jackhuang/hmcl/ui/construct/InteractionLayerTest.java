// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies uniform press feedback and interrupted transitions with deterministic pulse times.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
@Isolated("Temporarily supplies settings while initializing the cached animation policy")
public final class InteractionLayerTest {
    /// Initializes animation defaults without reading or changing persisted settings.
    @BeforeAll
    static void initializeAnimationPolicy() throws Exception {
        Field settingsField = SettingsManager.class.getDeclaredField("launcherSettings");
        settingsField.setAccessible(true);
        @Nullable Object previous = settingsField.get(null);
        if (previous == null)
            settingsField.set(null, new LauncherSettings());
        try {
            AnimationUtils.init();
        } finally {
            settingsField.set(null, previous);
        }
    }

    /// Checks uniform background rendering, press timing, and restoration of hover feedback.
    @Test
    void pressTintsWholeBackground() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setCornerRadii(new CornerRadii(8));
            layer.setHovered(true, 0);
            layer.advance(100_000_000);
            assertEquals(0.08, layer.getOpacity());
            layer.press(100_000_000);
            layer.advance(150_000_000);
            assertEquals(0.12, layer.getOpacity(), 1e-12);
            layer.advance(200_000_000);
            assertEquals(0.16, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            assertTrue(layer.getChildrenUnmodifiable().isEmpty());
            var fill = Objects.requireNonNull(layer.getBackground()).getFills().get(0);
            assertEquals(1.0, ((Color) fill.getFill()).getOpacity());
            assertEquals(new CornerRadii(8), fill.getRadii());
            layer.release(200_000_000);
            layer.advance(250_000_000);
            assertEquals(0.12, layer.getOpacity(), 1e-12);
            layer.advance(300_000_000);
            assertEquals(0.08, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Checks that pressing halfway through hover takes another 150 ms without changing speed.
    @Test
    void pressDuringHoverKeepsLinearRate() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setHovered(true, 0);
            // Input must sample the in-progress hover even when no pulse ran at this timestamp.
            layer.press(50_000_000);
            assertEquals(0.04, layer.getOpacity(), 1e-12);
            layer.advance(100_000_000);
            assertEquals(0.08, layer.getOpacity(), 1e-12);
            layer.advance(150_000_000);
            assertEquals(0.12, layer.getOpacity(), 1e-12);
            assertTrue(layer.isStateTransitioning());
            layer.advance(200_000_000);
            assertEquals(0.16, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Checks that early release and repeated presses continue at the same opacity rate.
    @Test
    void rapidClicksRemainContinuous() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.press(0);
            layer.advance(20_000_000);
            double pressed = layer.getOpacity();
            layer.release(20_000_000);
            assertEquals(pressed, layer.getOpacity());
            layer.advance(30_000_000);
            double fading = layer.getOpacity();
            assertEquals(0.008, fading, 1e-12);
            layer.press(30_000_000);
            assertEquals(fading, layer.getOpacity());
            layer.press(50_000_000);
            layer.advance(120_000_000);
            assertEquals(0.08, layer.getOpacity(), 1e-12);
            layer.advance(220_000_000);
            assertEquals(0.16, layer.getOpacity());
            layer.release(220_000_000);
            layer.advance(420_000_000);
            assertEquals(0, layer.getOpacity());
            assertTrue(layer.contains(100, 20));
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Checks focus priority, reset, and disabling animations during a held press.
    @Test
    void resetAndAnimationSwitchSettleFeedback() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setHovered(true, 0);
            layer.setFocusActive(true, 0);
            layer.press(0);
            layer.advance(50_000_000);
            layer.setAnimationEnabled(false);
            assertEquals(0.16, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            layer.release(50_000_000);
            assertEquals(0.10, layer.getOpacity());
            layer.setAnimationEnabled(true);
            layer.press(60_000_000);
            layer.reset();
            assertEquals(0.10, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            layer.setFocusActive(false, 60_000_000);
            layer.advance(72_500_000);
            assertEquals(0.09, layer.getOpacity(), 1e-12);
            layer.advance(85_000_000);
            assertEquals(0.08, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Creates an animated layer whose pulses are controlled entirely by each test.
    private static InteractionLayer createLayer() {
        InteractionLayer layer = new InteractionLayer(false);
        layer.setAnimationEnabled(true);
        layer.resize(200, 40);
        return layer;
    }

    /// Checks that arbitrary paints keep their alpha and backgrounds are reused throughout fades.
    @Test
    void paintsAndCornersRemainIndependentOfAnimation() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            var gradient = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(1, 0, 0, 0.5)), new Stop(1, Color.TRANSPARENT));
            layer.setFill(gradient);
            var background = Objects.requireNonNull(layer.getBackground());
            layer.press(0);
            layer.advance(50_000_000);
            assertSame(background, layer.getBackground());
            assertSame(gradient, background.getFills().get(0).getFill());
            double opacity = layer.getOpacity();
            layer.setCornerRadii(new CornerRadii(0.5, true));
            layer.resize(300, 60);
            assertEquals(opacity, layer.getOpacity());
            assertEquals(new CornerRadii(0.5, true),
                    Objects.requireNonNull(layer.getBackground()).getFills().get(0).getRadii());
            layer.setFill(null);
            assertNull(layer.getBackground());
            assertTrue(layer.contains(150, 30));
            layer.advance(200_000_000);
            layer.setFill(Color.color(0, 0, 1, 0.5));
            assertEquals(0.16, layer.getOpacity());
            assertEquals(0.5, ((Color) Objects.requireNonNull(layer.getBackground())
                    .getFills().get(0).getFill()).getOpacity());
            assertTrue(layer.getChildrenUnmodifiable().isEmpty());
        });
    }

    /// Checks that completion notifies once, while cancellation by reset remains silent.
    @Test
    void settlementCallbackFollowsCompletion() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            var notifications = new java.util.concurrent.atomic.AtomicInteger();
            layer.setOnStateSettled(notifications::incrementAndGet);
            layer.setHovered(true, 0);
            layer.advance(100_000_000);
            layer.advance(200_000_000);
            assertEquals(1, notifications.get());
            layer.press(200_000_000);
            layer.reset();
            layer.advance(300_000_000);
            assertEquals(1, notifications.get());
            layer.press(300_000_000);
            layer.setAnimationEnabled(false);
            assertEquals(2, notifications.get());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Runs assertions on the JavaFX thread and propagates failures to the test worker.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
