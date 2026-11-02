// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.WindowEvent;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
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
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies row sizing, tag folding, text interaction, and optional child lifecycle.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class TwoLineListItemLayoutTest {

    /// Verifies preferred width includes tags and updates when immutable values are replaced or removed.
    @Test
    public void testNaturalWidthUpdates() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Example", "a.jar");
            HBox parent = new HBox(item);
            new Scene(parent);
            layout(parent, 800);
            double originalWidth = item.getWidth();

            item.addTags(List.of("Fabric", "Quilt"));
            layout(parent, 800);
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
        });
    }

    /// Verifies constrained titles receive the remaining width and subtitles wrap below the whole first row.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testConstrainedTextAndFolding(boolean wrapTitle) {
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
            Label summary = collapsedTag(item);
            assertEquals("+2", summary.getText());
            assertEquals("Fabric\nNeoForge", prepareTooltipText(summary));
            assertEquals(item.getWidth(), item.getTitleLabel().getWidth() + summary.getWidth(), 0.001);
            for (Label tag : labels(item))
                assertFalse(tag.isVisible());
            assertTrue(item.getLayoutX() + item.getWidth() <= parent.getWidth());
            assertTrue(item.getSubtitleLabel().getLayoutY() >= item.getTitleLabel().getLayoutY() + item.getTitleLabel().getHeight());
            assertTrue(item.getSubtitleLabel().getHeight() > item.getSubtitleLabel().prefHeight(-1));
            assertTrue(item.getSubtitleLabel().getLayoutY() + item.getSubtitleLabel().getHeight() <= item.getHeight() + 0.001);
            if (wrapTitle)
                assertTrue(item.getTitleLabel().getHeight() > item.getTitleLabel().prefHeight(-1));

            item.setTitle("Mod");
            item.getTags().setAll(new Tag("A tag too wide for the available space ".repeat(10), Tag.Type.NORMAL));
            layout(parent, 300);
            Label tag = labels(item).get(0);
            assertFalse(tag.isVisible());
            assertEquals("+1", summary.getText());
            assertTrue(summary.getLayoutX() + summary.getWidth() <= item.getWidth());
            assertTrue(tag.isMouseTransparent());
            assertFalse(summary.isMouseTransparent());
            assertFalse(item.isMouseTransparent());
        });
    }

    /// Verifies partial and complete folding, tooltip contents, and expansion after tags change.
    @Test
    public void testFoldingAndUpdates() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", null);
            item.getTitleLabel().setMinWidth(Region.USE_PREF_SIZE);
            item.setTitleSpacing(4);
            item.addTags(List.of("Fabric Loader", "Fabric API", "Mod Menu"));
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 800);
            double naturalWidth = item.prefWidth(-1);
            Label summary = collapsedTag(item);
            assertFalse(summary.isVisible());
            assertTagsFit(item);

            @Unmodifiable List<Label> tags = labels(item);
            layout(parent, naturalWidth - tags.get(2).getWidth());
            int expanded = (int) tags.stream().filter(Node::isVisible).count();
            assertTrue(expanded > 0 && expanded < tags.size());
            for (int i = 0; i < tags.size(); i++)
                assertEquals(i < expanded, tags.get(i).isVisible());
            assertTrue(summary.isVisible());
            assertEquals("+" + (tags.size() - expanded), summary.getText());
            assertEquals(tags.subList(expanded, tags.size()).stream()
                    .map(Label::getText).collect(Collectors.joining("\n")), prepareTooltipText(summary));
            assertTrue(summary.getWidth() >= summary.prefWidth(-1));
            assertTrue(summary.getLayoutX() + summary.getWidth() <= item.getWidth());
            assertEquals(naturalWidth, item.prefWidth(-1));
            layout(parent, naturalWidth);
            assertTagsFit(item);
            assertFalse(summary.isVisible());

            layout(parent, item.minWidth(-1));
            assertEquals("+3", summary.getText());
            assertEquals(0, item.getTitleLabel().getWidth());
            assertEquals(summary.getWidth() + 4, item.getWidth(), 0.001);

            item.getTags().setAll(Tag.warning("Changed"), new Tag("Changed"));
            layout(parent, item.minWidth(-1));
            assertEquals("+2", summary.getText());
            assertEquals("Changed\nChanged", prepareTooltipText(summary));
            item.getTags().clear();
            layout(parent, 500);
            assertFalse(summary.isVisible());
            assertTrue(item.isMouseTransparent());
            item.addTag("Restored");
            layout(parent, 500);
            assertTagsFit(item);
            assertFalse(summary.isVisible());
        });
    }

    /// Verifies list mutations update text and warning styles while reusing existing labels.
    @Test
    public void testTagUpdatesAndReuse() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", null);
            item.addTags(List.of("Fabric", "Forge", "Quilt"));
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 800);
            @Unmodifiable List<Label> initial = labels(item);

            item.getTags().setAll(Tag.warning("Warning"), new Tag("Updated"), new Tag("Other"));
            layout(parent, 800);
            assertEquals(initial, labels(item));
            assertEquals(List.of("Warning", "Updated", "Other"), labels(item).stream().map(Label::getText).toList());
            assertTrue(initial.get(0).getPseudoClassStates().contains(PseudoClass.getPseudoClass("warning")));

            item.getTags().remove(0);
            item.getTags().add(new Tag("Added"));
            layout(parent, 800);
            assertEquals(List.of("Updated", "Other", "Added"), labels(item).stream().map(Label::getText).toList());
            assertFalse(labels(item).get(0).getPseudoClassStates().contains(PseudoClass.getPseudoClass("warning")));
            assertTagsFit(item);
        });
    }

    /// Verifies folded tags render updated text and CSS when expanded again.
    @Test
    public void testFoldedTagUpdates() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("A long title ".repeat(20), null);
            item.addTags(List.of("Fabric", "Forge", "Quilt"));
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 200);
            for (Label label : labels(item)) {
                assertFalse(label.isVisible());
            }

            Label first = labels(item).get(0);
            item.getTags().set(0, Tag.warning("Updated tag"));
            first.setStyle("-fx-font-size: 30px; -fx-padding: 4px 12px;");
            layout(parent, 200);
            double width = item.prefWidth(-1);
            layout(parent, width);
            assertTagsFit(item);
            assertSame(first, labels(item).get(0));
            Text renderedText = first.getChildrenUnmodifiable().stream().filter(Text.class::isInstance)
                    .map(Text.class::cast).findFirst().orElseThrow();
            assertEquals("Updated tag", renderedText.getText());
            assertEquals(30, first.getFont().getSize());
            assertEquals(Math.ceil(first.prefHeight(-1)), first.getHeight());

            // Re-expansion must also lay out an unchanged-size label whose text changed while hidden.
            layout(parent, 200);
            item.getTags().set(0, new Tag("Revised tag"));
            layout(parent, 200);
            layout(parent, item.prefWidth(-1));
            assertEquals("Revised tag", renderedText.getText());
            assertTagsFit(item);
        });
    }

    /// Verifies horizontal bias and row height follow wrapping and tag changes.
    @Test
    public void testContentBiasTracksChildren() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title ".repeat(20), "Subtitle ".repeat(20));
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 300);
            assertNull(item.getContentBias());
            double unwrappedHeight = item.getHeight();
            item.getTitleLabel().setWrapText(true);
            assertEquals(Orientation.HORIZONTAL, item.getContentBias());
            layout(parent, 300);
            assertTrue(item.getHeight() > unwrappedHeight);
            item.getTitleLabel().setWrapText(false);
            item.getSubtitleLabel().setWrapText(true);
            assertEquals(Orientation.HORIZONTAL, item.getContentBias());
            layout(parent, 300);
            assertTrue(item.getHeight() > unwrappedHeight);
            item.setSubtitle(null);
            assertNull(item.getContentBias());

            item.addTag("Tag");
            assertEquals(Orientation.HORIZONTAL, item.getContentBias());
            item.getTags().clear();
            assertNull(item.getContentBias());
        });
    }

    /// Verifies truncated text enables tooltip interaction and wrapping or removal disables it.
    /// On JavaFX versions before 23, verifies truncated labels remain mouse-transparent.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testTruncatedTextInteraction(boolean subtitle) {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", "Subtitle");
            Label label = subtitle ? item.getSubtitleLabel() : item.getTitleLabel();
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 250);
            assertTrue(label.isMouseTransparent());
            assertTrue(item.isMouseTransparent());

            String description = "A long description that cannot fit on one line. ".repeat(3);
            if (subtitle)
                item.setSubtitle(description);
            else
                item.setTitle(description);
            layout(parent, 250);
            if (FXUtils.JAVAFX_MAJOR_VERSION < 23) {
                assertNull(FXUtils.textTruncatedProperty(label));
                assertTrue(label.isMouseTransparent());
                assertTrue(item.isMouseTransparent());
                return;
            }
            assertTrue(requireNonNull(FXUtils.textTruncatedProperty(label)).get());
            assertFalse(label.isMouseTransparent());
            assertFalse(item.isMouseTransparent());

            layout(parent, 2000);
            assertFalse(requireNonNull(FXUtils.textTruncatedProperty(label)).get());
            assertTrue(item.isMouseTransparent());
            label.setWrapText(true);
            layout(parent, 250);
            assertFalse(requireNonNull(FXUtils.textTruncatedProperty(label)).get());
            assertTrue(item.isMouseTransparent());
            label.setWrapText(false);
            layout(parent, 250);
            assertFalse(item.isMouseTransparent());
            if (subtitle)
                item.setSubtitle(null);
            else
                item.setTitle("Title");
            layout(parent, 250);
            assertTrue(item.isMouseTransparent());
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
            assertEquals(item.getTitleLabel().getLayoutX() + item.getTitleLabel().getWidth() + 4, trailing.getLayoutX(), 0.001);
            assertTrue(labels(item).get(0).getLayoutX() >= trailing.getLayoutX() + trailing.getWidth() + 4);
            assertNotSame(labels(item).get(0), labels(other).get(0));

            Button replacement = new Button("Inherit");
            item.setTitleTrailing(replacement);
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
            LineComponent.setMargin(right, new Insets(0, 5, 0, 19));
            VBox parent = new VBox(line);
            new Scene(parent);
            layout(parent, 280);

            TwoLineListItem content = (TwoLineListItem) requireNonNull(line.lookup(".line-component-container"));
            assertSame(line, content.getParent());
            assertSame(content, right.getParent());
            Label subtitle = content.getSubtitleLabel();
            assertEquals(content.getTitleLabel().prefWidth(-1), content.getTitleLabel().getWidth(), 1);
            assertTrue(line.getHeight() >= 48);
            assertTrue(subtitle.getHeight() > subtitle.prefHeight(-1));
            assertTrue(subtitle.getLayoutY() + subtitle.getHeight() <= content.getHeight() + 0.001);
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
            assertFalse(content.isMouseTransparent());
            line.setRight(null);
            assertTrue(content.isMouseTransparent());
        });
    }

    /// Verifies binding, replacement, visibility, and managed-state changes for optional side nodes.
    @Test
    public void testSideNodeProperties() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", null);
            var source = new SimpleObjectProperty<@Nullable Node>();
            item.leadingProperty().bind(source);
            Button leading = new Button("Leading");
            source.set(leading);
            Button trailing = new Button("Trailing");
            item.setTrailing(trailing);
            HBox parent = new HBox(item);
            new Scene(parent);
            layout(parent, 800);
            assertSame(leading, item.getLeading());
            assertSame(item, leading.getParent());
            assertFalse(item.isMouseTransparent());

            double originalWidth = item.prefWidth(-1);
            leading.setVisible(false);
            assertEquals(originalWidth, item.prefWidth(-1));
            assertSame(item, leading.getParent());
            leading.setManaged(false);
            assertNull(leading.getParent());
            assertTrue(item.prefWidth(-1) < originalWidth);
            assertFalse(item.isMouseTransparent());
            leading.setManaged(true);
            leading.setVisible(true);
            assertSame(item, leading.getParent());

            Button replacement = new Button("Replacement");
            source.set(replacement);
            assertNull(leading.getParent());
            leading.setVisible(false);
            leading.setVisible(true);
            assertNull(leading.getParent());
            assertSame(item, replacement.getParent());
            source.set(null);
            assertFalse(item.isMouseTransparent());
            item.setTrailing(null);
            assertTrue(item.isMouseTransparent());
        });
    }

    /// Verifies side margins supply the entire gap and changes invalidate row sizing.
    @Test
    public void testSideMarginsControlSpacing() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", "Subtitle");
            Region leading = new Region();
            leading.setMinSize(32, 32);
            leading.setPrefSize(32, 32);
            leading.setMaxSize(32, 32);
            Button trailing = new Button("Action");
            item.setLeading(leading);
            item.setTrailing(trailing);
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 400);
            double width = item.prefWidth(-1);
            assertEquals(leading.getLayoutX() + leading.getWidth(), item.getTitleLabel().getLayoutX(), 0.001);
            assertEquals(trailing.getLayoutX(), item.getSubtitleLabel().getLayoutX() + item.getSubtitleLabel().getWidth(), 0.001);
            TwoLineListItem.setMargin(leading, new Insets(0, 3, 0, 5));
            TwoLineListItem.setMargin(trailing, new Insets(0, 7, 0, 9));
            layout(parent, 400);
            assertEquals(width + 24, item.prefWidth(-1), 0.001);
            assertEquals(leading.getLayoutX() + leading.getWidth() + 3,
                    item.getTitleLabel().getLayoutX(), 0.001);
            assertEquals(trailing.getLayoutX() - 9,
                    item.getSubtitleLabel().getLayoutX() + item.getSubtitleLabel().getWidth(), 0.001);
            TwoLineListItem.setMargin(leading, null);
            TwoLineListItem.setMargin(trailing, null);
            layout(parent, 400);
            assertEquals(width, item.prefWidth(-1), 0.001);
        });
    }

    /// Verifies side margins, vertical centering, wrapped text width, and folding before the trailing control.
    @Test
    public void testSideNodeLayout() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", "An explanation that wraps. ".repeat(15));
            item.setPadding(new Insets(7, 11, 13, 17));
            item.getSubtitleLabel().setWrapText(true);
            item.addTag("A long tag ".repeat(30));
            Region leading = new Region();
            leading.setMinSize(40, 40);
            leading.setPrefSize(40, 40);
            leading.setMaxSize(40, 40);
            Button trailing = new Button("Open");
            trailing.setMinWidth(Region.USE_PREF_SIZE);
            item.setLeading(leading);
            item.setTrailing(trailing);
            TwoLineListItem.setMargin(leading, new Insets(3, 17, 9, 2));
            TwoLineListItem.setMargin(trailing, new Insets(5, 3, 1, 19));
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 320);

            assertSame(item, leading.getParent());
            assertSame(item, trailing.getParent());
            assertEquals(19, leading.getLayoutX(), 0.001);
            double textLeft = leading.getLayoutX() + leading.getWidth() + 17;
            assertEquals(textLeft, item.getTitleLabel().getLayoutX(), 0.001);
            assertEquals(textLeft, item.getSubtitleLabel().getLayoutX(), 0.001);
            assertEquals(item.getWidth() - 14, trailing.getLayoutX() + trailing.getWidth(), 0.001);
            double textRight = trailing.getLayoutX() - 19;
            assertEquals(textRight - textLeft, item.getSubtitleLabel().getWidth(), 0.001);
            Label tag = labels(item).get(0);
            assertFalse(tag.isVisible());
            Label summary = collapsedTag(item);
            assertEquals("+1", summary.getText());
            assertTrue(summary.getLayoutX() + summary.getWidth() <= textRight);
            assertTrue(item.getSubtitleLabel().getHeight() > item.getSubtitleLabel().prefHeight(-1));
            double rowCenter = 7 + (item.getHeight() - 20) / 2;
            assertEquals(rowCenter - 3, leading.getLayoutY() + leading.getHeight() / 2, 1);
            assertEquals(rowCenter + 2, trailing.getLayoutY() + trailing.getHeight() / 2, 1);

            double heightWithMargins = item.prefHeight(item.getWidth());
            TwoLineListItem.setMargin(trailing, new Insets(5, 30, 1, 82));
            layout(parent, 320);
            assertTrue(item.getHeight() > heightWithMargins);
        });
    }

    /// Verifies flexible trailing content shrinks to preserve the tag minimum without exceeding row bounds.
    @Test
    public void testSideNodeWidthConstraints() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Setting", null);
            item.getTitleLabel().setMinWidth(Region.USE_PREF_SIZE);
            Label trailing = new Label("A long selected value ".repeat(30));
            trailing.setMinWidth(50);
            item.setTrailing(trailing);
            TwoLineListItem.setMargin(trailing, new Insets(0, 0, 0, 12));
            item.addTag("Tag");
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 300);
            assertEquals(0, item.getTitleLabel().getWidth());
            assertTrue(trailing.getWidth() >= 50 && trailing.getWidth() < trailing.prefWidth(-1));
            assertTrue(trailing.getLayoutX() >= item.getTitleLabel().getLayoutX() + item.getTitleLabel().getWidth());
            assertEquals(item.getWidth(), trailing.getLayoutX() + trailing.getWidth(), 0.001);
            Label summary = collapsedTag(item);
            assertTrue(summary.isVisible());
            assertTrue(summary.getLayoutX() + summary.getWidth() <= trailing.getLayoutX() - 12);
        });
    }

    /// Returns rendered tags in their scene-graph order.
    private static @Unmodifiable List<Label> labels(TwoLineListItem item) {
        return item.getChildrenUnmodifiable().stream().filter(node -> node.getStyleClass().contains("tag")
                        && !node.getStyleClass().contains("collapsed-tag"))
                .map(Label.class::cast).toList();
    }

    /// Returns the summary tag, including when it is hidden.
    private static Label collapsedTag(TwoLineListItem item) {
        return (Label) requireNonNull(item.lookup(".collapsed-tag"));
    }

    /// Initializes the hover tooltip and runs its preparation callback without opening a window.
    private static String prepareTooltipText(Label summary) {
        if (summary.getTooltip() == null) {
            summary.fireEvent(new MouseEvent(MouseEvent.MOUSE_ENTERED, 1, 1, 1, 1, MouseButton.NONE,
                    0, false, false, false, false, false, false, false, false, false, true,
                    new PickResult(summary, 1, 1)));
        }
        Tooltip tooltip = requireNonNull(summary.getTooltip());
        tooltip.getOnShowing().handle(new WindowEvent(tooltip, WindowEvent.WINDOW_SHOWING));
        return tooltip.getText();
    }

    /// Verifies every tag is a visible direct child displayed at its full preferred width.
    private static void assertTagsFit(TwoLineListItem item) {
        assertEquals(item.getTags().size(), labels(item).size());
        for (Label label : labels(item)) {
            assertSame(item, label.getParent());
            assertTrue(label.isVisible());
            assertEquals(Math.ceil(label.prefWidth(-1)), label.getWidth(), 0.001);
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
