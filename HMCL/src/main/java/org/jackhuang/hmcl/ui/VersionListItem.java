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

import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public final class VersionListItem extends StackPane {
    @FXML
    private StackPane imageViewContainer;
    @FXML
    private Label lblVersionName;
    @FXML
    private Label lblGameVersion;
    @FXML
    private ImageView imageView;
    private Runnable handler;

    public VersionListItem(String versionName) {
        this(versionName, "");
    }

    public VersionListItem(String versionName, String gameVersion) {
        FXUtils.loadFXML(this, "/assets/fxml/version-list-item.fxml");

        lblVersionName.setText(versionName);
        lblGameVersion.setText(gameVersion);

        FXUtils.limitSize(imageView, 32, 32);
        FXUtils.limitWidth(imageViewContainer, 32);
        FXUtils.limitHeight(imageViewContainer, 32);
    }

    public void onSettings() {
        handler.run();
    }

    public void setOnSettingsButtonClicked(Runnable handler) {
        this.handler = handler;
    }

    public void setVersionName(String versionName) {
        lblVersionName.setText(versionName);
    }

    public void setGameVersion(String gameVersion) {
        lblGameVersion.setText(gameVersion);
    }

    public void setImage(Image image, Rectangle2D viewport) {
        imageView.setImage(image);
        imageView.setViewport(viewport);
    }
}
