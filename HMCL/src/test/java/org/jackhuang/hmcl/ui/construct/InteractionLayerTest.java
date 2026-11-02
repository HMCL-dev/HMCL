// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies interaction continuity, state restoration, and persistent tab selection.
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

    /// Selection remains visible after pointer feedback ends, and selected controls still respond to presses.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pointerFeedbackRestoresPersistentState(boolean selected) throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setSelected(selected, 0);
            settleAnimation(layer, 0);
            double baseline = layer.getOpacity();
            assertEquals(selected, baseline > 0);

            layer.setHovered(true, SECOND);
            settleAnimation(layer, SECOND);
            double hovered = layer.getOpacity();
            assertTrue(hovered > baseline);

            layer.press(2 * SECOND);
            settleAnimation(layer, 2 * SECOND);
            assertTrue(layer.getOpacity() > hovered);

            layer.release(3 * SECOND);
            settleAnimation(layer, 3 * SECOND);
            assertEquals(hovered, layer.getOpacity());

            layer.setHovered(false, 4 * SECOND);
            settleAnimation(layer, 4 * SECOND);
            assertEquals(baseline, layer.getOpacity());
            layer.setSelected(false, 5 * SECOND);
            settleAnimation(layer, 5 * SECOND);
            assertEquals(0, layer.getOpacity());
        });
    }

    /// Retargeting hover to press preserves the displayed tint and the linear rate.
    @Test
    void retargetingPreservesLinearRate() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setHovered(true, 0);
            layer.advance(SAMPLE);
            double first = layer.getOpacity();
            assertTrue(first > 0);
            assertTrue(layer.isStateTransitioning());
            // No pulse at the input timestamp: input must sample the current fade itself.
            layer.press(2 * SAMPLE);
            assertEquals(2 * first, layer.getOpacity(), EPSILON);
            layer.advance(3 * SAMPLE);
            assertEquals(3 * first, layer.getOpacity(), EPSILON);
            layer.press(3 * SAMPLE);
            layer.advance(4 * SAMPLE);
            assertEquals(4 * first, layer.getOpacity(), EPSILON);
        });
    }

    /// Early release reverses immediately; a second press continues from the fading tint.
    @Test
    void rapidClicksRemainContinuous() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.press(0);
            layer.advance(2 * SAMPLE);
            double pressed = layer.getOpacity();
            assertTrue(layer.isStateTransitioning());
            layer.release(2 * SAMPLE);
            assertEquals(pressed, layer.getOpacity());
            layer.advance(3 * SAMPLE);
            double fading = layer.getOpacity();
            assertEquals(pressed / 2, fading, EPSILON);
            layer.press(3 * SAMPLE);
            assertEquals(fading, layer.getOpacity());
            layer.advance(4 * SAMPLE);
            assertEquals(pressed, layer.getOpacity(), EPSILON);
            layer.release(4 * SAMPLE);
            // A fade retraces the same distance in the same time, without a fixed release delay.
            layer.advance(6 * SAMPLE);
            assertEquals(0, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            assertTrue(layer.contains(100, 20));
        });
    }

    /// Reset clears a held press without losing selection or focus; disabled animation settles immediately.
    @Test
    void resetPreservesSelectionAndFocus() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setAnimationEnabled(false);
            layer.setSelected(true, 0);
            double selected = layer.getOpacity();
            layer.setHovered(true, 0);
            double hovered = layer.getOpacity();
            layer.setFocusActive(true, 0);
            double focused = layer.getOpacity();
            assertTrue(focused >= hovered);
            layer.press(0);
            layer.reset();
            assertEquals(focused, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            layer.setFocusActive(false, 0);
            assertEquals(hovered, layer.getOpacity());
            layer.setHovered(false, 0);
            assertEquals(selected, layer.getOpacity());
            layer.setAnimationEnabled(true);
            assertFalse(layer.isStateTransitioning());
            layer.setSelected(false, 0);
            layer.advance(SAMPLE);
            assertTrue(layer.isStateTransitioning());
            layer.setAnimationEnabled(false);
            assertEquals(0, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Theme paint and corner changes preserve interaction progress; hidden paint remains pickable.
    @Test
    void appearanceChangesPreserveFeedback() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            var gradient = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(1, 0, 0, 0.5)), new Stop(1, Color.TRANSPARENT));
            layer.setFill(gradient);
            layer.press(0);
            layer.advance(SAMPLE);
            assertEquals(gradient, Objects.requireNonNull(layer.getBackground()).getFills().get(0).getFill());
            double opacity = layer.getOpacity();
            var radii = new CornerRadii(0.5, true);
            layer.setCornerRadii(radii);
            layer.resize(300, 60);
            assertEquals(opacity, layer.getOpacity());
            assertEquals(radii, Objects.requireNonNull(layer.getBackground()).getFills().get(0).getRadii());
            layer.setFill(null);
            assertNull(layer.getBackground());
            assertTrue(layer.contains(150, 30));
            assertEquals(opacity, layer.getOpacity());
            Color paint = Color.color(0, 0, 1, 0.5);
            layer.setFill(paint);
            assertEquals(paint, Objects.requireNonNull(layer.getBackground()).getFills().get(0).getFill());
            assertEquals(opacity, layer.getOpacity());
        });
    }

    /// Completion notifies once; reset cancels silently and disabling animation reports settlement.
    @Test
    void settlementCallbackFollowsCompletion() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            var notifications = new AtomicInteger();
            layer.setOnStateSettled(notifications::incrementAndGet);
            layer.setHovered(true, 0);
            settleAnimation(layer, 0);
            layer.advance(2 * SECOND);
            assertEquals(1, notifications.get());
            layer.press(2 * SECOND);
            layer.reset();
            settleAnimation(layer, 2 * SECOND);
            assertEquals(1, notifications.get());
            layer.press(3 * SECOND);
            layer.setAnimationEnabled(false);
            assertEquals(2, notifications.get());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Reversing selection mid-fade is continuous and reset retains the latest selection.
    @Test
    void interruptedSelectionRemainsContinuous() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setSelected(true, 0);
            layer.advance(2 * SAMPLE);
            double selecting = layer.getOpacity();
            layer.setSelected(false, 2 * SAMPLE);
            assertEquals(selecting, layer.getOpacity());
            layer.advance(3 * SAMPLE);
            double fading = layer.getOpacity();
            assertEquals(selecting / 2, fading, EPSILON);
            layer.setSelected(true, 3 * SAMPLE);
            layer.press(3 * SAMPLE);
            assertEquals(fading, layer.getOpacity());
            layer.reset();
            double baseline = layer.getOpacity();
            assertTrue(baseline > selecting);
            assertFalse(layer.isStateTransitioning());
            layer.release(4 * SAMPLE);
            assertEquals(baseline, layer.getOpacity());
        });
    }

    /// Checks that controller selection updates tab backgrounds even without a showing window.
    @Test
    void tabSelectionUpdatesBackgrounds() throws Exception {
        onFxThread(() -> {
            var first = new TabItem<Region>();
            var second = new TabItem<Region>();
            TabHeader header = new TabHeader(first, second);
            var root = new StackPane(header);
            new Scene(root, 300, 100);
            root.applyCss();
            root.layout();
            @Unmodifiable List<RipplerContainer> views = header.lookupAll(".rippler-container").stream()
                    .map(RipplerContainer.class::cast).toList();
            assertEquals(2, views.size());
            assertEquals(1, views.stream().filter(view ->
                    feedbackOpacity(view) > 0).count());
            RipplerContainer previous = views.stream().filter(view ->
                    feedbackOpacity(view) > 0).findFirst().orElseThrow();
            double selectedOpacity = feedbackOpacity(previous);
            try {
                header.getController().selectTab(second);
                assertEquals(0, feedbackOpacity(previous));
                assertEquals(1, views.stream().filter(view ->
                        feedbackOpacity(view) > 0).count());
                header.getController().getTabs().remove(second);
                assertEquals(selectedOpacity, feedbackOpacity(previous));
            } finally {
                Objects.requireNonNull(header.getSkin()).dispose();
            }
        });
    }

    /// Finds feedback by node type rather than depending on the wrapper's child order.
    private static double feedbackOpacity(RipplerContainer view) {
        return view.getChildren().stream().filter(InteractionLayer.class::isInstance)
                .findFirst().orElseThrow().getOpacity();
    }

    /// A generous completion interval, independent of the visual timing tokens.
    private static final long SECOND = 1_000_000_000L;

    /// A short sampling interval for observing uninterrupted and interrupted fades.
    private static final long SAMPLE = 10_000_000L;

    /// Tolerance for comparing independently sampled opacity values.
    private static final double EPSILON = 1e-12;

    /// Advances well past a normal feedback transition and verifies it stops.
    private static void settleAnimation(InteractionLayer layer, long startedAt) {
        layer.advance(startedAt + SECOND);
        assertFalse(layer.isStateTransitioning());
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
