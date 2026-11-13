// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.NodeOrientation;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies card styling, content ownership, and activation boundaries in JavaFX scenes.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class JFXCardTest {
    /// Rounded outlines must not expose a bright background fringe outside the darker stroke.
    @Test
    void outlinedCornersHaveNoOuterHalo() throws Exception {
        onFxThread(() -> {
            JFXCard card = new JFXCard();
            card.setCardType(JFXCard.CardType.OUTLINED);
            scene(card);
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.web("#151515"));
            parameters.setTransform(new Scale(4, 4));
            WritableImage image = card.snapshot(parameters, null);
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            for (int corner = 0; corner < 4; corner++) {
                for (int y = 0; y < 48; y++) {
                    double previous = 0;
                    for (int x = 0; x < 72; x++) {
                        int sampleX = (corner & 1) == 0 ? x : width - 1 - x;
                        int sampleY = (corner & 2) == 0 ? y : height - 1 - y;
                        double current = image.getPixelReader().getColor(sampleX, sampleY).getRed();
                        // Moving inward should go from dark surroundings through the outline to the
                        // light surface, without a bright fringe followed by a darker outline.
                        assertTrue(current >= previous - 0.035,
                                "Unexpected outer halo at corner " + corner + ", x=" + x + ", y=" + y);
                        previous = current;
                    }
                }
            }
            return null;
        });
    }

    /// Applies CSS variants repeatedly, including bound properties and disabled hover precedence.
    @Test
    void variantsAndCss() throws Exception {
        onFxThread(() -> {
            JFXCard card = new JFXCard();
            StackPane root = scene(card);
            assertEquals(JFXCard.CardType.ELEVATED, card.getCardType());
            assertEquals(12, card.getBackground().getFills().get(0).getRadii().getTopLeftHorizontalRadius());
            assertEquals(Color.web("#F5F2FA"), card.getBackground().getFills().get(0).getFill());
            assertInstanceOf(DropShadow.class, card.getEffect());
            assertEquals(Color.TRANSPARENT, card.getFeedbackFill());

            card.setStyle("-jfx-card-type: outlined;");
            root.applyCss();
            // The type changes pseudo-classes during CSS application; resolve those on the next pass.
            root.applyCss();
            assertEquals(JFXCard.CardType.OUTLINED, card.getCardType());
            assertEquals(1, card.getBorder().getStrokes().get(0).getWidths().getTop());
            assertNull(card.getEffect());

            SimpleObjectProperty<JFXCard.@Nullable CardType> type =
                    new SimpleObjectProperty<>(JFXCard.CardType.FILLED);
            card.cardTypeProperty().bind(type);
            root.applyCss();
            assertEquals(Color.web("#E3E1E9"), card.getBackground().getFills().get(0).getFill());
            assertEquals(0, card.getInsets().getTop() - card.getPadding().getTop());
            assertNull(card.getEffect());
            card.setInteractive(true);
            mouse(card, MouseEvent.MOUSE_ENTERED, MouseButton.NONE);
            root.applyCss();
            assertInstanceOf(DropShadow.class, card.getEffect());
            assertNotEquals(Color.TRANSPARENT, card.getFeedbackFill());
            card.setDisable(true);
            root.applyCss();
            assertNull(card.getEffect());
            assertEquals(Color.TRANSPARENT, card.getFeedbackFill());

            card.setDisable(false);
            card.setInteractive(false);
            type.set(null);
            root.applyCss();
            assertEquals(JFXCard.CardType.ELEVATED, card.getCardType());
            assertEquals(1, ((DropShadow) card.getEffect()).getOffsetY());
            return null;
        });
    }

    /// Replacing content preserves the caller's clip and keeps the card shadow outside the content clip.
    @Test
    void contentLayoutAndClipping() throws Exception {
        onFxThread(() -> {
            Region content = new Region();
            content.setPrefSize(100, 60);
            Rectangle originalClip = new Rectangle(100, 60);
            content.setClip(originalClip);
            JFXCard card = new JFXCard(content);
            StackPane root = scene(card);
            assertEquals(132, card.prefWidth(-1));
            assertEquals(92, card.prefHeight(-1));
            assertSame(originalClip, content.getClip());
            assertNull(card.getClip(), "The card's shadow must not be clipped");
            Region clip = (Region) Objects.requireNonNull(card.getContainer().getClip());
            assertEquals(card.getWidth(), clip.getWidth());
            assertEquals(0, clip.getLayoutX(), 0.001);
            card.setStyle("-fx-background-radius: 24; -fx-padding: 0;");
            root.applyCss();
            root.layout();
            assertEquals(24, clip.getBackground().getFills().get(0).getRadii().getTopLeftHorizontalRadius());
            assertEquals(0, clip.getLayoutX(), 0.001);

            Button replacement = new Button("Details");
            card.setContent(replacement);
            assertNull(content.getParent());
            assertSame(originalClip, content.getClip());
            assertSame(card.lookup(".card-body"), replacement.getParent());
            card.setContent(null);
            assertNull(replacement.getParent());
            assertEquals(2, card.getChildren().size(), "Content removal must preserve the feedback layer");
            return null;
        });
    }

    /// The card owns section spacing, text hierarchy, action alignment, and empty-slot removal.
    @Test
    void sectionLayoutAndVisibility() throws Exception {
        onFxThread(() -> {
            Region content = fixedRegion(100, 40);
            Region graphic = fixedRegion(40, 40);
            Region trailing = fixedRegion(24, 24);
            Region action = fixedRegion(72, 32);
            JFXCard card = new JFXCard(content);
            card.setHeadline("Headline");
            card.setSubhead("Subhead");
            card.setHeaderGraphic(graphic);
            card.setHeaderAction(trailing);
            card.getActions().add(action);
            StackPane root = scene(card);
            Label headline = (Label) Objects.requireNonNull(card.lookup(".card-headline"));
            Label subhead = (Label) Objects.requireNonNull(card.lookup(".card-subhead"));
            Bounds cardBounds = bounds(card);
            assertEquals(cardBounds.getMinX() + 16, bounds(graphic).getMinX(), 0.01);
            assertEquals(bounds(graphic).getMaxX() + 16, bounds(headline).getMinX(), 0.01);
            assertEquals(8, bounds(subhead).getMinY() - bounds(headline).getMaxY(), 0.01);
            assertEquals(cardBounds.getMaxX() - 16, bounds(trailing).getMaxX(), 0.01);
            assertEquals(16, bounds(content).getMinY() - bounds(subhead).getMaxY(), 0.01);
            assertEquals(16, bounds(action).getMinY() - bounds(content).getMaxY(), 0.01);
            assertEquals(cardBounds.getMaxX() - 16, bounds(action).getMaxX(), 0.01);

            double withContent = card.prefHeight(card.getWidth());
            content.setVisible(false);
            root.applyCss();
            root.layout();
            assertEquals(withContent - 56, card.prefHeight(card.getWidth()), 0.01);
            content.setVisible(true);
            root.applyCss();
            root.layout();
            assertEquals(withContent, card.prefHeight(card.getWidth()), 0.01);
            card.setHeadline(null);
            card.setSubhead("");
            card.setHeaderGraphic(null);
            card.setHeaderAction(null);
            card.setContent(null);
            card.getActions().clear();
            root.applyCss();
            root.layout();
            assertEquals(0, card.prefHeight(card.getWidth()), 0.01);
            assertNull(content.getParent());
            assertNull(action.getParent());
            return null;
        });
    }

    /// Long text and action rows adapt to width, and logical start/end positions mirror in RTL.
    @Test
    void wrappingAndRightToLeft() throws Exception {
        onFxThread(() -> {
            JFXCard card = new JFXCard();
            card.setHeadline("A long headline that wraps across multiple lines");
            card.setSupportingText("Supporting text with enough words to test width-dependent card sizing. ".repeat(3));
            Region graphic = fixedRegion(32, 32);
            Region trailing = fixedRegion(24, 24);
            card.setHeaderGraphic(graphic);
            card.setHeaderAction(trailing);
            Region first = fixedRegion(100, 32);
            Region second = fixedRegion(100, 32);
            Region third = fixedRegion(100, 32);
            card.getActions().setAll(first, second, third);
            StackPane root = scene(card);
            double wideHeight = card.prefHeight(500);
            double narrowHeight = card.prefHeight(260);
            assertTrue(narrowHeight > wideHeight);
            root.resize(260, narrowHeight);
            root.layout();
            assertEquals(bounds(first).getMinY(), bounds(second).getMinY(), 0.01);
            assertEquals(bounds(second).getMaxY() + 8, bounds(third).getMinY(), 0.01);
            assertEquals(bounds(card).getMaxX() - 16, bounds(third).getMaxX(), 0.01);

            root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            root.applyCss();
            root.layout();
            assertEquals(bounds(card).getMaxX() - 16, bounds(graphic).getMaxX(), 0.01);
            assertEquals(bounds(card).getMinX() + 16, bounds(trailing).getMinX(), 0.01);
            assertTrue(bounds(first).getMinX() > bounds(second).getMinX());
            assertEquals(bounds(card).getMinX() + 16, bounds(third).getMinX(), 0.01);
            return null;
        });
    }

    /// Media moves between full-width and side layouts, preserving caller-owned node state.
    @Test
    void mediaPlacementAndCropping() throws Exception {
        onFxThread(() -> {
            Rectangle media = new Rectangle(200, 100, Color.DARKBLUE);
            Rectangle originalClip = new Rectangle(200, 100);
            media.setClip(originalClip);
            JFXCard card = new JFXCard();
            card.setHeadline("Headline");
            card.setSupportingText("Supporting text");
            card.setMedia(media);
            card.setMediaAspectRatio(2);
            StackPane root = scene(card);
            Region frame = (Region) Objects.requireNonNull(card.lookup(".card-media"));
            Label headline = (Label) Objects.requireNonNull(card.lookup(".card-headline"));
            assertEquals(card.getWidth(), frame.getWidth(), 0.01);
            assertEquals(frame.getWidth() / 2, frame.getHeight(), 0.01);
            assertTrue(bounds(headline).getMinY() > bounds(frame).getMaxY());
            assertSame(originalClip, media.getClip());
            assertEquals(1, media.getScaleX());
            assertEquals(1, media.getScaleY());

            card.setMediaPlacement(JFXCard.MediaPlacement.BELOW_HEADER);
            root.applyCss();
            root.layout();
            assertTrue(bounds(headline).getMaxY() < bounds(frame).getMinY());
            card.setMediaPlacement(JFXCard.MediaPlacement.END);
            root.applyCss();
            root.layout();
            assertEquals(80, frame.getWidth(), 0.01);
            assertEquals(80, frame.getHeight(), 0.01);
            assertEquals(bounds(card).getMaxX() - 16, bounds(frame).getMaxX(), 0.01);
            assertTrue(bounds(media).getWidth() > frame.getWidth(), "A wide image must be cropped in a square frame");
            card.setSideMediaSize(64);
            root.layout();
            assertEquals(64, frame.getWidth(), 0.01);
            card.setMediaPlacement(JFXCard.MediaPlacement.START);
            root.applyCss();
            root.layout();
            assertEquals(bounds(card).getMinX() + 16, bounds(frame).getMinX(), 0.01);
            card.setMediaPlacement(JFXCard.MediaPlacement.TOP);
            card.setMediaAspectRatio(1);
            root.applyCss();
            root.layout();
            assertEquals(frame.getWidth(), frame.getHeight(), 0.01);
            media.setWidth(100);
            media.setScaleX(2);
            media.setScaleY(2);
            root.layout();
            assertEquals(frame.getWidth(), bounds(media).getWidth(), 0.01,
                    "Changing media bounds and transforms must update center-cropping");
            assertEquals(2, media.getScaleX());
            assertEquals(2, media.getScaleY());
            card.setMedia(null);
            assertNull(media.getParent());
            assertSame(originalClip, media.getClip());
            return null;
        });
    }

    /// Full-bleed media is clipped to the inside of an outlined card rather than covering its stroke.
    @Test
    void mediaPreservesRoundedOutline() throws Exception {
        onFxThread(() -> {
            JFXCard card = new JFXCard();
            card.setCardType(JFXCard.CardType.OUTLINED);
            card.setStyle("-monet-outline-variant: #00ff00;");
            Region media = new Region();
            media.setStyle("-fx-background-color: #000000;");
            card.setMedia(media);
            card.setMediaAspectRatio(2);
            StackPane root = scene(card);
            root.resize(300, card.prefHeight(300));
            root.layout();
            Region clip = (Region) Objects.requireNonNull(card.getContainer().getClip());
            assertEquals(1, clip.getBackground().getFills().get(0).getInsets().getTop());
            assertEquals(11, clip.getBackground().getFills().get(0).getRadii().getTopLeftHorizontalRadius());
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setTransform(new Scale(4, 4));
            WritableImage image = card.snapshot(parameters, null);
            Color topStroke = image.getPixelReader().getColor((int) image.getWidth() / 2, 1);
            assertTrue(topStroke.getGreen() > 0.9 && topStroke.getRed() < 0.1);
            Color mediaPixel = image.getPixelReader().getColor((int) image.getWidth() / 2, 8);
            assertTrue(mediaPixel.getGreen() < 0.1);
            return null;
        });
    }

    /// Creates a fixed-size placeholder so spacing assertions do not depend on platform font metrics.
    private static Region fixedRegion(double width, double height) {
        Region region = new Region();
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
        return region;
    }

    /// Returns a node's layout bounds in scene coordinates, excluding visual effects.
    private static Bounds bounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    /// CSS-driven pseudo-class changes must reach the screen on normal pulses without manual CSS passes.
    @Test
    void cssVariantUpdatesOnPulse() throws Exception {
        Stage window = onFxThread(Stage::new);
        CompletableFuture<Void> styled = new CompletableFuture<>();
        AnimationTimer timer = onFxThread(() -> {
            JFXCard card = new JFXCard();
            window.setScene(scene(card).getScene());
            window.show();
            card.setStyle("-jfx-card-type: outlined;");
            AnimationTimer observer = new AnimationTimer() {
                /// Completes when the border and shadow both reflect the requested variant.
                @Override
                public void handle(long now) {
                    if (card.getBorder() != null && !card.getBorder().getStrokes().isEmpty()
                            && card.getBorder().getStrokes().get(0).getWidths().getTop() == 1
                            && card.getEffect() == null) {
                        stop();
                        styled.complete(null);
                    }
                }
            };
            observer.start();
            return observer;
        });
        try {
            styled.get(5, TimeUnit.SECONDS);
        } finally {
            onFxThread(() -> {
                timer.stop();
                window.close();
                return null;
            });
        }
    }

    /// Background activation is opt-in; child actions and secondary clicks remain independent.
    @Test
    void actionBoundaries() throws Exception {
        onFxThread(() -> {
            Button child = new Button("Child action");
            JFXCard card = new JFXCard(child);
            scene(card);
            AtomicInteger actions = new AtomicInteger();
            AtomicInteger childActions = new AtomicInteger();
            card.setOnAction(event -> {
                assertSame(card, event.getTarget());
                actions.incrementAndGet();
            });
            child.setOnAction(event -> childActions.incrementAndGet());
            Node background = card.getChildren().get(0);
            mouse(background, MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY);
            card.fire();
            card.executeAccessibleAction(AccessibleAction.FIRE);
            assertEquals(0, actions.get());
            assertFalse(card.isFocusTraversable());
            card.setInteractive(true);
            assertTrue(card.isFocusTraversable());
            assertTrue(card.isPressFeedbackEnabled());
            assertEquals(AccessibleRole.BUTTON, card.getAccessibleRole());
            mouse(background, MouseEvent.MOUSE_CLICKED, MouseButton.SECONDARY);
            child.fire();
            mouse(child, MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY);
            assertEquals(0, actions.get());
            assertEquals(1, childActions.get());
            mouse(background, MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY);
            card.executeAccessibleAction(AccessibleAction.FIRE);
            assertEquals(2, actions.get());
            card.setDisable(true);
            mouse(background, MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY);
            card.fire();
            card.executeAccessibleAction(AccessibleAction.FIRE);
            assertEquals(2, actions.get());
            return null;
        });
    }

    /// Space and Enter activate once per press; cancellation and descendant input cannot activate the card.
    @Test
    void keyboardActivation() throws Exception {
        Stage window = onFxThread(Stage::new);
        try {
            JFXCard card = onFxThread(() -> {
                JFXCard value = new JFXCard(new Button("Child action"));
                value.setInteractive(true);
                window.setScene(scene(value).getScene());
                window.show();
                window.requestFocus();
                value.requestFocus();
                return value;
            });
            // Process native focus notifications before dispatching keyboard input.
            onFxThread(() -> null);
            onFxThread(() -> {
                card.requestFocus();
                assertTrue(card.isFocused());
                AtomicInteger actions = new AtomicInteger();
                card.setOnAction(event -> actions.incrementAndGet());
                for (KeyCode code : new KeyCode[]{KeyCode.SPACE, KeyCode.ENTER}) {
                    key(card, KeyEvent.KEY_PRESSED, code);
                    key(card, KeyEvent.KEY_PRESSED, code);
                    assertEquals(code == KeyCode.SPACE ? 0 : 1, actions.get());
                    key(card, KeyEvent.KEY_RELEASED, code);
                }
                assertEquals(2, actions.get());
                key(card, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
                card.setInteractive(false);
                card.setInteractive(true);
                key(card, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
                assertEquals(2, actions.get());
                card.requestFocus();
                key(card, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
                key(card, KeyEvent.KEY_PRESSED, KeyCode.ESCAPE);
                key(card, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
                Button child = (Button) Objects.requireNonNull(card.getContent());
                child.requestFocus();
                key(child, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
                key(child, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
                assertEquals(2, actions.get());
                return null;
            });
        } finally {
            onFxThread(() -> {
                window.close();
                return null;
            });
        }
    }

    /// Attaches the card to a laid-out scene with the application theme.
    private static StackPane scene(JFXCard card) {
        StackPane root = new StackPane(card);
        Scene scene = new Scene(root, 300, 160);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(JFXCardTest.class.getResource("/assets/css/blue.css")).toExternalForm(),
                Objects.requireNonNull(JFXCardTest.class.getResource("/assets/css/root.css")).toExternalForm());
        root.applyCss();
        root.layout();
        return root;
    }

    /// Dispatches a pointer event through the real event chain of the requested target.
    private static void mouse(Node node, EventType<MouseEvent> type, MouseButton button) {
        node.fireEvent(new MouseEvent(type, 20, 20, 20, 20, button, 1,
                false, false, false, false, false, false, false, false, false, true,
                new PickResult(node, 20, 20)));
    }

    /// Dispatches an unmodified key to the requested target.
    private static void key(Node node, EventType<KeyEvent> type, KeyCode code) {
        node.fireEvent(new KeyEvent(type, "", "", code, false, false, false, false));
    }

    /// Runs an assertion or fixture operation on the toolkit thread and propagates failures.
    private static <T extends @Nullable Object> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
