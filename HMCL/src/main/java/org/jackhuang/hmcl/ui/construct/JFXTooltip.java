package org.jackhuang.hmcl.ui.construct;

import javafx.animation.FadeTransition;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

public class JFXTooltip extends Tooltip {
    //https://api.flutter.dev/flutter/material/Tooltip-class.html
    private static final double FADE_IN_MS = 150;
    private static final double FADE_OUT_MS = 75;

    public JFXTooltip() {
        this("");
    }

    public JFXTooltip(String text) {
        super(text);
        getScene().getRoot().setOpacity(0.0);
        showingProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                FadeTransition fadeIn;
                fadeIn = new FadeTransition(Duration.millis(FADE_IN_MS), getScene().getRoot());
                fadeIn.setToValue(1.0);
                fadeIn.play();
            }
        });
    }

    @Override
    public void hide() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_OUT_MS), getScene().getRoot());
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> super.hide());
        fadeOut.play();
    }
}

