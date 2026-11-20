// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ColorArea;
import org.glavo.m3fx.controls.M3ColorChannel;
import org.glavo.m3fx.controls.M3ColorSlider;
import org.glavo.m3fx.controls.M3ColorSwatch;
import org.glavo.m3fx.controls.M3ColorSwatchPicker;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3MenuItem;
import org.glavo.m3fx.controls.M3OverlayPane;
import org.glavo.m3fx.controls.M3ProgressIndicator;
import org.glavo.m3fx.controls.M3RgbColor;
import org.glavo.m3fx.controls.M3Slider;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.glavo.monetfx.Brightness;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies interaction continuity, state restoration, and persistent tab selection.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
@Isolated("Temporarily supplies settings while initializing the cached animation policy")
public final class InteractionLayerTest {

    /// A live selector can replace its options while open and commit the next option by keyboard.
    @Test
    void selectionMenuTracksOptionsAndKeyboard() throws Exception {
        onFxThread(() -> {
            var selector = new LineSelectButton<String>();
            selector.setItems("First", "Second");
            selector.setValue("First");
            selector.setDescriptionConverter(item -> item + " description");
            var stage = new Stage();
            try {
                var scene = new Scene(new StackPane(selector), 800, 450);
                M3ThemeManager.install(scene, M3Theme.defaultTheme());
                scene.getStylesheets().addAll("/assets/css/blue.css", "/assets/css/root.css");
                stage.setScene(scene);
                stage.show();
                selector.fire();
                var popup = Window.getWindows().stream().filter(PopupMenu.class::isInstance)
                        .map(PopupMenu.class::cast).filter(p -> p.getOwnerNode() == selector).findFirst().orElseThrow();
                selector.setItems("First", "Last");
                var last = assertInstanceOf(M3MenuItem.class, popup.getItems().getLast());
                assertEquals("Last description", last.getSupportingText());
                last.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false));
                assertEquals("Last", selector.getValue());
                assertTrue(last.isSelected());
                last.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
                assertFalse(popup.isShowing());
            } finally {
                stage.close();
            }
        });
    }

    /// Short menus hug the anchor in either orientation and tall menus stay reachable in a compact window.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void popupMenuFitsAndDismisses(boolean rtl) throws Exception {
        onFxThread(() -> {
            var anchor = new M3Button("Actions");
            var root = new StackPane(anchor);
            root.setNodeOrientation(rtl ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
            var stage = new Stage();
            var called = new AtomicInteger();
            var command = PopupMenu.item(null, "Run command", called::incrementAndGet);
            var popup = new PopupMenu(command);
            try {
                var scene = new Scene(root, 800, 450);
                M3ThemeManager.install(scene, M3Theme.defaultTheme());
                scene.getStylesheets().addAll("/assets/css/blue.css", "/assets/css/root.css");
                stage.setScene(scene);
                stage.show();
                Bounds anchorBounds = anchor.localToScreen(anchor.getLayoutBounds());
                popup.showBelow(anchor, anchor);
                assertTrue(popup.isShowing());
                assertEquals(anchorBounds.getMaxY(), popup.getY(), 2);
                assertEquals(rtl ? anchorBounds.getMinX() : anchorBounds.getMaxX() - popup.getWidth(), popup.getX(), 2);
                assertEquals(command.getHeight(), popup.getHeight(), 24);
                var menuSurface = (Region) popup.getScene().lookup(".m3-menu");
                assertNotNull(menuSurface.getBackground(), "The Material menu must resolve its theme surface");
                command.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                assertEquals(1, called.get());
                assertFalse(popup.isShowing());

                for (int i = 0; i < 24; i++)
                    popup.getItems().add(PopupMenu.item(null, "Item " + i, called::incrementAndGet));
                popup.showBeside(anchor, Pos.BOTTOM_RIGHT);
                assertTrue(popup.isShowing());
                assertTrue(popup.getY() >= stage.getY() - 1);
                assertTrue(popup.getY() + popup.getHeight() <= stage.getY() + stage.getHeight() + 2,
                        () -> "Popup " + popup.getY() + " + " + popup.getHeight() + ", stage " + stage.getY() + " + " + stage.getHeight());
                var bar = assertInstanceOf(ScrollBar.class, popup.getScene().lookup(".scroll-bar:vertical"));
                assertTrue(bar.isVisible());
                popup.showBeside(anchor, Pos.BOTTOM_RIGHT);
                assertFalse(popup.isShowing());

                popup.getItems().setAll(command);
                popup.showAt(anchor, anchorBounds.getMinX(), anchorBounds.getMinY());
                assertEquals(anchorBounds.getMinX() - (rtl ? popup.getWidth() : 0), popup.getX(), 2);
                assertEquals(anchorBounds.getMinY(), popup.getY(), 2);
                assertEquals(command.getHeight(), popup.getHeight(), 24);
            } finally {
                popup.hide();
                stage.close();
            }
        });
    }

    /// The preset palette and actions fit within the dialog at the launcher's compact overlay size.
    @Test
    void colorDialogFitsCompactWindow() throws Exception {
        onFxThread(() -> {
            var host = new M3OverlayPane();
            host.setAnimationsEnabled(false);
            var scene = new Scene(host, 800, 450);
            M3ThemeManager.install(scene, M3Theme.defaultTheme());
            scene.getStylesheets().addAll("/assets/css/blue.css", "/assets/css/root.css");
            var stage = new Stage();
            try {
                stage.setScene(scene);
                stage.show();
                var choice = new ColorPickerButton(i18n("settings.launcher.theme_color"),
                        ThemeColor.STANDARD_COLORS.stream().map(ThemeColor::color).toList());
                choice.setValue(ThemeColor.DEFAULT.color());
                var dialog = choice.createDialog();
                var pane = dialog.getDialogPane();
                pane.maxHeightProperty().bind(host.heightProperty().multiply(0.7));
                pane.setAnimationsEnabled(false);
                host.showDialog(dialog);
                host.applyCss();
                host.layout();
                Bounds body = pane.getContent().localToScene(pane.getContent().getLayoutBounds());
                // Allow for floating-point rounding when transforming nested controls into scene coordinates.
                var editorBounds = new BoundingBox(body.getMinX() - 0.01, body.getMinY() - 0.01,
                        body.getWidth() + 0.02, body.getHeight() + 0.02);
                var cells = pane.lookupAll(".color-swatch-cell");
                assertEquals(ThemeColor.STANDARD_COLORS.size(), cells.size());
                for (var cell : cells) {
                    assertTrue(editorBounds.contains(cell.localToScene(cell.getLayoutBounds())), "Preset outside dialog body");
                }
                for (String selector : List.of(".m3-color-area", ".m3-color-slider", ".m3-text-field")) {
                    var control = pane.lookup(selector);
                    Bounds bounds = control.localToScene(control.getLayoutBounds());
                    assertTrue(editorBounds.contains(bounds), () -> selector + " " + bounds + " outside " + body);
                }
                for (var action : pane.getActions()) {
                    Bounds bounds = action.localToScene(action.getLayoutBounds());
                    assertTrue(bounds.getMinY() >= body.getMaxY(), "Action overlaps editor");
                    assertTrue(bounds.getMaxY() <= pane.localToScene(pane.getLayoutBounds()).getMaxY()
                            - pane.getInsets().getBottom(), "Action outside dialog padding");
                }
            } finally {
                stage.close();
            }
        });
    }

    /// The editor rejects RGBA and preserves a legacy translucent setting until RGB is confirmed.
    @Test
    void colorDialogCommitsOnlyOpaqueRgbOnConfirmation() throws Exception {
        onFxThread(() -> {
            var choice = new ColorPickerButton("Color", List.of(Color.web("#FF000080")));
            var button = assertInstanceOf(M3Button.class, choice.getChildren().getFirst());
            var swatch = assertInstanceOf(M3ColorSwatch.class, button.getGraphic());
            var host = new M3OverlayPane();
            host.setAnimationsEnabled(false);
            var stage = new Stage();
            try {
                var scene = new Scene(host, 800, 600);
                M3ThemeManager.install(scene, M3Theme.defaultTheme());
                stage.setScene(scene);
                stage.show();
                var changes = new AtomicInteger();
                Color legacy = Color.web("#0000FF80");
                choice.setValue(legacy);
                assertEquals("#0000FF", button.getText());
                assertEquals(Color.BLUE, Objects.requireNonNull(swatch.getColor()).toFxColor());
                choice.valueProperty().addListener((obs, old, next) -> changes.incrementAndGet());
                var cancelled = choice.createDialog();
                cancelled.getDialogPane().setAnimationsEnabled(false);
                var handle = host.showDialog(cancelled);
                host.applyCss();
                host.layout();
                var sliders = cancelled.getDialogPane().lookupAll(".m3-color-slider");
                assertEquals(1, sliders.size());
                assertEquals(M3ColorChannel.HUE, assertInstanceOf(M3ColorSlider.class, sliders.iterator().next()).getChannel());
                var palette = assertInstanceOf(M3ColorSwatchPicker.class, cancelled.getDialogPane().lookup(".m3-color-swatch-picker"));
                assertEquals(List.of(new M3RgbColor(Color.RED)), palette.getItems());
                palette.selectColor(new M3RgbColor(Color.RED));
                var area = assertInstanceOf(M3ColorArea.class, cancelled.getDialogPane().lookup(".m3-color-area"));
                assertEquals(Color.RED, area.getValue().toFxColor());
                assertEquals(Color.BLUE, Objects.requireNonNull(swatch.getColor()).toFxColor());
                cancelled.getDialogPane().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertFalse(handle.isShowing());
                assertEquals(legacy, choice.getValue());
                assertEquals(0, changes.get());

                var dialog = choice.createDialog();
                dialog.getDialogPane().setAnimationsEnabled(false);
                handle = host.showDialog(dialog);
                host.applyCss();
                host.layout();
                area = assertInstanceOf(M3ColorArea.class, dialog.getDialogPane().lookup(".m3-color-area"));
                assertEquals(Color.BLUE, area.getValue().toFxColor());
                var field = assertInstanceOf(M3TextField.class, dialog.getDialogPane().lookup(".m3-text-field"));
                var ok = dialog.getDialogPane().getActions().getLast();
                for (String invalid : List.of("#XYZ", "#1234", "#33669980", "336699FF")) {
                    field.selectAll();
                    field.replaceSelection(invalid);
                    assertTrue(ok.isDisabled());
                    ok.fire();
                    assertTrue(handle.isShowing());
                    assertEquals(Color.BLUE, area.getValue().toFxColor());
                }
                field.setText("aBc");
                assertEquals(Color.web("#AABBCC"), area.getValue().toFxColor());
                field.setText("#336699");
                assertFalse(ok.isDisabled());
                assertEquals(0, changes.get());
                ok.fire();
                assertEquals(Color.web("#336699"), choice.getValue());
                assertEquals("#336699", button.getText());
                assertEquals(choice.getValue(), Objects.requireNonNull(swatch.getColor()).toFxColor());
                assertEquals(1, changes.get());
                assertFalse(handle.isShowing());
            } finally {
                stage.close();
            }
        });
    }

    /// Settings row labels must retain the slider indicator's inverse theme colors.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sliderIndicatorUsesThemeColors(boolean dark) throws Exception {
        onFxThread(() -> {
            var slider = new M3Slider(1, 256, 120);
            slider.setShowValueIndicator(true);
            slider.setValueChanging(true);
            var row = new StackPane(slider);
            row.getStyleClass().add("options-list-item");
            var scene = new Scene(row, 260, 100);
            M3ThemeManager.install(scene, M3Theme.fromSeed(Color.ROYALBLUE, dark ? Brightness.DARK : Brightness.LIGHT));
            row.applyCss();
            var indicator = assertInstanceOf(Label.class, slider.lookup(".m3-slider-value-indicator"));
            var expectedText = indicator.getTextFill();
            var expectedBackground = indicator.getBackground().getFills().getFirst().getFill();
            assertNotEquals(expectedBackground, expectedText);

            scene.getStylesheets().addAll("/assets/css/blue.css", "/assets/css/root.css");
            row.applyCss();
            assertEquals(expectedText, indicator.getTextFill());
            assertEquals(expectedBackground, indicator.getBackground().getFills().getFirst().getFill());
        });
    }

    /// Memory sliders retain fractional settings and their arrow keys advance by one percentage point.
    @Test
    void percentageSliderRetainsMemoryRatio() throws Exception {
        onFxThread(() -> {
            var slider = FXUtils.newPercentageSlider();
            var scene = new Scene(new StackPane(slider), 280, 64);
            M3ThemeManager.install(scene, M3Theme.defaultTheme());
            scene.getRoot().applyCss();
            slider.setValue(0.375);
            assertEquals(0.375, slider.getValue());
            assertTrue(slider.isShowValueIndicator());
            var formatter = Objects.requireNonNull(slider.getLabelFormatter());
            assertEquals("37%", formatter.toString(slider.getValue()));
            slider.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));
            assertEquals(0.385, slider.getValue(), 1e-12);
            assertEquals("38%", formatter.toString(slider.getValue()));
        });
    }

    /// Font dropdown rows display their own font family when the virtualized menu opens.
    @Test
    void fontOptionPreview() throws Exception {
        onFxThread(() -> {
            var box = FontComboBox.create();
            var stage = new Stage();
            try {
                var scene = new Scene(new StackPane(box), 320, 80);
                M3ThemeManager.install(scene, M3Theme.defaultTheme());
                stage.setScene(scene);
                stage.show();
                String family = box.getItems().stream().filter(name -> !name.equals(Font.getDefault().getFamily()))
                        .findFirst().orElse(Font.getDefault().getFamily());
                box.setValue(family);
                box.show();
                ListView<?> list = assertInstanceOf(ListView.class, box.queryAccessibleAttribute(AccessibleAttribute.FOCUS_NODE));
                list.applyCss();
                list.layout();
                assertTrue(list.lookupAll(".list-cell").stream().anyMatch(node -> node instanceof ListCell<?> cell
                        && family.equals(cell.getItem()) && family.equals(cell.getFont().getFamily())));
            } finally {
                stage.hide();
            }
        });
    }

    /// File options fit their radio buttons and enable the editor only while its option is selected.
    @Test
    void fileOptionsFollowRadioSelection() throws Exception {
        onFxThread(() -> {
            var list = new MultiFileItem<String>();
            var automatic = new MultiFileItem.Option<>("Automatic", "auto");
            var custom = new MultiFileItem.StringOption<>("Custom", "custom");
            list.loadChildren(List.of(automatic, custom));
            var scene = new Scene(list);
            scene.getRoot().applyCss();
            list.autosize();
            list.layout();
            var row = assertInstanceOf(Region.class, list.getChildren().getFirst());
            assertTrue(row.getHeight() >= automatic.left.getHeight() + row.getInsets().getTop() + row.getInsets().getBottom());

            list.setSelectedData("auto");
            assertTrue(automatic.isSelected());
            assertTrue(custom.getCustomField().isDisabled());
            custom.left.fire();
            assertEquals("custom", list.getSelectedData());
            assertFalse(automatic.isSelected());
            assertFalse(custom.getCustomField().isDisabled());
            custom.left.fire();
            assertSame(custom.left, list.getGroup().getSelectedToggle());
        });
    }

    /// Restores an action's icon without overriding permission changes made while the action was running.
    @Test
    void busyIconButtonRestoresGraphicAndHonorsAvailability() throws Exception {
        onFxThread(() -> {
            Region icon = new Region();
            M3IconButton button = new M3IconButton(icon);
            var available = new SimpleBooleanProperty(true);
            var loading = FXUtils.newButtonLoadingProperty(button);
            button.disableProperty().bind(loading.or(available.not()));
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> {
                actions.incrementAndGet();
                loading.set(true);
            });

            button.fire();
            assertInstanceOf(M3ProgressIndicator.class, button.getGraphic());
            button.fire();
            assertEquals(1, actions.get());
            available.set(false);
            loading.set(false);
            assertSame(icon, button.getGraphic());
            assertTrue(button.isDisabled());
            available.set(true);
            assertFalse(button.isDisabled());
        });
    }

    /// Initializes animation defaults without reading or changing persisted settings.
    @BeforeAll
    static void initializeAnimationPolicy() throws Exception {
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

    /// Selection remains visible after pointer feedback ends, and selected controls still respond to presses.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pointerFeedbackRestoresPersistentState(boolean selected) throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setSelected(selected, 0);
            settleAnimation(layer, 0);
            double baseline = layer.getOpacity();
            assertEquals(selected, baseline > 0);

            layer.setHovered(true, SECOND);
            settleAnimation(layer, SECOND);
            double hovered = layer.getOpacity();
            assertTrue(hovered > baseline);

            layer.press(2 * SECOND);
            settleAnimation(layer, 2 * SECOND);
            assertTrue(layer.getOpacity() > hovered);

            layer.release(3 * SECOND);
            settleAnimation(layer, 3 * SECOND);
            assertEquals(hovered, layer.getOpacity());

            layer.setHovered(false, 4 * SECOND);
            settleAnimation(layer, 4 * SECOND);
            assertEquals(baseline, layer.getOpacity());
            layer.setSelected(false, 5 * SECOND);
            settleAnimation(layer, 5 * SECOND);
            assertEquals(0, layer.getOpacity());
        });
    }

    /// Retargeting hover to press preserves the displayed tint and the linear rate.
    @Test
    void retargetingPreservesLinearRate() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setHovered(true, 0);
            layer.advance(SAMPLE);
            double first = layer.getOpacity();
            assertTrue(first > 0);
            assertTrue(layer.isStateTransitioning());
            // No pulse at the input timestamp: input must sample the current fade itself.
            layer.press(2 * SAMPLE);
            assertEquals(2 * first, layer.getOpacity(), EPSILON);
            layer.advance(3 * SAMPLE);
            assertEquals(3 * first, layer.getOpacity(), EPSILON);
            layer.press(3 * SAMPLE);
            layer.advance(4 * SAMPLE);
            assertEquals(4 * first, layer.getOpacity(), EPSILON);
        });
    }

    /// Early release reverses immediately; a second press continues from the fading tint.
    @Test
    void rapidClicksRemainContinuous() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.press(0);
            layer.advance(2 * SAMPLE);
            double pressed = layer.getOpacity();
            assertTrue(layer.isStateTransitioning());
            layer.release(2 * SAMPLE);
            assertEquals(pressed, layer.getOpacity());
            layer.advance(3 * SAMPLE);
            double fading = layer.getOpacity();
            assertEquals(pressed / 2, fading, EPSILON);
            layer.press(3 * SAMPLE);
            assertEquals(fading, layer.getOpacity());
            layer.advance(4 * SAMPLE);
            assertEquals(pressed, layer.getOpacity(), EPSILON);
            layer.release(4 * SAMPLE);
            // A fade retraces the same distance in the same time, without a fixed release delay.
            layer.advance(6 * SAMPLE);
            assertEquals(0, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            assertTrue(layer.contains(100, 20));
        });
    }

    /// Reset clears a held press without losing selection or focus; disabled animation settles immediately.
    @Test
    void resetPreservesSelectionAndFocus() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setAnimationEnabled(false);
            layer.setSelected(true, 0);
            double selected = layer.getOpacity();
            layer.setHovered(true, 0);
            double hovered = layer.getOpacity();
            layer.setFocusActive(true, 0);
            double focused = layer.getOpacity();
            assertTrue(focused >= hovered);
            layer.press(0);
            layer.reset();
            assertEquals(focused, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
            layer.setFocusActive(false, 0);
            assertEquals(hovered, layer.getOpacity());
            layer.setHovered(false, 0);
            assertEquals(selected, layer.getOpacity());
            layer.setAnimationEnabled(true);
            assertFalse(layer.isStateTransitioning());
            layer.setSelected(false, 0);
            layer.advance(SAMPLE);
            assertTrue(layer.isStateTransitioning());
            layer.setAnimationEnabled(false);
            assertEquals(0, layer.getOpacity());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Theme paint and corner changes preserve interaction progress; hidden paint remains pickable.
    @Test
    void appearanceChangesPreserveFeedback() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            var gradient = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(1, 0, 0, 0.5)), new Stop(1, Color.TRANSPARENT));
            layer.setFill(gradient);
            layer.press(0);
            layer.advance(SAMPLE);
            assertEquals(gradient, Objects.requireNonNull(layer.getBackground()).getFills().get(0).getFill());
            double opacity = layer.getOpacity();
            var radii = new CornerRadii(0.5, true);
            layer.setCornerRadii(radii);
            layer.resize(300, 60);
            assertEquals(opacity, layer.getOpacity());
            assertEquals(radii, Objects.requireNonNull(layer.getBackground()).getFills().get(0).getRadii());
            layer.setFill(null);
            assertNull(layer.getBackground());
            assertTrue(layer.contains(150, 30));
            assertEquals(opacity, layer.getOpacity());
            Color paint = Color.color(0, 0, 1, 0.5);
            layer.setFill(paint);
            assertEquals(paint, Objects.requireNonNull(layer.getBackground()).getFills().get(0).getFill());
            assertEquals(opacity, layer.getOpacity());
        });
    }

    /// Completion notifies once; reset cancels silently and disabling animation reports settlement.
    @Test
    void settlementCallbackFollowsCompletion() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            var notifications = new AtomicInteger();
            layer.setOnStateSettled(notifications::incrementAndGet);
            layer.setHovered(true, 0);
            settleAnimation(layer, 0);
            layer.advance(2 * SECOND);
            assertEquals(1, notifications.get());
            layer.press(2 * SECOND);
            layer.reset();
            settleAnimation(layer, 2 * SECOND);
            assertEquals(1, notifications.get());
            layer.press(3 * SECOND);
            layer.setAnimationEnabled(false);
            assertEquals(2, notifications.get());
            assertFalse(layer.isStateTransitioning());
        });
    }

    /// Reversing selection mid-fade is continuous and reset retains the latest selection.
    @Test
    void interruptedSelectionRemainsContinuous() throws Exception {
        onFxThread(() -> {
            InteractionLayer layer = createLayer();
            layer.setSelected(true, 0);
            layer.advance(2 * SAMPLE);
            double selecting = layer.getOpacity();
            layer.setSelected(false, 2 * SAMPLE);
            assertEquals(selecting, layer.getOpacity());
            layer.advance(3 * SAMPLE);
            double fading = layer.getOpacity();
            assertEquals(selecting / 2, fading, EPSILON);
            layer.setSelected(true, 3 * SAMPLE);
            layer.press(3 * SAMPLE);
            assertEquals(fading, layer.getOpacity());
            layer.reset();
            double baseline = layer.getOpacity();
            assertTrue(baseline > selecting);
            assertFalse(layer.isStateTransitioning());
            layer.release(4 * SAMPLE);
            assertEquals(baseline, layer.getOpacity());
        });
    }

    /// Checks that controller selection updates tab backgrounds even without a showing window.
    @Test
    void tabSelectionUpdatesBackgrounds() throws Exception {
        onFxThread(() -> {
            var first = new TabItem<Region>();
            var second = new TabItem<Region>();
            TabHeader header = new TabHeader(first, second);
            var root = new StackPane(header);
            new Scene(root, 300, 100);
            root.applyCss();
            root.layout();
            @Unmodifiable List<RipplerContainer> views = header.lookupAll(".rippler-container").stream()
                    .map(RipplerContainer.class::cast).toList();
            assertEquals(2, views.size());
            assertEquals(1, views.stream().filter(view ->
                    feedbackOpacity(view) > 0).count());
            RipplerContainer previous = views.stream().filter(view ->
                    feedbackOpacity(view) > 0).findFirst().orElseThrow();
            double selectedOpacity = feedbackOpacity(previous);
            try {
                header.getController().selectTab(second);
                assertEquals(0, feedbackOpacity(previous));
                assertEquals(1, views.stream().filter(view ->
                        feedbackOpacity(view) > 0).count());
                header.getController().getTabs().remove(second);
                assertEquals(selectedOpacity, feedbackOpacity(previous));
            } finally {
                Objects.requireNonNull(header.getSkin()).dispose();
            }
        });
    }

    /// Finds feedback by node type rather than depending on the wrapper's child order.
    private static double feedbackOpacity(RipplerContainer view) {
        return view.getChildren().stream().filter(InteractionLayer.class::isInstance)
                .findFirst().orElseThrow().getOpacity();
    }

    /// A generous completion interval, independent of the visual timing tokens.
    private static final long SECOND = 1_000_000_000L;

    /// A short sampling interval for observing uninterrupted and interrupted fades.
    private static final long SAMPLE = 10_000_000L;

    /// Tolerance for comparing independently sampled opacity values.
    private static final double EPSILON = 1e-12;

    /// Advances well past a normal feedback transition and verifies it stops.
    private static void settleAnimation(InteractionLayer layer, long startedAt) {
        layer.advance(startedAt + SECOND);
        assertFalse(layer.isStateTransitioning());
    }

    /// Creates an animated layer whose pulses are controlled entirely by each test.
    private static InteractionLayer createLayer() {
        InteractionLayer layer = new InteractionLayer(false);
        layer.setAnimationEnabled(true);
        layer.resize(200, 40);
        return layer;
    }

    /// Runs assertions on the JavaFX thread and propagates failures to the test worker.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
