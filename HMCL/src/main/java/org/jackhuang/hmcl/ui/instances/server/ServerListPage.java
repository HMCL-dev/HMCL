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

import com.jfoenix.controls.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.Subscription;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.server.Server;
import org.jackhuang.hmcl.server.ServerStatus;
import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ServerListPage extends ListPageBase<ServerListPage.ServerListItem> {
    private static final Object serversFileHandleLockObject = new Object();
    private final Map<Path, ServerStorage> serverStorageMap = new LinkedHashMap<>();

    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);
    private final BooleanProperty showHide = new SimpleBooleanProperty(this, "showHide", false);
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    private @Nullable HMCLGameInstance gameInstance;
    private List<ServerListItem> serverListEntries = new ArrayList<>();

    private int refreshCount = 0;

    public void launchAndEnterServer(ServerListItem item) {
        if (gameInstance != null) {
            Instances.launchAndEnterServer(gameInstance, item.server.getIp());
        }
    }

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

    public void showServerStatus(ServerListItem item) {
        if (gameInstance != null) {
            runInFX(() -> Controllers.dialog(new ServerStatusPane(item.observableServerStatus, item.server)));
        }
    }

    public void editServer(ServerListItem item) {
        if (gameInstance != null) {
            runInFX(() -> Controllers.dialog(new EditServerPane(EditServerPane.Type.EDIT, item.server, (server) -> {
                if (!server.equals(item.server)) {
                    Task.supplyAsync(Schedulers.io(), () -> {
                        ServerStorage storage = item.storageEntry.rootStorage();
                        return storage.updateEntry(item.storageEntry, oldValue -> oldValue
                                .withIpAndName(server.getIp(), server.getName())
                                .withPackStatus(server.getServerPackStatus()));
                    }).whenComplete(Schedulers.javafx(), (updated, exception) -> {
                        if (exception != null)
                            LOG.warning("Failed to save server data.", exception);

                        if (updated) {
                            int index = serverListEntries.indexOf(item);
                            if (index != -1) {
                                serverListEntries.set(index, new ServerListItem(item.storageEntry));
                            }
                            updateServerList();
                        }


                    }).start();
                }
            })));
        }
    }

    public void copyServerIp(ServerListItem item) {
        FXUtils.copyText(item.server.getIp(), i18n("server.manage.copy.server.ip.ok.toast"));
    }

    private void delete(ServerListItem... items) {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("server.delete"),
                () -> Task.supplyAsync(Schedulers.io(), () -> {
                    boolean result = false;
                    for (ServerListItem item : items) {
                        try {
                            item.storageEntry.delete();
                            serverListEntries.remove(item);
                            result = true;
                        } catch (IOException ignored) {
                        }
                    }
                    return result;
                }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (result) {
                        updateServerList();
                    }
                }).start(),
                null
        );
    }

    public void copyToInstance(ServerListItem item) {
        addServer(item.server);
    }

    public void addServer(Server server) {
        HMCLGameInstance gameInstance = this.gameInstance;
        if (gameInstance == null) return;

        Task.supplyAsync(Schedulers.io(), () -> {
            ServerStorage.ServerStorageEntry storageEntry;
            synchronized (serversFileHandleLockObject) {
                ServerStorage storage = serverStorageMap.get(gameInstance.getServersDatFilePath());
                if (storage == null) {
                    storage = new ServerStorage(gameInstance.getServersDatFilePath());
                    storage.holdInstances.add(gameInstance);
                    serverStorageMap.put(gameInstance.getServersDatFilePath(), storage);
                }
                storageEntry = storage.add(IconedServer.pack(server));
            }
            return storageEntry;
        }).whenComplete(Schedulers.javafx(), (storageEntry, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to save server data.", exception);
            }

            if (storageEntry != null) {
                serverListEntries.add(new ServerListItem(storageEntry));
                updateServerList();
            }
        }).start();
    }

    public void generateLaunchScript(ServerListItem item) {
        if (gameInstance != null) {
            Instances.generateLaunchScriptForQuickConnectServer(gameInstance, item.server.getIp());
        }
    }

    private void updateServerList() {
        if (serverListEntries == null || gameInstance == null) {
            getItems().clear();
            return;
        }

        var stream = serverListEntries.stream();
        if (!showAll.get()) {
            stream = stream.filter(entry -> entry.storageEntry.getServerDatFilePath().equals(gameInstance.getServersDatFilePath()));
        }
        if (!showHide.get()) {
            stream = stream.filter(entry -> !entry.server.isHidden());
        }

        getItems().setAll(stream.toList());
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        refresh();
    }

    private void refresh() {
        if (gameInstance == null)
            return;

        int currentRefresh = ++refreshCount;
        HMCLGameInstance gameInstance = this.gameInstance;

        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            synchronized (serversFileHandleLockObject) {
                serverStorageMap.values().forEach(ServerStorage::invalid);
                serverStorageMap.clear();

                for (HMCLGameInstance instance : gameInstance.getRepository().getSnapshot().getInstances()) {
                    if (serverStorageMap.containsKey(instance.getServersDatFilePath())) {
                        serverStorageMap.get(instance.getServersDatFilePath()).holdInstances.add(instance);
                    } else {
                        try {
                            ServerStorage storage = new ServerStorage(instance.getServersDatFilePath());
                            storage.holdInstances.add(instance);
                            serverStorageMap.put(instance.getServersDatFilePath(), storage);
                        } catch (IOException e) {
                            LOG.error("Failed to load servers from dat file: " + instance.getServersDatFilePath(), e);
                        }
                    }
                }

                return serverStorageMap.values().stream().flatMap(e -> e.storageEntries.stream()).map(ServerListItem::new).toList();
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (refreshCount != currentRefresh) {
                // A newer refresh task is running, discard this result
                return;
            }
            setLoading(false);

            if (exception != null) {
                LOG.warning("Failed to load server list page", exception);
            } else {
                serverListEntries = new ArrayList<>(result);
                updateServerList();
            }

        }).start();
    }

    private void addServer() {
        runInFX(() -> Controllers.dialog(new EditServerPane(EditServerPane.Type.ADD, null, this::addServer)));
    }

    private static final class ServerStorage {
        private final List<HMCLGameInstance> holdInstances = new ArrayList<>();
        private final Path serverDatFilePath;
        private final List<ServerStorageEntry> storageEntries;
        private boolean storageValid = true;

        private ServerStorage(Path serverDatFilePath) throws IOException {
            this.serverDatFilePath = serverDatFilePath;

            synchronized (serversFileHandleLockObject) {
                if (Files.exists(serverDatFilePath)) {
                    List<Server> servers = Server.loadFromServersDat(serverDatFilePath);
                    storageEntries = new CopyOnWriteArrayList<>();
                    for (Server server : servers) {
                        ServerStorageEntry entry = new ServerStorageEntry(IconedServer.pack(server));
                        storageEntries.add(entry);
                    }
                } else {
                    storageEntries = new CopyOnWriteArrayList<>();
                }
            }
        }

        private void save() throws IOException {
            synchronized (serversFileHandleLockObject) {
                if (!storageValid) return;

                Server.saveToServersDat(storageEntries.stream().map(e -> e.server).toList(), serverDatFilePath);
            }
        }

        private void invalid() {
            storageValid = false;
        }

        private void deleteEntry(ServerStorageEntry entry) throws IOException {
            synchronized (serversFileHandleLockObject) {
                if (!storageValid) return;

                int index = storageEntries.indexOf(entry);
                if (index != -1) {
                    ServerStorageEntry removed = storageEntries.remove(index);
                    try {
                        save();
                    } catch (IOException e) {
                        storageEntries.add(index, removed);
                        throw e;
                    }
                }
            }
        }

        private boolean updateEntry(ServerStorageEntry entry, UnaryOperator<@NotNull IconedServer> updater) throws IOException {
            synchronized (serversFileHandleLockObject) {
                if (!storageValid) return false;

                int index = storageEntries.indexOf(entry);
                if (index == -1) return false;

                IconedServer oldValue = entry.server;
                IconedServer newValue = updater.apply(oldValue);

                if (oldValue.equals(newValue)) return false;

                try {
                    entry.server = newValue;
                    save();
                } catch (IOException e) {
                    entry.server = oldValue;
                    throw e;
                }

                return true;
            }
        }

        public @Nullable ServerStorageEntry add(IconedServer server) throws IOException {
            synchronized (serversFileHandleLockObject) {
                if (!storageValid) return null;
                ServerStorageEntry entry = new ServerStorageEntry(IconedServer.pack(server));

                try {
                    storageEntries.add(entry);
                    save();
                } catch (IOException e) {
                    storageEntries.remove(entry);
                    throw e;
                }

                return entry;
            }
        }

        public class ServerStorageEntry {
            private volatile IconedServer server;

            public ServerStorageEntry(IconedServer server) {
                this.server = server;
            }

            private void delete() throws IOException {
                ServerStorage.this.deleteEntry(this);
            }

            private ServerStorage rootStorage() {
                return ServerStorage.this;
            }

            private Path getServerDatFilePath() {
                return serverDatFilePath;
            }
        }
    }

    public static final class ServerListItem {
        final @NotNull ServerListPage.ServerStorage.ServerStorageEntry storageEntry;
        final @NotNull IconedServer server;

        final @NotNull ObservableServerStatus observableServerStatus;

        public ServerListItem(@NotNull ServerListPage.ServerStorage.ServerStorageEntry storageEntry) {
            this(storageEntry, new ObservableServerStatus(storageEntry.server.getIp()));
        }

        public ServerListItem(@NotNull ServerListPage.ServerStorage.ServerStorageEntry storageEntry, @NotNull ObservableServerStatus observableServerStatus) {
            this.storageEntry = storageEntry;
            this.server = storageEntry.server;
            this.observableServerStatus = observableServerStatus;
        }
    }

    public static class IconedServer extends Server {
        final Image iconImage;

        public IconedServer(ServerPackStatus serverPackStatus, boolean hidden, @Nullable String icon, @Nullable String ip, @Nullable String name) {
            super(serverPackStatus, hidden, icon, ip, name);

            iconImage = parseImageOrDefault(icon);
        }

        public static IconedServer pack(Server server) {
            if (server instanceof IconedServer) {
                return (IconedServer) server;
            }
            return new IconedServer(
                    server.getServerPackStatus(),
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

        public IconedServer withIcon(@Nullable String newIcon) {
            return new IconedServer(getServerPackStatus(), isHidden(), newIcon, getIp(), getName());
        }

        public IconedServer withIpAndName(@Nullable String newIp, @Nullable String newName) {
            return new IconedServer(getServerPackStatus(), isHidden(), getIcon(), newIp, newName);
        }

        public IconedServer withPackStatus(ServerPackStatus status) {
            return new IconedServer(status, isHidden(), getIcon(), getIp(), getName());
        }
    }

    private final class ServerListCell extends MDListCell<ServerListItem> {

        private final ServerListPage page;

        private final ImageContainer serverIcon = new ImageContainer(32);
        private final TwoLineListItem contentLine1 = new TwoLineListItem();
        private final ServerAddressMaskPane contentLine2AddressMaskPane = new ServerAddressMaskPane("");
        private final ServerNetworkLatencyPane serverNetworkLatencyPane = new ServerNetworkLatencyPane();

        private final Tooltip statusBtnTooltip = new Tooltip();

        private Subscription serverStatusResultValueSubscription;
        private Subscription serverStatusPingingValueSubscription;

        public ServerListCell(JFXListView<ServerListItem> listView, ServerListPage page) {
            super(listView);
            this.page = page;

            this.getStyleClass().add("server-list-cell");

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            VBox content = new VBox(contentLine1, contentLine2AddressMaskPane);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            contentLine2AddressMaskPane.labelAddStyleClass("subtitle");

            JFXButton statusBtn = FXUtils.newToggleButton4(SVG.NONE);
            statusBtn.managedProperty().bind(statusBtn.visibleProperty());
            statusBtn.setOnAction(event -> {
                ServerListItem item = getItem();
                if (item != null)
                    page.showServerStatus(item);
            });
            FXUtils.installFastTooltip(statusBtn, statusBtnTooltip);

            JFXButton editBtn = FXUtils.newToggleButton4(SVG.EDIT);
            FXUtils.installFastTooltip(editBtn, i18n("server.manage.edit"));
            editBtn.setOnAction(event -> {
                ServerListItem item = getItem();
                if (item != null)
                    page.editServer(item);
            });

            JFXButton launchBtn = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH);
            FXUtils.installFastTooltip(launchBtn, i18n("instance.launch"));
            launchBtn.setOnAction(event -> {
                ServerListItem item = getItem();
                if (item != null) {
                    page.launchAndEnterServer(item);
                }
            });

            JFXButton btnMore = FXUtils.newToggleButton4(SVG.MORE_VERT);
            btnMore.setOnAction(event -> {
                ServerListItem item = getItem();
                if (item != null) {
                    showPopupMenu(item, JFXPopup.PopupHPosition.RIGHT, 0, getHeight());
                }
            });

            container.getChildren().setAll(
                    serverIcon,
                    content,
                    new StackPane(serverNetworkLatencyPane, statusBtn),
                    editBtn,
                    launchBtn,
                    btnMore
            );

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ServerListItem item, boolean empty) {
            if (serverStatusPingingValueSubscription != null) {
                serverStatusPingingValueSubscription.unsubscribe();
                serverStatusPingingValueSubscription = null;
            }
            if (serverStatusResultValueSubscription != null) {
                serverStatusResultValueSubscription.unsubscribe();
                serverStatusResultValueSubscription = null;
            }

            this.contentLine1.getTags().clear();

            if (empty || item == null) {
                setGraphic(null);
                serverIcon.setImage(null);
                contentLine1.setTitle("");
//                serverNetworkLatencyPane.error();
                statusBtnTooltip.setText("");
                contentLine2AddressMaskPane.set("");
            } else {
                serverIcon.setImage(item.server.iconImage);
                contentLine1.setTitle(item.server.getName() != null ? parseColorEscapes(item.server.getName()) : "");

                contentLine2AddressMaskPane.set(item.server.getIp());

                if (item.server.isHidden()) {
                    contentLine1.addTag(i18n("server.tag.hide"));
                }

                if (item.storageEntry.rootStorage().holdInstances.contains(page.gameInstance)) {
                    contentLine1.addTag(i18n("server.tag.hold.current"));
                    item.storageEntry.rootStorage().holdInstances.stream()
                            .filter(e -> !e.equals(page.gameInstance))
                            .map(gameInstance -> gameInstance.getId().id())
                            .forEach(contentLine1::addTag);
                } else {
                    item.storageEntry.rootStorage().holdInstances.stream()
                            .map(gameInstance -> gameInstance.getId().id())
                            .forEach(contentLine1::addTag);
                }

                item.observableServerStatus.refreshIfNoResultAsync(false);

                applyServerStatusResult(item, item.observableServerStatus.resultProperty().get());
                serverStatusResultValueSubscription = item.observableServerStatus.resultProperty().subscribe(result -> applyServerStatusResult(item, result));

                applyServerPinging(item, item.observableServerStatus.pingingProperty().get());
                serverStatusPingingValueSubscription = item.observableServerStatus.pingingProperty().subscribe(pinging -> applyServerPinging(item, pinging));
            }
        }

        private void applyServerPinging(ServerListItem item, boolean pinging) {
            if (pinging) {
                serverNetworkLatencyPane.ping();
                statusBtnTooltip.setText(i18n("server.manage.status.outside.pinging"));
            }
        }

        private void applyServerStatusResult(ServerListItem item, ServerStatusResult result) {
            if (result == null) {
                // pinging..., skip
                return;
            }
            ServerStatus serverStatus = result.getIfSucceed();
            if (serverStatus == null) {
                serverNetworkLatencyPane.error();
                ServerStatusResult.FailureResult.Reason reason = result.getFailureReasonIfFailed();
                statusBtnTooltip.setText(switch (Objects.requireNonNull(reason)) {
                    case EXCEPTION -> i18n("server.manage.status.outside.error");
                    case UNKNOWN_HOST -> i18n("server.manage.status.outside.error.unknownhost");
                    case BLOCKED_BY_MOJANG -> i18n("server.manage.status.outside.error.blocked");
                });
            } else {
                serverNetworkLatencyPane.pong(serverStatus.networkLatency());
                statusBtnTooltip.setText(i18n("server.manage.status.outside.pong", String.format("%,d", serverStatus.networkLatency())));
                // update latest server icon
                serverIcon.setImage(IconedServer.parseImageOrDefault(serverStatus.favicon()));

                if (!Objects.equals(serverStatus.favicon(), item.server.getIcon())) {
                    // save latest icon
                    Task.supplyAsync(Schedulers.io(), () -> {
                        ServerStorage storage = item.storageEntry.rootStorage();
                        return storage.updateEntry(item.storageEntry, server -> server.withIcon(serverStatus.favicon()));
                    }).whenComplete(Schedulers.javafx(), (updated, exception) -> {
                        if (exception != null)
                            LOG.warning("Failed to save server data.", exception);

                        if (updated) {
                            int index = serverListEntries.indexOf(item);
                            if (index != -1) {
                                serverListEntries.set(index, new ServerListItem(item.storageEntry, item.observableServerStatus));

                                updateServerList();
                            }
                        }

                    }).start();
                }
            }
        }

        private void showPopupMenu(ServerListPage.ServerListItem holder, JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
            PopupMenu popupMenu = new PopupMenu();
            JFXPopup popup = new JFXPopup(popupMenu);

            IconedMenuItem copyToInstanceMEnuItem = new IconedMenuItem(SVG.CONTENT_COPY, i18n("server.manage.copy.to.instance"), () -> page.copyToInstance(holder), popup);
            popupMenu.getContent().addAll(
                    new IconedMenuItem(SVG.EDIT, i18n("server.manage.edit"), () ->
                            page.editServer(holder), popup
                    ),
                    new IconedMenuItem(SVG.SERVER_SIGNAL_FULL, i18n("server.manage.status"), () ->
                            page.showServerStatus(holder), popup
                    ),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch_and_connect_server"), () ->
                            page.launchAndEnterServer(holder), popup
                    ),
                    new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), () ->
                            page.generateLaunchScript(holder), popup
                    ),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.CONTENT_COPY, i18n("server.manage.copy.server.ip"), () ->
                            page.copyServerIp(holder), popup
                    ),
                    new MenuSeparator(),
                    copyToInstanceMEnuItem,
                    new IconedMenuItem(SVG.DELETE_FOREVER, i18n("server.delete"), () ->
                            page.delete(holder), popup
                    )
            );
            if (page.gameInstance != null) {
                copyToInstanceMEnuItem.setDisable(holder.storageEntry.getServerDatFilePath().equals(page.gameInstance.getServersDatFilePath()));
            }

            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
            popup.show(this, vPosition, hPosition, initOffsetX, vPosition == JFXPopup.PopupVPosition.TOP ? initOffsetY : -initOffsetY);
        }
    }

    private final class ServerListPageSkin extends SkinBase<ServerListPage> {
        private final JFXListView<ServerListPage.ServerListItem> listView = new JFXListView<>();
        private final JFXTextField searchField = new JFXTextField();
        private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
        private final PauseTransition searchPause = new PauseTransition(Duration.millis(100));

        private final TransitionPane toolbarPane = new TransitionPane();
        private final HBox searchBar = new HBox();
        private final HBox toolbarNormal = new HBox();
        private final HBox toolbarSelecting = new HBox();

        ServerListPageSkin() {
            super(ServerListPage.this);

            StackPane rootStackPane = new StackPane();
            getChildren().setAll(rootStackPane);
            rootStackPane.setPadding(new Insets(10));
            rootStackPane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            rootStackPane.getChildren().setAll(root);
            root.getStyleClass().add("no-padding");

            listView.getStyleClass().add("no-horizontal-scrollbar");
            listView.setCellFactory(param -> new ServerListCell(listView, ServerListPage.this));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            {
                root.getContent().add(toolbarPane);
                FXUtils.setOverflowHidden(toolbarPane, 8);
                toolbarPane.setContent(toolbarNormal, ContainerAnimations.NONE);

                Insets toolbarPadding = new Insets(0, 5, 0, 5);

                toolbarNormal.setAlignment(Pos.CENTER_LEFT);
                toolbarNormal.setPadding(toolbarPadding);
                toolbarNormal.getChildren().setAll(
                        createCheckBox(box -> {
                            box.setText(i18n("server.manage.show_all"));
                            box.selectedProperty().bindBidirectional(ServerListPage.this.showAll);
                        }),
                        createCheckBox(box -> {
                            box.setText(i18n("server.manage.show_hide"));
                            box.selectedProperty().bindBidirectional(ServerListPage.this.showHide);
                        }),
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, ServerListPage.this::refresh),
                        createToolbarButton2(i18n("server.manage.add"), SVG.ADD, ServerListPage.this::addServer),
                        createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
                );


                searchBar.setAlignment(Pos.CENTER);
                searchBar.setPadding(toolbarPadding);
                searchField.setPromptText(i18n("search"));
                HBox.setHgrow(searchField, Priority.ALWAYS);
                searchPause.setOnFinished(e -> search());
                searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                        searchPause.setRate(1);
                        searchPause.playFromStart();
                    }
                });
                JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE, () -> {
                    changeToolbar(toolbarNormal);
                    searchField.clear();
                    searchPause.stop();
                    isSearching.set(false);
                    Bindings.bindContent(listView.getItems(), ServerListPage.this.getItems());
                });
                onEscPressed(searchField, closeSearchBar::fire);
                searchBar.getChildren().setAll(
                        createCheckBox(box -> {
                            box.setText(i18n("server.manage.show_all"));
                            box.selectedProperty().bindBidirectional(ServerListPage.this.showAll);
                        }),
                        createCheckBox(box -> {
                            box.setText(i18n("server.manage.show_hide"));
                            box.selectedProperty().bindBidirectional(ServerListPage.this.showHide);
                        }),
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, ServerListPage.this::refresh),
                        searchField,
                        closeSearchBar
                );

                var selectAll = createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () -> listView.getSelectionModel().selectRange(0, listView.getItems().size()));
                ListChangeListener<Object> listener = change -> {
                    selectAll.setDisable(!listView.getItems().isEmpty()
                            && listView.getSelectionModel().getSelectedItems().size() == listView.getItems().size());
                };
                listView.getSelectionModel().getSelectedItems().addListener(listener);
                listView.getItems().addListener(listener);
                toolbarSelecting.getChildren().setAll(
                        createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                            ServerListPage.this.delete(listView.getSelectionModel().getSelectedItems().toArray(ServerListItem[]::new));
                        }),
                        selectAll,
                        createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                                listView.getSelectionModel().clearSelection())
                );
                FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(), selectedItem -> {
                    if (selectedItem == null) {
                        changeToolbar(isSearching.get() ? searchBar : toolbarNormal);
                    } else {
                        changeToolbar(toolbarSelecting);
                    }
                });
            }

            StackPane placeholderContainer = new StackPane();
            placeholderContainer.getStyleClass().add("notice-pane");
            Label placeholderLabel = new Label(i18n("server.empty"));
            placeholderLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                if (isSearching.get()) {
                    return i18n("search.no_results_found");
                } else {
                    return i18n("server.empty");
                }
            }, isSearching));
            placeholderContainer.getChildren().add(placeholderLabel);
            listView.setPlaceholder(placeholderContainer);

            Bindings.bindContent(listView.getItems(), ServerListPage.this.getItems());
            ServerListPage.this.getItems().addListener((ListChangeListener<? super ServerListItem>) c -> {
                if (isSearching.get()) {
                    search();
                }
            });

            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.loadingProperty().bind(ServerListPage.this.loadingProperty());

            ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

            center.setContent(listView);
            root.getContent().add(center);
        }

        private JFXCheckBox createCheckBox(Consumer<JFXCheckBox> consumer) {
            JFXCheckBox box = new JFXCheckBox();
            // change style.
            consumer.accept(box);
            return box;
        }

        private void changeToolbar(HBox newToolbar) {
            Node oldToolbar = toolbarPane.getCurrentNode();
            if (newToolbar != oldToolbar) {
                toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
                if (newToolbar == searchBar) {
                    Platform.runLater(searchField::requestFocus);
                }
            }
        }

        private void search() {
            isSearching.set(true);

            Bindings.unbindContent(listView.getItems(), ServerListPage.this.getItems());

            String queryString = searchField.getText();
            if (StringUtils.isBlank(queryString)) {
                listView.getItems().setAll(ServerListPage.this.getItems());
            } else {
                listView.getItems().clear();

                Predicate<@Nullable String> predicate;
                try {
                    predicate = StringUtils.compileQuery(queryString);
                } catch (Throwable e) {
                    LOG.warning("Illegal regular expression", e);
                    return;
                }

                for (ServerListItem item : ServerListPage.this.getItems()) {
                    String serverIp = item.server.getIp();
                    String serverName = item.server.getName();

                    if (predicate.test(serverIp) || predicate.test(serverName)) {
                        listView.getItems().add(item);
                    }
                }
            }
        }
    }
}
