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

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.versions.VersionPage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Arrays;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ServerListPage extends ListPageBase<ServerData> implements VersionPage.VersionLoadable {
    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);
    private final BooleanProperty showHide = new SimpleBooleanProperty(this, "showHide", false);

    Profile profile;
    String version;
    private List<ServerDataHolder> serverDataHolders = Collections.emptyList();

    public ServerListPage() {
        showAll.addListener(e -> updateFilter());
        showHide.addListener(e -> updateFilter());
    }

    public static List<ServerData> readServersFromDat(Path datFile) {
        List<ServerData> set = new ArrayList<>();
        if (datFile != null && Files.exists(datFile)) {
            try {
                CompoundTag tags = NBTIO.readFile(datFile.toFile(), false, false);
                Tag serversTag = tags.get("servers");
                if (serversTag instanceof ListTag st) {
                    for (Tag tag : st) {
                        if (tag instanceof CompoundTag cTag) {
                            set.add(ServerData.fromCompoundTag(cTag));
                        }
                    }
                }
            } catch (IOException e) {
                LOG.error("Failed to read servers.dat file.", e);
            }
        }
        return set;
    }

    public static void saveServerToDat(Path datFile, List<ServerData> saveServerData) {
        ListTag tag = new ListTag("servers", CompoundTag.class);
        for (ServerData server : saveServerData) {
            CompoundTag serverTag = new CompoundTag("");
            server.writeToCompoundTag(serverTag);
            tag.add(serverTag);
        }

        CompoundTag root = new CompoundTag("");
        root.put(tag);

        try {
            NBTIO.writeFile(root, datFile.toFile(), false, false);
        } catch (IOException e) {
            LOG.error("Failed to write servers.dat file.", e);
        }
    }

    private void updateFilter() {
        if (profile == null || version == null) {
            getItems().clear();
        } else {
            getItems().setAll(serverDataHolders.stream()
                    .map(holder -> holder.serverData)
                    .filter(server -> showAll.get() || serverDataHolders.stream()
                            .filter(holder -> holder.serverData == server)
                            .anyMatch(holder -> holder.profile.equals(profile) && holder.holdInstances.contains(version)))
                    .filter(server -> showHide.get() || !server.hidden)
                    .toList()
            );
        }
    }

    @Override
    public void loadVersion(Profile profile, String id) {
        this.profile = profile;
        this.version = id;

        refresh();
    }

    void refresh() {
        if (profile == null || version == null)
            return;

        setLoading(true);

        Task.supplyAsync(Schedulers.io(), () -> {
            Map<ServerDataHolder, ServerDataHolder> map = new LinkedHashMap<>();
            for (Version version : profile.getRepository().getVersions()) {
                Path serverDat = profile.getRepository().getServersDatFilePath(version.getId());
                List<ServerData> dataList = readServersFromDat(serverDat);
                for (int i = 0; i < dataList.size(); i++) {
                    ServerDataHolder dataHolder = new ServerDataHolder(profile, serverDat, i, dataList.get(i));
                    if (!map.containsKey(dataHolder)) {
                        map.put(dataHolder, dataHolder);
                    } else {
                        dataHolder = map.get(dataHolder);
                    }
                    dataHolder.holdInstances.add(version.getId());
                }
            }
            return map.values();
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            serverDataHolders = result.stream().toList();
            updateFilter();

            if (exception != null)
                LOG.warning("Failed to load servers.", exception);

            setLoading(false);
        }).start();
    }

    public void delete(ServerData server) {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("server.delete"),
                () -> Task.runAsync(() -> {
                    ServerDataHolder holder = serverDataHolders.stream()
                            .filter(h -> h.serverData == server)
                            .findFirst()
                            .orElse(null);
                    if (holder != null) {
                        List<ServerData> dataList = readServersFromDat(holder.serverDatPath);
                        dataList.remove(server);
                        saveServerToDat(holder.serverDatPath, dataList);
                        Task.runAsync(Schedulers.javafx(), this::refresh).start();
                    }
                }).start(),
                null
        );
    }

    public void copyToInstance(ServerData server) {
        ServerDataHolder holder = serverDataHolders.stream()
                .filter(h -> h.serverData == server)
                .findFirst()
                .orElse(null);

        if (holder == null)
            return;

        Task.runAsync(() -> {
            Path datFilePath = profile.getRepository().getServersDatFilePath(version);
            List<ServerData> dataList = readServersFromDat(datFilePath);
            dataList.add(server);
            saveServerToDat(datFilePath, dataList);
            Task.runAsync(Schedulers.javafx(), this::refresh).start();
        }).start();
    }

    public void copyServerIp(ServerData server) {
        FXUtils.copyText(server.ip, i18n("servers.manage.copy.server.ip.ok.toast"));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ServerListPageSkin();
    }

    private final class ServerListPageSkin extends ToolbarListPageSkin<ServerData, ServerListPage> {

        ServerListPageSkin() {
            super(ServerListPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(ServerListPage skinnable) {
            JFXCheckBox chkShowAll = new JFXCheckBox(i18n("servers.show_all"));
            chkShowAll.selectedProperty().bindBidirectional(skinnable.showAll);

            JFXCheckBox chkShowHide = new JFXCheckBox(i18n("servers.show_hide"));
            chkShowHide.selectedProperty().bindBidirectional(skinnable.showHide);

            return Arrays.asList(
                    chkShowAll,
                    chkShowHide,
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh)
            );
        }

        @Override
        protected ListCell<ServerData> createListCell(JFXListView<ServerData> listView) {
            return new ServerListCell(getSkinnable());
        }
    }

    private static final class ServerListCell extends ListCell<ServerData> {

        private final ServerListPage page;

        private final RipplerContainer graphic;
        private final ImageView imageView;
        private final Tooltip leftTooltip;
        private final TwoLineListItem content;

        public ServerListCell(ServerListPage page) {
            this.page = page;

            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            {
                StackPane left = new StackPane();
                this.leftTooltip = new Tooltip();
                FXUtils.installSlowTooltip(left, leftTooltip);
                root.setLeft(left);
                left.setPadding(new Insets(0, 8, 0, 0));

                this.imageView = new ImageView();
                left.getChildren().add(imageView);
                FXUtils.limitSize(imageView, 32, 32);
            }

            {
                this.content = new TwoLineListItem();
                root.setCenter(content);
                content.setMouseTransparent(true);
            }

            {
                HBox right = new HBox(8);
                root.setRight(right);
                right.setAlignment(Pos.CENTER_RIGHT);

                JFXButton btnMore = FXUtils.newToggleButton4(SVG.MORE_VERT);
                right.getChildren().add(btnMore);
                btnMore.setOnAction(event -> {
                    ServerData server = getItem();
                    if (server != null)
                        showPopupMenu(server, JFXPopup.PopupHPosition.RIGHT, 0, root.getHeight());
                });
            }

            this.graphic = new RipplerContainer(root);
            graphic.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    ServerData server = getItem();
                    if (server != null)
                        showPopupMenu(server, JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
                }
            });
        }

        @Override
        protected void updateItem(ServerData server, boolean empty) {
            super.updateItem(server, empty);

            this.content.getTags().clear();

            if (empty || server == null) {
                setGraphic(null);
                imageView.setImage(null);
                leftTooltip.setText("");
                content.setTitle("");
                content.setSubtitle("");
            } else {
                imageView.setImage(server.iconImage.get() == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : server.iconImage.get());
                leftTooltip.setText(server.ip);
                content.setTitle(server.name != null ? parseColorEscapes(server.name) : "");
                content.setSubtitle(server.ip);

                if (server.hidden) {
                    content.addTag(i18n("server.tag.hide"));
                }

                ServerDataHolder holder = page.serverDataHolders.stream()
                        .filter(h -> h.serverData == server)
                        .findFirst()
                        .orElse(null);

                if (holder != null) {
                    if (holder.profile.equals(page.profile) && holder.holdInstances.contains(page.version)) {
                        // current instance holds this server data.
                        content.addTag(i18n("server.tag.hold.current"));
                        holder.holdInstances.stream().filter(e -> !e.equals(page.version)).forEach(content::addTag);
                    } else {
                        holder.holdInstances.forEach(content::addTag);
                    }
                }

                setGraphic(graphic);
            }
        }

        // Popup Menu

        public void showPopupMenu(ServerData server, JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
            PopupMenu popupMenu = new PopupMenu();
            JFXPopup popup = new JFXPopup(popupMenu);

            ServerDataHolder holder = page.serverDataHolders.stream()
                    .filter(h -> h.serverData == server)
                    .findFirst()
                    .orElse(null);

            boolean isCurrentInstancePath = holder != null && page.profile.getRepository().getServersDatFilePath(page.version).equals(holder.serverDatPath);

            IconedMenuItem copyToInstanceItem = new IconedMenuItem(SVG.CONTENT_COPY, i18n("servers.manage.copy.to.instance"), () -> page.copyToInstance(server), popup);
            copyToInstanceItem.setDisable(isCurrentInstancePath);

            popupMenu.getContent().addAll(
                    new IconedMenuItem(SVG.CONTENT_COPY, i18n("servers.manage.copy.server.ip"), () -> page.copyServerIp(server), popup),
                    copyToInstanceItem,
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.DELETE, i18n("servers.manage.delete"), () -> page.delete(server), popup)
            );

            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
            popup.show(this, vPosition, hPosition, initOffsetX, vPosition == JFXPopup.PopupVPosition.TOP ? initOffsetY : -initOffsetY);
        }
    }
}
