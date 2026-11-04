// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.decorator;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies direct page layout and compatibility with independently animated columns.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class DecoratorAnimatedPageTest {

    /// Checks wrapping, insets, minimum-size overflow, and column placement against the previous layout.
    @ParameterizedTest
    @ValueSource(ints = {100, 480, 960})
    public void testLayoutMatchesBorderPane(int width) {
        onFxThread(() -> {
            DecoratorAnimatedPage page = new DecoratorAnimatedPage();
            populate(page.getLeft(), page.getCenter());
            page.setPadding(new Insets(7, 11, 13, 17));
            new Scene(page);
            page.applyCss();

            VBox left = new VBox();
            FXUtils.setLimitWidth(left, 200);
            StackPane center = new StackPane();
            populate(left, center);
            // The old Control applied its insets outside the skin's BorderPane.
            StackPane reference = new StackPane(new BorderPane(center, null, null, null, left));
            reference.setPadding(page.getPadding());
            new Scene(reference);
            reference.applyCss();

            assertEquals(Orientation.HORIZONTAL, page.getContentBias());
            assertEquals(reference.minWidth(-1), page.minWidth(-1), 0.001);
            assertEquals(reference.prefWidth(-1), page.prefWidth(-1), 0.001);
            assertEquals(reference.minHeight(width), page.minHeight(width), 0.001);
            assertEquals(reference.prefHeight(width), page.prefHeight(width), 0.001);
            page.resize(width, 360);
            reference.resize(width, 360);
            page.layout();
            reference.layout();
            assertBounds(left, page.getLeft(), 17, 7);
            assertBounds(center, page.getCenter(), 17, 7);
            assertEquals(200, page.getLeft().getWidth());
            assertSame(page, page.getLeft().getParent());
            assertSame(page, page.getCenter().getParent());

            page.resize(width + 300, 520);
            reference.resize(width + 300, 520);
            page.layout();
            reference.layout();
            assertBounds(left, page.getLeft(), 17, 7);
            assertBounds(center, page.getCenter(), 17, 7);
        });
    }

    /// Checks that visibility preserves a column while managed-state changes release its space.
    @Test
    public void testColumnLifecycle() {
        onFxThread(() -> {
            DecoratorAnimatedPage page = new DecoratorAnimatedPage();
            Region oldContent = new Region();
            page.setCenter(oldContent);
            Region newContent = new Region();
            page.setCenter(newContent);
            assertNull(oldContent.getParent());
            assertSame(page.getCenter(), newContent.getParent());
            page.resize(800, 600);
            page.layout();
            page.getLeft().setVisible(false);
            page.layout();
            assertEquals(200, page.getCenter().getLayoutX());
            page.getLeft().setManaged(false);
            page.layout();
            assertEquals(0, page.getCenter().getLayoutX());
            assertEquals(800, page.getCenter().getWidth());
            assertEquals(0, page.minWidth(-1));
            page.getLeft().setManaged(true);
            page.getLeft().setVisible(true);
            page.layout();
            assertEquals(200, page.getCenter().getLayoutX());
            assertEquals(600, page.getCenter().getWidth());
            page.getCenter().setManaged(false);
            assertEquals(200, page.minWidth(-1));
        });
    }

    /// Checks that fractional layout respects snapping and remains independent of animation transforms.
    @Test
    public void testFractionalLayoutAndTransforms() {
        onFxThread(() -> {
            DecoratorAnimatedPage page = new DecoratorAnimatedPage();
            page.setSnapToPixel(false);
            page.setPadding(new Insets(1.25, 2.5, 3.75, 4.5));
            page.resize(800.5, 600.25);
            page.layout();
            assertEquals(4.5, page.getLeft().getLayoutX());
            assertEquals(204.5, page.getCenter().getLayoutX());
            assertEquals(593.5, page.getCenter().getWidth());
            assertEquals(595.25, page.getCenter().getHeight());
            page.getLeft().setTranslateX(-30);
            page.getCenter().setTranslateX(30);
            page.requestLayout();
            page.layout();
            assertEquals(204.5, page.getCenter().getLayoutX());
            assertEquals(-30, page.getLeft().getTranslateX());
            assertEquals(30, page.getCenter().getTranslateX());
        });
    }

    /// Checks that navigation still animates both columns and restores the incoming page.
    @Test
    public void testNavigationAnimation() {
        onFxThread(() -> {
            DecoratorAnimatedPage previous = new DecoratorAnimatedPage();
            DecoratorAnimatedPage next = new DecoratorAnimatedPage();
            Pane host = new Pane(previous, next);
            assertFalse(ContainerAnimations.NAVIGATION.shouldCache(next));
            Animation animation = ContainerAnimations.NAVIGATION.animate(host, previous, next,
                    Duration.millis(200), Interpolator.LINEAR);
            animation.play();
            animation.pause();
            animation.jumpTo(Duration.millis(100));
            assertEquals(-30, previous.getLeft().getTranslateX());
            assertEquals(30, previous.getCenter().getTranslateX());
            assertEquals(-30, next.getLeft().getTranslateX());
            assertEquals(30, next.getCenter().getTranslateX());
            animation.stop();
            ContainerAnimations.NAVIGATION.finish(next);
            assertEquals(0, next.getLeft().getTranslateX());
            assertEquals(0, next.getCenter().getTranslateX());
            assertEquals(1, next.getOpacity());
        });
    }

    /// Supplies matching sidebar and wrapping content to the old and new layouts.
    private static void populate(VBox left, StackPane center) {
        left.getChildren().setAll(new Label("Sidebar"), new Label("Second entry"));
        Label content = new Label("Content that wraps when the page becomes narrow. ".repeat(12));
        content.setWrapText(true);
        center.getChildren().setAll(content);
    }

    /// Compares layout positions and allocated dimensions without including transforms.
    private static void assertBounds(Region expected, Region actual, double offsetX, double offsetY) {
        assertEquals(expected.getLayoutX() + offsetX, actual.getLayoutX(), 0.001);
        assertEquals(expected.getLayoutY() + offsetY, actual.getLayoutY(), 0.001);
        assertEquals(expected.getWidth(), actual.getWidth(), 0.001);
        assertEquals(expected.getHeight(), actual.getHeight(), 0.001);
    }

    /// Runs assertions on the JavaFX thread and propagates failures to the test runner.
    private static void onFxThread(Runnable action) {
        FutureTask<Boolean> task = new FutureTask<>(action, true);
        Platform.runLater(task);
        try {
            task.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError(e);
        }
    }
}
