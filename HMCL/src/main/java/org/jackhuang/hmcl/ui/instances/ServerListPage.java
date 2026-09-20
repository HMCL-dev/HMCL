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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.glavo.nbt.io.MinecraftEdition;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ServerListPage extends ListPageBase<ServerListPage.ServerHolder> {
    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);
    private final BooleanProperty showHide = new SimpleBooleanProperty(this, "showHide", false);
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    private @Nullable HMCLGameInstance gameInstance;
    private List<ServerHolder> serverHolders;

    private int refreshCount = 0;

    public ServerListPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");

        showAll.addListener(e -> updateServerList());
        showHide.addListener(e -> updateServerList());

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ServerListPageSkin();
    }

    public void launchAndEnterServer(Server server) {
        if (gameInstance != null) {
            Instances.launchAndEnterServer(gameInstance, server.ip);
        }
    }

    public void copyServerIp(Server server) {
        FXUtils.copyText(server.ip, i18n("servers.manage.copy.server.ip.ok.toast"));
    }

    public void delete(Server server) {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("server.delete"),
                () -> Task.runAsync(() -> {
                    ServerHolder holder = server.holder;
                    if (holder == null) return;

                    Task.runAsync(Schedulers.io(), () -> {
                        List<Server> servers = Server.getServers(holder.fromServersDatFilePath);
                        servers.remove(server);
                        Server.saveServerToDat(holder.fromServersDatFilePath, servers);
                    }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                        if (exception != null)
                            LOG.warning("Failed to save server data.", exception);

                        refresh();
                    }).start();

                }).start(),

                null
        );
    }

    public void copyToInstance(Server server) {
        ServerHolder holder = server.holder;
        if (holder == null) return;
        if (gameInstance == null) return;

        Task.runAsync(Schedulers.io(), () -> {
            List<Server> servers = Server.getServers(gameInstance.getServersDatFilePath());
            servers.add(server);
            Server.saveServerToDat(gameInstance.getServersDatFilePath(), servers);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception != null)
                LOG.warning("Failed to save server data.", exception);

            refresh();
        }).start();
    }

    public void generateLaunchScript(Server server) {
        if (gameInstance != null) {
            Instances.generateLaunchScriptForQuickConnectServer(gameInstance, server.ip);
        }
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        refresh();
    }

    private void updateServerList() {
        if (serverHolders == null || gameInstance == null) {
            getItems().clear();
            return;
        }

        var stream = serverHolders.stream();
        if (!showAll.get()) {
            stream = stream.filter(holder -> holder.fromServersDatFilePath.equals(gameInstance.getServersDatFilePath()));
        }
        if (!showHide.get()) {
            stream = stream.filter(holder -> !holder.server.hidden);
        }

        getItems().setAll(stream.toList());
    }

    private void refresh() {
        if (gameInstance == null)
            return;

        int currentRefresh = ++refreshCount;
        HMCLGameInstance gameInstance = this.gameInstance;

        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            Map<Path, List<ServerHolder>> pathListMap = new LinkedHashMap<>();
            for (HMCLGameInstance instance : gameInstance.getRepository().getSnapshot().getInstances()) {
                if (pathListMap.containsKey(instance.getServersDatFilePath())) {
                    for (ServerHolder holder : pathListMap.get(instance.getServersDatFilePath())) {
                        holder.holdInstances.add(instance);
                    }
                } else {
                    ArrayList<ServerHolder> holders = new ArrayList<>();
                    pathListMap.put(instance.getServersDatFilePath(), holders);

                    List<Server> parsedServers = Server.getServers(instance.getServersDatFilePath());
                    for (int index = 0; index < parsedServers.size(); index++) {
                        Server server = parsedServers.get(index);
                        ServerHolder holder = new ServerHolder(instance.getServersDatFilePath(), index, server);
                        server.holder = holder;
                        holder.holdInstances.add(instance);

                        holders.add(holder);
                    }
                }
            }


            return pathListMap.values().stream().flatMap(Collection::stream).toList();
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (refreshCount != currentRefresh) {
                // A newer refresh task is running, discard this result
                return;
            }

            serverHolders = result;
            updateServerList();

            if (exception != null)
                LOG.warning("Failed to load server list page", exception);

            setLoading(false);
        }).start();
    }

    private static final class ServerListCell extends ListCell<ServerListPage.ServerHolder> {

        private final ServerListPage page;

        private final RipplerContainer graphic;
        private final ImageContainer imageView;
        private final Tooltip leftTooltip;
        private final TwoLineListItem content;
        private final JFXButton btnLaunch;

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

                this.imageView = new ImageContainer(32);
                left.getChildren().add(imageView);
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

                btnLaunch = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH);
                btnLaunch.managedProperty().bind(btnLaunch.visibleProperty());
                right.getChildren().add(btnLaunch);
                FXUtils.installFastTooltip(btnLaunch, i18n("instance.launch"));
                btnLaunch.setOnAction(event -> {
                    ServerHolder holder = getItem();
                    if (holder != null)
                        page.launchAndEnterServer(holder.server);
                });

                JFXButton btnMore = FXUtils.newToggleButton4(SVG.MORE_VERT);
                right.getChildren().add(btnMore);
                btnMore.setOnAction(event -> {
                    ServerHolder holder = getItem();
                    if (holder != null)
                        showPopupMenu(holder, JFXPopup.PopupHPosition.RIGHT, 0, root.getHeight());
                });
            }

            this.graphic = new RipplerContainer(root);
            graphic.setOnMouseClicked(event -> {
                if (event.getClickCount() != 1)
                    return;

                ServerHolder holder = getItem();
                if (holder == null)
                    return;

                if (event.getButton() == MouseButton.SECONDARY)
                    showPopupMenu(holder, JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
            });
        }

        @Override
        protected void updateItem(ServerHolder holder, boolean empty) {
            ServerHolder oldHolder = getItem();
            boolean oldEmpty = isEmpty();

            super.updateItem(holder, empty);

            if (oldHolder == holder && oldEmpty == empty) return;

            this.graphic.releaseRippleImmediately();
            this.content.getTags().clear();

            if (empty || holder == null) {
                setGraphic(null);
                imageView.setImage(null);
                leftTooltip.setText("");
                content.setTitle("");
                content.setSubtitle("");
            } else {
                imageView.setImage(holder.server.iconImage == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : holder.server.iconImage);
                leftTooltip.setText(holder.server.ip);
                content.setTitle(holder.server.name != null ? parseColorEscapes(holder.server.name) : "");
                content.setSubtitle(holder.server.ip);

                if (holder.server.hidden) {
                    content.addTag(i18n("server.tag.hide"));
                }

                if (holder.holdInstances.contains(page.gameInstance)) {
                    content.addTag(i18n("server.tag.hold.current"));
                    holder.holdInstances.stream()
                            .filter(e -> !e.equals(page.gameInstance))
                            .map(gameInstance -> gameInstance.getId().id())
                            .forEach(content::addTag);
                } else {
                    holder.holdInstances.stream()
                            .map(gameInstance -> gameInstance.getId().id())
                            .forEach(content::addTag);
                }

                setGraphic(graphic);
            }
        }

        // Popup Menu

        public void showPopupMenu(ServerListPage.ServerHolder holder, JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
            PopupMenu popupMenu = new PopupMenu();
            JFXPopup popup = new JFXPopup(popupMenu);

            IconedMenuItem copyToInstanceMEnuItem = new IconedMenuItem(SVG.CONTENT_COPY, i18n("servers.manage.copy.to.instance"), () -> page.copyToInstance(holder.server), popup);
            popupMenu.getContent().addAll(
                    new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch_and_connect_server"), () ->
                            page.launchAndEnterServer(holder.server), popup
                    ),
                    new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), () ->
                            page.generateLaunchScript(holder.server), popup
                    ),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.CONTENT_COPY, i18n("servers.manage.copy.server.ip"), () ->
                            page.copyServerIp(holder.server), popup
                    ),
                    new MenuSeparator(),
                    copyToInstanceMEnuItem,
                    new IconedMenuItem(SVG.DELETE_FOREVER, i18n("server.delete"), () ->
                            page.delete(holder.server), popup
                    )
            );
            if (page.gameInstance != null) {
                copyToInstanceMEnuItem.setDisable(getItem().fromServersDatFilePath.equals(page.gameInstance.getServersDatFilePath()));
            }

            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
            popup.show(this, vPosition, hPosition, initOffsetX, vPosition == JFXPopup.PopupVPosition.TOP ? initOffsetY : -initOffsetY);
        }
    }

    public static final class ServerHolder {
        final @NotNull List<GameInstance> holdInstances = new ArrayList<>();
        final @NotNull Path fromServersDatFilePath;
        final int inDatPathSlot;
        final @NotNull Server server;

        public ServerHolder(@NotNull Path fromServersDatFilePath, int inDatPathSlot, @NotNull Server server) {
            this.fromServersDatFilePath = fromServersDatFilePath;
            this.inDatPathSlot = inDatPathSlot;
            this.server = server;
        }
    }

    public static final class Server {
        final boolean acceptTextures;
        final boolean hidden;
        final @Nullable String icon;
        final @Nullable String ip;
        final @Nullable String name;

        transient final Image iconImage;
        transient ServerHolder holder;

        public Server(
                boolean acceptTextures,
                boolean hidden,
                @Nullable String icon,
                @Nullable String ip,
                @Nullable String name
        ) {
            this.acceptTextures = acceptTextures;
            this.hidden = hidden;
            this.icon = icon;
            this.ip = ip;
            this.name = name;

            this.iconImage = parseImage(icon);
        }

        public static Image parseImage(String imageBase64String) {
            if (imageBase64String == null || imageBase64String.isEmpty()) {
                return null;
            }
            try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(imageBase64String))) {
                // png format.
                return FXUtils.loadImage(bais, "icon.png", 0, 0, true, true);
            } catch (Exception e) {
                LOG.warning("Failed to decode server icon", e);
                return null;
            }
        }

        public static List<Server> getServers(@NotNull Path serversDatFilePath) {
            List<Server> set = new ArrayList<>();
            if (Files.exists(serversDatFilePath)) {
                try {
                    CompoundTag tags = (CompoundTag) NBTCodec.of(MinecraftEdition.JAVA_EDITION).readTag(serversDatFilePath);
                    Tag serversTag = tags.get("servers");
                    if (serversTag instanceof ListTag<?> st) {
                        for (Tag tag : st) {
                            if (tag instanceof CompoundTag cTag) {
                                set.add(new Server(
                                        cTag.get("acceptTextures") instanceof ByteTag bt && bt.getValue() != 0,
                                        cTag.get("hidden") instanceof ByteTag bt && bt.getValue() != 0,
                                        cTag.get("icon") instanceof StringTag stg ? stg.getValue() : null,
                                        cTag.get("ip") instanceof StringTag stg ? stg.getValue() : null,
                                        cTag.get("name") instanceof StringTag stg ? stg.getValue() : null
                                ));
                            }
                        }
                    }
                } catch (IOException e) {
                    LOG.error("Failed to read servers.dat file.", e);
                }
            }
            return set;
        }

        public static void saveServerToDat(Path datFile, List<Server> saveServerData) {
            ListTag<CompoundTag> tag = new ListTag<>();
            for (Server server : saveServerData) {
                CompoundTag serverTag = new CompoundTag();
                server.writeToCompoundTag(serverTag);
                tag.addTag(serverTag);
            }

            CompoundTag root = new CompoundTag();
            root.addTag("servers", tag);

            try (var output = Files.newOutputStream(datFile)) {
                NBTCodec.of(MinecraftEdition.JAVA_EDITION).writeTag(output, root);
            } catch (IOException e) {
                LOG.error("Failed to write servers.dat file.", e);
            }
        }

        public void writeToCompoundTag(CompoundTag tag) {
            tag.addByte("acceptTextures", (byte) (acceptTextures ? 1 : 0));
            tag.addByte("hidden", (byte) (hidden ? 1 : 0));
            if (icon != null) tag.addString("icon", icon);
            if (ip != null) tag.addString("ip", ip);
            if (name != null) tag.addString("name", name);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Server that = (Server) o;
            return Objects.equals(ip, that.ip) &&
                    Objects.equals(name, that.name) &&
                    Objects.equals(acceptTextures, that.acceptTextures) &&
                    Objects.equals(hidden, that.hidden) &&
                    Objects.equals(icon, that.icon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, name);
        }

        @Override
        public String toString() {
            return "Server{" +
                    "acceptTextures=" + acceptTextures +
                    ", hidden=" + hidden +
                    ", icon='" + icon + '\'' +
                    ", ip='" + ip + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    private final class ServerListPageSkin extends ToolbarListPageSkin<ServerListPage.ServerHolder, ServerListPage> {

        ServerListPageSkin() {
            super(ServerListPage.this);

            StackPane placeholderContainer = new StackPane();
            placeholderContainer.getStyleClass().add("notice-pane");
            Label placeholderLabel = new Label(i18n("server.empty"));
            placeholderContainer.getChildren().add(placeholderLabel);
            listView.setPlaceholder(placeholderContainer);
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
        protected ListCell<ServerListPage.ServerHolder> createListCell(JFXListView<ServerListPage.ServerHolder> listView) {
            return new ServerListCell(getSkinnable());
        }
    }
}
