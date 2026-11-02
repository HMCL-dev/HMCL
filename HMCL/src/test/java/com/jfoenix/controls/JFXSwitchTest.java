// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.skins.JFXSwitchSkin;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.LineInheritableSwitch;
import org.jackhuang.hmcl.ui.construct.LineSwitch;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises the switch through a live JavaFX scene, including CSS and inherited behavior.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class JFXSwitchTest {

    /// Initializes the cached animation policy without loading or changing persisted settings.
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

    /// Dragging overrides the inherited value once, despite synchronous refresh by the setting binder.
    @Test
    void inheritableRowDragging() throws Exception {
        onFxThread(() -> {
            LineInheritableSwitch row = new LineInheritableSwitch();
            row.overriddenProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            row.rawValueProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            AtomicInteger actions = new AtomicInteger();
            row.addEventHandler(javafx.event.ActionEvent.ACTION, event -> actions.incrementAndGet());
            VBox root = new VBox(row);
            applyStylesheets(new Scene(root, 300, 100));
            layout(root);
            JFXSwitch control = (JFXSwitch) Objects.requireNonNull(row.lookup(".jfx-switch"));
            control.setDisableAnimation(true);
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 36, false);
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
            assertTrue(row.isOverridden());
            assertTrue(row.getRawValue());
            assertTrue(row.isEffectiveValue());
        });
    }

    /// Covers handle and track dragging with representative sizes and layout directions.
    @ParameterizedTest
    @CsvSource({"false, 32, 16", "false, 24, 4", "true, 24, 40"})
    void handleDragging(boolean rtl, double size, double pressX) throws Exception {
        onFxThread(() -> {
            LineSwitch row = new LineSwitch();
            row.setTitle("Setting");
            VBox root = new VBox(row);
            root.setNodeOrientation(rtl ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
            applyStylesheets(new Scene(root, 300, 100));
            layout(root);
            JFXSwitch control = (JFXSwitch) Objects.requireNonNull(row.lookup(".jfx-switch"));
            control.setStyle("-jfx-size: " + size + ";");
            control.setDisableAnimation(true);
            layout(root);
            AtomicInteger actions = new AtomicInteger();
            AtomicInteger rowActions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            row.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> rowActions.incrementAndGet());
            Shape handle = part(control, "handle");
            double initialX = handle.localToScene(handle.getLayoutBounds()).getCenterX();
            mouse(control, MouseEvent.MOUSE_PRESSED, pressX, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, pressX + 12, true);
            layout(root);
            assertFalse(control.isSelected());
            assertEquals(28, handle.getBoundsInParent().getCenterX(), 0.001);
            double draggedX = handle.localToScene(handle.getLayoutBounds()).getCenterX();
            assertTrue(rtl ? draggedX < initialX : draggedX > initialX);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 60, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 60, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 60, false);
            layout(root);
            assertTrue(control.isSelected());
            assertTrue(row.isSelected());
            assertEquals(1, actions.get());
            assertEquals(0, rowActions.get());
            assertFalse(control.isArmed());

            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 30, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 30, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 30, false);
            assertTrue(control.isSelected());
            assertTrue(row.isSelected());
            assertEquals(1, actions.get());
            layout(root);
            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, -10, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, -10, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, -10, false);
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());

            layout(root);
            mouse(control, MouseEvent.MOUSE_PRESSED, pressX, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, pressX + 1, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, pressX + 1, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, pressX + 1, false);
            assertTrue(control.isSelected());
            assertTrue(row.isSelected());
            assertEquals(3, actions.get());
        });
    }

    /// Keeps secondary-button input from resetting an active primary drag.
    @Test
    void secondaryButtonDuringDrag() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setDisableAnimation(true);
            scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 20, true);
            mouse(control, MouseEvent.MOUSE_PRESSED, 20, MouseButton.SECONDARY, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 20, MouseButton.SECONDARY, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 36, false);
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
        });
    }

    /// A canceled drag must not become a click after re-enabling and re-entering the control.
    @Test
    void canceledDragRelease() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setDisableAnimation(true);
            scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
            control.setDisable(true);
            control.setDisable(false);
            mouse(control, MouseEvent.MOUSE_EXITED, 60, true);
            mouse(control, MouseEvent.MOUSE_ENTERED, 36, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 36, false);
            assertFalse(control.isSelected());
            assertFalse(control.isArmed());
            assertEquals(0, actions.get());
        });
    }

    /// Dispatches a primary pointer event at a position in the unscaled switch artwork.
    private static void mouse(JFXSwitch control, EventType<MouseEvent> type, double x, boolean down) {
        mouse(control, type, x, MouseButton.PRIMARY, down);
    }

    /// Dispatches a pointer event while preserving the primary button's independent state.
    private static void mouse(JFXSwitch control, EventType<MouseEvent> type, double x, MouseButton button, boolean down) {
        Point2D scenePoint = Objects.requireNonNull(control.lookup(".switch-artwork")).localToScene(x, 24);
        control.fireEvent(new MouseEvent(type, scenePoint.getX(), scenePoint.getY(),
                scenePoint.getX(), scenePoint.getY(), button, 1,
                false, false, false, false, down, false,
                button == MouseButton.SECONDARY && type == MouseEvent.MOUSE_PRESSED, false, false, false,
                new PickResult(control, scenePoint.getX(), scenePoint.getY())));
    }

    /// Verifies settings-row CSS, arbitrary sizes, bound-property precedence, and invalid-size fallback.
    @Test
    void styleableSize() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            VBox root = scene(control);
            root.getStyleClass().add("line-switch");
            layout(root);
            assertEquals(24, control.getSize());
            assertEquals(39, control.prefWidth(-1));
            control.setStyle("-jfx-size: 20px;");
            layout(root);
            assertEquals(20, control.getSize());
            assertEquals(32.5, control.prefWidth(-1));
            assertEquals(30, control.prefHeight(-1));
            Shape track = part(control, "track");
            assertEquals(52, track.getLayoutBounds().getWidth(), "The outline must stay inside the track");
            assertEquals(32, track.getLayoutBounds().getHeight());
            assertEquals(32.5, track.localToScene(track.getLayoutBounds()).getWidth(), 0.001);
            assertEquals(20, track.localToScene(track.getLayoutBounds()).getHeight(), 0.001);

            SimpleDoubleProperty boundSize = new SimpleDoubleProperty(40);
            control.sizeProperty().bind(boundSize);
            control.setStyle("-jfx-size: 28px;");
            layout(root);
            assertEquals(40, control.getSize());
            assertEquals(65, control.prefWidth(-1));
            boundSize.set(Double.NaN);
            layout(root);
            assertEquals(52, control.prefWidth(-1));
            assertEquals(48, control.prefHeight(-1));
            control.sizeProperty().unbind();
            control.setStyle("-jfx-size: 24px;");
            layout(root);
            assertEquals(24, control.getSize());
        });
    }

    /// Verifies keyboard, action, accessibility, label ownership, and replacement-skin behavior.
    @Test
    void behaviorAndSkinReplacement() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch("Wi-Fi");
            assertEquals(!AnimationUtils.isAnimationEnabled(), control.isDisableAnimation());
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
            assertEquals(24, part(control, "handle").getLayoutBounds().getWidth());
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

    /// Uses real pulses to animate selection without manually applying CSS or laying out after the change.
    @Test
    void selectionColorsOnPulse() throws Exception {
        CompletableFuture<Stage> window = new CompletableFuture<>();
        CompletableFuture<JFXSwitch> target = new CompletableFuture<>();
        AtomicInteger changes = new AtomicInteger();
        try {
            onFxThread(() -> {
                Platform.setImplicitExit(false);
                JFXSwitch control = new JFXSwitch();
                control.setDisableAnimation(false);
                control.setStyle("-jfx-switch-primary: #123456;");
                target.complete(control);
                VBox root = scene(control);
                Stage stage = new Stage();
                window.complete(stage);
                stage.setScene(root.getScene());
                stage.show();
                part(control, "track").fillProperty().addListener(observable -> changes.incrementAndGet());
                control.setSelected(true);
                assertEquals(0, changes.get());
            });
            Thread.sleep(400);
            onFxThread(() -> {
                assertEquals(Color.web("#123456"),
                        part(target.join(), "track").getFill());
                assertTrue(changes.get() > 2, "Normal pulses must produce intermediate colors");
            });
        } finally {
            onFxThread(() -> {
                if (window.isDone())
                    window.join().close();
            });
        }
    }

    /// Checks interrupted color and geometry animations, theme overrides, and immediate completion.
    @Test
    void selectionColorTransitions() throws Exception {
        CompletableFuture<JFXSwitch> created = new CompletableFuture<>();
        CompletableFuture<@Unmodifiable List<Paint>> off = new CompletableFuture<>();
        CompletableFuture<@Unmodifiable List<Paint>> on = new CompletableFuture<>();
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setShowSelectedIcon(true);
            control.setShowUnselectedIcon(true);
            control.setStyle("-jfx-switch-primary: #123456;");
            scene(control);
            created.complete(control);
            off.complete(palette(control));
            control.setDisableAnimation(true);
            control.setSelected(true);
            on.complete(palette(control));
            control.setSelected(false);
            control.applyCss();
            control.setDisableAnimation(false);
            control.setSelected(true);
            control.setSelected(false);
            control.setSelected(true);
            assertEquals(off.join(), palette(control), "Selection must start from the visible palette");
        });
        Thread.sleep(80);
        onFxThread(() -> {
            JFXSwitch control = created.join();
            @Unmodifiable List<Paint> current = palette(control);
            assertNotEquals(off.join(), current, "Colors must advance during the transition");
            assertNotEquals(on.join(), current, "Colors must interpolate before reaching the target");
            Shape handle = part(control, "handle");
            double center = handle.getBoundsInParent().getCenterX();
            double diameter = handle.getLayoutBounds().getWidth();
            control.setSelected(false);
            assertEquals(current, palette(control), "Reversing must preserve the displayed colors");
            assertEquals(center, handle.getBoundsInParent().getCenterX(), 0.001);
            assertEquals(diameter, handle.getLayoutBounds().getWidth(), 0.001);
        });
        Thread.sleep(300);
        onFxThread(() -> {
            JFXSwitch control = created.join();
            assertEquals(off.join(), palette(control));
            control.setSelected(true);
            control.setDisableAnimation(true);
            assertEquals(on.join(), palette(control), "Disabling animation must settle all colors");
            Shape handle = part(control, "handle");
            assertEquals(36, handle.getBoundsInParent().getCenterX());
            assertEquals(24, handle.getLayoutBounds().getWidth());
            control.setStyle("-jfx-switch-primary: #abcdef;");
            control.applyCss();
            assertEquals(Color.web("#abcdef"), palette(control).get(0), "CSS must still override animated values");
            control.setDisableAnimation(false);
            control.setSelected(false);
            control.setDisable(true);
            control.applyCss();
            @Unmodifiable List<Paint> disabled = palette(control);
            control.setSkin(null);
            control.setSkin(new JFXSwitchSkin(control));
            control.applyCss();
            assertEquals(disabled, palette(control), "Disabling must settle to the same palette as a fresh skin");
        });
    }

    /// Completes a CSS/layout pass and captures solid paints independently of geometry and opacity.
    private static @Unmodifiable List<Paint> palette(JFXSwitch control) {
        layout((VBox) control.getParent());
        return List.of(part(control, "track").getFill(),
                part(control, "track").getStroke(),
                part(control, "handle").getFill(),
                part(control, "state-layer").getFill(),
                part(control, "icon").getFill());
    }

    /// Attaches a switch to a scene with the application theme and component stylesheet.
    private static VBox scene(JFXSwitch control) {
        VBox root = new VBox(control);
        applyStylesheets(new Scene(root, 300, 100));
        layout(root);
        return root;
    }

    /// Loads the same shared component styles and color palette used by the application.
    private static void applyStylesheets(Scene scene) {
        scene.getStylesheets().addAll(
                Objects.requireNonNull(JFXSwitchTest.class.getResource("/assets/css/blue.css")).toExternalForm(),
                Objects.requireNonNull(JFXSwitchTest.class.getResource("/assets/css/root.css")).toExternalForm());
    }

    /// Applies pending CSS changes and synchronously lays out the scene root.
    private static void layout(VBox root) {
        root.applyCss();
        root.layout();
    }

    /// Returns a required shape from the default skin.
    private static Shape part(JFXSwitch control, String name) {
        return (Shape) Objects.requireNonNull(control.lookup(".switch-" + name));
    }

    /// Runs assertions on the application thread and propagates failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
