/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.download;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderRemoteVersion;
import org.jackhuang.hmcl.download.optifine.OptiFineRemoteVersion;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public final class VersionsPageItem extends StackPane {
    private final RemoteVersion remoteVersion;
    @FXML
    private Label lblSelfVersion;
    @FXML
    private Label lblGameVersion;
    @FXML
    private ImageView imageView;
    @FXML
    private HBox leftPane;
    @FXML
    private StackPane imageViewContainer;

    public VersionsPageItem(RemoteVersion remoteVersion) {
        this.remoteVersion = Objects.requireNonNull(remoteVersion);

        FXUtils.loadFXML(this, "/assets/fxml/download/versions-list-item.fxml");
        lblSelfVersion.setText(remoteVersion.getSelfVersion());

        if (remoteVersion instanceof GameRemoteVersion) {
            switch (remoteVersion.getVersionType()) {
                case RELEASE:
                    lblGameVersion.setText(i18n("version.game.release"));
                    imageView.setImage(new Image("/assets/img/icon.png", 32, 32, false, true));
                    break;
                case SNAPSHOT:
                    lblGameVersion.setText(i18n("version.game.snapshot"));
                    imageView.setImage(new Image("/assets/img/command.png", 32, 32, false, true));
                    break;
                default:
                    lblGameVersion.setText(i18n("version.game.old"));
                    imageView.setImage(new Image("/assets/img/grass.png", 32, 32, false, true));
                    break;
            }
        } else if (remoteVersion instanceof LiteLoaderRemoteVersion) {
            imageView.setImage(new Image("/assets/img/chicken.png", 32, 32, false, true));
            lblGameVersion.setText(remoteVersion.getGameVersion());
        } else if (remoteVersion instanceof OptiFineRemoteVersion) {
            // optifine has no icon.
            lblGameVersion.setText(remoteVersion.getGameVersion());
        } else if (remoteVersion instanceof ForgeRemoteVersion) {
            imageView.setImage(new Image("/assets/img/forge.png", 32, 32, false, true));
            lblGameVersion.setText(remoteVersion.getGameVersion());
        }

        leftPane.getChildren().remove(imageViewContainer);
    }

    public RemoteVersion getRemoteVersion() {
        return remoteVersion;
    }
}
