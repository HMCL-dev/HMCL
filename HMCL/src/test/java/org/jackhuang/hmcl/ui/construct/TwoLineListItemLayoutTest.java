// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies direct child layout, clipping, styling, and reuse by line components.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class TwoLineListItemLayoutTest {

    /// Verifies preferred width includes tags and updates when immutable values are replaced or removed.
    @Test
    public void testNaturalWidthAndDirectChildren() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Example", "a.jar");
            HBox parent = new HBox(item);
            new Scene(parent);
            layout(parent, 800);
            double originalWidth = item.getWidth();
            item.getTags();
            layout(parent, 800);
            assertEquals(originalWidth, item.getWidth());

            item.addTags(List.of("Fabric", "Quilt"));
            layout(parent, 800);
            assertEquals(4, item.getChildren().size());
            assertTrue(item.getChildren().stream().allMatch(Label.class::isInstance));
            assertTagsFit(item);
            double taggedWidth = item.getWidth();
            assertTrue(taggedWidth > originalWidth);

            item.getTags().set(0, new Tag("A longer loader tag", Tag.Type.NORMAL));
            layout(parent, 800);
            assertTagsFit(item);
            assertTrue(item.getWidth() > taggedWidth);
            item.getTags().clear();
            layout(parent, 800);
            assertEquals(originalWidth, item.getWidth());
            item.addTag("Fabric");
            layout(parent, 800);
            assertTagsFit(item);
        });
    }

    /// Verifies constrained titles receive the remaining width and subtitles wrap below the whole first row.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testConstrainedTextAndClipping(boolean wrapTitle) {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("A long title ".repeat(30), "A long subtitle ".repeat(30));
            item.getTitleLabel().setWrapText(wrapTitle);
            item.getSubtitleLabel().setWrapText(true);
            item.addTags(List.of("Fabric", "NeoForge"));
            Region icon = new Region();
            icon.setMinSize(40, 40);
            icon.setPrefSize(40, 40);
            icon.setMaxSize(40, 40);
            HBox parent = new HBox(8, icon, item);
            new Scene(parent);
            layout(parent, 300);

            assertEquals(252, item.getWidth(), 0.001);
            assertEquals(item.getWidth(), item.getTitleLabel().getWidth(), 0.001);
            for (Label tag : labels(item))
                assertEquals(0, ((Rectangle) tag.getClip()).getWidth());
            assertTrue(item.getBoundsInParent().getMaxX() <= parent.getWidth(), "Clipped tags expand the item's bounds");
            assertTrue(item.getSubtitleLabel().getLayoutY() >= item.getTitleLabel().getBoundsInParent().getMaxY());
            assertTrue(item.getSubtitleLabel().getHeight() > item.getSubtitleLabel().prefHeight(-1));
            assertTrue(item.getSubtitleLabel().getBoundsInParent().getMaxY() <= item.getHeight() + 0.001);
            if (wrapTitle)
                assertTrue(item.getTitleLabel().getHeight() > item.getTitleLabel().prefHeight(-1));

            item.setTitle("Mod");
            item.getTags().setAll(new Tag("A tag too wide for the available space ".repeat(10), Tag.Type.NORMAL));
            layout(parent, 300);
            Label tag = labels(item).get(0);
            Rectangle clip = (Rectangle) tag.getClip();
            assertTrue(clip.getWidth() > 0 && clip.getWidth() < tag.getWidth());
            assertEquals(item.getWidth(), tag.getLayoutX() + clip.getWidth(), 0.001);
            assertTrue(tag.isMouseTransparent());
        });
    }

    /// Verifies trailing-node lifecycle and independent rendering of repeated and shared tag values.
    @Test
    public void testTrailingNodeAndSharedTags() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", null);
            Tag shared = Tag.warning("Warning");
            item.getTags().addAll(shared, shared);
            TwoLineListItem other = new TwoLineListItem();
            other.getTags().add(shared);
            HBox parent = new HBox(item, other);
            new Scene(parent);
            Button trailing = new Button("Reset");
            item.setTitleSpacing(4);
            item.setTitleTrailing(trailing);
            layout(parent, 800);

            assertSame(item, trailing.getParent());
            assertFalse(item.isMouseTransparent());
            assertFalse(item.isPickOnBounds());
            assertTrue(item.getTitleLabel().isMouseTransparent());
            assertEquals(item.getTitleLabel().getBoundsInParent().getMaxX() + 4, trailing.getLayoutX(), 0.001);
            assertTrue(labels(item).get(0).getLayoutX() >= trailing.getBoundsInParent().getMaxX() + 4);
            assertNotSame(labels(item).get(0), labels(other).get(0));

            double visibleWidth = item.prefWidth(-1);
            trailing.setVisible(false);
            assertSame(item, trailing.getParent());
            assertEquals(visibleWidth, item.prefWidth(-1));
            assertTrue(item.isMouseTransparent());
            trailing.setManaged(false);
            assertNull(trailing.getParent());
            assertTrue(item.prefWidth(-1) < visibleWidth);
            trailing.setManaged(true);
            trailing.setVisible(true);
            assertSame(item, trailing.getParent());
            Button replacement = new Button("Inherit");
            item.setTitleTrailing(replacement);
            trailing.setVisible(false);
            trailing.setVisible(true);
            assertNull(trailing.getParent());
            assertSame(item, replacement.getParent());

            item.getTags().set(0, new Tag("Normal", Tag.Type.NORMAL));
            assertFalse(labels(item).get(0).getPseudoClassStates().contains(PseudoClass.getPseudoClass("warning")));
            assertTrue(labels(other).get(0).getPseudoClassStates().contains(PseudoClass.getPseudoClass("warning")));
            item.setTitleTrailing(null);
            assertTrue(item.isMouseTransparent());
        });
    }

    /// Verifies composed line rows adapt their height to width and subtitle changes without fixed-height updates.
    @Test
    public void testLineComponentWrappingAndSizing() {
        onFxThread(() -> {
            LinePane line = new LinePane();
            line.setTitle("Setting");
            line.setSubtitle("A long explanation that must remain visible. ".repeat(20));
            line.getTags().add(Tag.warning("Warning"));
            Button trailing = new Button("Reset");
            line.setTitleTrailing(trailing);
            Button right = new Button("Open");
            right.setMinWidth(Region.USE_PREF_SIZE);
            line.setRight(right);
            LineComponent.setMargin(right, new Insets(0, 5, 0, 7));
            VBox parent = new VBox(line);
            new Scene(parent);
            layout(parent, 280);

            TwoLineListItem content = (TwoLineListItem) requireNonNull(line.lookup(".title-container"));
            Label subtitle = content.getSubtitleLabel();
            assertEquals(content.getTitleLabel().prefWidth(-1), content.getTitleLabel().getWidth(), 1);
            assertTrue(line.getHeight() >= 48);
            assertTrue(subtitle.getHeight() > subtitle.prefHeight(-1));
            assertTrue(subtitle.getBoundsInParent().getMaxY() <= content.getHeight() + 0.001);
            double narrowHeight = line.getHeight();
            layout(parent, 600);
            assertTrue(line.getHeight() < narrowHeight);
            layout(parent, 280);
            assertEquals(narrowHeight, line.getHeight(), 0.001);

            line.setSubtitle("");
            layout(parent, 280);
            assertNull(subtitle.getParent());
            assertEquals(48, line.getHeight(), 0.001);
            assertFalse(content.isMouseTransparent());
            line.setTitleTrailing(null);
            assertTrue(content.isMouseTransparent());
        });
    }

    /// Verifies flattened selectors, theme colors, vertical centering, and subtitle removal.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testStylesAndAlignment(boolean dark) {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", "Subtitle");
            item.addTagWarning("Warning");
            HBox container = new HBox(item);
            container.getStyleClass().add("container");
            StackPane rippler = new StackPane(container);
            rippler.getStyleClass().add("rippler-container");
            StackPane parent = new StackPane(rippler);
            parent.getStyleClass().add("advanced-list-item");
            parent.setStyle("-monet-on-surface: #111111; -monet-on-surface-variant: #222222; "
                    + "-monet-on-secondary-container: #333333; -monet-secondary-container: #eeeeee; "
                    + "-monet-on-error-container: #444444;");
            Scene scene = new Scene(parent);
            scene.getStylesheets().add(requireNonNull(getClass().getResource("/assets/css/root.css")).toExternalForm());
            scene.getStylesheets().add(requireNonNull(getClass().getResource(
                    "/assets/css/brightness-" + (dark ? "dark" : "light") + ".css")).toExternalForm());
            parent.applyCss();
            parent.resize(500, 150);
            parent.layout();

            assertEquals(13, item.getTitleLabel().getFont().getSize());
            assertEquals(10, item.getSubtitleLabel().getFont().getSize());
            double top = item.getChildren().stream().mapToDouble(node -> node.getLayoutY()).min().orElseThrow();
            double bottom = item.getHeight() - item.getSubtitleLabel().getBoundsInParent().getMaxY();
            assertTrue(top > 0);
            assertEquals(top, bottom, 1);
            assertEquals(item.getInsets().getLeft(), item.getTitleLabel().getLayoutX(), 0.001);
            assertEquals(Color.web(dark ? "#2c0b0e" : "#F1AEB5"),
                    labels(item).get(0).getBackground().getFills().get(0).getFill());
            assertTagsFit(item);

            parent.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), true);
            parent.applyCss();
            assertEquals(Color.web("#333333"), item.getTitleLabel().getTextFill());
            item.setSubtitle(null);
            parent.layout();
            assertNull(item.getSubtitleLabel().getParent());
            item.setSubtitle("");
            parent.layout();
            assertSame(item, item.getSubtitleLabel().getParent());
        });
    }

    /// Returns rendered tags in their scene-graph order.
    private static @Unmodifiable List<Label> labels(TwoLineListItem item) {
        return item.getChildren().stream().filter(node -> node.getStyleClass().contains("tag"))
                .map(Label.class::cast).toList();
    }

    /// Verifies every tag is a direct child whose clip includes its full preferred width.
    private static void assertTagsFit(TwoLineListItem item) {
        assertEquals(item.getTags().size(), labels(item).size());
        for (Label label : labels(item)) {
            assertSame(item, label.getParent());
            assertEquals(label.getWidth(), ((Rectangle) label.getClip()).getWidth(), 0.001);
            assertTrue(label.getLayoutX() + label.getWidth() <= item.getWidth() - item.getInsets().getRight() + 0.001);
        }
    }

    /// Applies CSS and lays out a parent at the given width and its computed preferred height.
    private static void layout(Region parent, double width) {
        parent.applyCss();
        parent.resize(width, parent.prefHeight(width));
        parent.layout();
    }

    /// Runs assertions on the JavaFX application thread and waits up to 30 seconds.
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
