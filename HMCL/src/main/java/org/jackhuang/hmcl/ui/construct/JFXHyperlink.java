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

import com.jfoenix.controls.JFXButton;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

public final class JFXHyperlink extends StackPane {
    private final JFXButton jfxButton = new JFXButton();

    public JFXHyperlink(String text) {
        getStyleClass().add("jfx-hyperlink");

        jfxButton.setText(text);
        jfxButton.setGraphic(SVG.OPEN_IN_NEW.createIcon(16));

        getChildren().add(jfxButton);
    }

    public void setExternalLink(String externalLink) {
        jfxButton.setOnAction(e -> FXUtils.openLink(externalLink));
    }

    public void setOnAction(EventHandler<ActionEvent> value) {
        jfxButton.setOnAction(value);
    }
}

