// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.skins.JFXSwitchSkin;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
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
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises the switch through a live JavaFX scene, including CSS and inherited behavior.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
@Isolated("Temporarily supplies settings while initializing the cached animation policy")
public final class JFXSwitchTest {

    /// Initializes the cached animation policy without loading or changing persisted settings.
    @BeforeAll
    static void initializeAnimationPolicy() throws Exception {
        // AnimationUtils caches its policy at class initialization. Without in-memory settings,
        // that initialization calls SettingsManager.settings() and loads configuration from disk.
        // Preserve any previously initialized policy; animation tests select their own policy
        // explicitly, so their behavior does not depend on which test initialized the class first.
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

    /// Preserves compact row height and overrides the inherited value once despite synchronous binder refresh.
    @Test
    void inheritableRowDragging() throws Exception {
        onFxThread(() -> {
            LineInheritableSwitch row = new LineInheritableSwitch();
            row.setTitle("Setting");
            row.overriddenProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            row.rawValueProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            AtomicInteger actions = new AtomicInteger();
            row.addEventHandler(javafx.event.ActionEvent.ACTION, event -> actions.incrementAndGet());
            VBox root = new VBox(row);
            applyStylesheets(new Scene(root, 300, 100));
            layout(root);
            JFXSwitch control = (JFXSwitch) Objects.requireNonNull(row.lookup(".jfx-switch"));
            assertEquals(30, control.getHeight());
            assertEquals(50, row.getHeight());
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

    /// Preserves grab offsets, ignores secondary input, and commits only changed drag endpoints at either scale.
    @ParameterizedTest
    @CsvSource({"false, 32, 16", "true, 24, 4"})
    void handleDragging(boolean rtl, double size, double pressX) throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setStyle("-jfx-size: " + size + ";");
            control.setDisableAnimation(true);
            VBox root = scene(control);
            root.setNodeOrientation(rtl ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
            layout(root);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            Shape handle = part(control, "handle");
            double initialX = handle.localToScene(handle.getLayoutBounds()).getCenterX();
            mouse(control, MouseEvent.MOUSE_PRESSED, pressX, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, pressX + 12, true);
            layout(root);
            assertFalse(control.isSelected());
            double draggedX = handle.localToScene(handle.getLayoutBounds()).getCenterX();
            assertEquals((rtl ? -1 : 1) * 12 * size / 32, draggedX - initialX, 0.001,
                    "The handle must follow the pointer without snapping to its position");
            mouse(control, MouseEvent.MOUSE_PRESSED, pressX + 12, MouseButton.SECONDARY, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, pressX + 12, MouseButton.SECONDARY, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, pressX + 8, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, pressX + 20, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, pressX + 20, false);
            layout(root);
            assertTrue(control.isSelected(), "Commit the release position, preserving the original grab offset");
            assertEquals(1, actions.get());
            assertFalse(control.isArmed());

            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 30, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 30, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 30, false);
            assertTrue(control.isSelected());
            assertEquals(1, actions.get(), "Dragging back to the selected endpoint must not fire an action");
            layout(root);
            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 30, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, -10, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, -10, false);
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());
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

    /// Scales artwork and its target through CSS, respects bindings, and falls back for invalid sizes.
    @Test
    void styleableSize() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            VBox root = scene(control);
            assertEquals(52, control.prefWidth(-1));
            assertEquals(48, control.prefHeight(-1));
            control.setStyle("-jfx-size: 24px;");
            layout(root);
            assertEquals(39, control.prefWidth(-1));
            assertEquals(36, control.prefHeight(-1));
            Shape track = part(control, "track");
            assertEquals(39, track.localToScene(track.getLayoutBounds()).getWidth(), 0.001);
            assertEquals(24, track.localToScene(track.getLayoutBounds()).getHeight(), 0.001);

            SimpleDoubleProperty boundSize = new SimpleDoubleProperty(40);
            control.sizeProperty().bind(boundSize);
            control.setStyle("-jfx-size: 28px;");
            layout(root);
            assertEquals(65, control.prefWidth(-1));
            boundSize.set(Double.NaN);
            layout(root);
            assertEquals(52, control.prefWidth(-1));
            assertEquals(48, control.prefHeight(-1));
        });
    }

    /// Verifies keyboard and accessibility activation share selection and action-event semantics.
    @Test
    void keyboardAndAccessibility() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setAccessibleText("Wi-Fi");
            control.setDisableAnimation(true);
            scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
            assertEquals(AccessibleRole.TOGGLE_BUTTON, control.getAccessibleRole());
            assertEquals(true, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
            assertEquals("Wi-Fi", control.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());
            key(control, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
            boolean enterActivates = OperatingSystem.CURRENT_OS != OperatingSystem.MACOS;
            assertEquals(enterActivates, control.isSelected());
            assertEquals(enterActivates ? 3 : 2, actions.get());
            control.setDisable(true);
            control.fire();
            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertEquals(enterActivates ? 3 : 2, actions.get());
        });
    }

    /// Keeps the settings row compact and emits one action for row, pointer, and keyboard activation.
    @Test
    void rowActionDispatch() throws Exception {
        onFxThread(() -> {
            LineSwitch row = new LineSwitch();
            row.setTitle("Setting");
            VBox root = new VBox(row);
            applyStylesheets(new Scene(root, 300, 100));
            layout(root);
            JFXSwitch control = (JFXSwitch) Objects.requireNonNull(row.lookup(".jfx-switch"));
            assertEquals(30, control.getHeight());
            assertEquals(50, row.getHeight());
            control.setDisableAnimation(true);
            AtomicInteger rowActions = new AtomicInteger();
            AtomicInteger parentActions = new AtomicInteger();
            row.addEventHandler(javafx.event.ActionEvent.ACTION, event -> rowActions.incrementAndGet());
            root.addEventHandler(javafx.event.ActionEvent.ACTION, event -> parentActions.incrementAndGet());

            row.fire();
            assertTrue(row.isSelected());
            assertEquals(1, rowActions.get());
            assertEquals(1, parentActions.get());

            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 37, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 37, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 37, false);
            assertFalse(row.isSelected());
            assertEquals(2, rowActions.get());
            assertEquals(2, parentActions.get());

            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertTrue(row.isSelected());
            assertEquals(3, rowActions.get());
            assertEquals(3, parentActions.get());
            root.setDisable(true);
            row.fire();
            assertTrue(row.isSelected(), "A disabled row must not write the bound setting");
            assertEquals(3, rowActions.get());
            assertEquals(3, parentActions.get());
        });
    }

    /// Covers repeated keys, canceled input, bound selection, and click cancellation outside the target.
    @Test
    void inputLifecycle() throws Exception {
        onFxThread(() -> {
            var control = new JFXSwitch() {
                /// Exercises focus cancellation without depending on native window activation.
                void changeFocus(boolean focused) {
                    setFocused(focused);
                }
            };
            control.setDisableAnimation(true);
            scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 16, false);
            assertTrue(control.isArmed());
            assertEquals(0, actions.get());
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, actions.get());
            assertFalse(control.isArmed());

            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            control.setDisable(true);
            control.setDisable(false);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.ESCAPE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            control.changeFocus(true);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            control.changeFocus(false);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertFalse(control.isArmed());
            assertEquals(1, actions.get());

            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_EXITED, 60, true);
            assertFalse(control.isArmed());
            mouse(control, MouseEvent.MOUSE_RELEASED, 60, false);
            assertEquals(1, actions.get());
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_EXITED, 60, true);
            mouse(control, MouseEvent.MOUSE_ENTERED, 16, true);
            assertTrue(control.isArmed());
            mouse(control, MouseEvent.MOUSE_RELEASED, 16, false);
            assertEquals(2, actions.get());

            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            control.setSkin(null);
            control.setSkin(new JFXSwitchSkin(control));
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(2, actions.get());
            assertFalse(control.isArmed());
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(3, actions.get(), "The replacement skin must handle new input exactly once");

            SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
            control.selectedProperty().bind(selected);
            control.fire();
            control.executeAccessibleAction(AccessibleAction.FIRE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            assertTrue(control.isSelected());
            assertFalse(control.isArmed());
            assertEquals(3, actions.get(), "Bound selection must ignore every activation path");
            selected.set(false);
            assertFalse(control.isSelected());
            assertEquals(false, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    /// Keeps bound icon and animation options effective before skin creation and after replacement.
    @Test
    void optionUpdatesAcrossSkinReplacement() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            assertEquals(!AnimationUtils.isAnimationEnabled(), control.isDisableAnimation());
            SimpleBooleanProperty immediate = new SimpleBooleanProperty(true);
            control.disableAnimationProperty().bind(immediate);
            SimpleBooleanProperty selectedIcon = new SimpleBooleanProperty(false);
            control.showSelectedIconProperty().bind(selectedIcon);
            selectedIcon.set(true);
            control.setSelected(true);
            VBox root = scene(control);
            assertTrue(part(control, "icon").isVisible());
            selectedIcon.set(false);
            assertFalse(part(control, "icon").isVisible());

            control.setSkin(null);
            selectedIcon.set(true);
            control.setSkin(new JFXSwitchSkin(control));
            layout(root);
            assertTrue(part(control, "icon").isVisible());
            selectedIcon.set(false);
            assertFalse(part(control, "icon").isVisible());
            selectedIcon.set(true);
            assertTrue(part(control, "icon").isVisible());
            immediate.set(false);
            control.setSelected(false);
            immediate.set(true);
            assertEquals(16, part(control, "handle").getLayoutBounds().getWidth());
            assertEquals(16, part(control, "handle").getBoundsInParent().getCenterX());
        });
    }

    /// Uses real pulses to animate selection without manually applying CSS or laying out after the change.
    @Test
    void selectionColorsOnPulse() throws Exception {
        Stage window = callOnFxThread(Stage::new);
        try {
            JFXSwitch control = callOnFxThread(() -> {
                JFXSwitch target = new JFXSwitch();
                target.setDisableAnimation(false);
                target.setStyle("-jfx-switch-primary: #123456;");
                VBox root = scene(target);
                window.setScene(root.getScene());
                window.show();
                Paint initial = Objects.requireNonNull(part(target, "track").getFill());
                assertNotEquals(Color.web("#123456"), initial);
                target.setSelected(true);
                assertEquals(initial, part(target, "track").getFill());
                return target;
            });
            awaitFx("selection colors after automatic CSS and layout",
                    () -> Color.web("#123456").equals(part(control, "track").getFill()));
        } finally {
            onFxThread(window::close);
        }
    }

    /// Checks reversal continuity, eventual completion, theme overrides, and immediate completion.
    @Test
    void selectionColorTransitions() throws Exception {
        JFXSwitch control = callOnFxThread(() -> {
            JFXSwitch target = new JFXSwitch();
            target.setShowSelectedIcon(true);
            target.setShowUnselectedIcon(true);
            target.setStyle("-jfx-switch-primary: #123456;");
            target.setDisableAnimation(true);
            scene(target);
            return target;
        });
        @Unmodifiable List<Paint> off = callOnFxThread(() -> palette(control));
        @Unmodifiable List<Paint> on = callOnFxThread(() -> {
            control.setSelected(true);
            layout(control);
            return palette(control);
        });
        onFxThread(() -> {
            control.setSelected(false);
            control.applyCss();
            control.setDisableAnimation(false);
            control.setSelected(true);
            layout(control);
            control.setSelected(false);
            layout(control);
            control.setDisableAnimation(true);
            assertEquals(off, palette(control), "Settling a pre-frame reversal must not need another CSS pass");
            control.setDisableAnimation(false);
            control.setSelected(true);
            layout(control);
            control.setSelected(false);
            control.setSelected(true);
            layout(control);
            assertEquals(off, palette(control), "Selection must start from the visible palette");
        });
        awaitFx("selection colors to advance before reversal", () -> {
            @Unmodifiable List<Paint> current = palette(control);
            if (off.equals(current))
                return false;
            // Reverse in the same FX callback that observes progress. A busy CI worker may
            // skip intermediate frames, so continuity must also hold at the final frame.
            Shape handle = part(control, "handle");
            double center = handle.getBoundsInParent().getCenterX();
            double diameter = handle.getLayoutBounds().getWidth();
            control.setSelected(false);
            layout(control);
            assertEquals(current, palette(control), "Reversing must preserve the displayed colors");
            assertEquals(center, handle.getBoundsInParent().getCenterX(), 0.001);
            assertEquals(diameter, handle.getLayoutBounds().getWidth(), 0.001);
            return true;
        });
        awaitFx("reversed selection colors to settle", () -> off.equals(palette(control)));
        onFxThread(() -> {
            control.setSelected(true);
            control.setDisableAnimation(true);
            layout(control);
            assertEquals(on, palette(control), "Disabling animation must settle all colors");
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

    /// Dispatches an unmodified key event to the switch.
    private static void key(JFXSwitch control, EventType<KeyEvent> type, KeyCode code) {
        control.fireEvent(new KeyEvent(type, "", "", code, false, false, false, false));
    }

    /// Captures rendered paints without applying CSS, which could hide an incorrect animation result.
    private static @Unmodifiable List<Paint> palette(JFXSwitch control) {
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

    /// Applies pending CSS changes and synchronously lays out the given subtree.
    private static void layout(Region root) {
        root.applyCss();
        root.layout();
    }

    /// Returns a required shape from the default skin.
    private static Shape part(JFXSwitch control, String name) {
        return (Shape) Objects.requireNonNull(control.lookup(".switch-" + name));
    }

    /// Evaluates a condition once per pulse, propagating failures and stopping on completion or timeout.
    private static void awaitFx(String description, BooleanSupplier condition) throws Exception {
        CompletableFuture<@Nullable Void> completed = new CompletableFuture<>();
        AnimationTimer timer = new AnimationTimer() {
            /// Checks on the FX thread so observations and any dependent actions are atomic between pulses.
            @Override
            public void handle(long now) {
                try {
                    if (condition.getAsBoolean()) {
                        stop();
                        completed.complete(null);
                    }
                } catch (Throwable failure) {
                    stop();
                    completed.completeExceptionally(failure);
                }
            }
        };
        try {
            onFxThread(timer::start);
            completed.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException failure) {
            fail("Timed out waiting for " + description, failure);
        } finally {
            onFxThread(timer::stop);
        }
    }

    /// Runs assertions on the application thread and propagates failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        callOnFxThread(() -> {
            action.run();
            return null;
        });
    }

    /// Returns a value from the application thread without sharing mutable test holders.
    private static <T extends @Nullable Object> T callOnFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
