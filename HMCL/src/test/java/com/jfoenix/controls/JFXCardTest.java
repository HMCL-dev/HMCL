// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventType;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
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
            assertEquals(-16, clip.getLayoutX());
            card.setStyle("-fx-background-radius: 24; -fx-padding: 0;");
            root.applyCss();
            root.layout();
            assertEquals(24, clip.getBackground().getFills().get(0).getRadii().getTopLeftHorizontalRadius());
            assertEquals(0, clip.getLayoutX(), 0.001);

            Button replacement = new Button("Details");
            card.setContent(replacement);
            assertNull(content.getParent());
            assertSame(originalClip, content.getClip());
            assertSame(card.getContainer(), replacement.getParent());
            card.setContent(null);
            assertNull(replacement.getParent());
            assertEquals(2, card.getChildren().size(), "Content removal must preserve the feedback layer");
            return null;
        });
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
