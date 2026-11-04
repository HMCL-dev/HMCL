// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.decorator;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies direct page layout and compatibility with independently animated columns.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class DecoratorAnimatedPageTest {

    /// Checks sidebar width, insets, and content allocation at narrow and normal page widths.
    @ParameterizedTest
    @ValueSource(ints = {100, 480, 960})
    public void testColumnLayout(int width) {
        onFxThread(() -> {
            DecoratorAnimatedPage page = new DecoratorAnimatedPage();
            Region content = new Region();
            content.setMinSize(80, 40);
            page.setContent(content);
            page.setPadding(new Insets(7, 11, 13, 17));
            new Scene(page);
            page.applyCss();
            page.resize(width, 360);
            page.layout();
            assertEquals(17, page.getLeft().getLayoutX());
            assertEquals(7, page.getLeft().getLayoutY());
            assertEquals(200, page.getLeft().getLayoutBounds().getWidth());
            assertEquals(217, page.getCenter().getLayoutX());
            assertEquals(7, page.getCenter().getLayoutY());
            assertEquals(Math.max(80, width - 228), page.getCenter().getLayoutBounds().getWidth());
            assertEquals(340, page.getCenter().getLayoutBounds().getHeight());
            assertSame(page, page.getLeft().getParent());
            assertSame(page, page.getCenter().getParent());

            page.resize(width + 300, 520);
            page.layout();
            assertEquals(200, page.getLeft().getLayoutBounds().getWidth());
            assertEquals(width + 300 - 228, page.getCenter().getLayoutBounds().getWidth());
            assertEquals(500, page.getCenter().getLayoutBounds().getHeight());
        });
    }

    /// Checks that visibility preserves a column while managed-state changes release its space.
    @Test
    public void testColumnLifecycle() {
        onFxThread(() -> {
            DecoratorAnimatedPage page = new DecoratorAnimatedPage();
            Region oldContent = new Region();
            page.setContent(oldContent);
            Region newContent = new Region();
            page.setContent(newContent);
            assertNull(oldContent.getParent());
            assertSame(page.getCenter(), newContent.getParent());
            assertSame(newContent, page.getContent());
            page.setContent(null);
            assertNull(newContent.getParent());
            assertNull(page.getContent());
            page.resize(800, 600);
            page.layout();
            page.getLeft().setVisible(false);
            page.layout();
            assertEquals(200, page.getCenter().getLayoutX());
            page.getLeft().setManaged(false);
            page.layout();
            assertEquals(0, page.getCenter().getLayoutX());
            assertEquals(800, page.getCenter().getLayoutBounds().getWidth());
            assertEquals(0, page.minWidth(-1));
            page.getLeft().setManaged(true);
            page.getLeft().setVisible(true);
            page.layout();
            assertEquals(200, page.getCenter().getLayoutX());
            assertEquals(600, page.getCenter().getLayoutBounds().getWidth());
            page.getCenter().setManaged(false);
            assertEquals(200, page.minWidth(-1));
        });
    }

    /// Keeps bottom actions at their preferred height while long navigation lists scroll above them.
    @Test
    public void testSidebarBottomSizingAndReplacement() {
        onFxThread(() -> {
            DecoratorAnimatedPage page = new DecoratorAnimatedPage();
            AdvancedListBox sidebar = page.getSidebar();
            for (int i = 0; i < 100; i++) {
                sidebar.addNavigationDrawerItem("Entry " + i, null, null);
            }
            Region bottom = new Region();
            bottom.setPrefHeight(70);
            page.setSidebarBottom(bottom);
            new Scene(page);
            page.applyCss();
            page.resize(800, 300);
            page.layout();
            assertSame(page.getLeft(), sidebar.getParent());
            assertSame(page.getLeft(), bottom.getParent());
            assertSame(bottom, page.getSidebarBottom());
            assertEquals(70, bottom.getHeight(), 0.001);
            assertEquals(218, bottom.getLayoutY(), 0.001);
            assertEquals(218, sidebar.getHeight(), 0.001);
            assertTrue(sidebar.getContent().getLayoutBounds().getHeight() > sidebar.getViewportBounds().getHeight());

            bottom.setPrefHeight(110);
            page.layout();
            assertEquals(110, bottom.getHeight(), 0.001);
            assertEquals(178, sidebar.getHeight(), 0.001);
            bottom.setManaged(false);
            page.layout();
            assertEquals(300, sidebar.getHeight(), 0.001);

            Region replacement = new Region();
            replacement.setPrefHeight(40);
            page.setSidebarBottom(replacement);
            page.layout();
            assertNull(bottom.getParent());
            assertEquals(248, sidebar.getHeight(), 0.001);
            page.setSidebarBottom(null);
            page.layout();
            assertNull(replacement.getParent());
            assertNull(page.getSidebarBottom());
            assertSame(sidebar, page.getSidebar());
            assertEquals(300, sidebar.getHeight(), 0.001);
        });
    }

    /// Sizes real bottom navigation lists from their contents, including entries added after layout.
    @Test
    public void testSidebarBottomListUsesPreferredHeight() {
        onFxThread(() -> {
            DecoratorAnimatedPage page = new DecoratorAnimatedPage();
            AdvancedListBox bottom = new AdvancedListBox()
                    .addNavigationDrawerItem("Action", null, null);
            page.setSidebarBottom(bottom);
            new Scene(page);
            page.resize(800, 600);
            settleLayout(page);
            double initialHeight = bottom.getHeight();
            assertEquals(Math.max(bottom.minHeight(200), bottom.prefHeight(200)), initialHeight, 0.001);
            assertEquals(600 - initialHeight - 12, page.getSidebar().getHeight(), 0.001);

            bottom.addNavigationDrawerItem("Another action", null, null)
                    .addNavigationDrawerItem("Third action", null, null);
            settleLayout(page);
            assertTrue(bottom.getHeight() > initialHeight);
            assertEquals(Math.max(bottom.minHeight(200), bottom.prefHeight(200)), bottom.getHeight(), 0.001);
            assertEquals(600 - bottom.getHeight() - 12, page.getSidebar().getHeight(), 0.001);
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
            assertEquals(593.5, page.getCenter().getLayoutBounds().getWidth());
            assertEquals(595.25, page.getCenter().getLayoutBounds().getHeight());
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
            Region bottom = new Region();
            next.setSidebarBottom(bottom);
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
            assertSame(next.getLeft(), bottom.getParent());
            assertSame(next.getLeft(), next.getSidebar().getParent());
            assertEquals(next.getSidebar().localToScene(0, 0).getX(), bottom.localToScene(0, 0).getX(), 0.001);
            animation.jumpTo(animation.getTotalDuration());
            animation.stop();
            ContainerAnimations.NAVIGATION.finish(next);
            assertEquals(0, next.getLeft().getTranslateX());
            assertEquals(0, next.getCenter().getTranslateX());
            assertEquals(1, next.getOpacity());
        });
    }

    /// Restores mixed cache states through the same end-jump and stop sequence used for cancellation.
    @ParameterizedTest
    @ValueSource(ints = {0, 5, 10, 15})
    public void testNavigationCacheRestoration(int mask) {
        onFxThread(() -> {
            DecoratorAnimatedPage previous = new DecoratorAnimatedPage();
            DecoratorAnimatedPage next = new DecoratorAnimatedPage();
            @Unmodifiable List<Node> panels = List.of(previous.getLeft(), previous.getCenter(), next.getLeft(), next.getCenter());
            @Unmodifiable List<CacheHint> hints = List.of(CacheHint.DEFAULT, CacheHint.SPEED, CacheHint.QUALITY, CacheHint.SCALE);
            for (int i = 0; i < panels.size(); i++) {
                panels.get(i).setCache((mask & (1 << i)) != 0);
                panels.get(i).setCacheHint(hints.get(i));
            }
            Animation animation = ContainerAnimations.NAVIGATION.animate(new Pane(previous, next), previous, next,
                    Duration.millis(200), Interpolator.LINEAR);
            for (int i = 0; i < panels.size(); i++)
                assertEquals((mask & (1 << i)) != 0, panels.get(i).isCache());

            animation.play();
            animation.pause();
            for (int millis : List.of(1, 100, 199)) {
                animation.jumpTo(Duration.millis(millis));
                assertTrue(panels.stream().allMatch(Node::isCache));
                assertFalse(previous.isCache());
                assertFalse(next.isCache());
                for (int i = 0; i < panels.size(); i++)
                    assertEquals(hints.get(i), panels.get(i).getCacheHint());
            }
            animation.jumpTo(animation.getTotalDuration());
            animation.stop();
            for (int i = 0; i < panels.size(); i++) {
                assertEquals((mask & (1 << i)) != 0, panels.get(i).isCache());
                assertEquals(hints.get(i), panels.get(i).getCacheHint());
            }
        });
    }

    /// Allows scroll-pane skins to update viewport-dependent preferred sizes after CSS and layout.
    private static void settleLayout(DecoratorAnimatedPage page) {
        for (int pass = 0; pass < 3; pass++) {
            page.applyCss();
            page.layout();
        }
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
