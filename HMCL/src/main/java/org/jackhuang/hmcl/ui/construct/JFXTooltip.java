/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

