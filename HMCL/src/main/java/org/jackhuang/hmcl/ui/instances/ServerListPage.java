// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.google.gson.JsonPrimitive;
import com.jfoenix.controls.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.server.Server;
import org.jackhuang.hmcl.server.pinger.*;
import org.jackhuang.hmcl.server.status.ServerPlayerInfo;
import org.jackhuang.hmcl.server.status.ServerStatus;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jackhuang.hmcl.server.status.ServerVersionInfo;
import org.jackhuang.hmcl.server.storage.DatFileModifiedExternalException;
import org.jackhuang.hmcl.server.storage.ServerStorage;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.MinecraftChatComponentUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

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

    public static @NotNull Image parseServerFaviconImageOrDefault(@Nullable String imageBase64String) {
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

    public void launchAndEnterServer(ServerListItem item) {
        if (gameInstance != null) {
            Instances.launchAndEnterServer(gameInstance, item.getServer().ip());
        }
    }

    public void showServerStatus(ServerListItem item) {
        if (gameInstance != null) {
            runInFX(item::openServerStatusPane);
        }
    }

    public void editServer(ServerListItem item) {
        if (gameInstance != null) {
            runInFX(() -> Controllers.dialog(new EditServerDialog(EditServerDialog.Type.EDIT, item.getServer(), (server) -> {
                if (!server.equals(item.getServer())) {
                    Task.supplyAsync(Schedulers.io(), () -> {
                        ServerStorage storage = item.storageEntry.rootStorage();
                        return storage.updateEntry(item.storageEntry, oldValue -> oldValue
                                .withIpAndName(server.ip(), server.name())
                                .withIcon(server.icon())
                                .withPackStatus(server.serverPackStatus())
                        );
                    }).whenComplete(Schedulers.javafx(), (updated, exception) -> {
                        if (exception != null) {
                            LOG.warning("Failed to save server data.", exception);
                        }

                        if (exception instanceof DatFileModifiedExternalException e) {
                            showFileModifiedExternalWarnDialog(e);
                            return;
                        }

                        if (updated != null && updated) {
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

    private void delete(ServerListItem... items) {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("server.delete"),
                () -> Task.supplyAsync(Schedulers.io(), () -> {
                    List<ServerListItem> result = new ArrayList<>();
                    for (ServerListItem item : items) {
                        try {
                            item.storageEntry.delete();
                            result.add(item);
                        } catch (DatFileModifiedExternalException e) {
                            throw e;
                        } catch (IOException e) {
                            LOG.error("Failed to delete server entry.", e);
                        }
                    }
                    return result;
                }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception != null) {
                        LOG.warning("Failed to delete server data.", exception);
                    }
                    if (exception instanceof DatFileModifiedExternalException e) {
                        showFileModifiedExternalWarnDialog(e);
                        return;
                    }
                    if (result == null || result.isEmpty()) return;
                    serverListEntries.removeAll(result);
                    updateServerList();
                }).start(),
                null
        );
    }

    private void showFileModifiedExternalWarnDialog(@NotNull DatFileModifiedExternalException e) {
        Controllers.dialog(new MessageDialogPane.Builder(i18n("server.manage.error.file.modified"), null, MessageDialogPane.MessageType.ERROR)
                .addCancel(i18n("button.refresh"), this::refresh)
                .build()
        );
    }

    public void copyServerIp(ServerListItem item) {
        FXUtils.copyText(item.getServer().ip(), i18n("server.manage.copy.server.ip.ok.toast"));
    }

    public void copyToInstance(ServerListItem item) {
        if (gameInstance != null) {
            addServer(item.getServer(), gameInstance);
        }
    }

    public void addServer(Server server) {
        if (gameInstance != null) {
            addServer(server, gameInstance);
        }
    }

    public void addServer(Server server, @NotNull HMCLGameInstance toInstance) {
        Task.supplyAsync(Schedulers.io(), () -> {
            ServerStorage.ServerStorageEntry storageEntry;
            synchronized (serversFileHandleLockObject) {
                ServerStorage storage = serverStorageMap.get(toInstance.getServersDatFilePath());
                if (storage == null) {
                    storage = new ServerStorage(toInstance.getServersDatFilePath());
                    storage.getHoldInstances().add(toInstance);
                    serverStorageMap.put(toInstance.getServersDatFilePath(), storage);
                }
                storageEntry = storage.add(server);
            }
            return storageEntry;
        }).whenComplete(Schedulers.javafx(), (storageEntry, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to save server data.", exception);
            }

            if (exception instanceof DatFileModifiedExternalException e) {
                showFileModifiedExternalWarnDialog(e);
                return;
            }

            if (storageEntry != null) {
                serverListEntries.add(new ServerListItem(storageEntry));
                updateServerList();
            }
        }).start();
    }

    public void generateLaunchScript(ServerListItem item) {
        if (gameInstance != null) {
            Instances.generateLaunchScriptForQuickConnectServer(gameInstance, item.getServer().ip());
        }
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
                        serverStorageMap.get(instance.getServersDatFilePath()).getHoldInstances().add(instance);
                    } else {
                        try {
                            ServerStorage storage = new ServerStorage(instance.getServersDatFilePath());
                            storage.getHoldInstances().add(instance);
                            serverStorageMap.put(instance.getServersDatFilePath(), storage);
                        } catch (IOException e) {
                            LOG.error("Failed to load servers from dat file: " + instance.getServersDatFilePath(), e);
                        }
                    }
                }

                return serverStorageMap.values().stream().flatMap(e -> e.getStorageEntries().stream()).map(ServerListItem::new).toList();
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
        runInFX(() -> Controllers.dialog(new EditServerDialog(EditServerDialog.Type.ADD, null, this::addServer)));
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
            stream = stream.filter(entry -> !entry.getServer().hidden());
        }

        getItems().setAll(stream.toList());
    }

    private static class ServerNetworkLatencyPane extends StackPane {
        private static final SVG[] FRAMES = {
                SVG.SERVER_SIGNAL_0_BAR,
                SVG.SERVER_SIGNAL_1_BAR,
                SVG.SERVER_SIGNAL_2_BAR,
                SVG.SERVER_SIGNAL_3_BAR,
                SVG.SERVER_SIGNAL_FULL,
                SVG.SERVER_SIGNAL_3_BAR,
                SVG.SERVER_SIGNAL_2_BAR,
                SVG.SERVER_SIGNAL_1_BAR
        };

        private final SVGPath icon = SVG.NONE.createIcon();
        private final Timeline timeline = new Timeline(new KeyFrame(Duration.millis(125), e -> nextFrame()));
        private int index = 0;
        private State state = State.PINGING;

        public ServerNetworkLatencyPane() {
            timeline.setCycleCount(Animation.INDEFINITE);
            getChildren().setAll(icon);
            sceneProperty().addListener((obs, o, n) -> updateTimeline());
            ping();
        }

        private void nextFrame() {
            setIcon(FRAMES[index++ % FRAMES.length], "", 0.3);
        }

        private void updateTimeline() {
            if (state == State.PINGING && getScene() != null) {
                timeline.play();
            } else {
                timeline.stop();
            }
        }

        public void ping() {
            state = State.PINGING;
            index = 0;
            nextFrame();
            updateTimeline();
        }

        public void pong(long latency) {
            state = State.PONG;
            updateTimeline();

            SVG svg = latency < 150 ? SVG.SERVER_SIGNAL_FULL
                    : latency < 300 ? SVG.SERVER_SIGNAL_3_BAR
                    : latency < 600 ? SVG.SERVER_SIGNAL_2_BAR
                    : latency < 1000 ? SVG.SERVER_SIGNAL_1_BAR
                    : SVG.SERVER_SIGNAL_0_BAR;
            setIcon(svg, "", 1);
        }

        public void error() {
            state = State.ERROR;
            updateTimeline();
            setIcon(SVG.SERVER_SIGNAL_ERROR, "-fx-fill: -monet-error;", 1);
        }

        private void setIcon(SVG svg, String style, double opacity) {
            icon.setContent(svg.getPath());
            icon.setStyle(style);
            icon.setOpacity(opacity);
        }

        private enum State {
            PINGING,
            PONG,
            ERROR;
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
                serverIcon.setImage(item.serverFaviconImage);
                contentLine1.setTitle(item.getServer().name() != null ? parseColorEscapes(item.getServer().name()) : "");

                contentLine2AddressMaskPane.set(item.getServer().ip());

                if (item.getServer().hidden()) {
                    contentLine1.addTag(i18n("server.tag.hide"));
                }

                if (item.storageEntry.rootStorage().getHoldInstances().contains(page.gameInstance)) {
                    contentLine1.addTag(i18n("server.tag.hold.current"));
                    item.storageEntry.rootStorage().getHoldInstances().stream()
                            .filter(e -> !e.equals(page.gameInstance))
                            .map(gameInstance -> gameInstance.getId().id())
                            .forEach(contentLine1::addTag);
                } else {
                    item.storageEntry.rootStorage().getHoldInstances().stream()
                            .map(gameInstance -> gameInstance.getId().id())
                            .forEach(contentLine1::addTag);
                }

                item.observableServerStatus.refreshIfNoResultAsync(false);

                applyServerStatusResult(item, item.observableServerStatus.resultProperty().get());
                serverStatusResultValueSubscription = Subscription.subscribe(item.observableServerStatus.resultProperty(), result -> applyServerStatusResult(item, result));

                applyServerPinging(item, item.observableServerStatus.pingingProperty().get());
                serverStatusPingingValueSubscription = Subscription.subscribe(item.observableServerStatus.pingingProperty(), pinging -> applyServerPinging(item, pinging));
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
                ServerStatusResult.SuccessResult successResult = (ServerStatusResult.SuccessResult) result;
                serverNetworkLatencyPane.pong(successResult.getNetworkLatency());
                statusBtnTooltip.setText(i18n("server.manage.status.outside.pong", String.format("%,d", successResult.getNetworkLatency())));
                // update latest server icon
                serverIcon.setImage(parseServerFaviconImageOrDefault(serverStatus.favicon()));

//                if (!Objects.equals(serverStatus.favicon(), item.getServer().icon())) {
//                    // save latest icon
//                    Task.supplyAsync(Schedulers.io(), () -> {
//                        ServerStorage storage = item.storageEntry.rootStorage();
//                        return storage.updateEntry(item.storageEntry, server -> {
//                            if (Objects.equals(
//                                    ServerAddress.parse(Objects.requireNonNull(server.ip())),
//                                    ((ServerStatusResult.SuccessResult) result).getRawServerAddress())
//                            ) {
//                                return server.withIcon(serverStatus.favicon());
//                            }
//                            return server;
//                        });
//                    }).whenComplete(Schedulers.javafx(), (updated, exception) -> {
//                        if (exception != null) {
//                            LOG.warning("Failed to save server data.", exception);
//                        }
//
//                        if (exception instanceof DatFileModifiedExternalException e) {
//                            showFileModifiedExternalWarnDialog(e);
//                            return;
//                        }
//
//                        if (updated != null && updated) {
//                            int index = serverListEntries.indexOf(item);
//                            if (index != -1) {
//                                serverListEntries.set(index, new ServerListItem(item.storageEntry, item.observableServerStatus));
//
//                                updateServerList();
//                            }
//                        }
//
//                    }).start();
//                }
            }
        }

        private void showPopupMenu(ServerListPage.ServerListItem holder, JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
            PopupMenu popupMenu = new PopupMenu();
            JFXPopup popup = new JFXPopup(popupMenu);

            IconedMenuItem copyToInstanceMEnuItem = new IconedMenuItem(SVG.CONTENT_COPY, i18n("server.manage.copy.to.instance"),
                    () -> page.copyToInstance(holder), popup
            );
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

                listView.setOnContextMenuRequested(event -> {
                    ServerListItem selectedItem = listView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                        listView.getSelectionModel().clearSelection();
                        selectedItem.openServerStatusPane();
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
                    String serverIp = item.getServer().ip();
                    String serverName = item.getServer().name();

                    if (predicate.test(serverIp) || predicate.test(serverName)) {
                        listView.getItems().add(item);
                    }
                }
            }
        }
    }

    public static class ServerStatusDialog extends TransitionPane implements DialogAware {
        private final Server server;
        private final SpinnerPane refreshSpinner = new SpinnerPane();
        private final JFXDialogLayout rootLayout = new JFXDialogLayout();
        private final Label lblErrorMessage = new Label();
        private final ObservableServerStatus observableServerStatus;
        private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
        private final @Nullable HMCLGameInstance currentGameInstance;

        public ServerStatusDialog(ObservableServerStatus reference, Server server, @Nullable HMCLGameInstance gameInstance) {
            this(reference, server, false, gameInstance);
        }

        public ServerStatusDialog(ObservableServerStatus reference, Server server, boolean fromDnD, @Nullable HMCLGameInstance gameInstance) {
            this.server = server;
            if (gameInstance == null) {
                this.currentGameInstance = GameDirectoryManager.getSelectedRepository().getSelectedInstance();
            } else {
                this.currentGameInstance = gameInstance;
            }
            this.observableServerStatus = Objects.requireNonNullElseGet(reference, () -> new ObservableServerStatus(server.ip()));

            getStyleClass().add("skin-pane");
            getChildren().setAll(rootLayout);
            rootLayout.setHeading(new Label(i18n("server.manage.status.head")));

            {
                JFXButton refreshBtn = new JFXButton(i18n("button.refresh"));
                refreshBtn.getStyleClass().add("dialog-refresh");
                refreshBtn.setOnAction(e -> observableServerStatus.refreshAsync(true));
                refreshSpinner.getStyleClass().add("small-spinner-pane");
                refreshSpinner.setContent(refreshBtn);
            }

            HBox actions = new HBox(refreshSpinner);
            actions.setAlignment(Pos.CENTER_RIGHT);

            if (fromDnD) {
                // add to current instance

                JFXButton addToInstallBtn = new JFXButton(i18n("server.dnd.add"));
                addToInstallBtn.getStyleClass().add("dialog-accept");
                addToInstallBtn.setOnAction(ignored0 -> {
                    fireEvent(new DialogCloseEvent());
                    // open server manager
                    if (currentGameInstance == null) {
                        JFXButton gotoDownload = new JFXButton(i18n("instance.empty.launch.goto_download"));
                        gotoDownload.getStyleClass().add("dialog-accept");
                        gotoDownload.setOnAction(ignored1 -> Controllers.navigate(Controllers.getDownloadPage()));

                        Controllers.confirmAction(i18n("server.dnd.add.instanceempty.desc"), i18n("server.dnd.add.instanceempty"),
                                MessageDialogPane.MessageType.ERROR,
                                gotoDownload,
                                null);
                    } else {
                        if (Controllers.getGameInstancePage().getScene() == null) {
                            Instances.modifyServerList(currentGameInstance);
                        }

                        if (!(Controllers.getGameInstancePage().getSelectedTab() instanceof ServerListPage)) {
                            Controllers.getGameInstancePage().showServerList();
                        }

                        ServerListPage serverListPage = Controllers.getGameInstancePage().getServerListTab().getNode();
                        if (serverListPage != null) {
                            serverListPage.addServer(server, currentGameInstance);
                        }
                    }
                });
                actions.getChildren().add(addToInstallBtn);
            }

            JFXButton launchBtn = new JFXButton(i18n("instance.launch"));
            launchBtn.getStyleClass().add("dialog-accept");
            launchBtn.setOnAction(e -> {
                fireEvent(new DialogCloseEvent());
                Instances.launchAndEnterServer(currentGameInstance, server.ip());
            });
            actions.getChildren().add(launchBtn);

            JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
            cancelBtn.getStyleClass().add("dialog-cancel");
            cancelBtn.setOnAction(e -> onClose());
            onEscPressed(this, cancelBtn::fire);
            actions.getChildren().add(cancelBtn);

            lblErrorMessage.setWrapText(true);
            lblErrorMessage.setMaxWidth(400);
            rootLayout.getActions().addAll(lblErrorMessage, actions);

            applyResult(observableServerStatus.resultProperty().get());
            listenerHolder.add(onWeakChangeAndOperate(observableServerStatus.resultProperty(), this::applyResult));

            applyPinging(observableServerStatus.pingingProperty().get());
            listenerHolder.add(onWeakChangeAndOperate(observableServerStatus.pingingProperty(), this::applyPinging));

            observableServerStatus.refreshIfNoResultOrFailedAsync(true);
        }

        private static Node createDescriptionRow(String title, String content, @Nullable String tooltip, Insets inVBoxInsets) {
            BorderPane row = new BorderPane();
            VBox.setMargin(row, inVBoxInsets);
            row.setLeft(new Label(title));
            Label right = new Label(content);
            if (tooltip != null) {
                installFastTooltip(right, tooltip);
            }
            row.setRight(right);
            return row;
        }

        public void applyPinging(boolean pinging) {
            if (pinging) {
                lblErrorMessage.setText("");
                refreshSpinner.showSpinner();
            } else {
                refreshSpinner.hideSpinner();
            }
        }

        public void applyResult(ServerStatusResult statusResult) {
            ServerStatus status;

            if (statusResult == null) {
                // pinging..., continue
                status = null;
            } else {
                // ping failed
                status = statusResult.getIfSucceed();
                if (status == null) {
                    ServerStatusResult.FailureResult.Reason reason = statusResult.getFailureReasonIfFailed();
                    lblErrorMessage.setText(switch (Objects.requireNonNull(reason)) {
                        case EXCEPTION -> i18n("server.manage.status.error");
                        case UNKNOWN_HOST -> i18n("server.manage.status.error.unknownhost");
                        case BLOCKED_BY_MOJANG -> i18n("server.manage.status.error.blocked");
                    });
                }
            }

            Image serverIcon = parseServerFaviconImageOrDefault(server.icon());
            if (status != null) {
                serverIcon = parseServerFaviconImageOrDefault(status.favicon());
            }

            VBox rootContent = new VBox();
            rootContent.setSpacing(10);
            rootLayout.setBody(rootContent);

            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);
            titleContainer.setAlignment(Pos.CENTER_LEFT);
            VBox.setMargin(titleContainer, new Insets(12, 0, 0, 0));
            rootContent.getChildren().add(titleContainer);

            ImageContainer imageView = new ImageContainer(64);
            imageView.setImage(serverIcon);
            titleContainer.getChildren().add(imageView);

            VBox titleContent = new VBox();
            titleContent.setAlignment(Pos.CENTER_LEFT);
            titleContent.setSpacing(8);
            HBox.setMargin(titleContent, new Insets(0, 0, 0, 12));
            titleContainer.getChildren().add(titleContent);

            TwoLineListItem title = new TwoLineListItem();
            title.getTitleLabel().setWrapText(true);
            title.setTitle(server.name());
            titleContent.getChildren().add(title);

            ServerAddressMaskPane addressMaskPane = new ServerAddressMaskPane(server.ip());
            addressMaskPane.labelAddStyleClass("subtitle");
            titleContent.getChildren().add(addressMaskPane);

            if (status != null) {
                VBox motdContentBox = new VBox();
                motdContentBox.setSpacing(5);
                motdContentBox.setAlignment(Pos.CENTER_LEFT);


                String[] motdLines = MinecraftChatComponentUtils
                        .toPlainStringFromChatComponent(status.description())
                        .split("\\r?\\n");
                boolean haveMOTDContent = false;
                for (int i = 0; i < Math.min(motdLines.length, 2); i++) {
                    String line = motdLines[i].trim();
                    if (line.length() > 80) {
                        line = line.substring(0, 77).trim() + "...";
                    }
                    Label lineLabel = new Label(line);
                    lineLabel.setMaxWidth(560);
                    lineLabel.setWrapText(true);
                    motdContentBox.getChildren().add(lineLabel);
                    if (!line.isEmpty()) {
                        haveMOTDContent = true;
                    }
                }

                if (haveMOTDContent) {
                    VBox.setMargin(motdContentBox, new Insets(4, 0, 0, 4));
                    rootContent.getChildren().add(motdContentBox);
                }

                Separator titleEndSeparator = new Separator();
                titleEndSeparator.setMaxWidth(Double.MAX_VALUE);
                titleEndSeparator.setPadding(new Insets(4, 0, 0, 0));
                rootContent.getChildren().add(titleEndSeparator);

                String playersTooltip = null;
                if (status.players() instanceof ServerPlayerInfo.FullServerPlayerInfo fullInfo) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.join("\n", fullInfo.samples().stream()
                            .map(sample -> {
                                if (sample.isAnonymous()) {
                                    return i18n("server.anonymous_player");
                                }
                                return MinecraftChatComponentUtils.toPlainStringFromChatComponent(new JsonPrimitive(sample.name()));
                            })
                            .toList())
                    );

                    if (fullInfo.samples().size() < fullInfo.online()) {
                        sb.append("\n");
                        sb.append(i18n("server.and_more_players", String.valueOf(fullInfo.online() - fullInfo.samples().size())));
                    }

                    playersTooltip = sb.toString();
                }

                String modsTooltip = null;
                int showCount = 15;
                if (status.modInfo() != null && !status.modInfo().modList().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.join("\n", status.modInfo().modList().stream()
                            .limit(showCount)
                            .map(it -> it.modId() + " - " + it.version())
                            .toList())
                    );
                    if (showCount < status.modInfo().modList().size()) {
                        sb.append("\n");
                        sb.append(i18n("server.and_more_mods", String.valueOf(status.modInfo().modList().size() - showCount)));
                    }

                    modsTooltip = sb.toString();
                }

                rootContent.getChildren().add(createDescriptionRow(i18n("server.type"), status.modInfo() != null ? i18n("server.type.mod", status.modInfo().type(), status.modInfo().modList().size()) : i18n("server.type.vanilla"), modsTooltip, new Insets(0, 4, 0, 4)));

                if (status.players() instanceof ServerPlayerInfo.FullServerPlayerInfo info) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.onlineplayers"), i18n("server.onlineplayers.full", info.online(), info.max()), playersTooltip, new Insets(0, 4, 0, 4)));
                } else if (status.players() instanceof ServerPlayerInfo.OnlyPlayerNumberServerVersionInfo info) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.onlineplayers"), i18n("server.onlineplayers.full", info.online(), info.max()), playersTooltip, new Insets(0, 4, 0, 4)));
                } else if (status.players() instanceof ServerPlayerInfo.UnknownServerPlayerInfo info) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.onlineplayers"), i18n("server.onlineplayers.unknown"), playersTooltip, new Insets(0, 4, 0, 4)));
                }

                if (status.version() instanceof ServerVersionInfo.FullServerVersionInfo info) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.playversion"), i18n("server.playversion.full", MinecraftChatComponentUtils.toPlainStringFromChatComponent(new JsonPrimitive(info.protocolName())), info.protocolVersion()), null, new Insets(0, 4, 0, 4)));
                } else if (status.version() instanceof ServerVersionInfo.OnlyProtocolVersionServerVersionInfo info) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.playversion"), i18n("server.playversion.onlyversion", info.protocolVersion()), null, new Insets(0, 4, 0, 4)));
                } else {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.playversion"), i18n("server.playversion.unknown"), null, new Insets(0, 4, 0, 4)));
                }

                ServerStatusPinger pinger = ((ServerStatusResult.SuccessResult) statusResult).getPinger();
                if (pinger instanceof ModernServerStatusPinger) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.pinger"), i18n("server.pinger.modern"), null, new Insets(0, 4, 0, 4)));
                    title.addTag(i18n("server.pinger.modern"));
                } else if (pinger instanceof LegacyServerStatusPinger) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.pinger"), i18n("server.pinger.legacy"), null, new Insets(0, 4, 0, 4)));
                    title.addTag(i18n("server.pinger.legacy"));
                } else if (pinger instanceof OldServerStatusPinger.BetaServerStatusPinger) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.pinger"), i18n("server.pinger.beta"), null, new Insets(0, 4, 0, 4)));
                    title.addTag(i18n("server.pinger.beta"));
                } else if (pinger instanceof OldServerStatusPinger.AlphaServerStatusPinger) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.pinger"), i18n("server.pinger.alpha"), null, new Insets(0, 4, 0, 4)));
                    title.addTag(i18n("server.pinger.alpha"));
                } else if (pinger instanceof ClassicServerStatusPinger) {
                    rootContent.getChildren().add(createDescriptionRow(i18n("server.pinger"), i18n("server.pinger.classic"), null, new Insets(0, 4, 0, 4)));
                    title.addTag(i18n("server.pinger.classic"));
                }

                rootContent.getChildren().add(createDescriptionRow(i18n("server.latency"), String.format("%,dms", ((ServerStatusResult.SuccessResult) statusResult).getNetworkLatency()), null, new Insets(0, 4, 0, 4)));

            }
        }

        private void onClose() {
            fireEvent(new DialogCloseEvent());
        }
    }

    public static class ServerAddressMaskPane extends HBox {
        private final Label atLabel = new Label();
        private final Label realServerLabel = new Label();
        private final Label queryLabel = new Label();

        private final Tooltip atLabelTooltip = new Tooltip();
        private final Tooltip queryLabelTooltip = new Tooltip();

        public ServerAddressMaskPane(@Nullable String serverIP) {
            getChildren().add(atLabel);
            getChildren().add(realServerLabel);
            getChildren().add(queryLabel);

            atLabel.setPickOnBounds(true);
            queryLabel.setPickOnBounds(true);

            //        FXUtils.installFastTooltip(atLabel, atLabelTooltip);
            //        FXUtils.installFastTooltip(queryLabel, queryLabelTooltip);
            set(serverIP);

            BoxBlur blur = new BoxBlur();
            blur.setIterations(3);

            atLabel.setEffect(blur);
            queryLabel.setEffect(blur);

            atLabel.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                atLabel.setEffect(null);
            });
            atLabel.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                atLabel.setEffect(blur);
            });

            queryLabel.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                queryLabel.setEffect(null);
            });
            queryLabel.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                queryLabel.setEffect(blur);
            });
        }

        public void set(@Nullable String serverIP) {
            if (serverIP == null) {
                atLabel.setText("");
                realServerLabel.setText("");
                queryLabel.setText("");

                atLabelTooltip.setText("");
                queryLabelTooltip.setText("");
                return;
            }
            int queryStart = serverIP.lastIndexOf('?');
            if (queryStart != -1) {
                String queryStr = serverIP.substring(queryStart);
                queryLabel.setText(queryStr);
                atLabelTooltip.setText(queryStr);
                serverIP = serverIP.substring(0, queryStart);
            } else {
                queryLabel.setText("");
                atLabelTooltip.setText("");
            }

            int atEnd = serverIP.indexOf('@');
            if (atEnd != -1) {
                String atStr = serverIP.substring(0, atEnd + 1);
                atLabel.setText(atStr);
                atLabelTooltip.setText(atStr);
                serverIP = serverIP.substring(atEnd + 1);
            } else {
                atLabel.setText("");
                atLabelTooltip.setText("");
            }

            realServerLabel.setText(serverIP);
        }

        public void labelAddStyleClass(String styleClass) {
            atLabel.getStyleClass().add(styleClass);
            realServerLabel.getStyleClass().add(styleClass);
            queryLabel.getStyleClass().add(styleClass);
        }
    }

    public static class ObservableServerStatus {

        private static final Semaphore PING_SEMAPHORE = new Semaphore(5);

        private final String serverIp;
        private final ReadOnlyObjectWrapper<ServerStatusResult> resultProperty = new ReadOnlyObjectWrapper<>();
        private final ReadOnlyBooleanWrapper pingingProperty = new ReadOnlyBooleanWrapper();
        private final AtomicInteger refreshId = new AtomicInteger();

        public ObservableServerStatus(String serverIp) {
            this.serverIp = serverIp;
        }

        public void refreshIfNoResultAsync(boolean urgent) {
            if (resultProperty.get() == null) {
                refreshAsync(urgent);
            }
        }

        public void refreshIfNoResultOrFailedAsync(boolean urgent) {
            ServerStatusResult result = resultProperty.get();
            if (result == null || result.isFailure()) {
                refreshAsync(urgent);
            }
        }

        public void refreshAsync(boolean urgent) {
            Task.runAsync(Schedulers.javafx(), () -> {

                if (pingingProperty.get() && !urgent) {
                    return;
                }

                int currentRefreshId = refreshId.incrementAndGet();
                pingingProperty.set(true);

                Task.supplyAsync(Schedulers.io(), () -> {
                    // expired.
                    if (currentRefreshId != refreshId.get()) return null;

                    if (urgent) {
                        return ServerStatusPinger.getStatus(serverIp);
                    }

                    PING_SEMAPHORE.acquireUninterruptibly();
                    try {
                        // expired.
                        if (currentRefreshId != refreshId.get()) return null;

                        return ServerStatusPinger.getStatus(serverIp);
                    } finally {
                        PING_SEMAPHORE.release();
                    }
                }).whenComplete(Schedulers.javafx(), (result, ignored) -> {
                    // expired.
                    if (currentRefreshId != refreshId.get()) return;

                    try {
                        resultProperty.set(result);
                    } finally {
                        pingingProperty.set(false);
                    }
                }).start();
            }).start();
        }

        public ReadOnlyBooleanProperty pingingProperty() {
            return pingingProperty.getReadOnlyProperty();
        }

        public ReadOnlyObjectProperty<ServerStatusResult> resultProperty() {
            return resultProperty.getReadOnlyProperty();
        }
    }

    public static class EditServerDialog extends TransitionPane implements DialogAware {
        private final Consumer<Server> handleCallback;

        private final Server reference;
        private final Type type;
        private final GridPane body = new GridPane();
        private final JFXTextField serverNameField = new JFXTextField();
        private final JFXTextField serverIpField = new JFXTextField();
        private final JFXComboBox<Server.ServerPackStatus> serverPackStatusField = new JFXComboBox<>();

        private final JFXButton btnAccept = new JFXButton();
        private final JFXButton btnCancel = new JFXButton();
        private final SpinnerPane spinner = new SpinnerPane();
        private final Label lblErrorLabel = new Label();

        private final SimpleBooleanProperty stillActionProgress = new SimpleBooleanProperty(false);

        private boolean cancelled;

        public EditServerDialog(Type type, @Nullable Server reference, Consumer<Server> handleCallback) {
            this.type = type;
            this.reference = reference;
            this.handleCallback = handleCallback;
            this.cancelled = false;

            getStyleClass().add("skin-pane");

            String referenceServerName;
            String referenceServerIP;
            Server.ServerPackStatus referenceServerPackStatus;
            if (reference != null) {
                referenceServerName = reference.name();
                referenceServerIP = reference.ip();
                referenceServerPackStatus = reference.serverPackStatus();
            } else {
                referenceServerName = null;
                referenceServerIP = null;
                referenceServerPackStatus = null;
            }

            JFXDialogLayout rootLayout = new JFXDialogLayout();
            getChildren().setAll(rootLayout);

            rootLayout.setHeading(new Label(switch (type) {
                    case EDIT -> i18n("server.manage.edit.head");
                    case ADD -> i18n("server.manage.add.head");
                }));

            VBox bodyVbox = new VBox();
            bodyVbox.setSpacing(8);

            VBox.setMargin(body, new Insets(20, 0, 0, 0));
            body.setVgap(22);
            body.setHgap(15);
            body.setAlignment(Pos.CENTER);

            ColumnConstraints col0 = new ColumnConstraints();
            col0.setMinWidth(USE_PREF_SIZE);
            body.getColumnConstraints().add(col0);
            ColumnConstraints col1 = new ColumnConstraints();
            col1.setHgrow(Priority.ALWAYS);
            body.getColumnConstraints().add(col1);


            // server name
            {
                Label label = new Label();
                label.setText(i18n("server.name"));
                GridPane.setHalignment(label, HPos.LEFT);
                body.add(label, 0, 0);

                serverNameField.setPromptText(i18n("server.name.def"));
                serverNameField.setTextFormatter(new TextFormatter<>(change -> {
                    if (change.getControlNewText().length() <= 32) {
                        return change;
                    }
                    return null;
                }));
                body.add(serverNameField, 1, 0);

                if (referenceServerName != null) {
                    serverNameField.setText(referenceServerName);
                }
            }

            // server ip
            {
                Label label = new Label();
                label.setText(i18n("server.ip"));
                GridPane.setHalignment(label, HPos.LEFT);
                body.add(label, 0, 1);

                serverIpField.setValidators(
                        new RequiredValidator(),
                        new MinecraftServerAddressValidator(i18n("server.address.malformed"), false)
                );
                setValidateWhileTextChanged(serverIpField, true);

                body.add(serverIpField, 1, 1);

                if (referenceServerIP != null) {
                    serverIpField.setText(referenceServerIP);
                }

            }

            // serverPackStatusField
            {
                Label label = new Label();
                label.setText(i18n("server.resourcepack"));
                GridPane.setHalignment(label, HPos.LEFT);
                body.add(label, 0, 2);

                serverPackStatusField.setConverter(stringConverter(status -> i18n("server.resourcepack." + status.name().toLowerCase(Locale.ROOT))));
                serverPackStatusField.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal == null && oldVal != null) {
                        serverPackStatusField.setValue(oldVal);
                    }
                });
                serverPackStatusField.getItems().setAll(Server.ServerPackStatus.values());
                body.add(serverPackStatusField, 1, 2);
                serverPackStatusField.setValue(
                        Objects.requireNonNullElse(referenceServerPackStatus, Server.ServerPackStatus.PROMPT)
                );
            }

            bodyVbox.getChildren().add(body);
            rootLayout.setBody(bodyVbox);

            btnAccept.setText(switch (type) {
                case EDIT -> i18n("server.manage.edit.accept");
                case ADD -> i18n("server.manage.add.accept");
            });
            btnAccept.getStyleClass().add("dialog-accept");
            btnAccept.disableProperty().bind(new BooleanBinding() {
                {
                    bind(serverIpField.textProperty());
                }

                @Override
                protected boolean computeValue() {
                    return !serverIpField.validate();
                }
            });

            lblErrorLabel.setWrapText(true);
            lblErrorLabel.setMaxWidth(400);

            btnCancel.setText(i18n("button.cancel"));
            btnCancel.getStyleClass().add("dialog-cancel");
            onEscPressed(this, btnCancel::fire);

            spinner.getStyleClass().add("small-spinner-pane");
            spinner.setContent(btnAccept);

            HBox actions = new HBox(spinner, btnCancel);
            actions.setAlignment(Pos.CENTER_RIGHT);

            rootLayout.setActions(lblErrorLabel, actions);

            btnCancel.setOnAction(e -> onCancel());
            btnAccept.setOnAction(ignored -> {
                String serverName;
                String serverIP = serverIpField.getText();
                Server.ServerPackStatus serverPackStatus = serverPackStatusField.getValue();
                if (serverIP == null) return;
                if (serverNameField.getText() == null || serverNameField.getText().isEmpty()) {
                    serverName = i18n("server.name.def");
                } else {
                    serverName = serverNameField.getText();
                }

                if (!stillActionProgress.get()) {
                    onAdd(serverPackStatus, serverName, serverIP);
                    return;
                }

                fireEvent(new DialogCloseEvent());
                if (reference != null) {
                    handleCallback.accept(reference
                            .withIpAndName(serverIP, serverName)
                            .withPackStatus(serverPackStatus)
                    );
                } else {
                    handleCallback.accept(new Server(serverPackStatus, false, null, serverIP, serverName));
                }
            });

            ChangeListener<String> contentChangeListener = (ignored0, ignored1, ignored2) -> {
                if (stillActionProgress.get()) {
                    hideStillAction();
                }
            };
            //        serverNameField.textProperty().addListener(contentChangeListener);
            serverIpField.textProperty().addListener(contentChangeListener);
        }

        private void showStillAction(String errorMessage) {
            stillActionProgress.set(true);
            lblErrorLabel.setText(errorMessage);
            btnAccept.setText(switch (type) {
                case EDIT -> i18n("server.manage.edit.still");
                case ADD -> i18n("server.manage.add.still");
            });
        }

        private void hideStillAction() {
            stillActionProgress.set(false);
            lblErrorLabel.setText("");
            btnAccept.setText(switch (type) {
                case EDIT -> i18n("server.manage.edit.accept");
                case ADD -> i18n("server.manage.add.accept");
            });
        }

        private void onAdd(Server.ServerPackStatus packStatus, String serverName, String serverIP) {
            body.setDisable(true);
            spinner.showSpinner();

            Task.supplyAsync(Schedulers.io(), () ->
                    ServerStatusPinger.getStatus(serverIP)
            ).whenComplete(Schedulers.javafx(), (result, ignored) -> {

                ServerStatus status = result.getIfSucceed();
                if (status == null) {
                    spinner.hideSpinner();
                    body.setDisable(false);

                    ServerStatusResult.FailureResult.Reason reason = result.getFailureReasonIfFailed();
                    showStillAction(switch (Objects.requireNonNull(reason)) {
                        case EXCEPTION -> i18n("server.manage.status.error");
                        case UNKNOWN_HOST -> i18n("server.manage.status.error.unknownhost");
                        case BLOCKED_BY_MOJANG -> i18n("server.manage.status.error.blocked");
                    });
                } else {
                    if (getScene() == null || cancelled) {
                        return;
                    }
                    fireEvent(new DialogCloseEvent());
                    if (reference != null) {
                        handleCallback.accept(reference
                                .withIpAndName(serverIP, serverName)
                                .withPackStatus(packStatus)
                                .withIcon(status.favicon())
                        );
                    } else {
                        handleCallback.accept(new Server(packStatus, false, status.favicon(), serverIP, serverName));
                    }
                }
            }).start();
        }

        private void onCancel() {
            cancelled = true;
            fireEvent(new DialogCloseEvent());
        }

        @Override
        public void onDialogShown() {
            serverNameField.requestFocus();
        }

        public enum Type {
            EDIT,
            ADD
        }
    }

    public final class ServerListItem {
        private final @NotNull ServerStorage.ServerStorageEntry storageEntry;
        private final Image serverFaviconImage;

        final @NotNull ObservableServerStatus observableServerStatus;

        public ServerListItem(@NotNull ServerStorage.ServerStorageEntry storageEntry) {
            this(storageEntry, new ObservableServerStatus(storageEntry.getServer().ip()));
        }

        public ServerListItem(@NotNull ServerStorage.ServerStorageEntry storageEntry, @NotNull ObservableServerStatus observableServerStatus) {
            this.storageEntry = storageEntry;
            this.observableServerStatus = observableServerStatus;

            this.serverFaviconImage = parseServerFaviconImageOrDefault(storageEntry.getServer().icon());
        }

        public Server getServer() {
            return storageEntry.getServer();
        }

        public void openServerStatusPane() {
            Controllers.dialog(new ServerStatusDialog(observableServerStatus, getServer(), ServerListPage.this.gameInstance));
        }
    }
}
