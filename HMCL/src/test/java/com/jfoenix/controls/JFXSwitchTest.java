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
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
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

    /// Preserves compact row height and covers dragging with representative sizes and layout directions.
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
            assertEquals(30, control.getHeight());
            assertEquals(50, row.getHeight());
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
            assertEquals(30, control.prefHeight(-1));
            root.getStyleClass().remove("line-switch");
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
            assertEquals(36, control.prefHeight(-1));
        });
    }

    /// Verifies keyboard and accessibility activation share selection and action-event semantics.
    @Test
    void keyboardAndAccessibility() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            control.setAccessibleText("Wi-Fi");
            assertEquals(!AnimationUtils.isAnimationEnabled(), control.isDisableAnimation());
            control.setDisableAnimation(true);
            scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            control.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
            control.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.SPACE, false, false, false, false));
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
            assertEquals(AccessibleRole.TOGGLE_BUTTON, control.getAccessibleRole());
            assertEquals(true, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
            assertEquals("Wi-Fi", control.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());
            control.setDisable(true);
            control.fire();
            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertEquals(2, actions.get());
        });
    }

    /// Emits one action at the row and its parent for row, pointer, and keyboard activation.
    @Test
    void rowActionDispatch() throws Exception {
        onFxThread(() -> {
            LineSwitch row = new LineSwitch();
            VBox root = new VBox(row);
            applyStylesheets(new Scene(root, 300, 100));
            layout(root);
            JFXSwitch control = (JFXSwitch) Objects.requireNonNull(row.lookup(".jfx-switch"));
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
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 36, false);
            assertFalse(row.isSelected());
            assertEquals(2, rowActions.get());
            assertEquals(2, parentActions.get());

            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertTrue(row.isSelected());
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
            assertEquals(2, actions.get());
            selected.set(false);
            assertFalse(control.isSelected());
            assertEquals(false, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    /// Keeps bound options and keyboard activation working across skin replacement.
    @Test
    void optionUpdatesAcrossSkinReplacement() throws Exception {
        onFxThread(() -> {
            JFXSwitch control = new JFXSwitch();
            SimpleBooleanProperty immediate = new SimpleBooleanProperty(true);
            control.disableAnimationProperty().bind(immediate);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            SimpleBooleanProperty selectedIcon = new SimpleBooleanProperty(false);
            control.showSelectedIconProperty().bind(selectedIcon);
            control.setShowUnselectedIcon(true);
            control.setShowUnselectedIcon(false);
            selectedIcon.set(true);
            selectedIcon.set(false);
            VBox root = scene(control);
            assertEquals(16, part(control, "handle").getLayoutBounds().getWidth());

            selectedIcon.set(true);
            selectedIcon.set(false);
            selectedIcon.set(true);
            control.setSelected(true);
            layout(root);
            assertTrue(part(control, "icon").isVisible());
            selectedIcon.set(false);
            assertFalse(part(control, "icon").isVisible());
            selectedIcon.set(true);
            assertTrue(part(control, "icon").isVisible());

            control.setSkin(null);
            selectedIcon.set(false);
            selectedIcon.set(true);
            selectedIcon.set(false);
            control.setSkin(new JFXSwitchSkin(control));
            selectedIcon.set(true);
            layout(root);
            assertEquals(1, control.lookupAll(".switch-track").size());
            assertEquals(52, control.prefWidth(-1));
            assertEquals(48, control.prefHeight(-1));
            assertTrue(part(control, "icon").isVisible());
            immediate.set(false);
            control.setSelected(false);
            immediate.set(true);
            assertEquals(16, part(control, "handle").getLayoutBounds().getWidth());
            assertEquals(16, part(control, "handle").getBoundsInParent().getCenterX());
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
        });
    }

    /// Uses real pulses to animate selection without manually applying CSS or laying out after the change.
    @Test
    void selectionColorsOnPulse() throws Exception {
        CompletableFuture<Stage> window = new CompletableFuture<>();
        CompletableFuture<JFXSwitch> target = new CompletableFuture<>();
        try {
            onFxThread(() -> {
                JFXSwitch control = new JFXSwitch();
                control.setDisableAnimation(false);
                control.setStyle("-jfx-switch-primary: #123456;");
                target.complete(control);
                VBox root = scene(control);
                Stage stage = new Stage();
                window.complete(stage);
                stage.setScene(root.getScene());
                stage.show();
                Paint initial = Objects.requireNonNull(part(control, "track").getFill());
                assertNotEquals(Color.web("#123456"), initial);
                control.setSelected(true);
                assertEquals(initial, part(control, "track").getFill());
            });
            awaitFx("selection colors after automatic CSS and layout",
                    () -> Color.web("#123456").equals(part(target.join(), "track").getFill()));
        } finally {
            onFxThread(() -> {
                if (window.isDone())
                    window.join().close();
            });
        }
    }

    /// Checks reversal continuity, eventual completion, theme overrides, and immediate completion.
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
            control.setDisableAnimation(true);
            scene(control);
            created.complete(control);
            off.complete(palette(control));
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
        awaitFx("selection colors to advance before reversal", () -> {
            JFXSwitch control = created.join();
            @Unmodifiable List<Paint> current = palette(control);
            if (off.join().equals(current))
                return false;
            // Reverse in the same FX callback that observes progress. A busy CI worker may
            // skip intermediate frames, so continuity must also hold at the final frame.
            Shape handle = part(control, "handle");
            double center = handle.getBoundsInParent().getCenterX();
            double diameter = handle.getLayoutBounds().getWidth();
            control.setSelected(false);
            assertEquals(current, palette(control), "Reversing must preserve the displayed colors");
            assertEquals(center, handle.getBoundsInParent().getCenterX(), 0.001);
            assertEquals(diameter, handle.getLayoutBounds().getWidth(), 0.001);
            return true;
        });
        awaitFx("reversed selection colors to settle", () -> off.join().equals(palette(created.join())));
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
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
