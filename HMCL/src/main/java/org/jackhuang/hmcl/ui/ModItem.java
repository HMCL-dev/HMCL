/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.mod.ModInfo;

import java.util.function.Consumer;

public final class ModItem extends BorderPane {
    private final Label lblModFileName = new Label();
    private final Label lblModAuthor = new Label();
    private final JFXCheckBox chkEnabled = new JFXCheckBox();

    public ModItem(ModInfo info, Consumer<ModItem> deleteCallback) {
        lblModFileName.setStyle("-fx-font-size: 15;");
        lblModAuthor.setStyle("-fx-font-size: 10;");
        BorderPane.setAlignment(chkEnabled, Pos.CENTER);
        setLeft(chkEnabled);

        VBox center = new VBox();
        BorderPane.setAlignment(center, Pos.CENTER);
        center.getChildren().addAll(lblModFileName, lblModAuthor);
        setCenter(center);

        JFXButton right = new JFXButton();
        right.setOnMouseClicked(e -> deleteCallback.accept(this));
        right.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(right, Pos.CENTER);
        right.setGraphic(SVG.close("black", 15, 15));
        setRight(right);

        setStyle("-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;");
        JFXDepthManager.setDepth(this, 1);
        lblModFileName.setText(info.getFileName());
        lblModAuthor.setText(info.getName() + ", " + Main.i18n("archive.version") + ": " + info.getVersion() + ", " + Main.i18n("archive.game_version") + ": " + info.getGameVersion() + ", " + Main.i18n("archive.author") + ": " + info.getAuthors());
        chkEnabled.setSelected(info.isActive());
        chkEnabled.selectedProperty().addListener((a, b, newValue) -> {
            info.activeProperty().set(newValue);
        });
    }
}
