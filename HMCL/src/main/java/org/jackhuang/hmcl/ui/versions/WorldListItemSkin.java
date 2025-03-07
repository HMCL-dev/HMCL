/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

import java.time.Instant;

import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.formatDateTime;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class WorldListItemSkin extends SkinBase<WorldListItem> {

    public WorldListItemSkin(WorldListItem skinnable) {
        super(skinnable);

        World world = skinnable.getWorld();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("md-list-cell");
        root.setPadding(new Insets(8));

        {
            StackPane left = new StackPane();
            FXUtils.installSlowTooltip(left, world.getFile().toString());
            root.setLeft(left);
            left.setPadding(new Insets(0, 8, 0, 0));

            ImageView imageView = new ImageView();
            left.getChildren().add(imageView);
            FXUtils.limitSize(imageView, 32, 32);
            imageView.setImage(world.getIcon() == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : world.getIcon());
        }

        {
            TwoLineListItem item = new TwoLineListItem();
            root.setCenter(item);
            if (world.getWorldName() != null)
                item.setTitle(parseColorEscapes(world.getWorldName()));
            item.setSubtitle(i18n("world.datetime", formatDateTime(Instant.ofEpochMilli(world.getLastPlayed())), world.getGameVersion() == null ? i18n("message.unknown") : world.getGameVersion()));

            if (world.getGameVersion() != null)
                item.getTags().add(world.getGameVersion());
            if (world.isLocked())
                item.getTags().add(i18n("world.locked"));
        }

        {
            HBox right = new HBox(8);
            root.setRight(right);
            right.setAlignment(Pos.CENTER_RIGHT);

            JFXButton btnReveal = new JFXButton();
            right.getChildren().add(btnReveal);
            FXUtils.installFastTooltip(btnReveal, i18n("world.reveal"));
            btnReveal.getStyleClass().add("toggle-icon4");
            btnReveal.setGraphic(SVG.FOLDER_OPEN.createIcon(Theme.blackFill(), -1));
            btnReveal.setOnAction(event -> skinnable.reveal());

            JFXButton btnExport = new JFXButton();
            right.getChildren().add(btnExport);
            FXUtils.installFastTooltip(btnExport, i18n("world.export"));
            btnExport.getStyleClass().add("toggle-icon4");
            btnExport.setGraphic(SVG.OUTPUT.createIcon(Theme.blackFill(), -1));
            btnExport.setOnAction(event -> skinnable.export());

            JFXButton btnBackup = new JFXButton();
            right.getChildren().add(btnBackup);
            FXUtils.installFastTooltip(btnBackup, i18n("world.backup"));
            btnBackup.getStyleClass().add("toggle-icon4");
            btnBackup.setGraphic(SVG.ARCHIVE.createIcon(Theme.blackFill(), -1));
            btnBackup.setOnAction(event -> skinnable.showBackupPage());

            JFXButton btnDatapack = new JFXButton();
            right.getChildren().add(btnDatapack);
            FXUtils.installFastTooltip(btnDatapack, i18n("world.datapack"));
            btnDatapack.getStyleClass().add("toggle-icon4");
            btnDatapack.setGraphic(SVG.EXTENSION.createIcon(Theme.blackFill(), -1));
            btnDatapack.setOnAction(event -> skinnable.manageDatapacks());

            JFXButton btnInfo = new JFXButton();
            right.getChildren().add(btnInfo);
            FXUtils.installFastTooltip(btnInfo, i18n("world.info"));
            btnInfo.getStyleClass().add("toggle-icon4");
            btnInfo.setGraphic(SVG.INFO.createIcon(Theme.blackFill(), -1));
            btnInfo.setOnAction(event -> skinnable.showInfo());
        }

        getChildren().setAll(new RipplerContainer(root));
    }
}
