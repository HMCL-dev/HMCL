// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
            assertEquals(5, item.getChildren().size());
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
            assertEquals("- Fabric\n- NeoForge", summary.getTooltip().getText());
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
            for (Node node : item.getChildren()) {
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
                            .map(tag -> "- " + tag.getText()).collect(Collectors.joining("\n")), summary.getTooltip().getText());
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
            assertEquals("- Changed\n- Changed", summary.getTooltip().getText());
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
            summary.getTooltip().setShowDelay(Duration.ZERO);
            var position = summary.localToScreen(1, 1);
            summary.fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED, 1, 1, position.getX(), position.getY(),
                    MouseButton.NONE, 0, false, false, false, false, false, false, false, false, false, true,
                    new PickResult(summary, 1, 1)));
            PauseTransition delay = new PauseTransition(Duration.millis(200));
            delay.setOnFinished(event -> {
                try {
                    assertEquals(1, actions.get());
                    assertEquals(0, outerActions.get());
                    assertEquals(1, clicks.get());
                    for (@Nullable Node node = summary; node != null; node = node.getParent())
                        assertFalse(node.isMouseTransparent());
                    assertTrue(summary.getTooltip().isShowing());
                    assertEquals("- Fabric\n- Warning", summary.getTooltip().getText());
                    layout(parent, 3000);
                    assertFalse(summary.isVisible());
                    assertFalse(summary.getTooltip().isShowing());
                    checked.complete(null);
                } catch (Throwable failure) {
                    checked.completeExceptionally(failure);
                } finally {
                    stage.close();
                }
            });
            delay.play();
        });
        checked.get(30, TimeUnit.SECONDS);
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
            TwoLineListItem.setMargin(leading, new Insets(3, 5, 9, 2));
            TwoLineListItem.setMargin(trailing, new Insets(5, 3, 1, 7));
            VBox parent = new VBox(item);
            new Scene(parent);
            layout(parent, 320);

            assertSame(item, leading.getParent());
            assertSame(item, trailing.getParent());
            assertEquals(19, leading.getLayoutX(), 0.001);
            double textLeft = leading.getBoundsInParent().getMaxX() + 5 + TwoLineListItem.SIDE_SPACING;
            assertEquals(textLeft, item.getTitleLabel().getLayoutX(), 0.001);
            assertEquals(textLeft, item.getSubtitleLabel().getLayoutX(), 0.001);
            assertEquals(item.getWidth() - 14, trailing.getBoundsInParent().getMaxX(), 0.001);
            double textRight = trailing.getLayoutX() - 7 - TwoLineListItem.SIDE_SPACING;
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
            TwoLineListItem.setMargin(trailing, new Insets(5, 30, 1, 70));
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
            assertTrue(summary.getBoundsInParent().getMaxX() <= trailing.getLayoutX() - TwoLineListItem.SIDE_SPACING);
        });
    }

    /// Verifies subclass trailing slots keep their order, collapse when hidden, and preserve independent actions.
    @Test
    public void testGroupedLineButtonTrailingNodes() throws ReflectiveOperationException {
        initializeAnimations();
        onFxThread(() -> {
            ExtraLineButton line = new ExtraLineButton();
            line.setTitle("Setting");
            line.setTrailingText("Value");
            Label icon = new Label(">");
            line.setTrailingIcon(icon);
            Button extra = new Button("Extra");
            line.setExtraTrailing(extra);
            VBox parent = new VBox(line);
            new Scene(parent);
            layout(parent, 400);
            TwoLineListItem content = (TwoLineListItem) requireNonNull(line.lookup(".line-component-container"));
            HBox group = (HBox) requireNonNull(content.getTrailing());
            assertEquals("Value", ((Label) group.getChildren().get(0)).getText());
            assertSame(extra, group.getChildren().get(1));
            assertSame(icon, group.getChildren().get(2));

            AtomicInteger rowActions = new AtomicInteger();
            AtomicInteger extraActions = new AtomicInteger();
            line.setOnAction(event -> rowActions.incrementAndGet());
            extra.setOnAction(event -> extraActions.incrementAndGet());
            extra.fire();
            assertEquals(1, extraActions.get());
            assertEquals(0, rowActions.get());
            line.fire();
            assertEquals(1, rowActions.get());

            for (Node child : group.getChildren()) {
                child.setVisible(false);
                child.setManaged(false);
            }
            layout(parent, 400);
            assertNull(group.getParent());
            double collapsedWidth = content.prefWidth(-1);
            extra.setVisible(true);
            extra.setManaged(true);
            assertSame(content, group.getParent());
            assertFalse(content.isMouseTransparent());
            assertTrue(content.prefWidth(-1) > collapsedWidth);

            line.setTrailingText(null);
            line.setExtraTrailing(null);
            assertSame(icon, content.getTrailing());
            icon.setManaged(true);
            icon.setVisible(true);
            assertSame(content, icon.getParent());
            line.setTrailingIcon((Node) null);
            assertNull(content.getTrailing());
            assertNull(icon.getParent());
        });
    }

    /// Supplies an extra trailing slot between the value and icon, as expandable headers do.
    @NotNullByDefault
    private static final class ExtraLineButton extends LineButton {
        /// Places the icon after the extra trailing slot.
        @Override
        protected int getTrailingIconIndex() {
            return IDX_TRAILING + 2;
        }

        /// Replaces the optional extra control.
        private void setExtraTrailing(@Nullable Node node) {
            setNode(IDX_TRAILING + 1, node);
        }
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
            double top = item.getChildren().stream().filter(Node::isVisible).mapToDouble(node -> node.getLayoutY()).min().orElseThrow();
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
        return item.getChildren().stream().filter(node -> node.getStyleClass().contains("tag")
                        && !node.getStyleClass().contains("collapsed-tag"))
                .map(Label.class::cast).toList();
    }

    /// Returns the summary tag, including when it is hidden.
    private static Label collapsedTag(TwoLineListItem item) {
        return (Label) requireNonNull(item.lookup(".collapsed-tag"));
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
