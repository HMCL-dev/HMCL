// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXListView;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.ComponentRemoteVersionList;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.download.VersionsPage;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies direct child layout, tag folding, styling, and reuse by line components.
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
            assertEquals(5, item.getChildrenUnmodifiable().size());
            assertTrue(item.getChildrenUnmodifiable().stream().allMatch(Label.class::isInstance));
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
            assertTrue(item.getBoundsInParent().getMaxX() <= parent.getWidth(), "Hidden tags expand the item's bounds");
            assertTrue(item.getSubtitleLabel().getLayoutY() >= item.getTitleLabel().getBoundsInParent().getMaxY());
            assertTrue(item.getSubtitleLabel().getHeight() > item.getSubtitleLabel().prefHeight(-1));
            assertTrue(item.getSubtitleLabel().getBoundsInParent().getMaxY() <= item.getHeight() + 0.001);
            if (wrapTitle)
                assertTrue(item.getTitleLabel().getHeight() > item.getTitleLabel().prefHeight(-1));

            item.setTitle("Mod");
            item.getTags().setAll(new Tag("A tag too wide for the available space ".repeat(10), Tag.Type.NORMAL));
            layout(parent, 300);
            Label tag = labels(item).get(0);
            assertFalse(tag.isVisible());
            assertEquals("+1", summary.getText());
            assertTrue(summary.getBoundsInParent().getMaxX() <= item.getWidth());
            assertTrue(tag.isMouseTransparent());
            assertFalse(summary.isMouseTransparent());
            assertFalse(item.isMouseTransparent());
        });
    }

    /// Verifies whole-tag folding across count digit changes, CSS sizing, resizing, and list mutations.
    @Test
    public void testFoldingBoundariesAndUpdates() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("A title with a preferred minimum", null);
            item.getTitleLabel().setMinWidth(Region.USE_PREF_SIZE);
            item.setTitleSpacing(4);
            item.addTags(IntStream.rangeClosed(1, 12).mapToObj(i -> "Tag " + i).toList());
            VBox parent = new VBox(item);
            new Scene(parent);
            for (Node node : item.getChildrenUnmodifiable()) {
                if (node.getStyleClass().contains("tag"))
                    node.setStyle("-fx-font-size: 19px; -fx-padding: 3px 11px; -fx-label-padding: 0 2px;");
            }
            layout(parent, 2000);
            double naturalWidth = item.prefWidth(-1);
            double titleWidth = item.getTitleLabel().prefWidth(-1);
            Label summary = collapsedTag(item);
            assertFalse(summary.isVisible());
            assertTagsFit(item);

            boolean singleDigit = false;
            boolean doubleDigit = false;
            for (double width = Math.ceil(item.minWidth(-1)); width <= naturalWidth; width++) {
                layout(parent, width);
                @Unmodifiable List<Label> labels = labels(item);
                int expanded = (int) labels.stream().filter(Node::isVisible).count();
                int hidden = labels.size() - expanded;
                assertEquals(hidden > 0, summary.isVisible());
                assertEquals(naturalWidth, item.prefWidth(-1));
                for (int i = 0; i < labels.size(); i++) {
                    Label tag = labels.get(i);
                    assertEquals(i < expanded, tag.isVisible());
                    if (tag.isVisible()) {
                        assertEquals(Math.ceil(tag.prefWidth(-1)), tag.getWidth(), 0.001);
                        assertTrue(tag.getBoundsInParent().getMaxX() <= item.getWidth());
                    }
                }
                if (hidden > 0) {
                    assertEquals("+" + hidden, summary.getText());
                    assertEquals(labels.subList(expanded, labels.size()).stream()
                            .map(Label::getText).collect(Collectors.joining("\n")), prepareTooltipText(summary));
                    assertTrue(summary.getWidth() >= summary.prefWidth(-1), "Summary text is clipped");
                    assertTrue(summary.getBoundsInParent().getMaxX() <= item.getWidth(), "Summary exceeds row bounds");
                    assertTrue(summary.getLayoutX() >= item.getTitleLabel().getBoundsInParent().getMaxX() + 4);
                    if (expanded > 0)
                        assertEquals(Math.ceil(titleWidth), item.getTitleLabel().getWidth(), 0.001);
                    singleDigit |= hidden == 9;
                    doubleDigit |= hidden == 10;
                }
            }
            assertTrue(singleDigit && doubleDigit);
            layout(parent, item.minWidth(-1));
            assertEquals("+12", summary.getText());
            assertEquals(0, item.getTitleLabel().getWidth());
            assertEquals(summary.getWidth() + 4, item.getWidth(), 0.001);

            item.getTags().setAll(Tag.warning("Changed"), new Tag("Changed", Tag.Type.NORMAL));
            layout(parent, item.minWidth(-1));
            assertEquals("+2", summary.getText());
            assertEquals("Changed\nChanged", prepareTooltipText(summary));
            item.getTags().clear();
            layout(parent, 500);
            assertFalse(summary.isVisible());
            assertFalse(summary.getTooltip().isShowing());
            assertTrue(item.isMouseTransparent());
            item.addTag("Restored");
            layout(parent, 500);
            assertTagsFit(item);
            assertFalse(summary.isVisible());
        });
    }

    /// Verifies construction, empty-list access, CSS, and sizing do not allocate tag rendering state.
    @Test
    public void testTagStateIsLazy() throws ReflectiveOperationException {
        var stateField = TwoLineListItem.class.getDeclaredField("tagState");
        stateField.setAccessible(true);
        var valuesField = TwoLineListItem.class.getDeclaredField("tags");
        valuesField.setAccessible(true);
        onFxThread(() -> {
            try {
                TwoLineListItem item = new TwoLineListItem("Title", "Subtitle");
                assertNull(valuesField.get(item));
                assertNull(stateField.get(item));
                VBox parent = new VBox(item);
                new Scene(parent);
                layout(parent, 200);
                item.minWidth(-1);
                item.minHeight(200);
                assertNull(valuesField.get(item));
                assertNull(stateField.get(item));

                var values = item.getTags();
                values.clear();
                layout(parent, 400);
                assertSame(values, item.getTags());
                assertNull(stateField.get(item));
                assertEquals(2, item.getChildrenUnmodifiable().size());

                values.add(new Tag("First tag", Tag.Type.NORMAL));
                assertNotNull(stateField.get(item));
                layout(parent, 400);
                assertTagsFit(item);
                values.clear();
                layout(parent, 400);
                assertTrue(labels(item).isEmpty());
                values.add(Tag.warning("Reused item"));
                layout(parent, 400);
                assertTagsFit(item);
            } catch (IllegalAccessException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /// Verifies labels are reused by position and only count changes add or remove renderers.
    @Test
    public void testTagLabelReuse() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", null);
            AtomicInteger added = new AtomicInteger();
            AtomicInteger removed = new AtomicInteger();
            item.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    for (Node node : change.getAddedSubList()) {
                        if (isExpandedTag(node))
                            added.incrementAndGet();
                    }
                    for (Node node : change.getRemoved()) {
                        if (isExpandedTag(node))
                            removed.incrementAndGet();
                    }
                }
            });
            for (int i = 0; i < 1000; i++)
                item.addTag("Tag " + i);
            assertEquals(1000, added.get());
            assertEquals(0, removed.get());
            @Unmodifiable List<Label> initial = labels(item);
            item.getTags().add(1, Tag.warning("Inserted"));
            assertSame(initial.get(0), labels(item).get(0));
            assertSame(initial.get(1), labels(item).get(1));
            assertEquals("Inserted", initial.get(1).getText());
            assertTrue(initial.get(1).getPseudoClassStates().contains(PseudoClass.getPseudoClass("warning")));
            assertEquals(1001, added.get());
            assertEquals(0, removed.get());
            item.getTags().set(2, new Tag("Replaced", Tag.Type.NORMAL));
            assertEquals(1001, added.get());
            assertEquals(0, removed.get());
            assertSame(initial.get(2), labels(item).get(2));
            assertEquals("Replaced", initial.get(2).getText());
            item.getTags().remove(1, 3);
            assertEquals(2, removed.get());
            assertSame(initial.get(1), labels(item).get(1));
            assertEquals("Tag 2", initial.get(1).getText());
            assertFalse(initial.get(1).getPseudoClassStates().contains(PseudoClass.getPseudoClass("warning")));

            @Unmodifiable List<Label> beforeSort = labels(item);
            FXCollections.sort(item.getTags(), Comparator.comparing(Tag::text).reversed());
            assertEquals(beforeSort, labels(item));
            for (int i = 0; i < beforeSort.size(); i++)
                assertEquals(item.getTags().get(i).text(), beforeSort.get(i).getText());
            item.getTags().setAll(IntStream.range(0, beforeSort.size()).mapToObj(i -> Tag.warning("Replacement " + i)).toList());
            assertEquals(beforeSort, labels(item));
            assertEquals(1001, added.get());
            assertEquals(2, removed.get());
            for (int i = 0; i < beforeSort.size(); i++) {
                assertEquals("Replacement " + i, beforeSort.get(i).getText());
                assertTrue(beforeSort.get(i).getPseudoClassStates().contains(PseudoClass.getPseudoClass("warning")));
            }
            item.getTags().clear();
            assertTrue(labels(item).isEmpty());
            assertTrue(beforeSort.stream().allMatch(label -> label.getParent() == null));
        });
    }

    /// Verifies folded tags reuse dimensions during resizing and prepare tooltip text only on demand.
    @Test
    public void testManyFoldedTagsReuseMeasurements() throws ReflectiveOperationException {
        var stateField = TwoLineListItem.class.getDeclaredField("tagState");
        stateField.setAccessible(true);
        var metricsField = stateField.getType().getDeclaredField("tagMetrics");
        metricsField.setAccessible(true);
        var measureField = stateField.getType().getDeclaredField("tagMeasureText");
        measureField.setAccessible(true);
        onFxThread(() -> {
            try {
                TwoLineListItem item = new TwoLineListItem("A long title ".repeat(100), null);
                for (int i = 0; i < 1000; i++)
                    item.addTag("Tag " + i);
                Object state = requireNonNull(stateField.get(item));
                Text measuringText = (Text) measureField.get(state);
                AtomicInteger measurements = new AtomicInteger();
                measuringText.textProperty().addListener(observable -> measurements.incrementAndGet());
                VBox parent = new VBox(item);
                new Scene(parent);
                layout(parent, 200);
                // Let newly installed label skins finish their initial layout before checking reuse.
                layout(parent, 200);
                Label summary = collapsedTag(item);
                assertEquals("+1000", summary.getText());
                assertNull(summary.getTooltip());
                Object metrics = requireNonNull(metricsField.get(state));
                int originalMeasurements = measurements.get();
                assertTrue(originalMeasurements < 10, "Measured summaries for the hidden suffix");
                for (int width = 200; width < 400; width++) {
                    layout(parent, width);
                    assertSame(metrics, metricsField.get(state), "Resizing remeasured hidden tags");
                    assertEquals(originalMeasurements, measurements.get());
                    assertNull(summary.getTooltip());
                }

                String description = prepareTooltipText(summary);
                assertEquals(1000, description.lines().count());
                assertTrue(description.startsWith("Tag 0\n"));
                assertTrue(description.endsWith("Tag 999"));
                layout(parent, 250);
                assertSame(description, prepareTooltipText(summary));

                double originalWidth = item.prefWidth(-1);
                labels(item).get(500).setStyle("-fx-font-size: 30px; -fx-padding: 3px 15px;");
                layout(parent, 250);
                assertTrue(item.prefWidth(-1) > originalWidth);
                assertNotSame(metrics, metricsField.get(state));
                double summaryWidth = summary.getWidth();
                summary.setFont(Font.font(30));
                layout(parent, 250);
                assertTrue(summary.getWidth() > summaryWidth);
                assertTrue(summary.getBoundsInParent().getMaxX() <= item.getWidth());

                item.getTags().set(999, Tag.warning("Changed"));
                layout(parent, 250);
                assertTrue(summary.getTooltip().getText().isEmpty());
                assertTrue(prepareTooltipText(summary).endsWith("Changed"));
                item.getTags().clear();
                layout(parent, 250);
                assertFalse(summary.isVisible());
                assertTrue(summary.getTooltip().getText().isEmpty());
            } catch (IllegalAccessException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /// Verifies hidden tags do not create text clips and resume skin layout after text and CSS changes.
    @Test
    public void testHiddenTagSkinLayout() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("A long title ".repeat(20), null);
            item.addTags(IntStream.range(0, 100).mapToObj(i -> "Tag " + i).toList());
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 200);
            layout(parent, 200);
            Label summary = collapsedTag(item);
            assertNull(summary.getTooltip());
            for (Label label : labels(item)) {
                assertFalse(label.isVisible());
                assertEquals(0, label.getWidth());
                assertNotNull(label.getSkin());
                assertTrue(label.getChildrenUnmodifiable().stream().allMatch(child -> child.getClip() == null));
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
            assertNull(summary.getTooltip());

            // Re-expansion must also lay out an unchanged-size label whose text changed while hidden.
            layout(parent, 200);
            first.setText("Revised tag");
            layout(parent, 200);
            layout(parent, item.prefWidth(-1));
            assertEquals("Revised tag", renderedText.getText());
            assertTagsFit(item);
        });
    }

    /// Verifies horizontal bias follows wrapping and tag changes, including optional children.
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

            Label side = new Label("Side text ".repeat(20));
            side.setWrapText(true);
            item.setLeading(side);
            assertEquals(Orientation.HORIZONTAL, item.getContentBias());
            side.setManaged(false);
            assertNull(item.getContentBias());
            item.setLeading(null);
            side.setManaged(true);
            item.setTrailing(side);
            assertEquals(Orientation.HORIZONTAL, item.getContentBias());
            item.setTrailing(null);
            item.setTitleTrailing(side);
            assertEquals(Orientation.HORIZONTAL, item.getContentBias());
            item.setTitleTrailing(null);
            item.addTag("Tag");
            assertEquals(Orientation.HORIZONTAL, item.getContentBias());
            item.getTags().clear();
            assertNull(item.getContentBias());

            LinePane line = new LinePane();
            line.setTitle("Title");
            parent.getChildren().setAll(line);
            layout(parent, 300);
            assertNull(line.getContentBias());
            line.setSubtitle("Wrapped subtitle ".repeat(20));
            layout(parent, 300);
            assertEquals(Orientation.HORIZONTAL, line.getContentBias());
            assertTrue(line.getHeight() > 48);
            line.setSubtitle(null);
            layout(parent, 300);
            assertNull(line.getContentBias());
            assertEquals(48, line.getHeight());
            line.getTags().add(new Tag("Tag"));
            layout(parent, 300);
            assertEquals(Orientation.HORIZONTAL, line.getContentBias());
            line.getTags().clear();
            layout(parent, 300);
            assertNull(line.getContentBias());
        });
    }

    /// Verifies nonwrapping side and title-trailing labels reuse their cached preferred height on resize.
    @Test
    public void testNonwrappingHeightMeasurementsAreCached() {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", "Subtitle");
            AtomicInteger measurements = new AtomicInteger();
            Label side = new Label("Side") {
                /// Counts preferred-height computations made by the row.
                @Override
                protected double computePrefHeight(double width) {
                    measurements.incrementAndGet();
                    return super.computePrefHeight(width);
                }
            };
            item.setLeading(side);
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 400);
            layout(parent, 400);
            int initial = measurements.get();
            for (int width = 400; width < 600; width++)
                layout(parent, width);
            assertEquals(initial, measurements.get());
            item.setLeading(null);
            item.setTitleTrailing(side);
            layout(parent, 400);
            initial = measurements.get();
            for (int width = 400; width < 600; width++)
                layout(parent, width);
            assertEquals(initial, measurements.get());
        });
    }

    /// Returns whether a child renders a tag value rather than the collapsed summary.
    private static boolean isExpandedTag(Node node) {
        return node.getStyleClass().contains("tag") && !node.getStyleClass().contains("collapsed-tag");
    }

    /// Verifies summary hover opens a tooltip and clicks activate only the nearest row once.
    @Test
    public void testCollapsedTagInteraction() throws Exception {
        initializeAnimations();
        CompletableFuture<Void> checked = new CompletableFuture<>();
        onFxThread(() -> {
            // Closing this test window must not shut down the toolkit used by later tests.
            Platform.setImplicitExit(false);
            LineButton line = new LineButton();
            line.setTitle("A very long title ".repeat(10));
            line.getTags().addAll(new Tag("Fabric", Tag.Type.NORMAL), Tag.warning("Warning"));
            RipplerContainer outer = new RipplerContainer(line);
            VBox parent = new VBox(outer);
            Stage stage = new Stage();
            stage.setScene(new Scene(parent, 250, 100));
            stage.show();
            layout(parent, 250);
            TwoLineListItem content = (TwoLineListItem) requireNonNull(line.lookup(".line-component-container"));
            Label summary = collapsedTag(content);
            AtomicInteger actions = new AtomicInteger();
            AtomicInteger outerActions = new AtomicInteger();
            AtomicInteger clicks = new AtomicInteger();
            line.setOnAction(event -> actions.incrementAndGet());
            outer.setOnAction(event -> outerActions.incrementAndGet());
            parent.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> clicks.incrementAndGet());
            summary.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY,
                    1, false, false, false, false, false, false, false, false, false, true,
                    new PickResult(summary, 1, 1)));
            verifyTooltipHover(stage, summary, () -> {
                assertEquals(1, actions.get());
                assertEquals(0, outerActions.get());
                assertEquals(1, clicks.get());
                assertEquals("Fabric\nWarning", summary.getTooltip().getText());
                layout(parent, 3000);
                assertFalse(summary.isVisible());
                assertFalse(summary.getTooltip().isShowing());
            }, checked);
        });
        checked.get(30, TimeUnit.SECONDS);
    }

    /// Verifies mouse picking and tooltip activation through the download cell's actual wrappers.
    @Test
    public void testDownloadCellTooltip() throws Exception {
        initializeAnimations();
        CompletableFuture<Void> checked = new CompletableFuture<>();
        onFxThread(() -> {
            Platform.setImplicitExit(false);
            Stage stage = new Stage();
            try {
                var type = Class.forName("org.jackhuang.hmcl.ui.instances.DownloadListPage$DownloadListCell");
                var constructor = type.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                JFXListView<@Nullable Object> list = new JFXListView<>();
                list.getProperties().put("no-smooth-scrolling", true);
                list.setCellFactory(view -> {
                    try {
                        @SuppressWarnings("unchecked")
                        ListCell<@Nullable Object> cell = (ListCell<@Nullable Object>) constructor.newInstance(null, view, null);
                        return cell;
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                });
                list.getItems().add(null);
                VBox parent = new VBox(list);
                parent.setStyle("-monet-on-surface: #111111; -monet-on-surface-variant: #222222; "
                        + "-monet-on-secondary-container: #333333; -monet-secondary-container: #eeeeee; "
                        + "-monet-surface-container-low-transparent-80: #eeeeee; "
                        + "-monet-inverse-on-surface: #ffffff; -monet-inverse-surface: #222222; -monet-surface-tint: #333333;");
                Scene scene = new Scene(parent, 300, 200);
                scene.getStylesheets().add(requireNonNull(getClass().getResource("/assets/css/root.css")).toExternalForm());
                stage.setScene(scene);
                stage.show();
                layout(parent, 300);
                ListCell<?> cell = list.lookupAll(".list-cell").stream().filter(ListCell.class::isInstance)
                        .map(ListCell.class::cast).filter(candidate -> candidate.getIndex() == 0).findFirst().orElseThrow();
                // Supply visual data directly, avoiding repository access, image loading, and navigation.
                var contentField = type.getDeclaredField("content");
                contentField.setAccessible(true);
                TwoLineListItem content = (TwoLineListItem) contentField.get(cell);
                content.setTitle("A long addon title ".repeat(10));
                content.addTags(List.of("Fabric", "Quilt"));
                var wrapperField = type.getDeclaredField("wrapper");
                wrapperField.setAccessible(true);
                cell.setGraphic((Node) wrapperField.get(cell));
                layout(parent, 300);
                assertInstanceOf(ImageContainer.class, content.getLeading());
                assertSame(content, content.getLeading().getParent());
                assertInstanceOf(RipplerContainer.class, content.getParent());
                assertEquals(new Insets(0, 8, 0, 0), TwoLineListItem.getMargin(content.getLeading()));
                Label summary = collapsedTag(content);
                verifyTooltipHover(stage, summary, () ->
                        assertEquals("Fabric\nQuilt", summary.getTooltip().getText()), checked);
            } catch (Throwable failure) {
                stage.close();
                checked.completeExceptionally(failure);
            }
        });
        checked.get(30, TimeUnit.SECONDS);
    }

    /// Verifies page skin initialization measures a complete row without reparenting its content.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testRemoteVersionPageInitialization(boolean game) throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            DownloadProvider provider = new DownloadProvider() {
                /// Supplies an empty version list without accessing the network.
                @Override
                public @Unmodifiable Task<ComponentRemoteVersionList<?>> getVersionsAsync(
                        GameComponentType type, @Nullable GameVersionNumber version, boolean refresh) {
                    return Task.completed(ComponentRemoteVersionList.of(type));
                }
            };
            VersionsPage page = new VersionsPage(null, "Versions", GameVersionNumber.asGameVersion("1.20.1"),
                    provider, game ? GameComponentType.GAME : GameComponentType.FORGE, () -> {});
            try {
                var type = Class.forName("org.jackhuang.hmcl.ui.download.VersionsPage$VersionsPageSkin");
                var constructor = type.getDeclaredConstructor(VersionsPage.class);
                constructor.setAccessible(true);
                // CSS initialization needs font settings without loading the user's configuration.
                var settingsField = SettingsManager.class.getDeclaredField("launcherSettings");
                settingsField.setAccessible(true);
                @Nullable Object previous = settingsField.get(null);
                LauncherSettings settings = new LauncherSettings();
                settings.launcherFontFamilyProperty().set("System");
                try {
                    settingsField.set(null, settings);
                    var skin = constructor.newInstance(page);
                    var listField = type.getDeclaredField("list");
                    listField.setAccessible(true);
                    ListView<?> list = (ListView<?>) listField.get(skin);
                    assertTrue(Double.isFinite(list.getFixedCellSize()));
                    assertTrue(list.getFixedCellSize() > 20);
                } finally {
                    settingsField.set(null, previous);
                }
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /// Verifies dependency rows share one text layout with their icon and fit long text within a fixed height.
    @Test
    public void testDependencyAddonRowLayout() throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            RemoteAddon addon = new RemoteAddon("", "", "A long dependency title ".repeat(12),
                    "A long dependency description ".repeat(12), List.of(), "", "", RemoteAddon.BROKEN.data(), null);
            try {
                var type = Class.forName("org.jackhuang.hmcl.ui.instances.DownloadPage$DependencyAddonItem");
                var constructor = type.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                LineButton line = (LineButton) constructor.newInstance(null, addon, null);
                VBox parent = new VBox(line);
                new Scene(parent);
                layout(parent, 300);
                TwoLineListItem content = line.container;
                assertEquals(1, line.lookupAll("*").stream().filter(TwoLineListItem.class::isInstance).count());
                assertInstanceOf(ImageContainer.class, line.getLeading());
                assertSame(content, line.getLeading().getParent());
                assertEquals(addon.title(), line.getTitle());
                assertEquals(addon.description(), line.getSubtitle());
                assertEquals(60, line.getHeight());
                assertEquals(24, line.getLeading().getLayoutX(), 0.001);
                assertEquals(8, content.getTitleLabel().getLayoutX() - line.getLeading().getBoundsInParent().getMaxX(), 0.001);
                assertFalse(content.getSubtitleLabel().isWrapText());
                assertTrue(requireNonNull(FXUtils.textTruncatedProperty(content.getTitleLabel())).get());
                assertTrue(requireNonNull(FXUtils.textTruncatedProperty(content.getSubtitleLabel())).get());
                assertTrue(content.getSubtitleLabel().getBoundsInParent().getMaxY() <= 50);
                line.getTags().addAll(new Tag("Fabric", Tag.Type.NORMAL), new Tag("Quilt", Tag.Type.NORMAL));
                layout(parent, 300);
                Label summary = collapsedTag(content);
                assertEquals("+2", summary.getText());
                assertTrue(summary.getBoundsInParent().getMaxX() <= content.getWidth() - content.getInsets().getRight());
                assertEquals("Fabric\nQuilt", prepareTooltipText(summary));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /// Verifies version rows use their list width and fold or expand tags when that width changes.
    @Test
    public void testRemoteVersionCellWidth() throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            DownloadProvider provider = new DownloadProvider() {
                /// Supplies an empty version list without accessing the network.
                @Override
                public @Unmodifiable Task<ComponentRemoteVersionList<?>> getVersionsAsync(
                        GameComponentType type, @Nullable GameVersionNumber version, boolean refresh) {
                    return Task.completed(ComponentRemoteVersionList.of(type));
                }
            };
            VersionsPage page = new VersionsPage(null, "Versions", GameVersionNumber.asGameVersion("1.20.1"),
                    provider, GameComponentType.FORGE, () -> {});
            ListView<ComponentRemoteVersion> list = new ListView<>();
            list.setFixedCellSize(70);
            try {
                var type = Class.forName("org.jackhuang.hmcl.ui.download.VersionsPage$RemoteVersionListCell");
                var constructor = type.getDeclaredConstructor(VersionsPage.class, ListView.class);
                constructor.setAccessible(true);
                list.setCellFactory(view -> {
                    try {
                        @SuppressWarnings("unchecked")
                        ListCell<ComponentRemoteVersion> cell = (ListCell<ComponentRemoteVersion>) constructor.newInstance(page, view);
                        return cell;
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                });
                list.getItems().add(new ForgeRemoteVersion(GameVersionNumber.asGameVersion("1.20.1"),
                        "47.4.3-experimental-".repeat(5), Instant.EPOCH, List.of()));
                VBox parent = new VBox(list);
                new Scene(parent);
                layout(parent, 200);
                ListCell<?> cell = list.lookupAll(".list-cell").stream().filter(ListCell.class::isInstance)
                        .map(ListCell.class::cast).filter(candidate -> candidate.getIndex() == 0).findFirst().orElseThrow();
                var contentField = type.getDeclaredField("twoLineListItem");
                contentField.setAccessible(true);
                TwoLineListItem content = (TwoLineListItem) contentField.get(cell);
                assertTrue(cell.getWidth() <= 200);
                assertInstanceOf(RipplerContainer.class, cell.getGraphic());
                assertSame(content, ((RipplerContainer) cell.getGraphic()).getContainer());
                assertSame(content, content.getLeading().getParent());
                assertSame(content, content.getTrailing().getParent());
                assertEquals(new Insets(0, 16, 0, 0), TwoLineListItem.getMargin(content.getLeading()));
                assertEquals(new Insets(0, 0, 0, 16), TwoLineListItem.getMargin(content.getTrailing()));
                Label summary = collapsedTag(content);
                assertTrue(summary.isVisible());
                assertTrue(summary.localToScene(summary.getBoundsInLocal()).getMaxX() <= 200);

                layout(parent, 1600);
                assertTrue(cell.getWidth() > 200);
                assertFalse(summary.isVisible());
                assertTagsFit(content);

                @Unmodifiable List<Label> originalLabels = labels(content);
                var update = type.getDeclaredMethod("updateItem", ComponentRemoteVersion.class, boolean.class);
                update.setAccessible(true);
                update.invoke(cell, new ForgeRemoteVersion(GameVersionNumber.asGameVersion("1.21.1"),
                        "52.0.1", Instant.EPOCH, List.of()), false);
                content.layout();
                assertEquals(originalLabels, labels(content));
                assertEquals("1.21.1", labels(content).get(0).getText());
                assertTagsFit(content);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /// Verifies truncation tooltips are lazy, reusable, and preserve row clicks for either text label.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testTruncatedTextTooltip(boolean subtitle) throws Exception {
        initializeAnimations();
        CompletableFuture<Void> checked = new CompletableFuture<>();
        onFxThread(() -> {
            Platform.setImplicitExit(false);
            TwoLineListItem item = new TwoLineListItem("Title", "Subtitle");
            Label label = subtitle ? item.getSubtitleLabel() : item.getTitleLabel();
            RipplerContainer inner = new RipplerContainer(item);
            RipplerContainer outer = new RipplerContainer(inner);
            VBox parent = new VBox(outer);
            Stage stage = new Stage();
            stage.setScene(new Scene(parent, 250, 100));
            stage.show();
            try {
                layout(parent, 250);
                assertTrue(label.isMouseTransparent());
                assertTrue(item.isMouseTransparent());
                assertTrue(label.getProperties().values().stream().noneMatch(Tooltip.class::isInstance));

                label.setText("A long description that cannot fit on one line. ".repeat(3));
                layout(parent, 250);
                assertTrue(requireNonNull(FXUtils.textTruncatedProperty(label)).get());
                Tooltip tooltip = label.getProperties().values().stream().filter(Tooltip.class::isInstance)
                        .map(Tooltip.class::cast).findFirst().orElseThrow();
                assertEquals(label.getText(), tooltip.getText());
                assertFalse(label.isMouseTransparent());
                assertFalse(item.isMouseTransparent());

                layout(parent, 2000);
                assertFalse(requireNonNull(FXUtils.textTruncatedProperty(label)).get());
                assertTrue(label.isMouseTransparent());
                assertTrue(item.isMouseTransparent());
                label.setText(label.getText() + "Updated");
                layout(parent, 250);
                assertEquals(label.getText(), tooltip.getText());
                assertTrue(label.getProperties().containsValue(tooltip));

                AtomicInteger innerActions = new AtomicInteger();
                AtomicInteger outerActions = new AtomicInteger();
                inner.setOnAction(event -> innerActions.incrementAndGet());
                outer.setOnAction(event -> outerActions.incrementAndGet());
                label.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY,
                        1, false, false, false, false, false, false, false, false, false, true,
                        new PickResult(label, 1, 1)));
                verifyTooltipHover(stage, label, tooltip, () -> {
                    assertEquals(1, innerActions.get());
                    assertEquals(0, outerActions.get());
                    label.setWrapText(true);
                    layout(parent, 250);
                    assertFalse(requireNonNull(FXUtils.textTruncatedProperty(label)).get());
                    assertTrue(label.isMouseTransparent());
                    assertTrue(item.isMouseTransparent());
                    label.setWrapText(false);
                    layout(parent, 250);
                    assertFalse(item.isMouseTransparent());
                    if (subtitle)
                        item.setSubtitle(null);
                    else
                        label.setText("Title");
                    layout(parent, 250);
                    assertTrue(item.isMouseTransparent());
                }, checked);
            } catch (Throwable failure) {
                stage.close();
                checked.completeExceptionally(failure);
            }
        });
        checked.get(30, TimeUnit.SECONDS);
    }

    /// Checks the collapsed tag's tooltip using a real pointer.
    private static void verifyTooltipHover(Stage stage, Label summary, Runnable assertions, CompletableFuture<Void> checked) {
        verifyTooltipHover(stage, summary, summary.getTooltip(), assertions, checked);
    }

    /// Moves a real pointer over a label and checks its tooltip without overriding the production delay.
    private static void verifyTooltipHover(Stage stage, Label label, @Nullable Tooltip tooltip,
                                           Runnable assertions, CompletableFuture<Void> checked) {
        PauseTransition settle = new PauseTransition(Duration.millis(200));
        settle.setOnFinished(event -> {
            var position = label.localToScreen(label.getWidth() / 2, label.getHeight() / 2);
            new Robot().mouseMove(position);
            // Entering the window may produce only an enter event; move again inside the tag.
            PauseTransition move = new PauseTransition(Duration.millis(100));
            move.setOnFinished(ignored -> {
                new Robot().mouseMove(position.add(1, 0));
                PauseTransition check = new PauseTransition(Duration.millis(200));
                check.setOnFinished(finished -> {
                    try {
                        assertTrue(label.isHover(), "Label does not receive hover");
                        for (@Nullable Node node = label; node != null; node = node.getParent())
                            assertFalse(node.isMouseTransparent());
                        assertTrue(requireNonNull(tooltip != null ? tooltip : label.getTooltip()).isShowing(), "Tooltip is not showing");
                        assertions.run();
                        checked.complete(null);
                    } catch (Throwable failure) {
                        checked.completeExceptionally(failure);
                    } finally {
                        @Nullable Tooltip current = tooltip != null ? tooltip : label.getTooltip();
                        if (current != null)
                            current.hide();
                        stage.close();
                    }
                });
                check.play();
            });
            move.play();
        });
        settle.play();
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
            assertSame(item, item.leadingProperty().getBean());
            assertSame(item, item.trailingProperty().getBean());
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
    @ParameterizedTest
    @ValueSource(doubles = {0, 8, 12, 16})
    public void testSideMarginsControlSpacing(double spacing) {
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
            assertEquals(leading.getBoundsInParent().getMaxX(), item.getTitleLabel().getLayoutX(), 0.001);
            assertEquals(trailing.getLayoutX(), item.getSubtitleLabel().getBoundsInParent().getMaxX(), 0.001);
            TwoLineListItem.setMargin(leading, new Insets(0, 3 + spacing, 0, 5));
            TwoLineListItem.setMargin(trailing, new Insets(0, 7, 0, 9 + spacing));
            layout(parent, 400);
            assertEquals(width + 24 + 2 * spacing, item.prefWidth(-1), 0.001);
            assertEquals(leading.getBoundsInParent().getMaxX() + 3 + spacing,
                    item.getTitleLabel().getLayoutX(), 0.001);
            assertEquals(trailing.getLayoutX() - 9 - spacing,
                    item.getSubtitleLabel().getBoundsInParent().getMaxX(), 0.001);
            TwoLineListItem.setMargin(leading, null);
            TwoLineListItem.setMargin(trailing, null);
            layout(parent, 400);
            assertEquals(width, item.prefWidth(-1), 0.001);
        });
    }

    /// Verifies line defaults are applied once and preserve explicit margins, including zero.
    @Test
    public void testLineComponentDefaultMargins() {
        onFxThread(() -> {
            LinePane line = new LinePane();
            line.setTitle("Title");
            Label leading = new Label("Icon");
            Button trailing = new Button("Action");
            line.setLeading(leading);
            line.setRight(trailing);
            Insets leadingMargin = new Insets(0, 12, 0, 0);
            Insets trailingMargin = new Insets(0, 0, 0, 12);
            assertEquals(leadingMargin, TwoLineListItem.getMargin(leading));
            assertEquals(trailingMargin, TwoLineListItem.getMargin(trailing));
            line.setLeading((Node) null);
            line.setRight(null);
            line.setLeading(leading);
            line.setRight(trailing);
            assertEquals(leadingMargin, TwoLineListItem.getMargin(leading));
            assertEquals(trailingMargin, TwoLineListItem.getMargin(trailing));

            VBox parent = new VBox(line);
            new Scene(parent);
            layout(parent, 400);
            double width = line.container.prefWidth(-1);
            line.setLeading((Node) null);
            line.setRight(null);
            LineComponent.setMargin(leading, new Insets(0, 8, 0, 0));
            LineComponent.setMargin(trailing, Insets.EMPTY);
            line.setLeading(leading);
            line.setRight(trailing);
            layout(parent, 400);
            assertEquals(new Insets(0, 8, 0, 0), TwoLineListItem.getMargin(leading));
            assertEquals(Insets.EMPTY, TwoLineListItem.getMargin(trailing));
            assertEquals(width - 16, line.container.prefWidth(-1), 0.001);
            assertEquals(leading.getBoundsInParent().getMaxX() + 8,
                    line.container.getTitleLabel().getLayoutX(), 0.001);
        });
    }

    /// Verifies migrated game, Java, and theme-pack cells attach their icon and actions directly to the row.
    @ParameterizedTest
    @ValueSource(strings = {
            "org.jackhuang.hmcl.ui.instances.GameListCell",
            "org.jackhuang.hmcl.ui.main.JavaManagementPage$JavaItemCell",
            "org.jackhuang.hmcl.ui.main.ThemePackManagementPage$ThemePackItemCell"
    })
    public void testMigratedCellStructure(String className) throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            try {
                var type = Class.forName(className);
                var constructor = type.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                JFXListView<Object> list = new JFXListView<>();
                list.resize(400, 100);
                ListCell<?> cell = (ListCell<?>) (constructor.getParameterCount() == 0
                        ? constructor.newInstance()
                        : constructor.newInstance(className.contains("JavaItemCell") ? list : null));
                var contentField = type.getDeclaredField("content");
                contentField.setAccessible(true);
                TwoLineListItem content = (TwoLineListItem) contentField.get(cell);
                var graphicField = type.getDeclaredField("graphic");
                graphicField.setAccessible(true);
                RipplerContainer graphic = (RipplerContainer) graphicField.get(cell);
                assertSame(content, graphic.getContainer());
                assertSame(content, content.getLeading().getParent());
                assertSame(content, content.getTrailing().getParent());
                cell.setGraphic(graphic);
                content.setTitle("Title");
                content.setSubtitle("Subtitle");
                VBox parent = new VBox(cell);
                parent.setStyle("-monet-secondary-container: #eeeeee;");
                new Scene(parent);
                layout(parent, 400);
                assertEquals(content.getLeading().getBoundsInParent().getMaxX() + 8,
                        content.getTitleLabel().getLayoutX(), 0.001);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /// Verifies advanced items retain graphic bindings, margins, actions, and graphic release after flattening.
    @Test
    public void testAdvancedItemDirectLayout() throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            AdvancedListItem control = new AdvancedListItem();
            control.setTitle("Title");
            control.setSubtitle("Subtitle");
            Region leading = new Region();
            leading.setMinSize(32, 32);
            leading.setPrefSize(32, 32);
            leading.setMaxSize(32, 32);
            leading.setMouseTransparent(true);
            AdvancedListItem.setMargin(leading, new Insets(0, 6, 0, 6));
            control.setLeftGraphic(leading);
            Button action = new Button("Action");
            control.setRightGraphic(action);
            var skin = new AdvancedListItemSkin(control);
            control.setSkin(skin);
            VBox parent = new VBox(control);
            new Scene(parent);
            layout(parent, 400);
            TwoLineListItem content = (TwoLineListItem) requireNonNull(control.lookup(".two-line-list-item"));
            assertInstanceOf(RipplerContainer.class, content.getParent());
            assertSame(content, leading.getParent());
            assertSame(content, action.getParent());
            assertEquals(6, leading.getLayoutX() - content.getInsets().getLeft(), 0.001);
            assertEquals(leading.getBoundsInParent().getMaxX() + 6, content.getTitleLabel().getLayoutX(), 0.001);
            AtomicInteger rowActions = new AtomicInteger();
            AtomicInteger buttonActions = new AtomicInteger();
            control.setOnAction(event -> rowActions.incrementAndGet());
            action.setOnAction(event -> buttonActions.incrementAndGet());
            action.fire();
            assertEquals(1, buttonActions.get());
            assertEquals(0, rowActions.get());
            ((RipplerContainer) content.getParent()).fire();
            assertEquals(1, rowActions.get());
            control.setTitle("Updated");
            assertEquals("Updated", content.getTitle());
            Button replacement = new Button("Replacement");
            control.setRightGraphic(replacement);
            assertNull(action.getParent());
            assertSame(content, replacement.getParent());
            skin.dispose();
            assertNull(leading.getParent());
            assertNull(replacement.getParent());
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
            double textLeft = leading.getBoundsInParent().getMaxX() + 17;
            assertEquals(textLeft, item.getTitleLabel().getLayoutX(), 0.001);
            assertEquals(textLeft, item.getSubtitleLabel().getLayoutX(), 0.001);
            assertEquals(item.getWidth() - 14, trailing.getBoundsInParent().getMaxX(), 0.001);
            double textRight = trailing.getLayoutX() - 19;
            assertEquals(textRight, item.getSubtitleLabel().getBoundsInParent().getMaxX(), 0.001);
            Label tag = labels(item).get(0);
            assertFalse(tag.isVisible());
            Label summary = collapsedTag(item);
            assertEquals("+1", summary.getText());
            assertTrue(summary.getBoundsInParent().getMaxX() <= textRight);
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
            assertTrue(trailing.getLayoutX() >= item.getTitleLabel().getBoundsInParent().getMaxX());
            assertEquals(item.getWidth(), trailing.getBoundsInParent().getMaxX(), 0.001);
            Label summary = collapsedTag(item);
            assertTrue(summary.isVisible());
            assertTrue(summary.getBoundsInParent().getMaxX() <= trailing.getLayoutX() - 12);
        });
    }

    /// Verifies the private trailing group reuses its label and observes only the current icon.
    @Test
    public void testLineButtonTrailingGroup() throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            LineButton line = new LineButton();
            line.setTitle("Setting");
            assertNull(line.container.getTrailing());
            line.setTrailingText("Value");
            Button icon = new Button("Icon");
            line.setTrailingIcon(icon);
            VBox parent = new VBox(line);
            new Scene(parent);
            layout(parent, 400);
            TwoLineListItem content = line.container;
            HBox group = (HBox) requireNonNull(content.getTrailing());
            Label text = (Label) group.getChildren().get(0);
            assertEquals("Value", text.getText());
            assertEquals(List.of(text, icon), group.getChildren());

            AtomicInteger rowActions = new AtomicInteger();
            AtomicInteger iconActions = new AtomicInteger();
            line.setOnAction(event -> rowActions.incrementAndGet());
            icon.setOnAction(event -> iconActions.incrementAndGet());
            icon.fire();
            assertEquals(1, iconActions.get());
            assertEquals(0, rowActions.get());
            line.fire();
            assertEquals(1, rowActions.get());

            line.setTrailingText(null);
            assertEquals(List.of(icon), group.getChildren());
            icon.setVisible(false);
            icon.setManaged(false);
            assertNull(group.getParent());
            double collapsedWidth = content.prefWidth(-1);
            icon.setVisible(true);
            icon.setManaged(true);
            assertSame(content, group.getParent());
            assertTrue(content.prefWidth(-1) > collapsedWidth);

            Button replacement = new Button("Replacement");
            line.setTrailingIcon(replacement);
            assertNull(icon.getParent());
            icon.setVisible(false);
            icon.setManaged(false);
            assertTrue(group.isVisible());
            assertTrue(group.isManaged());
            line.setTrailingText("Restored");
            assertEquals(List.of(text, replacement), group.getChildren());
            assertEquals("Restored", text.getText());
            line.setTrailingText("");
            line.setTrailingIcon((Node) null);
            assertSame(group, content.getTrailing());
            assertNull(group.getParent());
            assertTrue(group.getChildren().isEmpty());
            replacement.setVisible(false);
            replacement.setManaged(false);
            assertFalse(group.isVisible());
            assertFalse(group.isManaged());
            line.setTrailingText("Value");
            assertSame(group, content.getTrailing());
            assertEquals(List.of(text), group.getChildren());
            assertSame(content, group.getParent());
        });
    }

    /// Verifies the update selector composes its own action without modifying the value/icon group.
    @Test
    public void testUpdateChannelButtonComposition() throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            try {
                var type = Class.forName("org.jackhuang.hmcl.ui.main.SettingsPage$UpdateChannelButton");
                var constructor = type.getDeclaredConstructor(Node.class);
                constructor.setAccessible(true);
                Button update = new Button("Update");
                LineButton line = (LineButton) constructor.newInstance(update);
                line.setTitle("Update channel");
                line.setTrailingText("Stable");
                VBox parent = new VBox(line);
                new Scene(parent);
                layout(parent, 500);
                HBox outer = (HBox) line.container.getTrailing();
                assertSame(update, outer.getChildren().get(0));
                HBox selection = (HBox) outer.getChildren().get(1);
                assertEquals(new Insets(0, 0, 0, 12), TwoLineListItem.getMargin(outer));
                assertNull(HBox.getMargin(selection));
                Label text = (Label) selection.getChildren().get(0);
                assertEquals("Stable", text.getText());
                assertSame(line.getTrailingIcon(), selection.getChildren().get(1));
                double width = outer.prefWidth(-1);
                update.setVisible(false);
                update.setManaged(false);
                assertTrue(outer.prefWidth(-1) < width);
                line.setTrailingText("Development");
                assertEquals("Development", text.getText());
                assertSame(outer, line.container.getTrailing());
                assertSame(outer, selection.getParent());
                line.setTrailingText(null);
                line.setTrailingText("Restored");
                assertEquals(List.of(text, line.getTrailingIcon()), selection.getChildren());
                AtomicInteger actions = new AtomicInteger();
                update.setOnAction(event -> actions.incrementAndGet());
                update.fire();
                assertEquals(1, actions.get());
                assertEquals(List.of(update, selection), outer.getChildren());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /// Verifies sublist headers own their action group and preserve the description/action/arrow order.
    @Test
    public void testSublistHeaderTrailingGroup() throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            ComponentSublist sublist = new ComponentSublist();
            sublist.setDescription("Description");
            Button extra = new Button("Extra");
            sublist.setTrailing(extra);
            ComponentSublistWrapper wrapper = new ComponentSublistWrapper(sublist);
            VBox parent = new VBox(wrapper);
            new Scene(parent);
            layout(parent, 500);
            LineButton header = (LineButton) wrapper.getChildren().get(0);
            HBox group = (HBox) header.container.getTrailing();
            assertEquals("Description", ((Label) group.getChildren().get(0)).getText());
            HBox actions = (HBox) header.getTrailingIcon();
            assertSame(actions, group.getChildren().get(1));
            assertSame(extra, actions.getChildren().get(0));
            Node arrow = actions.getChildren().get(1);
            assertTrue(arrow.getStyleClass().contains("expand-icon"));

            double width = actions.prefWidth(-1);
            extra.setVisible(false);
            extra.setManaged(false);
            assertTrue(actions.prefWidth(-1) < width);
            Button replacement = new Button("Replacement");
            sublist.setTrailing(replacement);
            assertNull(extra.getParent());
            assertEquals(List.of(replacement, arrow), actions.getChildren());
            sublist.setTrailing(null);
            assertEquals(List.of(arrow), actions.getChildren());
            sublist.setDescription("");
            assertEquals(List.of(actions), group.getChildren());
            sublist.setDescription("Restored");
            assertEquals("Restored", ((Label) group.getChildren().get(0)).getText());
            assertSame(actions, group.getChildren().get(1));
        });
    }

    /// Initializes the animation default with in-memory settings without loading or saving user configuration.
    private static void initializeAnimations() throws ReflectiveOperationException {
        var field = SettingsManager.class.getDeclaredField("launcherSettings");
        field.setAccessible(true);
        @Nullable Object previous = field.get(null);
        LauncherSettings settings = new LauncherSettings();
        settings.animationDisabledProperty().set(true);
        try {
            field.set(null, settings);
            AnimationUtils.init();
        } finally {
            field.set(null, previous);
        }
    }

    /// Verifies flattened selectors, theme colors, vertical centering, and subtitle removal.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testStylesAndAlignment(boolean dark) {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Title", "Subtitle");
            item.addTagWarning("Warning");
            item.getStyleClass().add("container");
            StackPane rippler = new StackPane(item);
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
            double top = item.getChildrenUnmodifiable().stream().filter(Node::isVisible).mapToDouble(node -> node.getLayoutY()).min().orElseThrow();
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
            assertNull(label.getClip());
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
