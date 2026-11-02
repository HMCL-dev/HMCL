// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
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
            assertEquals(0.08, layer.stateOpacity());
            layer.press(5, 7, 100_000_000);
            layer.advance(150_000_000);
            assertTrue(layer.stateOpacity() > 0.08 && layer.stateOpacity() < 0.16);
            layer.advance(200_000_000);
            assertEquals(0.16, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            assertTrue(layer.getChildrenUnmodifiable().isEmpty());
            var fill = Objects.requireNonNull(layer.getBackground()).getFills().get(0);
            assertEquals(0.16, ((Color) fill.getFill()).getOpacity(), 1e-7);
            assertEquals(new CornerRadii(8), fill.getRadii());
            layer.release(200_000_000);
            layer.advance(300_000_000);
            assertTrue(layer.stateOpacity() > 0.08 && layer.stateOpacity() < 0.16);
            layer.advance(400_000_000);
            assertEquals(0.08, layer.stateOpacity());
            assertFalse(layer.isAnimating());
        });
    }

    /// Checks that early release and repeated presses continue from the displayed tint.
    @Test
    void rapidClicksRemainContinuous() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.press(0, 0, 0);
            layer.advance(20_000_000);
            double pressed = layer.stateOpacity();
            layer.release(20_000_000);
            assertEquals(pressed, layer.stateOpacity());
            layer.advance(50_000_000);
            double fading = layer.stateOpacity();
            assertTrue(fading > 0 && fading < pressed);
            layer.press(100, 100, 50_000_000);
            assertEquals(fading, layer.stateOpacity());
            layer.press(50, 50, 70_000_000);
            layer.advance(150_000_000);
            assertEquals(0.16, layer.stateOpacity());
            layer.release(150_000_000);
            layer.advance(350_000_000);
            assertEquals(0, layer.stateOpacity());
            assertNull(layer.getBackground());
            assertFalse(layer.isAnimating());
        });
    }

    /// Checks focus priority, reset, and disabling animations during a held press.
    @Test
    void resetAndAnimationSwitchSettleFeedback() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setHovered(true, 0);
            layer.setFocusActive(true, 0);
            layer.press(0, 0, 0);
            layer.advance(50_000_000);
            layer.setAnimationEnabled(false);
            assertEquals(0.16, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            layer.release(50_000_000);
            assertEquals(0.10, layer.stateOpacity());
            layer.setAnimationEnabled(true);
            layer.press(0, 0, 60_000_000);
            layer.reset();
            assertEquals(0.10, layer.stateOpacity());
            assertFalse(layer.isAnimating());
            layer.setFocusActive(false, 60_000_000);
            layer.advance(260_000_000);
            assertEquals(0.08, layer.stateOpacity());
        });
    }

    /// Creates an animated layer whose pulses are controlled entirely by each test.
    private static InteractionLayer createLayer() {
        InteractionLayer layer = new InteractionLayer(false);
        layer.setAnimationEnabled(true);
        layer.resize(200, 40);
        return layer;
    }

    /// Runs assertions on the JavaFX thread and propagates failures to the test worker.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
