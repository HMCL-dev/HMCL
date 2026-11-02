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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies feedback timing, state composition, painting, and tab selection on the FX thread.
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

    /// Verifies the same press cycle with and without a persistent selection tint.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pressTintsWholeBackground(boolean selected) throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setSelected(selected, 0);
            layer.reset();
            double baseOpacity = selected ? 0.08 : 0;
            layer.setCornerRadii(new CornerRadii(8));
            layer.setHovered(true, 0);
            layer.advance(150_000_000);
            assertEquals(baseOpacity + 0.08, layer.getOpacity(), 1e-12);
            layer.press(150_000_000);
            layer.advance(187_500_000);
            assertEquals(baseOpacity + 0.10, layer.getOpacity(), 1e-12);
            layer.advance(225_000_000);
            assertEquals(baseOpacity + 0.12, layer.getOpacity(), 1e-12);
            assertFalse(layer.isStateTransitioning());
            assertTrue(layer.getChildrenUnmodifiable().isEmpty());
            var fill = Objects.requireNonNull(layer.getBackground()).getFills().get(0);
            assertEquals(1.0, ((Color) fill.getFill()).getOpacity());
            assertEquals(new CornerRadii(8), fill.getRadii());
            layer.release(225_000_000);
            layer.advance(262_500_000);
            assertEquals(baseOpacity + 0.10, layer.getOpacity(), 1e-12);
            layer.advance(300_000_000);
            assertEquals(baseOpacity + 0.08, layer.getOpacity(), 1e-12);
            assertFalse(layer.isStateTransitioning());
            layer.setHovered(false, 300_000_000);
            layer.advance(450_000_000);
            layer.reset();
            assertEquals(baseOpacity, layer.getOpacity());
            layer.setSelected(false, 450_000_000);
            layer.advance(600_000_000);
            assertEquals(0, layer.getOpacity());
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
            layer.press(75_000_000);
            assertEquals(0.04, layer.getOpacity(), 1e-12);
            layer.advance(150_000_000);
            assertEquals(0.08, layer.getOpacity(), 1e-12);
            layer.advance(187_500_000);
            assertEquals(0.10, layer.getOpacity(), 1e-12);
            assertTrue(layer.isStateTransitioning());
            layer.advance(225_000_000);
            assertEquals(0.12, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Checks that early release and repeated presses continue at the same opacity rate.
    @Test
    void rapidClicksRemainContinuous() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.press(0);
            layer.advance(30_000_000);
            double pressed = layer.getOpacity();
            layer.release(30_000_000);
            assertEquals(pressed, layer.getOpacity());
            layer.advance(45_000_000);
            double fading = layer.getOpacity();
            assertEquals(0.008, fading, 1e-12);
            layer.press(45_000_000);
            assertEquals(fading, layer.getOpacity());
            layer.press(75_000_000);
            layer.advance(180_000_000);
            assertEquals(0.08, layer.getOpacity(), 1e-12);
            layer.advance(255_000_000);
            assertEquals(0.12, layer.getOpacity());
            layer.release(255_000_000);
            layer.advance(480_000_000);
            assertEquals(0, layer.getOpacity());
            assertTrue(layer.contains(100, 20));
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Checks early release below, at, and above hover opacity without completing the press first.
    @ParameterizedTest
    @CsvSource({
            "112500000, 0.06, 131250000, 0.07, 150000000",
            "150000000, 0.08, 150000000, 0.08, 150000000",
            "187500000, 0.10, 206250000, 0.09, 225000000"
    })
    void earlyReleaseReturnsDirectlyToHover(long releasedAt, double releasedOpacity,
                                            long sampledAt, double sampledOpacity, long settledAt) throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setHovered(true, 0);
            layer.press(0);
            layer.release(releasedAt);
            assertEquals(releasedOpacity, layer.getOpacity(), 1e-12);
            assertEquals(releasedOpacity != 0.08, layer.isStateTransitioning());
            layer.advance(sampledAt);
            assertEquals(sampledOpacity, layer.getOpacity(), 1e-12);
            layer.advance(settledAt);
            assertEquals(0.08, layer.getOpacity());
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
            layer.advance(75_000_000);
            layer.setAnimationEnabled(false);
            assertEquals(0.12, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            layer.release(75_000_000);
            assertEquals(0.12, layer.getOpacity());
            layer.setAnimationEnabled(true);
            layer.press(90_000_000);
            layer.reset();
            assertEquals(0.12, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            layer.setFocusActive(false, 90_000_000);
            layer.advance(127_500_000);
            assertEquals(0.10, layer.getOpacity(), 1e-12);
            layer.advance(165_000_000);
            assertEquals(0.08, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
        });
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
            layer.advance(75_000_000);
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
            layer.advance(300_000_000);
            layer.setFill(Color.color(0, 0, 1, 0.5));
            assertEquals(0.12, layer.getOpacity());
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
            var notifications = new AtomicInteger();
            layer.setOnStateSettled(notifications::incrementAndGet);
            layer.setHovered(true, 0);
            layer.advance(150_000_000);
            layer.advance(300_000_000);
            assertEquals(1, notifications.get());
            layer.press(300_000_000);
            layer.reset();
            layer.advance(450_000_000);
            assertEquals(1, notifications.get());
            layer.press(450_000_000);
            layer.setAnimationEnabled(false);
            assertEquals(2, notifications.get());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Checks that selection can reverse mid-transition without jumping or delaying press feedback.
    @Test
    void interruptedSelectionRemainsContinuous() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setSelected(true, 0);
            layer.setSelected(false, 75_000_000);
            assertEquals(0.04, layer.getOpacity(), 1e-12);
            layer.advance(112_500_000);
            assertEquals(0.02, layer.getOpacity(), 1e-12);
            layer.setSelected(true, 112_500_000);
            layer.press(112_500_000);
            assertEquals(0.02, layer.getOpacity(), 1e-12);
            layer.setAnimationEnabled(false);
            assertEquals(0.20, layer.getOpacity());
            layer.reset();
            assertEquals(0.08, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
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
                    feedbackOpacity(view) == 0.08).count());
            RipplerContainer previous = views.stream().filter(view ->
                    feedbackOpacity(view) == 0.08).findFirst().orElseThrow();
            try {
                header.getController().selectTab(second);
                assertEquals(0, feedbackOpacity(previous));
                assertEquals(1, views.stream().filter(view ->
                        feedbackOpacity(view) == 0.08).count());
                header.getController().getTabs().remove(second);
                assertEquals(0.08, feedbackOpacity(previous));
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
