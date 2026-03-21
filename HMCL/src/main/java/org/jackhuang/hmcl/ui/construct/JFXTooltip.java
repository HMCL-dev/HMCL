package org.jackhuang.hmcl.ui.construct;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;

public class JFXTooltip {
    // https://api.flutter.dev/flutter/material/Tooltip-class.html
    private static final double FADE_IN_MS = AnimationUtils.isAnimationEnabled() ? 150 : 0;
    private static final double FADE_OUT_MS = AnimationUtils.isAnimationEnabled() ? 75 : 0;

    private final Popup popup;
    private final Label label;
    private final PauseTransition showDelayTransition;
    private final PauseTransition showDurationTransition;

    private final FadeTransition fadeIn;
    private final FadeTransition fadeOut;

    private double mouseX;
    private double mouseY;

    private EventHandler<MouseEvent> enteredHandler;
    private EventHandler<MouseEvent> exitedHandler;
    private EventHandler<MouseEvent> pressedHandler;
    private Node attachedNode;

    public JFXTooltip() {
        this("");
    }

    public JFXTooltip(String text) {
        popup = new Popup();
        popup.setAutoHide(false);

        label = new Label(text);
        StackPane root = new StackPane(label);
        root.getStyleClass().add("jfx-tooltip");
        root.setMouseTransparent(true);

        popup.getContent().add(root);

        fadeIn = new FadeTransition(Duration.millis(FADE_IN_MS), root);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        fadeOut = new FadeTransition(Duration.millis(FADE_OUT_MS), root);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(event -> popup.hide());

        showDelayTransition = new PauseTransition(Duration.millis(500));
        showDurationTransition = new PauseTransition(Duration.millis(5000));
        showDurationTransition.setOnFinished(e -> hideTooltip());
    }

    public void setShowDelay(Duration delay) {
        this.showDelayTransition.setDuration(delay);
    }

    public void setShowDuration(Duration duration) {
        this.showDurationTransition.setDuration(duration);
    }

    public final StringProperty textProperty() {
        return label.textProperty();
    }

    public final void setText(String value) {
        label.setText(value);
    }

    private void hideTooltip() {
        showDelayTransition.stop();
        showDurationTransition.stop();
        if (popup.isShowing()) {
            fadeIn.stop();
            fadeOut.playFromStart();
        }
    }

    public void install(Node targetNode) {
        if (attachedNode != null) {
            uninstall();
        }
        this.attachedNode = targetNode;

        enteredHandler = event -> {
            mouseX = event.getScreenX();
            mouseY = event.getScreenY();
            showDelayTransition.playFromStart();
        };

        exitedHandler = event -> hideTooltip();
        pressedHandler = event -> hideTooltip();

        targetNode.addEventHandler(MouseEvent.MOUSE_ENTERED, enteredHandler);
        targetNode.addEventHandler(MouseEvent.MOUSE_EXITED, exitedHandler);
        targetNode.addEventHandler(MouseEvent.MOUSE_PRESSED, pressedHandler);

        showDelayTransition.setOnFinished(e -> {
            if (targetNode.getScene() != null && targetNode.getScene().getWindow() != null) {
                fadeOut.stop();
                popup.show(targetNode.getScene().getWindow(), mouseX + 5, mouseY);
                fadeIn.playFromStart();
                showDurationTransition.playFromStart();
            }
        });
    }

    public void uninstall() {
        if (attachedNode != null) {
            hideTooltip();
            attachedNode.removeEventHandler(MouseEvent.MOUSE_ENTERED, enteredHandler);
            attachedNode.removeEventHandler(MouseEvent.MOUSE_EXITED, exitedHandler);
            attachedNode.removeEventHandler(MouseEvent.MOUSE_PRESSED, pressedHandler);

            attachedNode = null;
            enteredHandler = null;
            exitedHandler = null;
            pressedHandler = null;
        }
    }
}