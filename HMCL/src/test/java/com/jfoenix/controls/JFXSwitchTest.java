// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.skins.JFXSwitchSkin;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.event.EventType;
import javafx.scene.AccessibleAction;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import org.jackhuang.hmcl.ui.construct.LineInheritableSwitch;
import org.jackhuang.hmcl.ui.construct.LineButtonBase;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.LineSwitch;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.LauncherSettings;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Objects;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises the switch through a live JavaFX scene, including CSS and inherited behavior.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class JFXSwitchTest {
    /// Checks native pointer focus dismissal and Tab focus feedback without discarding actual focus.
    @Test
    void nativeFocusFeedback() throws Exception {
        CompletableFuture<Stage> window = new CompletableFuture<>();
        CompletableFuture<JFXSwitch> target = new CompletableFuture<>();
        CompletableFuture<Button> before = new CompletableFuture<>();
        try {
            onFxThread(() -> {
                Platform.setImplicitExit(false);
                Button button = new Button("Before");
                before.complete(button);
                JFXSwitch control = new JFXSwitch();
                control.setDisableAnimation(true);
                target.complete(control);
                VBox root = new VBox(button, control);
                Stage stage = new Stage();
                window.complete(stage);
                Scene scene = new Scene(root, 300, 160);
                applyStylesheets(scene);
                stage.setScene(scene);
                stage.show();
                stage.toFront();
                layout(root);
                button.requestFocus();
            });
            Thread.sleep(250);
            onFxThread(() -> {
                var artwork = Objects.requireNonNull(target.join().lookup(".switch-artwork"));
                Robot robot = new Robot();
                robot.mouseMove(Objects.requireNonNull(artwork.localToScreen(16, 24)));
                robot.mousePress(MouseButton.PRIMARY);
            });
            Thread.sleep(100);
            onFxThread(() -> new Robot().mouseRelease(MouseButton.PRIMARY));
            Thread.sleep(100);
            onFxThread(() -> new Robot().mouseMove(Objects.requireNonNull(
                    window.join().getScene().getRoot().localToScreen(200, 140))));
            Thread.sleep(100);
            onFxThread(() -> {
                JFXSwitch control = target.join();
                control.applyCss();
                assertTrue(control.isFocused());
                assertFalse(control.isHover());
                assertEquals(0, part(control, "state-layer").getOpacity());
                if (FXUtils.focusVisibleProperty(control) != null) {
                    // Replacing the skin must not turn pointer-acquired focus into visible focus.
                    control.setSkin(null);
                    control.setSkin(new JFXSwitchSkin(control));
                    control.applyCss();
                    assertTrue(control.isFocused());
                    assertEquals(0, part(control, "state-layer").getOpacity());
                }
                before.join().requestFocus();
                Robot robot = new Robot();
                robot.keyPress(KeyCode.TAB);
                robot.keyRelease(KeyCode.TAB);
            });
            Thread.sleep(150);
            onFxThread(() -> {
                JFXSwitch control = target.join();
                control.applyCss();
                assertTrue(control.isFocused());
                assertEquals(0.12, part(control, "state-layer").getOpacity());
                before.join().requestFocus();
                control.applyCss();
                assertEquals(0, part(control, "state-layer").getOpacity());
            });
        } finally {
            onFxThread(() -> {
                new Robot().mouseRelease(MouseButton.PRIMARY);
                if (window.isDone())
                    window.join().close();
            });
        }
    }

    /// Uses native pointer input to verify picking, row bindings, and inheritable overrides.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeRowDragging(boolean inheritable) throws Exception {
        Field settingsField = SettingsManager.class.getDeclaredField("launcherSettings");
        settingsField.setAccessible(true);
        @Nullable Object previous = settingsField.get(null);
        if (previous == null)
            settingsField.set(null, new LauncherSettings());
        CompletableFuture<Stage> window = new CompletableFuture<>();
        CompletableFuture<JFXSwitch> target = new CompletableFuture<>();
        AtomicInteger presses = new AtomicInteger();
        AtomicInteger actions = new AtomicInteger();
        try {
            onFxThread(() -> {
                Platform.setImplicitExit(false);
                LineButtonBase row;
                if (inheritable) {
                    LineInheritableSwitch inheritedRow = new LineInheritableSwitch();
                    // Model the setting binder, including synchronous refresh when override mode changes.
                    inheritedRow.overriddenProperty().addListener(observable ->
                            inheritedRow.setEffectiveValue(inheritedRow.getRawValue()));
                    inheritedRow.rawValueProperty().addListener(observable ->
                            inheritedRow.setEffectiveValue(inheritedRow.getRawValue()));
                    row = inheritedRow;
                } else {
                    row = new LineSwitch();
                }
                row.setTitle("Setting");
                row.addEventHandler(javafx.event.ActionEvent.ACTION, event -> actions.incrementAndGet());
                VBox root = new VBox(row);
                Scene scene = new Scene(root, 400, 120);
                applyStylesheets(scene);
                Stage stage = new Stage();
                window.complete(stage);
                stage.setScene(scene);
                stage.show();
                stage.toFront();
                layout(root);
                JFXSwitch control = (JFXSwitch) Objects.requireNonNull(row.lookup(".jfx-switch"));
                control.setDisableAnimation(true);
                control.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> presses.incrementAndGet());
                target.complete(control);
                for (@Nullable javafx.scene.Node node = control; node != null; node = node.getParent())
                    assertFalse(node.isMouseTransparent(), "Transparent ancestor: " + node);
            });
            Thread.sleep(250);
            onFxThread(() -> {
                var artwork = Objects.requireNonNull(target.join().lookup(".switch-artwork"));
                Point2D start = Objects.requireNonNull(artwork.localToScreen(16, 24));
                Robot robot = new Robot();
                robot.mouseMove(start);
                robot.mousePress(MouseButton.PRIMARY);
            });
            Thread.sleep(100);
            onFxThread(() -> {
                assertEquals(1, presses.get(), "Native press must hit the switch");
                var artwork = Objects.requireNonNull(target.join().lookup(".switch-artwork"));
                new Robot().mouseMove(Objects.requireNonNull(artwork.localToScreen(36, 24)));
            });
            Thread.sleep(100);
            onFxThread(() -> new Robot().mouseRelease(MouseButton.PRIMARY));
            Thread.sleep(150);
            onFxThread(() -> {
                assertTrue(target.join().isSelected());
                assertEquals(1, actions.get());
                LineButtonBase row = (LineButtonBase) window.join().getScene().getRoot().getChildrenUnmodifiable().get(0);
                if (row instanceof LineInheritableSwitch inheritedRow) {
                    assertTrue(inheritedRow.isOverridden());
                    assertTrue(inheritedRow.getRawValue());
                    assertTrue(inheritedRow.isEffectiveValue());
                } else {
                    assertTrue(((LineSwitch) row).isSelected());
                }
            });
        } finally {
            onFxThread(() -> {
                new Robot().mouseRelease(MouseButton.PRIMARY);
                if (window.isDone())
                    window.join().close();
            });
            settingsField.set(null, previous);
        }
    }

    /// Exercises dragging from the handle and either side of it at both sizes and layout directions.
    @ParameterizedTest
    @CsvSource({"false, 24, 16", "true, 24, 16", "false, 32, 16", "true, 32, 16",
            "false, 24, 4", "true, 24, 4", "false, 32, 4", "true, 32, 4",
            "false, 24, 40", "true, 24, 40", "false, 32, 40", "true, 32, 40"})
    void handleDragging(boolean rtl, double size, double pressX) throws Exception {
        Field settingsField = SettingsManager.class.getDeclaredField("launcherSettings");
        settingsField.setAccessible(true);
        @Nullable Object previous = settingsField.get(null);
        if (previous == null)
            settingsField.set(null, new LauncherSettings());
        try {
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
                mouse(control, MouseEvent.MOUSE_PRESSED, pressX, true);
                mouse(control, MouseEvent.MOUSE_DRAGGED, pressX + 12, true);
                layout(root);
                assertFalse(control.isSelected());
                Region handle = part(control, "handle");
                assertEquals(28, handle.getLayoutX() + handle.getWidth() / 2, 0.001);
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
                mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
                mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
                control.setDisable(true);
                mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
                mouse(control, MouseEvent.MOUSE_CLICKED, 36, false);
                assertFalse(control.isSelected());
                assertEquals(2, actions.get());
                control.setDisable(false);
                layout(root);
                mouse(control, MouseEvent.MOUSE_PRESSED, pressX, true);
                mouse(control, MouseEvent.MOUSE_DRAGGED, pressX + 1, true);
                mouse(control, MouseEvent.MOUSE_RELEASED, pressX + 1, false);
                mouse(control, MouseEvent.MOUSE_CLICKED, pressX + 1, false);
                assertTrue(control.isSelected());
                assertTrue(row.isSelected());
                assertEquals(3, actions.get());
            });
        } finally {
            settingsField.set(null, previous);
        }
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

            SimpleDoubleProperty boundSize = new SimpleDoubleProperty(40);
            control.sizeProperty().bind(boundSize);
            control.setStyle("-jfx-size: 28px;");
            layout(root);
            assertEquals(40, control.getSize());
            assertEquals(65, control.prefWidth(-1));
            boundSize.set(16);
            layout(root);
            assertEquals(26, control.prefWidth(-1));
            assertEquals(24, control.prefHeight(-1));
            boundSize.set(Double.NaN);
            layout(root);
            assertEquals(52, control.prefWidth(-1));
            assertEquals(48, control.prefHeight(-1));
            boundSize.set(-1);
            layout(root);
            assertEquals(52, control.prefWidth(-1));
            boundSize.set(Double.POSITIVE_INFINITY);
            layout(root);
            assertEquals(52, control.prefWidth(-1));
            control.sizeProperty().unbind();
            control.setStyle("-jfx-size: 24px;");
            layout(root);
            assertEquals(24, control.getSize());
        });
    }

    /// Checks compact visual bounds, layout space, and density changes without scaling labels.
    @Test
    void compactDimensions() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setDisableAnimation(true);
            control.setSize(24);
            VBox root = scene(control);
            Region track = part(control, "track");
            Region handle = part(control, "handle");
            assertEquals(39, control.prefWidth(-1));
            assertEquals(39, control.minWidth(-1));
            assertEquals(36, control.getHeight());
            assertEquals(36, control.minHeight(-1));
            assertEquals(39, track.localToScene(track.getLayoutBounds()).getWidth(), 0.001);
            assertEquals(24, track.localToScene(track.getLayoutBounds()).getHeight(), 0.001);
            assertEquals(12, handle.localToScene(handle.getLayoutBounds()).getWidth(), 0.001);
            control.setSelected(true);
            control.setShowSelectedIcon(true);
            layout(root);
            assertEquals(18, handle.localToScene(handle.getLayoutBounds()).getWidth(), 0.001);
            control.arm();
            layout(root);
            assertEquals(21, handle.localToScene(handle.getLayoutBounds()).getWidth(), 0.001);
            control.disarm();
            Label graphic = new Label("Network");
            control.setGraphic(graphic);
            layout(root);
            double graphicWidth = graphic.localToScene(graphic.getLayoutBounds()).getWidth();
            double compactWidth = control.prefWidth(-1);
            var icon = Objects.requireNonNull(control.lookup(".switch-icon"));
            double compactIconWidth = icon.localToScene(icon.getLayoutBounds()).getWidth();
            control.setSize(32);
            layout(root);
            assertTrue(control.isSelected());
            assertEquals(48, control.getHeight());
            assertEquals(compactWidth + 13, control.prefWidth(-1), 0.001);
            assertEquals(graphicWidth, graphic.localToScene(graphic.getLayoutBounds()).getWidth(), 0.001);
            assertEquals(compactIconWidth / 0.75, icon.localToScene(icon.getLayoutBounds()).getWidth(), 0.001);
        });
    }

    /// Keeps unused animation infrastructure unallocated, then reuses and stops it across state changes.
    @Test
    void lazyAnimations() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            VBox root = scene(control);
            assertNull(skinField(control, "geometryTransition"));
            assertNull(skinField(control, "colorTransition"));
            assertTrue(((List<?>) skinField(control, "colors")).isEmpty());

            control.setDisableAnimation(true);
            control.setSelected(true);
            layout(root);
            control.setSelected(false);
            control.setShowUnselectedIcon(true);
            layout(root);
            assertNull(skinField(control, "geometryTransition"));
            assertNull(skinField(control, "colorTransition"));
            assertTrue(((List<?>) skinField(control, "colors")).isEmpty());
            control.setSkin(null);
            control.setSkin(new JFXSwitchSkin(control));
            layout(root);

            control.setDisableAnimation(false);
            control.setSelected(true);
            layout(root);
            Animation geometry = assertInstanceOf(Animation.class, skinField(control, "geometryTransition"));
            Animation colors = assertInstanceOf(Animation.class, skinField(control, "colorTransition"));
            assertEquals(5, ((List<?>) skinField(control, "colors")).size());
            control.setDisableAnimation(true);
            assertEquals(Animation.Status.STOPPED, geometry.getStatus());
            assertEquals(Animation.Status.STOPPED, colors.getStatus());
            control.setSelected(false);
            layout(root);
            control.setDisableAnimation(false);
            control.setSelected(true);
            layout(root);
            assertSame(geometry, skinField(control, "geometryTransition"));
            assertSame(colors, skinField(control, "colorTransition"));
            control.setSkin(null);
            assertEquals(Animation.Status.STOPPED, geometry.getStatus());
            assertEquals(Animation.Status.STOPPED, colors.getStatus());
        });
    }

    /// Inspects allocation state without adding testing accessors to the production skin.
    private static @Nullable Object skinField(JFXSwitch control, String name) {
        try {
            Field field = JFXSwitchSkin.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(Objects.requireNonNull(control.getSkin()));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /// Avoids an unused icon node, then reuses a correctly styled icon across visibility and selection changes.
    @Test
    void lazyIcon() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setStyle("-jfx-switch-on-primary-container: #123456;");
            VBox root = scene(control);
            var artwork = (javafx.scene.Parent) Objects.requireNonNull(control.lookup(".switch-artwork"));
            assertEquals(3, artwork.getChildrenUnmodifiable().size());
            assertNull(control.lookup(".switch-icon"));
            control.setShowSelectedIcon(true);
            assertNull(control.lookup(".switch-icon"));

            control.setSelected(true);
            layout(root);
            var icon = (SVGPath) Objects.requireNonNull(control.lookup(".switch-icon"));
            assertTrue(icon.isVisible());
            assertEquals(4, artwork.getChildrenUnmodifiable().size());
            assertEquals(Color.web("#123456"), icon.getFill(), "Initial styling must not animate from black");
            String check = icon.getContent();
            control.setShowSelectedIcon(false);
            assertFalse(icon.isVisible());
            control.setShowUnselectedIcon(true);
            control.setSelected(false);
            layout(root);
            assertSame(icon, control.lookup(".switch-icon"));
            assertTrue(icon.isVisible());
            assertNotEquals(check, icon.getContent());
            control.setShowUnselectedIcon(false);
            control.setShowUnselectedIcon(true);
            assertSame(icon, control.lookup(".switch-icon"));
            assertEquals(4, artwork.getChildrenUnmodifiable().size());
        });
    }

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
            assertEquals(Color.web("#767683"), handle.getBackground().getFills().get(0).getFill());

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

    /// Defers selection styling to CSS/layout, coalesces changes, and keeps initial and theme colors immediate.
    @Test
    void deferredSelectionColors() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setSelected(true);
            control.setStyle("-jfx-switch-primary: #123456;");
            VBox root = scene(control);
            Region track = part(control, "track");
            assertEquals(Color.web("#123456"), track.getBackground().getFills().get(0).getFill());
            AtomicInteger changes = new AtomicInteger();
            track.backgroundProperty().addListener(observable -> changes.incrementAndGet());

            control.setSelected(false);
            control.setSelected(true);
            assertEquals(0, changes.get(), "Selection listeners must not force a CSS pass");
            layout(root);
            assertEquals(0, changes.get(), "Changes returning to the styled state should be coalesced");

            control.setStyle("-jfx-switch-primary: #abcdef;");
            layout(root);
            assertEquals(Color.web("#abcdef"), track.getBackground().getFills().get(0).getFill(),
                    "A completed layout must not leave selection animation pending for a theme change");

            control.setSelected(false);
            control.setDisableAnimation(true);
            layout(root);
            assertEquals(Color.web("#e3e1e9"), track.getBackground().getFills().get(0).getFill());
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
                control.setStyle("-jfx-switch-primary: #123456;");
                target.complete(control);
                VBox root = scene(control);
                Stage stage = new Stage();
                window.complete(stage);
                stage.setScene(root.getScene());
                stage.show();
                part(control, "track").backgroundProperty().addListener(observable -> changes.incrementAndGet());
                control.setSelected(true);
                assertEquals(0, changes.get());
            });
            Thread.sleep(400);
            onFxThread(() -> {
                assertEquals(Color.web("#123456"),
                        part(target.join(), "track").getBackground().getFills().get(0).getFill());
                assertTrue(changes.get() > 2, "Normal pulses must produce intermediate colors");
            });
        } finally {
            onFxThread(() -> {
                if (window.isDone())
                    window.join().close();
            });
        }
    }

    /// Checks visible color interpolation, reversal continuity, CSS precedence, and immediate completion.
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
            assertEquals(off.join(), palette(control), "Selection must start from the visible palette");
        });
        Thread.sleep(80);
        onFxThread(() -> {
            JFXSwitch control = created.join();
            @Unmodifiable List<Paint> current = palette(control);
            assertNotEquals(off.join(), current, "Colors must advance during the transition");
            assertNotEquals(on.join(), current, "Colors must interpolate before reaching the target");
            control.setSelected(false);
            assertEquals(current, palette(control), "Reversing must preserve the displayed colors");
        });
        Thread.sleep(300);
        onFxThread(() -> {
            JFXSwitch control = created.join();
            assertEquals(off.join(), palette(control));
            control.setSelected(true);
            control.setDisableAnimation(true);
            assertEquals(on.join(), palette(control), "Disabling animation must settle all colors");
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
        return List.of(part(control, "track").getBackground().getFills().get(0).getFill(),
                part(control, "track").getBorder().getStrokes().get(0).getTopStroke(),
                part(control, "handle").getBackground().getFills().get(0).getFill(),
                part(control, "state-layer").getBackground().getFills().get(0).getFill(),
                Objects.requireNonNull(((SVGPath) control.lookup(".switch-icon")).getFill()));
    }

    /// Verifies interrupted transitions settle to the latest state and disabling animation snaps immediately.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void animationRetargeting(boolean compact) throws Exception {
        CompletableFuture<JFXSwitch> created = new CompletableFuture<>();
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setSize(compact ? 24 : 32);
            scene(control);
            created.complete(control);
            control.setSelected(true);
            control.setSelected(false);
            control.setSelected(true);
        });
        Thread.sleep(100);
        onFxThread(() -> {
            JFXSwitch control = created.join();
            VBox root = (VBox) control.getParent();
            layout(root);
            Region handle = part(control, "handle");
            double center = handle.getLayoutX() + handle.getWidth() / 2;
            double diameter = handle.getWidth();
            control.setSelected(false);
            layout(root);
            assertEquals(center, handle.getLayoutX() + handle.getWidth() / 2, 0.001);
            assertEquals(diameter, handle.getWidth(), 0.001);
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
            assertEquals(compact ? 18 : 24, handle.localToScene(handle.getLayoutBounds()).getWidth(), 0.001);
            assertEquals(36, handle.getLayoutX() + handle.getWidth() / 2, 0.001);
            control.setSelected(false);
            control.setDisableAnimation(true);
            layout(root);
            assertEquals(16, handle.getWidth());
            assertEquals(16, handle.getLayoutX() + handle.getWidth() / 2);
        });
    }

    /// Checks theme lookup overrides and logical movement mirrored by the scene graph.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void themeAndRtl(boolean compact) throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setSize(compact ? 24 : 32);
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
