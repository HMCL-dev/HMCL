/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions.server;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;

import java.nio.file.Path;
import java.util.List;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.ui.versions.server.ServerListPage.readServersFromDat;
import static org.jackhuang.hmcl.ui.versions.server.ServerListPage.saveServerToDat;
import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ServerListItem extends Control {
    final ServerDataHolder serverDataHolder;
    private final ServerListPage parent;

    public ServerListItem(ServerListPage parent, ServerDataHolder serverDataHolder) {
        this.serverDataHolder = serverDataHolder;
        this.parent = parent;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ServerListItemSkin();
    }

    public void showPopupMenu(JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
        PopupMenu popupMenu = new PopupMenu();
        JFXPopup popup = new JFXPopup(popupMenu);

        IconedMenuItem copyToInstance = new IconedMenuItem(SVG.CONTENT_COPY, i18n("servers.manage.copy.to.instance"), () -> {
            Task.runAsync(() -> {
                Path datFilePath = parent.profile.getRepository().getServersDatFilePath(parent.version);
                List<ServerData> dataList = readServersFromDat(datFilePath);
                dataList.add(serverDataHolder.serverData);
                saveServerToDat(datFilePath, dataList);
                Task.runAsync(Schedulers.javafx(), parent::refresh).start();
            }).start();
        }, popup);

        copyToInstance.setDisable(parent.profile.getRepository().getServersDatFilePath(parent.version).equals(serverDataHolder.serverDatPath));
        popupMenu.getContent().addAll(
                new IconedMenuItem(SVG.CONTENT_COPY, i18n("servers.manage.copy.server.ip"), () ->
                        FXUtils.copyText(serverDataHolder.serverData.ip, i18n("servers.manage.copy.server.ip.ok.toast")), popup),
                copyToInstance,
                new MenuSeparator(),
                new IconedMenuItem(SVG.DELETE, i18n("servers.manage.delete"), this::delete, popup)
        );


        JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
        popup.show(this, vPosition, hPosition, initOffsetX, vPosition == JFXPopup.PopupVPosition.TOP ? initOffsetY : -initOffsetY);
    }

    public void delete() {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("server.delete"),
                () -> Task.runAsync(() -> {
                    List<ServerData> dataList = readServersFromDat(serverDataHolder.serverDatPath);
                    dataList.remove(serverDataHolder.serverData);
                    saveServerToDat(serverDataHolder.serverDatPath, dataList);
                    Task.runAsync(Schedulers.javafx(), parent::refresh).start();
                }).start(),
                null
        );
    }

    private class ServerListItemSkin extends SkinBase<ServerListItem> {
        protected ServerListItemSkin() {
            super(ServerListItem.this);

            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            {
                StackPane left = new StackPane();
                root.setLeft(left);
                left.setPadding(new Insets(0, 8, 0, 0));

                ImageView imageView = new ImageView();
                left.getChildren().add(imageView);
                FXUtils.limitSize(imageView, 32, 32);
                imageView.setImage(serverDataHolder.serverData.iconImage.get() == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : serverDataHolder.serverData.iconImage.get());
            }

            {
                TwoLineListItem item = new TwoLineListItem();
                root.setCenter(item);
                item.setMouseTransparent(true);
                if (serverDataHolder.serverData.name != null)
                    item.setTitle(parseColorEscapes(serverDataHolder.serverData.name));
                item.setSubtitle(serverDataHolder.serverData.ip);

                if (serverDataHolder.serverData.hidden) {
                    item.addTag(i18n("server.tag.hide"));
                }

                if (serverDataHolder.profile.equals(parent.profile) && serverDataHolder.holdInstances.contains(parent.version)) {
                    // current instance holds this server data.
                    item.addTag(i18n("server.tag.hold.current"));
                } else {
                    for (String holdInstance : serverDataHolder.holdInstances) {
                        item.addTag(holdInstance);
                    }
                }
            }


            {
                HBox right = new HBox(8);
                root.setRight(right);
                right.setAlignment(Pos.CENTER_RIGHT);

                JFXButton btnMore = new JFXButton();
                right.getChildren().add(btnMore);
                btnMore.getStyleClass().add("toggle-icon4");
                btnMore.setGraphic(SVG.MORE_VERT.createIcon());
                btnMore.setOnAction(event -> showPopupMenu(JFXPopup.PopupHPosition.RIGHT, 0, root.getHeight()));
            }

            RipplerContainer container = new RipplerContainer(root);

            container.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.SECONDARY)
                    showPopupMenu(JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
            });
            getChildren().setAll(container);
        }
    }
}
