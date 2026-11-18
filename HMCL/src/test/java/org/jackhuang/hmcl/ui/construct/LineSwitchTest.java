// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Switch;
import org.glavo.m3fx.controls.M3SwitchEvent;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises settings row activation with the themed switch.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class LineSwitchTest {

    /// Dragging overrides the inherited value once, even when the binder refreshes synchronously.
    @Test
    void inheritableRowDragging() throws Exception {
        onFxThread(() -> {
            LineInheritableSwitch row = new LineInheritableSwitch();
            row.setTitle("Setting");
            row.overriddenProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            row.rawValueProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            AtomicInteger actions = new AtomicInteger();
            row.addEventHandler(ActionEvent.ACTION, event -> actions.incrementAndGet());

            M3Switch control = layoutSwitch(new VBox(row));
            assertEquals(50, row.getHeight());
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 40, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 40, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 40, false);

            assertTrue(row.isOverridden());
            assertTrue(row.getRawValue());
            assertTrue(row.isEffectiveValue());
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
        });
    }

    /// Row activation honors a veto and uses the original target even when enabling an override changes the effective value.
    @Test
    void inheritableRowToggleRequest() throws Exception {
        onFxThread(() -> {
            LineInheritableSwitch row = new LineInheritableSwitch();
            row.setRawValue(true);
            row.setEffectiveValue(false);
            row.overriddenProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            row.rawValueProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            AtomicInteger actions = new AtomicInteger();
            row.addEventHandler(ActionEvent.ACTION, event -> actions.incrementAndGet());
            VBox root = new VBox(row);
            M3Switch control = layoutSwitch(root);
            AtomicInteger requests = new AtomicInteger();
            root.addEventFilter(M3SwitchEvent.TOGGLE_REQUEST, event -> {
                assertTrue(event.getRequestedValue());
                if (requests.incrementAndGet() == 1) event.consume();
            });
            row.fire();
            assertFalse(row.isOverridden());
            assertFalse(control.isSelected());
            assertEquals(0, actions.get());

            root.setDisable(true);
            row.fire();
            assertEquals(1, requests.get());
            assertEquals(0, actions.get());

            root.setDisable(false);
            row.fire();
            assertTrue(row.isOverridden());
            assertTrue(row.getRawValue());
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
        });
    }

    /// Row, pointer and keyboard activation each dispatch a single action to the parent.
    @Test
    void rowActionDispatch() throws Exception {
        onFxThread(() -> {
            LineSwitch row = new LineSwitch();
            row.setTitle("Setting");
            VBox root = new VBox(row);
            M3Switch control = layoutSwitch(root);
            assertEquals(50, row.getHeight());
            assertEquals(row.getTitle(), control.getAccessibleText());
            AtomicInteger rowActions = new AtomicInteger();
            AtomicInteger parentActions = new AtomicInteger();
            row.addEventHandler(ActionEvent.ACTION, event -> rowActions.incrementAndGet());
            root.addEventHandler(ActionEvent.ACTION, event -> parentActions.incrementAndGet());

            row.fire();
            assertTrue(row.isSelected());
            assertEquals(1, rowActions.get());
            assertEquals(1, parentActions.get());

            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 36, false);
            assertFalse(row.isSelected());
            assertEquals(2, rowActions.get());
            assertEquals(2, parentActions.get());

            control.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
            control.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.SPACE, false, false, false, false));
            assertTrue(row.isSelected());
            assertEquals(3, rowActions.get());
            assertEquals(3, parentActions.get());

            root.setDisable(true);
            row.fire();
            assertTrue(row.isSelected());
            assertEquals(3, rowActions.get());
            assertEquals(3, parentActions.get());
        });
    }

    /// Applies the application styles and locates the row's switch at its native size.
    private static M3Switch layoutSwitch(VBox root) {
        Scene scene = new Scene(root, 300, 100);
        M3ThemeManager.install(scene, M3Theme.defaultTheme());
        scene.getStylesheets().addAll("/assets/css/blue.css", "/assets/css/root.css");
        root.applyCss();
        root.layout();
        M3Switch control = (M3Switch) Objects.requireNonNull(root.lookup(".m3-switch"));
        assertEquals(48, control.getHeight());
        control.setAnimationsEnabled(false);
        return control;
    }

    /// Sends a pointer event at the specified horizontal position on the switch indicator.
    private static void mouse(M3Switch control, EventType<MouseEvent> type, double x, boolean down) {
        Node indicator = Objects.requireNonNull(control.lookup(".m3-selection-indicator"));
        Point2D point = indicator.localToScene(x, indicator.getLayoutBounds().getCenterY());
        indicator.fireEvent(new MouseEvent(type, point.getX(), point.getY(), point.getX(), point.getY(),
                MouseButton.PRIMARY, 1, false, false, false, false, down, false, false, false, false, false,
                new PickResult(indicator, point.getX(), point.getY())));
    }

    /// Runs assertions after JavaFX has initialized and propagates failures to the test thread.
    private static void onFxThread(Runnable action) throws Exception {
        CompletableFuture.runAsync(action, Platform::runLater).get(10, TimeUnit.SECONDS);
    }
}
