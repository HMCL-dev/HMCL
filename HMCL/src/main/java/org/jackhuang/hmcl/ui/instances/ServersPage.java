// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.image.ImageLoaders;
import org.jackhuang.hmcl.server.Server;
import org.jackhuang.hmcl.server.ServersDatFile;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Base64Utils;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ServersPage extends ListPaneBase<ServersPage.ServerListItem> {
    /// Switches between toolbar modes.
    private final TransitionPane toolbarPane = new TransitionPane();
    /// Actions available while browsing.
    private final HBox toolbarNormal = new HBox();
    /// Actions on the selected items.
    private final HBox toolbarSelecting = new HBox();
    /// Toolbar containing the search input.
    private final HBox searchBar = new HBox();
    /// Text used to filter the list.
    private final JFXTextField searchField = new JFXTextField();
    /// Whether search mode is active.
    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
    /// Delays filtering while the query is being edited.
    private final PauseTransition searchPause = new PauseTransition(Duration.millis(100));

    /// All loaded items before search filtering.
    private final ObservableList<ServerListItem> sourceItems = FXCollections.observableArrayList();

    /// Source items matching the current search.
    private final FilteredList<ServerListItem> filteredItems = new FilteredList<>(sourceItems);

    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private final Lock handleLock = new ReentrantLock();
    private @Nullable HMCLGameInstance gameInstance;
    private volatile Map<Path, Set<HMCLGameInstance>> holdInstancesMap = new HashMap<>();
    private volatile Map<Path, ServersDatFile> serversDatFiles = new HashMap<>();
    private List<ServerListItem> serverListEntries = new ArrayList<>();

    private int refreshCount = 0;

    public ServersPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");

        showAll.addListener(e -> updateServerList());

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));

        initializeView();
    }

    @Override
    protected ItemCell<ServerListItem> createItemCell(ListView<ServerListItem> listView) {
        return new ServerCell(listView, this);
    }

    /// Configures the list and its page actions.
    private void initializeView() {
        getStyleClass().add("notice-pane");
        var listView = getListView();
        listView.setItems(filteredItems);

        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        {
            setToolbar(toolbarPane);
            FXUtils.setOverflowHidden(toolbarPane, 8);
            toolbarPane.setContent(toolbarNormal, ContainerAnimations.NONE);

            Insets toolbarPadding = new Insets(0, 5, 0, 5);

            toolbarNormal.setAlignment(Pos.CENTER_LEFT);
            toolbarNormal.setPadding(toolbarPadding);
            toolbarNormal.getChildren().setAll(
                    createCheckBox(box -> {
                        box.setText(i18n("server.manage.show_all"));
                        box.selectedProperty().bindBidirectional(showAll);
                    }),
                    createToolbarButton(i18n("button.refresh"), SVG.REFRESH, this::refresh),
                    createToolbarButton(i18n("server.manage.add"), SVG.ADD, this::addServer),
                    createToolbarButton(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
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

            JFXButton closeSearchBar = createToolbarButton(null, SVG.CLOSE, () -> {
                changeToolbar(toolbarNormal);
                searchField.clear();
                searchPause.stop();
                isSearching.set(false);
                filteredItems.setPredicate(null);
            });
            onEscPressed(searchField, closeSearchBar::fire);
            searchBar.getChildren().setAll(
                    createCheckBox(box -> {
                        box.setText(i18n("server.manage.show_all"));
                        box.selectedProperty().bindBidirectional(showAll);
                    }),
                    createToolbarButton(i18n("button.refresh"), SVG.REFRESH, this::refresh),
                    searchField,
                    closeSearchBar
            );

            var selectAll = createToolbarButton(i18n("button.select_all"), SVG.SELECT_ALL, () -> listView.getSelectionModel().selectRange(0, listView.getItems().size()));
            ListChangeListener<Object> listener = change -> {
                selectAll.setDisable(!listView.getItems().isEmpty()
                        && listView.getSelectionModel().getSelectedItems().size() == listView.getItems().size());
            };
            listView.getSelectionModel().getSelectedItems().addListener(listener);
            listView.getItems().addListener(listener);
            toolbarSelecting.getChildren().setAll(
                    createToolbarButton(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                        delete(listView.getSelectionModel().getSelectedItems().toArray(ServerListItem[]::new));
                    }),
                    selectAll,
                    createToolbarButton(i18n("button.cancel"), SVG.CANCEL, () ->
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

    }

    public static @NotNull Image parseServerFaviconImageOrDefault(@Nullable String imageBase64String) {
        if (imageBase64String != null && !imageBase64String.isEmpty()) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64Utils.decode(imageBase64String))) {
                // png format.
                return ImageLoaders.DEFAULT.load(bais, "icon.png", 0, 0, true, true);
            } catch (Exception e) {
                LOG.warning("Failed to decode server icon", e);
            }
        }
        return FXUtils.newBuiltinImage("/assets/img/unknown_server.png");
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        refresh();
    }

    private void updateServerList() {
        if (serverListEntries == null || gameInstance == null) {
            sourceItems.clear();
            return;
        }

        var stream = serverListEntries.stream();
        if (!showAll.get()) {
            stream = stream.filter(entry -> entry.storageEntry.getServersDatFile().getFile().equals(gameInstance.getServersDatFilePath()));
        }

        sourceItems.setAll(stream.toList());
    }

    private void refresh() {
        if (gameInstance == null)
            return;

        int currentRefresh = ++refreshCount;
        HMCLGameInstance gameInstance = this.gameInstance;

        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            handleLock.lock();
            try {
                Set<Path> errorPath = new HashSet<>();
                Map<Path, ServersDatFile> serversDatFiles = new HashMap<>();
                Map<Path, Set<HMCLGameInstance>> holdInstancesMap = new HashMap<>();

                List<ServerListItem> collectList = new ArrayList<>();

                for (HMCLGameInstance instance : gameInstance.getRepository().getSnapshot().getInstances()) {
                    Path serversDatFilePath = instance.getServersDatFilePath();
                    if (errorPath.contains(serversDatFilePath)) {
                        continue;
                    }

                    Set<HMCLGameInstance> holdInstances = holdInstancesMap.computeIfAbsent(serversDatFilePath, key -> new LinkedHashSet<>());
                    holdInstances.add(instance);

                    if (!serversDatFiles.containsKey(serversDatFilePath)) {
                        try {
                            var serversDatFile = new ServersDatFile(serversDatFilePath);
                            serversDatFiles.put(serversDatFilePath, serversDatFile);
                            for (ServersDatFile.ServerEntry serverEntry : serversDatFile.getEntries()) {
                                if (!serverEntry.getServer().isHidden()) {
                                    collectList.add(new ServerListItem(serverEntry, holdInstances));
                                }
                            }
                        } catch (IOException e) {
                            errorPath.add(serversDatFilePath);
                            LOG.error("Failed to load servers from dat file: " + serversDatFilePath, e);
                        }
                    }
                }

                this.serversDatFiles = serversDatFiles;
                this.holdInstancesMap = holdInstancesMap;
                return collectList;
            } finally {
                handleLock.unlock();
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

    public void addServer(Server server) {
        if (gameInstance != null) {
            addServerToInstance(server, gameInstance);
        } else {
            HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
            HMCLGameInstance instance = repository.getSelectedInstance();
            if (instance != null) {
                addServerToInstance(server, instance);
            }
        }
    }

    public void addServerToInstance(Server server, @NotNull HMCLGameInstance toInstance) {
        ServersDatFile serversDatFile = serversDatFiles.get(toInstance.getServersDatFilePath());
        if (serversDatFile != null) {
            addServerToServersDatFile(server, serversDatFile, toInstance);
            return;
        }

        Task.supplyAsync(Schedulers.io(), () ->
                new ServersDatFile(toInstance.getServersDatFilePath())
        ).whenComplete(Schedulers.javafx(), (datFile, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to load servers from dat file: " + toInstance.getServersDatFilePath(), exception);
            }

            ServersDatFile dirt = serversDatFiles.get(toInstance.getServersDatFilePath());
            if (dirt != null) {
                addServerToServersDatFile(server, dirt, toInstance);
                return;
            }
            if (datFile != null) {
                serversDatFiles.put(toInstance.getServersDatFilePath(), datFile);
                addServerToServersDatFile(server, datFile, toInstance);
            }
        }).start();
    }

    public void addServerToServersDatFile(Server server, @NotNull ServersDatFile serversDatFile, @NotNull HMCLGameInstance toInstance) {
        Task.supplyAsync(Schedulers.io(), () -> {
            handleLock.lock();
            try {
                return serversDatFile.addServer(server);
            } finally {
                handleLock.unlock();
            }
        }).whenComplete(Schedulers.javafx(), (serverEntry, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to save server data.", exception);
            }

            if (exception instanceof ServersDatFile.FileModifiedExternalException e) {
                showModifiedErrorDialog(e);
                return;
            }

            if (exception instanceof IllegalStateException e) {
                showModifiedErrorDialog(e);
                return;
            }

            if (serverEntry != null) {
                Set<HMCLGameInstance> holdInstances = holdInstancesMap.computeIfAbsent(serverEntry.getServersDatFile().getFile(), path -> {
                    LinkedHashSet<HMCLGameInstance> set = new LinkedHashSet<>();
                    set.add(toInstance);
                    return set;
                });
                serverListEntries.add(new ServerListItem(serverEntry, holdInstances));
                updateServerList();
            }
        }).start();
    }

    public void editServer(ServerListItem item) {
        if (gameInstance != null) {
            runInFX(() -> Controllers.dialog(new EditServerDialog(EditServerDialog.Type.EDIT, item.getServer(), (newServer) -> {
                if (!serverListEntries.contains(item)) {
                    return;
                }

                Server oldServer = item.getServer();
                Task.supplyAsync(Schedulers.io(), () -> {
                    item.storageEntry.modify(s -> s
                            .withServerPackStatus(newServer.getServerPackStatus())
                            .withIp(newServer.getIp())
                            .withName(newServer.getName())
                            .withIcon(newServer.getIcon())
                    );
                    return item.storageEntry.getServer();
                }).whenComplete(Schedulers.javafx(), (updatedServer, exception) -> {
                    if (exception != null) {
                        LOG.warning("Failed to save server data.", exception);
                    }

                    if (exception instanceof ServersDatFile.FileModifiedExternalException e) {
                        showModifiedErrorDialog(e);
                        return;
                    }

                    if (exception instanceof IllegalStateException e) {
                        showModifiedErrorDialog(e);
                        return;
                    }

                    if (oldServer.equals(updatedServer)) {
                        return;
                    }
                    int index = serverListEntries.indexOf(item);
                    if (index != -1) {
                        serverListEntries.set(index, item.renew());
                    }
                    updateServerList();

                }).start();
            })));
        }
    }

    private void delete(ServerListItem... items) {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("server.manage.file.delete"),
                () -> Task.supplyAsync(Schedulers.io(), () -> {
                    List<ServerListItem> result = new ArrayList<>();
                    for (ServerListItem item : items) {
                        try {
                            item.storageEntry.delete();
                            result.add(item);
                        } catch (ServersDatFile.FileModifiedExternalException | IllegalStateException e) {
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

                    if (exception instanceof ServersDatFile.FileModifiedExternalException e) {
                        showModifiedErrorDialog(e);
                        return;
                    }

                    if (exception instanceof IllegalStateException e) {
                        showModifiedErrorDialog(e);
                        return;
                    }

                    if (result == null || result.isEmpty()) return;
                    Controllers.showToast(i18n("server.manage.file.deleted.toast", result.size()));
                    serverListEntries.removeAll(result);
                    updateServerList();
                }).start(),
                null
        );
    }

    private void showModifiedErrorDialog(@NotNull ServersDatFile.FileModifiedExternalException e) {
        Controllers.dialog(new MessageDialogPane.Builder(i18n("server.manage.file.error.external"), null, MessageDialogPane.MessageType.ERROR)
                .addCancel(i18n("button.refresh"), this::refresh)
                .build()
        );
    }

    private void showModifiedErrorDialog(@NotNull IllegalStateException e) {
        Controllers.dialog(new MessageDialogPane.Builder(i18n("server.manage.file.error.state"), null, MessageDialogPane.MessageType.ERROR)
                .addCancel(i18n("button.refresh"), this::refresh)
                .build()
        );
    }

    public void launchAndEnterServer(ServerListItem item) {
        if (gameInstance != null) {
            Instances.launchAndEnterServer(gameInstance, item.getServer().getIp());
        }
    }

    public void copyServerIp(ServerListItem item) {
        FXUtils.copyText(item.getServer().getIp(), i18n("server.manage.ip.copied.toast"));
    }

    public void copyToInstance(ServerListItem item) {
        if (gameInstance != null) {
            addServerToInstance(item.getServer(), gameInstance);
        }
    }

    public void generateLaunchScript(ServerListItem item) {
        if (gameInstance != null) {
            Instances.generateLaunchScriptForQuickConnectServer(gameInstance, item.getServer().getIp());
        }
    }

    private void addServer() {
        runInFX(() -> Controllers.dialog(new EditServerDialog(EditServerDialog.Type.ADD, null, this::addServer)));
    }

    public static class ServerAddressMaskPane extends HBox {
        private final Label atLabel = new Label();
        private final Label realServerLabel = new Label();
        private final Label queryLabel = new Label();

        public ServerAddressMaskPane(@Nullable String serverIP) {
            getChildren().add(atLabel);
            getChildren().add(realServerLabel);
            getChildren().add(queryLabel);

            atLabel.setPickOnBounds(true);
            queryLabel.setPickOnBounds(true);

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
                return;
            }
            int queryStart = serverIP.lastIndexOf('?');
            if (queryStart != -1) {
                String queryStr = serverIP.substring(queryStart);
                queryLabel.setText(queryStr);
                serverIP = serverIP.substring(0, queryStart);
            } else {
                queryLabel.setText("");
            }

            int atEnd = serverIP.indexOf('@');
            if (atEnd != -1) {
                String atStr = serverIP.substring(0, atEnd + 1);
                atLabel.setText(atStr);
                serverIP = serverIP.substring(atEnd + 1);
            } else {
                atLabel.setText("");
            }

            realServerLabel.setText(serverIP);
        }

        public void labelAddStyleClass(String styleClass) {
            atLabel.getStyleClass().add(styleClass);
            realServerLabel.getStyleClass().add(styleClass);
            queryLabel.getStyleClass().add(styleClass);
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

        public EditServerDialog(Type type, @Nullable Server reference, Consumer<Server> handleCallback) {
            this.type = type;
            this.reference = reference;
            this.handleCallback = handleCallback;

            getStyleClass().add("skin-pane");

            String referenceServerName;
            String referenceServerIP;
            Server.ServerPackStatus referenceServerPackStatus;
            if (reference != null) {
                referenceServerName = reference.getName();
                referenceServerIP = reference.getIp();
                referenceServerPackStatus = reference.getServerPackStatus();
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
                        new Validator(i18n("server.address.malformed"), str -> {
                            if (StringUtils.isBlank(str))
                                return true;
                            try {
                                ServerAddress.parse(str);
                                return true;
                            } catch (Exception ignored) {
                                return false;
                            }
                        })
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

            btnCancel.setText(i18n("button.cancel"));
            btnCancel.getStyleClass().add("dialog-cancel");
            onEscPressed(this, btnCancel::fire);

            spinner.getStyleClass().add("small-spinner-pane");
            spinner.setContent(btnAccept);

            HBox actions = new HBox(spinner, btnCancel);
            actions.setAlignment(Pos.CENTER_RIGHT);

            rootLayout.setActions(actions);

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

                fireEvent(new DialogCloseEvent());
                if (reference != null) {
                    handleCallback.accept(reference
                            .withName(serverName)
                            .withIp(serverIP)
                            .withServerPackStatus(serverPackStatus)
                    );
                } else {
                    handleCallback.accept(Server.createServer(serverPackStatus, serverName, serverIP));
                }
            });
        }

        private void onCancel() {
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

    public static final class ServerListItem {
        private final @NotNull ServersDatFile.ServerEntry storageEntry;
        private final @NotNull Set<HMCLGameInstance> holdInstances;
        private final Image serverFaviconImage;

        public ServerListItem(@NotNull ServersDatFile.ServerEntry storageEntry, @NotNull Set<HMCLGameInstance> holdInstances) {
            this.storageEntry = storageEntry;
            this.holdInstances = holdInstances;
            this.serverFaviconImage = parseServerFaviconImageOrDefault(getServer().getIcon());
        }

        public Server getServer() {
            return storageEntry.getServer();
        }

        public ServerListItem renew() {
            return new ServerListItem(storageEntry, holdInstances);
        }
    }

    private final class ServerCell extends ItemCell<ServerListItem> {

        private final ServersPage page;

        private final ImageContainer serverIcon = new ImageContainer(32);
        private final TwoLineListItem contentLine1 = new TwoLineListItem();
        private final ServerAddressMaskPane contentLine2AddressMaskPane = new ServerAddressMaskPane("");

        private final Tooltip statusBtnTooltip = new Tooltip();

        private Subscription serverStatusResultValueSubscription;
        private Subscription serverStatusPingingValueSubscription;

        public ServerCell(ListView<ServerListItem> listView, ServersPage page) {
            super(listView);
            this.page = page;

            getStyleClass().add("server-list-cell");

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            VBox content = new VBox(contentLine1, contentLine2AddressMaskPane);
            HBox.setHgrow(content, Priority.ALWAYS);
            setSelectable();

            contentLine2AddressMaskPane.labelAddStyleClass("subtitle");

            JFXButton editBtn = FXUtils.newToggleButton4(SVG.EDIT);
            FXUtils.installFastTooltip(editBtn, i18n("server.manage.edit"));
            editBtn.setOnAction(event -> {
                @Nullable ServerListItem item = getItem();
                if (item != null)
                    page.editServer(item);
            });

            JFXButton launchBtn = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH);
            FXUtils.installFastTooltip(launchBtn, i18n("instance.launch"));
            launchBtn.setOnAction(event -> {
                @Nullable ServerListItem item = getItem();
                if (item != null) {
                    page.launchAndEnterServer(item);
                }
            });

            JFXButton btnMore = FXUtils.newToggleButton4(SVG.MORE_VERT);
            btnMore.setOnAction(event -> {
                @Nullable ServerListItem item = getItem();
                if (item != null) {
                    showPopupMenu(item, JFXPopup.PopupHPosition.RIGHT, 0, getHeight());
                }
            });

            container.getChildren().setAll(
                    serverIcon,
                    content,
                    editBtn,
                    launchBtn,
                    btnMore
            );

            StackPane.setMargin(container, new Insets(8));
            setContent(container);
        }

        @Override
        protected void updateListItem(@Nullable ServerListItem item) {
            if (serverStatusPingingValueSubscription != null) {
                serverStatusPingingValueSubscription.unsubscribe();
                serverStatusPingingValueSubscription = null;
            }
            if (serverStatusResultValueSubscription != null) {
                serverStatusResultValueSubscription.unsubscribe();
                serverStatusResultValueSubscription = null;
            }

            this.contentLine1.getTags().clear();

            if (item == null) {
                serverIcon.setImage(null);
                contentLine1.setTitle("");
                statusBtnTooltip.setText("");
                contentLine2AddressMaskPane.set("");
            } else {
                serverIcon.setImage(item.serverFaviconImage);
                contentLine1.setTitle(item.getServer().getName() != null ? parseColorEscapes(item.getServer().getName()) : "");

                contentLine2AddressMaskPane.set(item.getServer().getIp());

                List<String> tags = new ArrayList<>();
                for (HMCLGameInstance checkInstance : item.holdInstances) {
                    if (checkInstance.equals(gameInstance)) {
                        tags.add(0, i18n("server.tag.hold.current"));
                    } else {
                        tags.add(checkInstance.getId().id());
                    }
                }
                contentLine1.addTags(tags);
            }
        }

        private void showPopupMenu(ServersPage.ServerListItem holder, JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
            PopupMenu popupMenu = new PopupMenu();
            JFXPopup popup = new JFXPopup(popupMenu);

            IconedMenuItem copyToInstanceMenuItem = new IconedMenuItem(SVG.CONTENT_COPY, i18n("server.manage.copy.to.instance"),
                    () -> page.copyToInstance(holder), popup
            );
            popupMenu.getContent().addAll(
                    new IconedMenuItem(SVG.EDIT, i18n("server.manage.edit"), () ->
                            page.editServer(holder), popup
                    ),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch_and_connect_server"), () ->
                            page.launchAndEnterServer(holder), popup
                    ),
                    new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), () ->
                            page.generateLaunchScript(holder), popup
                    ),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.CONTENT_COPY, i18n("server.manage.ip.copy"), () ->
                            page.copyServerIp(holder), popup
                    ),
                    new MenuSeparator(),
                    copyToInstanceMenuItem,
                    new IconedMenuItem(SVG.DELETE_FOREVER, i18n("server.manage.file.delete"), () ->
                            page.delete(holder), popup
                    )
            );
            if (page.gameInstance != null) {
                copyToInstanceMenuItem.setDisable(holder.storageEntry.getServersDatFile().getFile().equals(page.gameInstance.getServersDatFilePath()));
            }

            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
            popup.show(this, vPosition, hPosition, initOffsetX, vPosition == JFXPopup.PopupVPosition.TOP ? initOffsetY : -initOffsetY);
        }
    }

    private JFXCheckBox createCheckBox(Consumer<JFXCheckBox> consumer) {
        JFXCheckBox box = new JFXCheckBox();
        // change style.
        consumer.accept(box);
        return box;
    }

    /// Shows the requested toolbar and focuses the search input when appropriate.
    private void changeToolbar(HBox newToolbar) {
        Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
            if (newToolbar == searchBar) {
                Platform.runLater(searchField::requestFocus);
            }
        }
    }

    /// Applies the search text to the displayed items.
    private void search() {
        isSearching.set(true);

        String queryString = searchField.getText();
        if (StringUtils.isBlank(queryString)) {
            filteredItems.setPredicate(null);
        } else {
            Predicate<@Nullable String> predicate;
            try {
                predicate = StringUtils.compileQuery(queryString);
            } catch (Throwable e) {
                LOG.warning("Illegal regular expression", e);
                filteredItems.setPredicate(item -> false);
                return;
            }

            filteredItems.setPredicate(item -> {
                String serverIp = item.getServer().getIp();
                String serverName = item.getServer().getName();

                return predicate.test(serverIp) || predicate.test(serverName);
            });
        }
    }
}
