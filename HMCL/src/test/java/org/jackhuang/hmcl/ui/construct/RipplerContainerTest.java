// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXRippler;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.Popup;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.ui.construct.FeedbackTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies layout, theme updates, animation configuration, lifecycle cleanup, and native pointer picking.
@NotNullByDefault
public final class RipplerContainerTest {
    /// The settings replaced temporarily while these tests run.
    private static @Nullable Object previousSettings;

    /// Installs detached settings without reading or writing user configuration.
    @BeforeAll
    static void installSettings() {
        fx(() -> {
            Field field = SettingsManager.class.getDeclaredField("launcherSettings");
            field.setAccessible(true);
            previousSettings = field.get(null);
            LauncherSettings settings = new LauncherSettings();
            settings.animationDisabledProperty().set(true);
            field.set(null, settings);
            return true;
        });
    }

    /// Restores the original settings reference after all windows have been closed.
    @AfterAll
    static void restoreSettings() {
        fx(() -> {
            Field field = SettingsManager.class.getDeclaredField("launcherSettings");
            field.setAccessible(true);
            field.set(null, previousSettings);
            return true;
        });
    }

    /// Returns the wrapper's sole effect node without depending on private fields.
    private static InteractionLayer layer(RipplerContainer wrapper) {
        return (InteractionLayer) wrapper.getChildren().stream().filter(InteractionLayer.class::isInstance).findFirst().orElseThrow();
    }

    /// Padding sizes content but never shrinks the unified feedback layer.
    @Test
    void layoutAndStackingUseOnlyOneEffectNode() {
        runFx(() -> {
            Region content = new Region();
            content.setPrefSize(80, 40);
            RipplerContainer wrapper = new RipplerContainer(content);
            wrapper.setPadding(new Insets(5, 7, 11, 13));
            new Scene(wrapper);
            wrapper.applyCss();
            assertEquals(100, wrapper.prefWidth(-1), 0.001);
            assertEquals(56, wrapper.prefHeight(-1), 0.001);
            wrapper.resize(200, 80);
            wrapper.layout();
            assertEquals(180, content.getWidth());
            assertEquals(64, content.getHeight());
            assertEquals(13, content.getLayoutX());
            assertEquals(5, content.getLayoutY());
            InteractionLayer layer = layer(wrapper);
            assertEquals(200, layer.getWidth());
            assertEquals(80, layer.getHeight());
            assertEquals(0, layer.getLayoutX());
            assertEquals(0, layer.getLayoutY());
            assertEquals(2, wrapper.getChildren().size());
            assertNull(wrapper.getShape());
            assertNull(wrapper.getClip());
            assertNull(layer.getClip());
            AtomicInteger changes = new AtomicInteger();
            wrapper.getChildren().addListener((ListChangeListener<Node>) change -> changes.incrementAndGet());
            wrapper.setPosition(FeedbackPosition.BACK);
            assertEquals(0, changes.get());
            wrapper.positionProperty().set(FeedbackPosition.FRONT);
            assertSame(layer, wrapper.getChildren().get(1));
            wrapper.setPosition(FeedbackPosition.BACK);
            assertSame(layer, wrapper.getChildren().get(0));
            assertFalse(content.isPickOnBounds());
            assertFalse(wrapper.isPickOnBounds());
            assertFalse(layer.isManaged());
            assertThrows(NullPointerException.class, () -> wrapper.setPosition(null));
            assertEquals(FeedbackPosition.BACK, wrapper.getPosition());
            for (int i = 0; i < 50; i++) {
                layer.press(20, 20);
                layer.release();
                wrapper.resize(200 + i, 80);
                wrapper.layout();
            }
            wrapper.resetFeedback();
            assertEquals(2, wrapper.getChildren().size());
            assertEquals(0, layer.rippleCount());
            assertFalse(layer.isAnimating());
        });
    }

    /// Theme changes repaint a settled state without replacing a caller-owned background.
    @Test
    void feedbackTracksThemeAndCssWithoutReplacingBackground() {
        runFx(() -> {
            LauncherSettings settings = SettingsManager.settings();
            @Nullable String oldMode = settings.themeBrightnessModeProperty().get();
            boolean hadOverride = settings.getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE);
            try {
                settings.getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE);
                settings.themeBrightnessModeProperty().set("light");
                RipplerContainer wrapper = new RipplerContainer(new Region());
                Background background = new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY));
                wrapper.setBackground(background);
                wrapper.feedbackFillProperty().bind(Bindings.createObjectBinding(
                        () -> Themes.getColorScheme().getOnSurface(), Themes.colorSchemeProperty()));
                InteractionLayer layer = layer(wrapper);
                layer.setHovered(true);
                layer.reset();
                Color lightFill = (Color) layer.getFill();
                settings.themeBrightnessModeProperty().set("dark");
                assertNotEquals(lightFill, layer.getFill());
                assertEquals(Themes.getColorScheme().getOnSurface(), layer.getFill());
                assertEquals(0.08, layer.stateOpacity());
                assertSame(background, wrapper.getBackground());
                wrapper.feedbackFillProperty().unbind();
                new Scene(wrapper);
                wrapper.setStyle("-hmcl-feedback-fill: #123456;");
                wrapper.applyCss();
                assertEquals(Color.web("#123456"), wrapper.getFeedbackFill());
                assertEquals(wrapper.getFeedbackFill(), layer.getFill());
                wrapper.resetFeedback();
            } finally {
                settings.themeBrightnessModeProperty().set(oldMode);
                if (!hadOverride)
                    settings.getThemeAppearanceOverrides().remove(LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE);
            }
        });
    }

    /// Stylesheets use the surface content color unless the list item is selected.
    @Test
    void feedbackColorFollowsTheContainerRole() {
        runFx(() -> {
            RipplerContainer wrapper = new RipplerContainer(new Region());
            StackPane item = new StackPane(wrapper);
            item.getStyleClass().add("advanced-list-item");
            item.setStyle("-monet-on-surface: #123456; -monet-on-surface-variant: #abcdef; -monet-on-secondary-container: #654321;");
            Scene scene = new Scene(item);
            scene.getStylesheets().add(Objects.requireNonNull(RipplerContainer.class.getResource("/assets/css/root.css")).toExternalForm());
            item.applyCss();
            assertEquals(Color.web("#123456"), wrapper.getFeedbackFill());
            item.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), true);
            item.applyCss();
            assertEquals(Color.web("#654321"), wrapper.getFeedbackFill());
            item.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), false);
            item.applyCss();
            assertEquals(Color.web("#123456"), wrapper.getFeedbackFill());
            item.setStyle("-monet-on-surface: #ffffff; -monet-on-secondary-container: #654321;");
            item.applyCss();
            assertEquals(Color.WHITE, layer(wrapper).getFill());
        });
    }

    /// Layers use the cached animation default and retain manual control independently of settings.
    @Test
    void animationUsesCachedDefaultWithoutObservingSettings() {
        runFx(() -> {
            LauncherSettings settings = SettingsManager.settings();
            RipplerContainer wrapper = new RipplerContainer(new Region());
            InteractionLayer layer = layer(wrapper);
            boolean defaultEnabled = AnimationUtils.isAnimationEnabled();
            assertEquals(defaultEnabled, layer.isAnimationEnabled());
            @Nullable Boolean previous = settings.animationDisabledProperty().get();
            try {
                settings.animationDisabledProperty().set(defaultEnabled);
                assertEquals(defaultEnabled, layer.isAnimationEnabled());
                assertEquals(defaultEnabled, layer(new RipplerContainer(new Region())).isAnimationEnabled());
                layer.setAnimationEnabled(true);
                layer.press(20, 20);
                settings.animationDisabledProperty().set(null);
                assertTrue(layer.isAnimationEnabled());
                assertEquals(1, layer.rippleCount());
                layer.setAnimationEnabled(false);
                assertSettled(layer);
                assertEquals(0.10, layer.stateOpacity());
                settings.animationDisabledProperty().set(false);
                assertFalse(layer.isAnimationEnabled());
                layer.setAnimationEnabled(true);
                assertEquals(0, layer.rippleCount());
            } finally {
                wrapper.resetFeedback();
                settings.animationDisabledProperty().set(previous);
            }
        });
    }

    /// Real native picking distinguishes uncovered background, nested buttons, and front feedback.
    @Test
    void nativePickingPreservesContentInputAndBubbling() {
        Fixture fixture = openFixture();
        try {
            AtomicInteger bubbles = new AtomicInteger();
            AtomicInteger buttonActions = new AtomicInteger();
            runFx(() -> {
                fixture.wrapper.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> bubbles.incrementAndGet());
                fixture.button.setOnAction(event -> buttonActions.incrementAndGet());
            });
            move(fixture, 250, 100);
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.08);
            runFx(() -> fixture.robot.mousePress(MouseButton.PRIMARY));
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.10);
            assertTrue(bubbles.get() > 0);
            runFx(() -> fixture.robot.mouseRelease(MouseButton.PRIMARY));
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.08);

            move(fixture, 50, 35);
            runFx(() -> fixture.robot.mousePress(MouseButton.PRIMARY));
            awaitFx(fixture.button::isPressed);
            assertEquals(0.08, fx(() -> layer(fixture.wrapper).stateOpacity()));
            runFx(() -> fixture.robot.mouseRelease(MouseButton.PRIMARY));
            awaitFx(() -> buttonActions.get() == 1);

            runFx(() -> fixture.wrapper.setPosition(FeedbackPosition.FRONT));
            move(fixture, 50, 35);
            runFx(() -> fixture.robot.mousePress(MouseButton.PRIMARY));
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.10);
            assertFalse(fx(fixture.button::isPressed));
            runFx(() -> fixture.robot.mouseRelease(MouseButton.PRIMARY));
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.08);
            assertEquals(1, buttonActions.get());

            runFx(() -> {
                fixture.wrapper.setPosition(FeedbackPosition.BACK);
                fixture.content.setMouseTransparent(true);
            });
            move(fixture, 50, 35);
            runFx(() -> fixture.robot.mousePress(MouseButton.PRIMARY));
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.10);
            move(fixture, -20, -20);
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0);
            move(fixture, 250, 100);
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.08);
            runFx(() -> fixture.robot.mouseRelease(MouseButton.PRIMARY));

            int secondaryBefore = bubbles.get();
            runFx(() -> fixture.robot.mousePress(MouseButton.SECONDARY));
            awaitFx(() -> bubbles.get() > secondaryBefore);
            assertEquals(0.08, fx(() -> layer(fixture.wrapper).stateOpacity()));
            runFx(() -> fixture.robot.mouseRelease(MouseButton.SECONDARY));
            runFx(() -> fixture.wrapper.setPressFeedbackEnabled(false));
            int disabledBefore = bubbles.get();
            runFx(() -> fixture.robot.mousePress(MouseButton.PRIMARY));
            awaitFx(() -> bubbles.get() > disabledBefore);
            assertEquals(0.08, fx(() -> layer(fixture.wrapper).stateOpacity()));
            runFx(() -> fixture.robot.mouseRelease(MouseButton.PRIMARY));
            assertEquals(2, fx(() -> fixture.wrapper.getChildren().size()));
        } finally {
            closeFixture(fixture);
        }
    }

    /// Waveform clipping never removes transparent corners from the background hit area.
    @Test
    void activeWaveformsPreserveNativeCornerPicking() {
        Fixture fixture = openFixture();
        try {
            AtomicInteger backgroundHits = new AtomicInteger();
            runFx(() -> {
                InteractionLayer layer = layer(fixture.wrapper);
                layer.setAnimationEnabled(true);
                layer.setCornerRadius(20);
                fixture.wrapper.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getTarget() == layer)
                        backgroundHits.incrementAndGet();
                });
                layer.press(100, 50);
            });
            move(fixture, 2, 2);
            runFx(() -> fixture.robot.mousePress(MouseButton.PRIMARY));
            awaitFx(() -> backgroundHits.get() == 1);
            runFx(() -> fixture.robot.mouseRelease(MouseButton.PRIMARY));
            awaitFx(() -> !layer(fixture.wrapper).isPressed());
            runFx(() -> fixture.wrapper.setPosition(FeedbackPosition.FRONT));
            awaitPulses();
            move(fixture, 3, 3);
            awaitPulses();
            runFx(() -> {
                layer(fixture.wrapper).press(100, 50);
                fixture.robot.mousePress(MouseButton.PRIMARY);
            });
            awaitFx(() -> backgroundHits.get() == 2);
            runFx(() -> fixture.robot.mouseRelease(MouseButton.PRIMARY));
        } finally {
            closeFixture(fixture);
        }
    }

    /// Disable, detach, window hiding, focus loss, and cell reset cancel every waveform phase.
    @Test
    void lifecycleChangesCancelTransientFeedback() {
        Fixture fixture = openFixture();
        try {
            runFx(() -> {
                InteractionLayer layer = layer(fixture.wrapper);
                layer.setAnimationEnabled(true);
                layer.press(20, 20);
                layer.release();
                layer.press(30, 30);
                assertEquals(2, layer.rippleCount());
                fixture.wrapper.resetFeedback();
                assertSettled(layer);
                layer.press(20, 20);
                fixture.wrapper.setDisable(true);
                assertSettled(layer);
                fixture.wrapper.setDisable(false);
                layer.press(20, 20);
                fixture.root.getChildren().clear();
                assertSettled(layer);
                fixture.root.getChildren().add(fixture.wrapper);
                assertEquals(0, layer.rippleCount());
                layer.press(20, 20);
                fixture.stage.hide();
                assertSettled(layer);
                fixture.stage.show();
                fixture.stage.requestFocus();
            });
            awaitFx(fixture.stage::isFocused);
            Stage other = fx(() -> {
                layer(fixture.wrapper).press(20, 20);
                Stage stage = new Stage();
                stage.setScene(new Scene(new Pane(), 100, 100));
                stage.setX(fixture.stage.getX() + 450);
                stage.setY(fixture.stage.getY());
                stage.setAlwaysOnTop(true);
                stage.show();
                stage.requestFocus();
                return stage;
            });
            try {
                awaitFx(() -> !fixture.stage.isFocused());
                runFx(() -> assertSettled(layer(fixture.wrapper)));
            } finally {
                runFx(other::close);
            }
        } finally {
            closeFixture(fixture);
        }
    }

    /// Unfocused windows retain hover feedback while focus loss cancels every waveform.
    @Test
    void unfocusedWindowsStillRespondToNativeHover() {
        Fixture fixture = openFixture();
        try {
            move(fixture, 250, 100);
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.08);
            runFx(() -> {
                fixture.wrapper.setFocusTraversable(true);
                fixture.wrapper.requestFocus();
            });
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.10);
            Stage other = fx(() -> {
                InteractionLayer layer = layer(fixture.wrapper);
                layer.setAnimationEnabled(true);
                layer.press(20, 20);
                layer.release();
                layer.press(30, 30);
                Stage stage = new Stage();
                stage.setScene(new Scene(new Pane(), 100, 100));
                stage.setX(fixture.stage.getX() + 450);
                stage.setY(fixture.stage.getY());
                stage.setAlwaysOnTop(true);
                stage.show();
                stage.requestFocus();
                return stage;
            });
            try {
                awaitFx(() -> !fixture.stage.isFocused());
                runFx(() -> {
                    assertSettled(layer(fixture.wrapper));
                    assertEquals(0.08, layer(fixture.wrapper).stateOpacity());
                });
                boolean @Unmodifiable [] animationModes = {false, true};
                for (boolean disabled : animationModes) {
                    runFx(() -> layer(fixture.wrapper).setAnimationEnabled(!disabled));
                    move(fixture, -20, -20);
                    awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0);
                    move(fixture, 250, 100);
                    awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.08);
                    runFx(() -> {
                        assertFalse(fixture.stage.isFocused());
                        assertSettled(layer(fixture.wrapper));
                    });
                }
            } finally {
                runFx(other::close);
            }
            runFx(fixture.stage::requestFocus);
            awaitFx(fixture.stage::isFocused);
            awaitFx(() -> layer(fixture.wrapper).stateOpacity() == 0.10);
            runFx(() -> assertSettled(layer(fixture.wrapper)));
        } finally {
            closeFixture(fixture);
        }
    }

    /// Popup menu items remain interactive while their owner window supplies popup focus.
    @Test
    void popupItemsReceiveNativeBackgroundPresses() {
        Fixture fixture = openFixture();
        Popup popup = fx(() -> {
            Label label = new Label("Popup menu item");
            RipplerContainer item = (RipplerContainer) PopupMenu.wrapPopupMenuItem(label);
            layer(item).setAnimationEnabled(false);
            item.setPrefSize(220, 60);
            item.setStyle("-fx-background-color: white;");
            Popup window = new Popup();
            window.getContent().add(item);
            window.show(fixture.stage, fixture.stage.getX() + 40, fixture.stage.getY() + 60);
            return window;
        });
        try {
            RipplerContainer item = fx(() -> (RipplerContainer) popup.getContent().get(0));
            awaitPulses();
            runFx(() -> fixture.robot.mouseMove(item.localToScreen(100, 30)));
            awaitFx(() -> layer(item).stateOpacity() == 0.08);
            runFx(() -> fixture.robot.mousePress(MouseButton.PRIMARY));
            awaitFx(() -> layer(item).stateOpacity() == 0.10);
            runFx(() -> fixture.robot.mouseRelease(MouseButton.PRIMARY));
            awaitFx(() -> layer(item).stateOpacity() == 0.08);
            runFx(() -> {
                layer(item).setAnimationEnabled(true);
                layer(item).press(100, 30);
                popup.hide();
                assertSettled(layer(item));
            });
        } finally {
            runFx(popup::hide);
            closeFixture(fixture);
        }
    }

    /// A large set of wrappers returns to the idle node budget after layout and repeated input.
    @Test
    void bulkLayoutAndInputKeepTheNodeBudget() {
        runFx(() -> {
            VBox legacy = new VBox();
            VBox current = new VBox();
            for (int i = 0; i < 500; i++) {
                JFXRippler rippler = new JFXRippler(new Region());
                legacy.getChildren().add(new StackPane(new Region(), rippler, new Region()));
                current.getChildren().add(new RipplerContainer(new Region()));
            }
            long start = System.nanoTime();
            for (Node node : legacy.getChildren()) {
                StackPane pane = (StackPane) node;
                pane.resize(400, 48);
                pane.layout();
            }
            long legacyLayout = System.nanoTime() - start;
            start = System.nanoTime();
            for (Node node : current.getChildren()) {
                RipplerContainer wrapper = (RipplerContainer) node;
                wrapper.resize(400, 48);
                wrapper.layout();
                InteractionLayer layer = layer(wrapper);
                layer.setAnimationEnabled(true);
                layer.press(20, 20);
                layer.release();
                layer.press(100, 20);
                wrapper.resetFeedback();
                assertSettled(layer);
            }
            long currentLayoutAndInput = System.nanoTime() - start;
            assertEquals(3501, nodeCount(legacy));
            assertEquals(1501, nodeCount(current));
            System.out.printf("500 rows: legacy=%d nodes, feedback=%d nodes; layout=%d ms, feedback layout plus input=%d ms (smoke timings, not a benchmark)%n",
                    nodeCount(legacy), nodeCount(current), legacyLayout / 1_000_000, currentLayoutAndInput / 1_000_000);
        });
    }

    /// Counts actual scene-graph nodes, excluding properties and unattached clip/shape objects.
    private static int nodeCount(Node node) {
        int count = 1;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable())
                count += nodeCount(child);
        }
        return count;
    }

    /// Exports light and dark feedback samples for visual inspection of the rendered result.
    @Test
    void exportFeedbackGallery() {
        fx(() -> {
            HBox gallery = new HBox(16);
            gallery.setPadding(new Insets(16));
            gallery.setStyle("-fx-background-color: #8c9199;");
            ArrayList<RipplerContainer> wrappers = new ArrayList<>();
            for (boolean dark : List.of(false, true)) {
                VBox column = new VBox(8);
                @Unmodifiable List<String> labels = List.of("Idle", "Hover 8%", "Focus 10%", "Pressed 10%", "Animated ripple", "Overlapping ripples");
                for (String text : labels) {
                    Label label = new Label(text);
                    label.setStyle("-fx-font-size: 16; -fx-text-fill: " + (dark ? "#f0f0f0" : "#202020") + ";");
                    label.setMouseTransparent(true);
                    RipplerContainer wrapper = new RipplerContainer(label);
                    wrapper.setPrefSize(260, 56);
                    wrapper.setPadding(new Insets(12));
                    wrapper.setStyle("-fx-background-color: " + (dark ? "#202124" : "#ffffff") + "; -fx-background-radius: 3;");
                    wrapper.setFeedbackFill(dark ? Color.WHITE : Color.BLACK);
                    column.getChildren().add(wrapper);
                    wrappers.add(wrapper);
                }
                gallery.getChildren().add(column);
            }
            new Scene(gallery);
            gallery.applyCss();
            gallery.resize(568, 408);
            gallery.layout();
            for (int i = 0; i < wrappers.size(); i++) {
                InteractionLayer layer = layer(wrappers.get(i));
                layer.setAnimationEnabled(false);
                switch (i % 6) {
                    case 1 -> layer.setHovered(true);
                    case 2 -> layer.setFocusActive(true);
                    case 3 -> layer.press(60, 28);
                    case 4 -> {
                        layer.setAnimationEnabled(true);
                        layer.press(60, 28);
                        layer.advance(System.nanoTime() + 100_000_000);
                    }
                    case 5 -> {
                        layer.setAnimationEnabled(true);
                        layer.press(60, 28);
                        layer.release();
                        layer.press(200, 28);
                        layer.advance(System.nanoTime() + 100_000_000);
                    }
                    default -> {
                    }
                }
            }
            WritableImage image = gallery.snapshot(null, null);
            BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) {
                for (int x = 0; x < png.getWidth(); x++)
                    png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
            Path output = Path.of("build", "reports", "feedback-gallery.png");
            Files.createDirectories(output.getParent());
            ImageIO.write(png, "png", output.toFile());
            for (RipplerContainer wrapper : wrappers)
                wrapper.resetFeedback();
            return true;
        });
    }

    /// Asserts that no waveform or pulse work survived a cancellation.
    private static void assertSettled(InteractionLayer layer) {
        assertEquals(0, layer.rippleCount());
        assertFalse(layer.isAnimating());
    }

    /// Opens a small dedicated window for real pointer input and saves the original pointer position.
    private static Fixture openFixture() {
        Fixture fixture = fx(() -> {
            Button button = new Button("Nested action");
            button.resizeRelocate(20, 20, 130, 30);
            Pane content = new Pane(button);
            RipplerContainer wrapper = new RipplerContainer(content);
            layer(wrapper).setAnimationEnabled(false);
            StackPane root = new StackPane(wrapper);
            root.setPadding(new Insets(10));
            Stage stage = new Stage();
            stage.setTitle("Interaction feedback test");
            stage.setScene(new Scene(root, 400, 180));
            stage.setX(100);
            stage.setY(100);
            stage.setAlwaysOnTop(true);
            stage.show();
            stage.requestFocus();
            root.applyCss();
            root.layout();
            Robot robot = new Robot();
            return new Fixture(stage, root, wrapper, content, button, robot, robot.getMousePosition());
        });
        awaitFx(fixture.stage::isFocused);
        awaitPulses();
        runFx(() -> fixture.robot.mouseMove(fixture.stage.getX() + fixture.stage.getWidth() + 20,
                fixture.stage.getY() + fixture.stage.getHeight() + 20));
        awaitPulses();
        return fixture;
    }

    /// Moves the native pointer to a position in the wrapper's local coordinate system.
    private static void move(Fixture fixture, double x, double y) {
        runFx(() -> fixture.robot.mouseMove(fixture.wrapper.localToScreen(x, y)));
    }

    /// Releases any test-held mouse buttons, closes the window, and restores the pointer position.
    private static void closeFixture(Fixture fixture) {
        runFx(() -> {
            fixture.robot.mouseRelease(MouseButton.PRIMARY, MouseButton.SECONDARY);
            fixture.wrapper.resetFeedback();
            fixture.stage.close();
            fixture.robot.mouseMove(fixture.pointer);
        });
    }

    /// Holds the native-input fixture and its restoration state.
    ///
    /// @param stage the dedicated test window
    /// @param root the scene root used for detach tests
    /// @param wrapper the feedback wrapper
    /// @param content the content plane containing a nested button
    /// @param button the nested interactive control
    /// @param robot the native input driver
    /// @param pointer the pointer position to restore
    @NotNullByDefault
    private record Fixture(Stage stage, StackPane root, RipplerContainer wrapper, Pane content,
                           Button button, Robot robot, Point2D pointer) {
    }
}
