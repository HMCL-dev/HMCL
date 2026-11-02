// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.skins.JFXSwitchSkin;
import javafx.application.Platform;
import javafx.geometry.NodeOrientation;
import javafx.scene.AccessibleAction;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ContentDisplay;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises the switch through a live JavaFX scene, including CSS and inherited behavior.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class JFXSwitchTest {
    /// Checks MD3 geometry, icons, pressed state, and per-part disabled opacity.
    @Test
    void geometryAndStates() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setDisableAnimation(true);
            VBox root = scene(control);
            Region track = part(control, "track");
            Region handle = part(control, "handle");
            assertEquals(52, track.getWidth());
            assertEquals(32, track.getHeight());
            assertEquals(48, control.getHeight());
            assertEquals(16, handle.getWidth());
            assertEquals(16, handle.getLayoutX() + handle.getWidth() / 2);
            assertEquals(Color.web("#79747E"), handle.getBackground().getFills().get(0).getFill());

            control.setSelected(true);
            layout(root);
            assertEquals(24, handle.getWidth());
            assertEquals(36, handle.getLayoutX() + handle.getWidth() / 2);
            control.arm();
            layout(root);
            assertEquals(28, handle.getWidth());
            control.disarm();
            control.setSelected(false);
            control.setShowUnselectedIcon(true);
            layout(root);
            assertEquals(24, handle.getWidth());
            assertTrue(control.lookup(".switch-icon").isVisible());
            control.setDisable(true);
            layout(root);
            assertEquals(1, control.getOpacity());
            assertEquals(0.12, track.getOpacity());
            assertEquals(0.38, handle.getOpacity());
            assertEquals(0, part(control, "state-layer").getOpacity());
            control.setSelected(true);
            layout(root);
            assertEquals(1, handle.getOpacity());
        });
    }

    /// Verifies keyboard, action, accessibility, label ownership, and replacement-skin behavior.
    @Test
    void behaviorAndSkinReplacement() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch("Wi-Fi");
            control.setDisableAnimation(true);
            Label graphic = new Label("Network");
            control.setGraphic(graphic);
            VBox root = scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            control.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
            control.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.SPACE, false, false, false, false));
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());
            control.setDisable(true);
            control.fire();
            assertEquals(2, actions.get());
            control.setDisable(false);
            control.setSkin(null);
            control.setSkin(new JFXSwitchSkin(control));
            control.setSelected(true);
            layout(root);
            assertSame(graphic, control.getGraphic());
            assertEquals(1, control.lookupAll(".switch-track").size());
            assertEquals(24, part(control, "handle").getWidth());
            assertTrue(control.prefWidth(-1) > 64);
            control.setGraphic(new Label("Replacement"));
            control.setText("Updated");
            control.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            layout(root);
            assertEquals(1, control.lookupAll(".switch-track").size());
            control.setGraphic(null);
            layout(root);
            assertEquals(52, control.prefWidth(-1));
        });
    }

    /// Verifies interrupted transitions settle to the latest state and disabling animation snaps immediately.
    @Test
    void animationRetargeting() throws Exception {
        CompletableFuture<JFXSwitch> created = new CompletableFuture<>();
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            scene(control);
            created.complete(control);
            control.setSelected(true);
            control.setSelected(false);
            control.setSelected(true);
        });
        // Allow JavaFX pulses to complete the last transition before asserting its geometry.
        Thread.sleep(350);
        onFxThread(() -> {
            JFXSwitch control = created.join();
            VBox root = (VBox) control.getParent();
            layout(root);
            Region handle = part(control, "handle");
            assertEquals(24, handle.getWidth(), 0.001);
            assertEquals(36, handle.getLayoutX() + handle.getWidth() / 2, 0.001);
            control.setSelected(false);
            control.setDisableAnimation(true);
            layout(root);
            assertEquals(16, handle.getWidth());
            assertEquals(16, handle.getLayoutX() + handle.getWidth() / 2);
        });
    }

    /// Checks theme lookup overrides and logical movement mirrored by the scene graph.
    @Test
    void themeAndRtl() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setDisableAnimation(true);
            control.setStyle("-jfx-switch-primary: #123456;");
            control.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            VBox root = scene(control);
            Region handle = part(control, "handle");
            double offX = handle.localToScene(handle.getWidth() / 2, 0).getX();
            control.setSelected(true);
            layout(root);
            double onX = handle.localToScene(handle.getWidth() / 2, 0).getX();
            assertTrue(onX < offX);
            assertEquals(Color.web("#123456"), part(control, "track").getBackground().getFills().get(0).getFill());
        });
    }

    /// Attaches a switch to a scene so its user-agent stylesheet and skin are loaded.
    private static VBox scene(JFXSwitch control) {
        VBox root = new VBox(control);
        new Scene(root, 300, 100);
        layout(root);
        return root;
    }

    /// Applies pending CSS changes and synchronously lays out the scene root.
    private static void layout(VBox root) {
        root.applyCss();
        root.layout();
    }

    /// Returns a required region from the default skin.
    private static Region part(JFXSwitch control, String name) {
        return (Region) Objects.requireNonNull(control.lookup(".switch-" + name));
    }

    /// Runs assertions on the application thread and propagates failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
