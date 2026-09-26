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
package org.jackhuang.hmcl.ui.instances.server;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Subscription;
import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.server.Server;
import org.jackhuang.hmcl.server.ServerStatus;
import org.jackhuang.hmcl.server.ServerStatusGetter;
import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
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

    public void launchAndEnterServer(ServerHolder holder) {
        if (gameInstance != null) {
            Instances.launchAndEnterServer(gameInstance, holder.server.getIp());
        }
    }

    public void showServerStatus(ServerHolder holder) {
        if (gameInstance != null) {
            runInFX(() -> Controllers.dialog(new ServerStatusPane(holder.server)));
        }
    }

    public void copyServerIp(ServerHolder holder) {
        FXUtils.copyText(holder.server.getIp(), i18n("servers.manage.copy.server.ip.ok.toast"));
    }

    public void delete(ServerHolder holder) {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("server.delete"),
                () -> Task.runAsync(Schedulers.io(), () -> {
                    List<Server> servers = holder.cachedServers;
                    servers.remove(holder.inDatPathSlot);
                    Server.saveToServersDat(servers, holder.fromServersDatFilePath);
                }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception != null)
                        LOG.warning("Failed to save server data.", exception);

                    refresh();
                }).start(),

                null
        );
    }

    public void copyToInstance(ServerHolder holder) {
        addServer(holder.server);
    }

    public void addServer(Server server) {
        if (gameInstance == null) return;

        Task.runAsync(Schedulers.io(), () -> {
            Path serversDatFilePath = gameInstance.getServersDatFilePath();
            List<Server> servers;
            if (Files.exists(serversDatFilePath)) {
                servers = Server.loadFromServersDat(serversDatFilePath);
            } else {
                servers = new ArrayList<>();
            }
            servers.add(server);
            Server.saveToServersDat(servers, serversDatFilePath);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception != null)
                LOG.warning("Failed to save server data.", exception);

            refresh();
        }).start();
    }

    public void generateLaunchScript(ServerHolder holder) {
        if (gameInstance != null) {
            Instances.generateLaunchScriptForQuickConnectServer(gameInstance, holder.server.getIp());
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
            stream = stream.filter(holder -> !holder.server.isHidden());
        }

        getItems().setAll(stream.toList());
    }

    private void addServer() {
        runInFX(() -> Controllers.dialog(new AddServerPane(this::addServer)));
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

                    if (Files.exists(instance.getServersDatFilePath())) {
                        List<Server> parsedServers = Server.loadFromServersDat(instance.getServersDatFilePath());
                        for (int index = 0; index < parsedServers.size(); index++) {
                            Server server = parsedServers.get(index);
                            ServerHolder holder = new ServerHolder(instance.getServersDatFilePath(), parsedServers, index, IconedServer.pack(server));
                            holder.holdInstances.add(instance);

                            holders.add(holder);
                        }
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
        private final ImageContainer serverIcon;
        private final TwoLineListItem content;
        private final ServerNetworkLatencyPane serverNetworkLatencyPane;

        private Subscription serverNetworkLatencyValueSubscription;

        public ServerListCell(ServerListPage page) {
            this.page = page;

            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            // server icon
            {
                StackPane left = new StackPane();
                root.setLeft(left);
                left.setPadding(new Insets(0, 8, 0, 0));

                this.serverIcon = new ImageContainer(32);
                left.getChildren().add(serverIcon);
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

                StackPane statusPane = new StackPane();
                JFXButton statusBtn = FXUtils.newToggleButton4(SVG.NONE);
                statusBtn.managedProperty().bind(statusBtn.visibleProperty());
                statusBtn.setOnAction(event -> {
                    ServerHolder holder = getItem();
                    if (holder != null)
                        page.showServerStatus(holder);
                });
                serverNetworkLatencyPane = new ServerNetworkLatencyPane();

                statusPane.getChildren().add(serverNetworkLatencyPane);
                statusPane.getChildren().add(statusBtn);
                right.getChildren().add(statusPane);

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

            if (serverNetworkLatencyValueSubscription != null) {
                serverNetworkLatencyValueSubscription.unsubscribe();
                serverNetworkLatencyValueSubscription = null;
            }

            if (oldHolder == holder && oldEmpty == empty) return;

            this.graphic.releaseRippleImmediately();
            this.content.getTags().clear();

            if (empty || holder == null) {
                setGraphic(null);
                serverIcon.setImage(null);
                content.setTitle("");
                content.setSubtitle("");
                serverNetworkLatencyPane.error();
            } else {
                serverIcon.setImage(holder.server.iconImage);
                content.setTitle(holder.server.getName() != null ? parseColorEscapes(holder.server.getName()) : "");
                content.setSubtitle(holder.server.getIp());

                if (holder.server.isHidden()) {
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

                holder.startGetServerStatusAsync();
                applyServerStatusResult(holder, holder.serverStatusResultWrapper.getReadOnlyProperty().get());
                serverNetworkLatencyValueSubscription = holder.serverStatusResultWrapper.subscribe(result -> applyServerStatusResult(holder, result));
            }
        }

        private void applyServerStatusResult(ServerHolder holder, ServerStatusResult result) {
            if (result == null) {
                serverNetworkLatencyPane.ping();
                return;
            }
            ServerStatus serverStatus = result.getIfSuccess();
            if (serverStatus == null) {
                serverNetworkLatencyPane.error();
            } else {
                serverNetworkLatencyPane.pong(serverStatus.networkLatency());

                // update latest server icon
                serverIcon.setImage(IconedServer.parseImageOrDefault(serverStatus.favicon()));
                if (!Objects.equals(serverStatus.favicon(), holder.server.getIcon())) {
                    // save latest icon
                    Task.runAsync(Schedulers.io(), () -> {
                        try {
                            IconedServer newServer = holder.server.withIcon(serverStatus.favicon());
                            holder.cachedServers.set(holder.inDatPathSlot, newServer);
                            Server.saveToServersDat(holder.cachedServers, holder.fromServersDatFilePath);
                        } catch (Exception e) {
                            LOG.error("Failed to save servers dat", e);
                        }
                    }).start();
                }
            }
        }

        // Popup Menu

        public void showPopupMenu(ServerListPage.ServerHolder holder, JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
            PopupMenu popupMenu = new PopupMenu();
            JFXPopup popup = new JFXPopup(popupMenu);

            IconedMenuItem copyToInstanceMEnuItem = new IconedMenuItem(SVG.CONTENT_COPY, i18n("servers.manage.copy.to.instance"), () -> page.copyToInstance(holder), popup);
            popupMenu.getContent().addAll(
                    new IconedMenuItem(SVG.SERVER_SIGNAL_FULL, i18n("servers.manager.status"), () ->
                            page.showServerStatus(holder), popup
                    ),
                    new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch_and_connect_server"), () ->
                            page.launchAndEnterServer(holder), popup
                    ),
                    new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), () ->
                            page.generateLaunchScript(holder), popup
                    ),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.CONTENT_COPY, i18n("servers.manage.copy.server.ip"), () ->
                            page.copyServerIp(holder), popup
                    ),
                    new MenuSeparator(),
                    copyToInstanceMEnuItem,
                    new IconedMenuItem(SVG.DELETE_FOREVER, i18n("server.delete"), () ->
                            page.delete(holder), popup
                    )
            );
            if (page.gameInstance != null) {
                copyToInstanceMEnuItem.setDisable(getItem().fromServersDatFilePath.equals(page.gameInstance.getServersDatFilePath()));
            }

            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
            popup.show(this, vPosition, hPosition, initOffsetX, vPosition == JFXPopup.PopupVPosition.TOP ? initOffsetY : -initOffsetY);
        }
    }

    public static class IconedServer extends Server {
        final Image iconImage;

        public IconedServer(boolean acceptTextures, boolean hidden, @Nullable String icon, @Nullable String ip, @Nullable String name) {
            super(acceptTextures, hidden, icon, ip, name);

            iconImage = parseImageOrDefault(icon);
        }

        public IconedServer withIcon(@Nullable String newIcon) {
            return new IconedServer(isAcceptTextures(), isHidden(), newIcon, getIp(), getName());
        }

        public static IconedServer pack(Server server) {
            if (server instanceof IconedServer) {
                return (IconedServer) server;
            }
            return new IconedServer(
                    server.isAcceptTextures(),
                    server.isHidden(),
                    server.getIcon(),
                    server.getIp(),
                    server.getName()
            );
        }

        public static @NotNull Image parseImageOrDefault(@Nullable String imageBase64String) {
            if (imageBase64String != null && !imageBase64String.isEmpty()) {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(imageBase64String))) {
                    // png format.
                    return FXUtils.loadImage(bais, "icon.png", 0, 0, true, true);
                } catch (Exception e) {
                    LOG.warning("Failed to decode server icon", e);
                }
            }
            return FXUtils.newBuiltinImage("/assets/img/unknown_server.png");
        }
    }

    public static final class ServerHolder {
        final @NotNull List<GameInstance> holdInstances = new ArrayList<>();
        final @NotNull Path fromServersDatFilePath;
        final @NotNull List<Server> cachedServers;
        final int inDatPathSlot;
        final @NotNull IconedServer server;

        final @NotNull ReadOnlyObjectWrapper<ServerStatusResult> serverStatusResultWrapper = new ReadOnlyObjectWrapper<>(null);
        private final AtomicBoolean serverStatusComputed = new AtomicBoolean(false);

        public void startGetServerStatusAsync() {
            if (serverStatusResultWrapper.get() != null) return;
            if (serverStatusComputed.compareAndSet(false, true)) {
                reGetServerStatusAsync();
            }
        }

        public void reGetServerStatusAsync() {
            Task.supplyAsync(Schedulers.io(), () -> ServerStatusGetter.getStatus(server.getIp()))
                    .whenComplete(Schedulers.javafx(), (result, ignored) -> {
                        serverStatusResultWrapper.set(result);
                    }).start();
        }

        public ServerHolder(@NotNull Path fromServersDatFilePath, @NotNull List<Server> cachedServers, int inDatPathSlot, @NotNull IconedServer server) {
            this.fromServersDatFilePath = fromServersDatFilePath;
            this.inDatPathSlot = inDatPathSlot;
            this.cachedServers = cachedServers;
            this.server = server;
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
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("servers.manager.add"), SVG.ADD, skinnable::addServer)
            );
        }

        @Override
        protected ListCell<ServerListPage.ServerHolder> createListCell(JFXListView<ServerListPage.ServerHolder> listView) {
            return new ServerListCell(getSkinnable());
        }
    }
}
