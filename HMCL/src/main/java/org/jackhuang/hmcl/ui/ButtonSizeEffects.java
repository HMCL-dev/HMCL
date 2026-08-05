/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;

public final class ButtonSizeEffects {
    private static final Duration ANIMATION_DURATION = Duration.millis(150);
    private static final double SCALE_FACTOR = 1.05;

    private ButtonSizeEffects() {

    }

    public static void hoverScale (Node node) {
        if (settings().enableButtonHoverScaleProperty().get()) {
            ScaleTransition scaleIn = new ScaleTransition(ANIMATION_DURATION, node);
            scaleIn.setToX(SCALE_FACTOR);
            scaleIn.setToY(SCALE_FACTOR);

            ScaleTransition scaleOut = new ScaleTransition(ANIMATION_DURATION, node);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);

            node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> scaleIn.playFromStart());
            node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> scaleOut.playFromStart());
        }
    }

    public static void hoverScaleToVBox (VBox vbox) {
        if (settings().enableButtonHoverScaleProperty().get()) {
            for (Node child : vbox.getChildren()) {
                hoverScale(child);
            }
        }
    }
}
