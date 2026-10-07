/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.decorator;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jackhuang.hmcl.JavaFXLauncher;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises real window-manager geometry; enable with `hmcl.test.nativeWindows=true` in the test JVM.
/// These tests require a desktop session and intentionally show, maximize, and hide windows.
@NotNullByDefault
@EnabledIfSystemProperty(named = "hmcl.test.nativeWindows", matches = "true")
final class WindowBoundsTest {
    /// The normal content rectangle used across window styles.
    private static final Rectangle2D INITIAL = new Rectangle2D(300, 200, 920, 560);

    /// Starts the toolkit and keeps it alive between test windows.
    @BeforeAll
    static void startToolkit() throws Exception {
        assertTrue(JavaFXLauncher.isStarted());
        onFxThread(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    /// Verifies Scene reuse, repeated style changes, and hiding do not accumulate frame or shadow offsets.
    @Test
    void preservesContentAcrossStylesAndHiding() throws Exception {
        Fixture fixture = onFxThread(Fixture::new);
        try {
            for (int i = 0; i < 6; i++) {
                StageStyle style = i % 2 == 0 ? StageStyle.TRANSPARENT : nativeStyle();
                onFxThread(() -> {
                    fixture.replace(style, false);
                    return null;
                });
                awaitContent(fixture, INITIAL);
            }
            onFxThread(() -> {
                fixture.stage.hide();
                assertRectangle(INITIAL, fixture.saved);
                fixture.stage.show();
                return null;
            });
            awaitContent(fixture, INITIAL);
        } finally {
            onFxThread(() -> {
                fixture.close();
                return null;
            });
        }
    }

    /// Verifies native and custom minimum sizes, resizing, and position persistence use content coordinates.
    @Test
    void tracksResizingAndMinimumContentSize() throws Exception {
        Fixture fixture = onFxThread(Fixture::new);
        try {
            for (StageStyle style : new StageStyle[] {StageStyle.TRANSPARENT, nativeStyle()}) {
                onFxThread(() -> {
                    fixture.replace(style, false);
                    return null;
                });
                awaitContent(fixture, INITIAL);
                onFxThread(() -> {
                    fixture.stage.setWidth(fixture.stage.getMinWidth());
                    fixture.stage.setHeight(fixture.stage.getMinHeight());
                    fixture.stage.setX(fixture.stage.getX() + 40);
                    fixture.stage.setY(fixture.stage.getY() + 20);
                    return null;
                });
                await(() -> {
                    assertTrue(fixture.content.getWidth() >= 800 && fixture.content.getWidth() < 802,
                            () -> "Content width: " + fixture.content.getWidth() + ", scene: " + fixture.scene.getWidth()
                                    + ", stage: " + fixture.stage.getWidth() + ", minimum: " + fixture.stage.getMinWidth());
                    assertTrue(fixture.content.getHeight() >= 490 && fixture.content.getHeight() < 492,
                            () -> "Content height: " + fixture.content.getHeight());
                    assertEquals(fixture.content.getWidth(), fixture.saved.getWidth(), 0.01);
                    assertEquals(fixture.content.getHeight(), fixture.saved.getHeight(), 0.01);
                    assertEquals(340, fixture.saved.getMinX(), 0.01);
                    assertEquals(220, fixture.saved.getMinY(), 0.01);
                    return true;
                });
                // Native minimum sizes are rounded to whole logical units and then to physical pixels.
                // Test fractional-size preservation above that constraint, where both styles can fit exactly.
                Rectangle2D resized = onFxThread(() -> {
                    Rectangle2D expected = new Rectangle2D(340, 220,
                            fixture.saved.getWidth() + 24, fixture.saved.getHeight() + 24);
                    fixture.stage.setWidth(fixture.stage.getWidth() + 24);
                    fixture.stage.setHeight(fixture.stage.getHeight() + 24);
                    return expected;
                });
                awaitContent(fixture, resized);
                for (int i = 0; i < 4; i++) {
                    StageStyle replacementStyle = i % 2 == 0 ? StageStyle.TRANSPARENT : nativeStyle();
                    onFxThread(() -> {
                        fixture.replace(replacementStyle, false);
                        return null;
                    });
                    awaitContent(fixture, resized);
                }
                onFxThread(() -> {
                    fixture.close();
                    fixture.saved = INITIAL;
                    return null;
                });
            }
        } finally {
            onFxThread(() -> {
                fixture.close();
                return null;
            });
        }
    }

    /// Verifies saving bounds on detach cannot turn overlapping size notifications into new window minimums.
    @Test
    void keepsMinimumSizeStableWhenDetachedDuringResize() throws Exception {
        Fixture fixture = onFxThread(Fixture::new);
        AtomicBoolean resizeAgain = new AtomicBoolean();
        AtomicBoolean detached = new AtomicBoolean();
        InvalidationListener clientSizeListener = observable -> {
            if (resizeAgain.getAndSet(false)) {
                // Leave a second outer-size request pending while the first client notification is delivered.
                fixture.stage.setWidth(fixture.stage.getWidth() + 60);
            }
        };
        ChangeListener<Number> detachListener = (observable, oldValue, newValue) -> {
            if (detached.compareAndSet(false, true)) {
                Objects.requireNonNull(fixture.bounds).close();
            }
        };
        try {
            onFxThread(() -> {
                fixture.scene.widthProperty().addListener(clientSizeListener);
                return null;
            });
            for (StageStyle style : new StageStyle[] {StageStyle.TRANSPARENT, nativeStyle()}) {
                onFxThread(() -> {
                    fixture.replace(style, false);
                    return null;
                });
                awaitContent(fixture, INITIAL);
                List<Double> minimumChanges = new ArrayList<>();
                onFxThread(() -> {
                    fixture.stage.minWidthProperty().addListener((observable, oldValue, newValue) ->
                            minimumChanges.add(newValue.doubleValue()));
                    fixture.stage.minHeightProperty().addListener((observable, oldValue, newValue) ->
                            minimumChanges.add(newValue.doubleValue()));
                    detached.set(false);
                    fixture.scene.widthProperty().addListener(detachListener);
                    resizeAgain.set(true);
                    fixture.stage.setWidth(fixture.stage.getWidth() + 20);
                    fixture.stage.setHeight(fixture.stage.getHeight() + 20);
                    return null;
                });
                await(detached::get);
                onFxThread(() -> {
                    fixture.scene.widthProperty().removeListener(detachListener);
                    fixture.close();
                    assertTrue(minimumChanges.isEmpty(), () -> "Minimum sizes changed during resizing: " + minimumChanges);
                    fixture.saved = INITIAL;
                    return null;
                });
            }
        } finally {
            onFxThread(() -> {
                fixture.scene.widthProperty().removeListener(clientSizeListener);
                fixture.scene.widthProperty().removeListener(detachListener);
                fixture.close();
                return null;
            });
        }
    }

    /// Verifies a style switch while maximized retains the normal rectangle for subsequent restoration.
    @Test
    void preservesNormalBoundsWhenMaximized() throws Exception {
        Fixture fixture = onFxThread(Fixture::new);
        try {
            onFxThread(() -> {
                fixture.replace(OperatingSystem.CURRENT_OS == OperatingSystem.MACOS
                        ? nativeStyle() : StageStyle.TRANSPARENT, false);
                return null;
            });
            awaitContent(fixture, INITIAL);
            onFxThread(() -> {
                fixture.stage.setMaximized(true);
                return null;
            });
            await(() -> fixture.stage.isMaximized() && fixture.content.getWidth() > INITIAL.getWidth());
            onFxThread(() -> {
                assertRectangle(INITIAL, fixture.saved);
                fixture.replace(nativeStyle(), true);
                return null;
            });
            await(() -> fixture.stage.isMaximized() && fixture.content.getWidth() > INITIAL.getWidth());
            onFxThread(() -> {
                assertRectangle(INITIAL, fixture.saved);
                fixture.stage.hide();
                assertRectangle(INITIAL, fixture.saved);
                fixture.stage.show();
                return null;
            });
            await(() -> fixture.stage.isMaximized() && fixture.content.getWidth() > INITIAL.getWidth());
            onFxThread(() -> {
                assertRectangle(INITIAL, fixture.saved);
                fixture.stage.setMaximized(false);
                return null;
            });
            awaitContent(fixture, INITIAL);
        } finally {
            onFxThread(() -> {
                fixture.close();
                return null;
            });
        }
    }

    /// Verifies full-screen and iconified states do not replace the saved normal rectangle.
    @Test
    void preservesNormalBoundsThroughFullScreenAndMinimization() throws Exception {
        Fixture fixture = onFxThread(Fixture::new);
        try {
            for (StageStyle style : new StageStyle[] {StageStyle.TRANSPARENT, nativeStyle()}) {
                onFxThread(() -> {
                    fixture.replace(style, false);
                    fixture.stage.setFullScreenExitHint("");
                    return null;
                });
                awaitContent(fixture, INITIAL);
                onFxThread(() -> {
                    fixture.stage.setFullScreen(true);
                    return null;
                });
                await(() -> fixture.stage.isFullScreen() && fixture.content.getWidth() > INITIAL.getWidth());
                onFxThread(() -> {
                    assertRectangle(INITIAL, fixture.saved);
                    fixture.stage.setFullScreen(false);
                    return null;
                });
                awaitContent(fixture, INITIAL);
                onFxThread(() -> {
                    fixture.stage.setIconified(true);
                    return null;
                });
                await(() -> fixture.stage.isIconified());
                onFxThread(() -> {
                    assertRectangle(INITIAL, fixture.saved);
                    fixture.stage.setIconified(false);
                    return null;
                });
                awaitContent(fixture, INITIAL);
            }
        } finally {
            onFxThread(() -> {
                fixture.close();
                return null;
            });
        }
    }

    /// Returns EXTENDED on JavaFX 27 or later when supported, otherwise the traditional native frame.
    ///
    /// Queries capabilities on the JavaFX application thread, waiting for the result when called elsewhere.
    private static StageStyle nativeStyle() throws Exception {
        if (!Platform.isFxApplicationThread()) {
            return onFxThread(WindowBoundsTest::nativeStyle);
        }
        try {
            int version = Integer.parseInt(System.getProperty("javafx.version", "0").split("[.\\-+]")[0]);
            if (version < 27 || !Platform.isSupported(ConditionalFeature.valueOf("EXTENDED_WINDOW"))) {
                return StageStyle.DECORATED;
            }
            return StageStyle.valueOf("EXTENDED");
        } catch (IllegalArgumentException e) {
            return StageStyle.DECORATED;
        }
    }

    /// Waits for actual node geometry and persisted bounds to agree with the expected rectangle.
    private static void awaitContent(Fixture fixture, Rectangle2D expected) throws Exception {
        await(() -> {
            Point2D origin = Objects.requireNonNull(fixture.content.localToScreen(0, 0));
            assertRectangle(expected, new Rectangle2D(origin.getX(), origin.getY(),
                    fixture.content.getWidth(), fixture.content.getHeight()));
            assertRectangle(expected, fixture.saved);
            assertEquals(expected.getWidth(), fixture.bounds.contentWidthProperty().get(), 0.01);
            assertEquals(expected.getHeight(), fixture.bounds.contentHeightProperty().get(), 0.01);
            assertEquals(800 + fixture.stage.getWidth() - fixture.content.getWidth(), fixture.stage.getMinWidth(), 0.01);
            assertEquals(490 + fixture.stage.getHeight() - fixture.content.getHeight(), fixture.stage.getMinHeight(), 0.01);
            return true;
        });
    }

    /// Compares logical screen coordinates, allowing floating-point conversion error.
    private static void assertRectangle(Rectangle2D expected, Rectangle2D actual) {
        assertEquals(expected.getMinX(), actual.getMinX(), 0.01, "content x");
        assertEquals(expected.getMinY(), actual.getMinY(), 0.01, "content y");
        assertEquals(expected.getWidth(), actual.getWidth(), 0.01, "content width");
        assertEquals(expected.getHeight(), actual.getHeight(), 0.01, "content height");
    }

    /// Retries assertions while asynchronous native events and layout pulses are being delivered.
    private static void await(Callable<Boolean> check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try {
                if (onFxThread(check)) {
                    return;
                }
            } catch (java.util.concurrent.ExecutionException e) {
                if (!(e.getCause() instanceof AssertionError) || System.nanoTime() >= deadline) {
                    throw e;
                }
            }
            assertTrue(System.nanoTime() < deadline, "Native window did not reach the expected state");
            Thread.sleep(50);
        }
    }

    /// Runs an action on the JavaFX application thread and propagates its result or failure.
    private static <T extends @Nullable Object> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    /// Owns a retained scene and mirrors the decorator's shadow and stage-replacement lifecycle.
    @NotNullByDefault
    private static final class Fixture {
        /// The node whose on-screen content geometry is checked.
        final Region content = new Region();

        /// The retained scene root providing custom shadow padding.
        final StackPane root = new StackPane(content);

        /// The scene transferred between stages.
        final Scene scene = new Scene(root);

        /// The last normal bounds reported by the tracker.
        Rectangle2D saved = INITIAL;

        /// The current stage, initially an unshown placeholder.
        Stage stage = new Stage();

        /// The current tracker, absent before the first replacement or after closing.
        @Nullable WindowBounds bounds;

        /// Creates a scene without showing a window.
        Fixture() {
            // Measure client geometry independently of StackPane's outward pixel snapping during layout.
            root.setSnapToPixel(false);
        }

        /// Transfers the retained scene to a new style and optionally starts maximized.
        void replace(StageStyle style, boolean maximized) {
            close();
            Insets normalInsets = style == StageStyle.TRANSPARENT ? new Insets(8) : Insets.EMPTY;
            root.setPadding(normalInsets);
            stage = new Stage(style);
            stage.setScene(scene);
            javafx.beans.InvalidationListener decorationListener = o ->
                    root.setPadding(WindowState.isMaximized(stage) || stage.isFullScreen() ? Insets.EMPTY : normalInsets);
            stage.maximizedProperty().addListener(decorationListener);
            stage.fullScreenProperty().addListener(decorationListener);
            bounds = new WindowBounds(stage, root, saved, 800, 490, value -> saved = value);
            stage.setMaximized(maximized && WindowState.supportsMaximization(stage));
            stage.show();
        }

        /// Releases listeners before removing the scene and hiding its previous stage.
        void close() {
            if (bounds != null) {
                bounds.close();
                bounds = null;
            }
            stage.setScene(null);
            stage.hide();
        }
    }
}
