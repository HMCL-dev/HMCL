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
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public final class VersionItem extends StackPane {
    @FXML
    private Pane icon;
    @FXML
    private VBox content;
    @FXML
    private StackPane header;
    @FXML
    private StackPane body;
    @FXML
    private JFXButton btnDelete;
    @FXML
    private JFXButton btnSettings;
    @FXML
    private JFXButton btnLaunch;
    @FXML
    private Label lblVersionName;
    @FXML
    private Label lblGameVersion;
    @FXML
    private ImageView iconView;

    public VersionItem() {
        FXUtils.loadFXML(this, "/assets/fxml/version-item.fxml");
        FXUtils.limitWidth(this, 160);
        FXUtils.limitHeight(this, 156);
        setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 5.0, 0.12, -1.0, 1.0));
        btnSettings.setGraphic(SVG.gear("black", 15, 15));
        btnDelete.setGraphic(SVG.delete("black", 15, 15));
        btnLaunch.setGraphic(SVG.launch("black", 15, 15));

        icon.translateYProperty().bind(Bindings.createDoubleBinding(() -> header.getBoundsInParent().getHeight() - icon.getHeight() / 2 - 16, header.boundsInParentProperty(), icon.heightProperty()));
        FXUtils.limitSize(iconView, 32, 32);
    }

    public void setVersionName(String versionName) {
        lblVersionName.setText(versionName);
    }

    public void setGameVersion(String gameVersion) {
        lblGameVersion.setText(gameVersion);
    }

    public void setImage(Image image) {
        iconView.setImage(image);
    }

    public void setOnSettingsButtonClicked(EventHandler<? super MouseEvent> handler) {
        btnSettings.setOnMouseClicked(handler);
    }

    public void setOnDeleteButtonClicked(EventHandler<? super MouseEvent> handler) {
        btnDelete.setOnMouseClicked(handler);
    }

    public void setOnLaunchButtonClicked(EventHandler<? super MouseEvent> handler) {
        btnLaunch.setOnMouseClicked(handler);
    }
}
