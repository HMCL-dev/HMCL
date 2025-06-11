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
import com.jfoenix.controls.JFXPopup;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.ChunkBaseApp;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.time.Instant;

import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.formatDateTime;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class WorldListItemSkin extends SkinBase<WorldListItem> {

    private final BorderPane root;

    public WorldListItemSkin(WorldListItem skinnable) {
        super(skinnable);

        World world = skinnable.getWorld();

        root = new BorderPane();
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
            item.setMouseTransparent(true);
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

            JFXButton btnMore = new JFXButton();
            right.getChildren().add(btnMore);
            btnMore.getStyleClass().add("toggle-icon4");
            btnMore.setGraphic(SVG.MORE_VERT.createIcon(Theme.blackFill(), -1));
            btnMore.setOnAction(event -> showPopupMenu(JFXPopup.PopupHPosition.RIGHT, 0, root.getHeight()));
        }

        RipplerContainer container = new RipplerContainer(root);
        container.setOnMouseClicked(event -> {
            if (event.getClickCount() != 1)
                return;

            if (event.getButton() == MouseButton.PRIMARY)
                skinnable.showManagePage();
            else if (event.getButton() == MouseButton.SECONDARY)
                showPopupMenu(JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
        });

        getChildren().setAll(container);
    }

    // Popup Menu

    public void showPopupMenu(JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
        PopupMenu popupMenu = new PopupMenu();
        JFXPopup popup = new JFXPopup(popupMenu);

        WorldListItem item = getSkinnable();
        World world = item.getWorld();

        popupMenu.getContent().addAll(
                new IconedMenuItem(SVG.SETTINGS, i18n("world.manage.button"), item::showManagePage, popup));

        if (ChunkBaseApp.isSupported(world)) {
            popupMenu.getContent().addAll(
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.EXPLORE, i18n("world.chunkbase.seed_map"), () -> ChunkBaseApp.openSeedMap(world), popup),
                    new IconedMenuItem(SVG.VISIBILITY, i18n("world.chunkbase.stronghold"), () -> ChunkBaseApp.openStrongholdFinder(world), popup),
                    new IconedMenuItem(SVG.FORT, i18n("world.chunkbase.nether_fortress"), () -> ChunkBaseApp.openNetherFortressFinder(world), popup)
            );

            if (GameVersionNumber.compare(world.getGameVersion(), "1.13") >= 0) {
                popupMenu.getContent().add(new IconedMenuItem(SVG.LOCATION_CITY, i18n("world.chunkbase.end_city"),
                        () -> ChunkBaseApp.openEndCityFinder(world), popup));
            }
        }

        popupMenu.getContent().addAll(
                new MenuSeparator(),
                new IconedMenuItem(SVG.OUTPUT, i18n("world.export"), item::export, popup),
                new IconedMenuItem(SVG.FOLDER_OPEN, i18n("folder.world"), item::reveal, popup));

        popup.show(root, JFXPopup.PopupVPosition.TOP, hPosition, initOffsetX, initOffsetY);
    }
}
