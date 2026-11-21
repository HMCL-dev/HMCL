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
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.image.apng.argb8888.Argb8888Bitmap;
import org.jackhuang.hmcl.image.apng.argb8888.Argb8888Processor;
import org.jackhuang.hmcl.image.apng.argb8888.DefaultImageArgb8888Director;
import org.jackhuang.hmcl.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.image.apng.error.PngException;
import org.jackhuang.hmcl.image.apng.reader.DefaultPngChunkReader;
import org.jackhuang.hmcl.image.apng.reader.PngReadHelper;
import org.jackhuang.hmcl.image.apng.reader.PngSource;
import org.jackhuang.hmcl.server.Server;
import org.jackhuang.hmcl.server.ServersDatFile;
import org.jackhuang.hmcl.server.status.ServerStatus;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jackhuang.hmcl.server.status.pinger.ModernServerStatusPinger;
import org.jackhuang.hmcl.server.status.pinger.ServerStatusPinger;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.javafx.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ServersPage extends ListPageBase<ServersPage.ServerListItem> {
    private final DataStorage dataStorage = new DataStorage();
    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private @Nullable HMCLGameInstance gameInstance;
    private List<ServerListItem> serverListEntries = new ArrayList<>();

    private int refreshCount = 0;

    public static class DataStorage {
        private volatile Map<Path, ServersDatFile> serversDatFileMap = Map.of();
        private volatile Map<Path, Set<HMCLGameInstance>> holdInstancesMap = Map.of();
        private volatile Map<Path, Exception> failedExceptions = Map.of();
        private final Lock handleLock = new ReentrantLock();

        public <T> @Nullable T withLock(@NotNull Callable<@Nullable T> callable) throws Exception {
            handleLock.lock();
            try {
                return callable.call();
            } finally {
                handleLock.unlock();
            }
        }

        public void reload() throws Exception {
            withLock(() -> {
                Map<Path, ServersDatFile> serversDatFileMap = new LinkedHashMap<>();
                Map<Path, Set<HMCLGameInstance>> holdInstancesMap = new LinkedHashMap<>();
                Map<Path, Exception> failedExceptions = new HashMap<>();

                for (HMCLGameInstance instance : GameDirectoryManager.getSelectedRepository().getSnapshot().getInstances()) {
                    Path serversDatFilePath = instance.getServersDatFilePath();

                    if (failedExceptions.containsKey(serversDatFilePath)) {
                        continue;
                    }

                    Set<HMCLGameInstance> holdInstances = holdInstancesMap.computeIfAbsent(serversDatFilePath, key -> new LinkedHashSet<>());
                    holdInstances.add(instance);
                    if (!serversDatFileMap.containsKey(serversDatFilePath)) {
                        try {
                            var serversDatFile = this.serversDatFileMap.get(serversDatFilePath);
                            if (serversDatFile != null) {
                                serversDatFile.reload();
                            } else {
                                serversDatFile = new ServersDatFile(serversDatFilePath);
                            }
                            serversDatFileMap.put(serversDatFilePath, serversDatFile);
                        } catch (IOException e) {
                            failedExceptions.put(serversDatFilePath, e);
                            LOG.error("Failed to load servers from dat file: " + serversDatFilePath, e);
                        }
                    }
                }

                this.serversDatFileMap = Collections.unmodifiableMap(serversDatFileMap);
                this.holdInstancesMap = Collections.unmodifiableMap(holdInstancesMap);
                this.failedExceptions = Collections.unmodifiableMap(failedExceptions);
                return null;
            });
        }
    }

    public ServersPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");

        showAll.addListener(e -> updateServerList());

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));
    }

    public static @NotNull Image parseServerFaviconImageOrDefault(@Nullable String imageBase64String) {
        if (imageBase64String != null && !imageBase64String.isEmpty()) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64Utils.decode(imageBase64String))) {
                // png format.
                Argb8888Processor<Argb8888Bitmap> processor = new Argb8888Processor<>(new DefaultImageArgb8888Director());
                Argb8888Bitmap bitmap = PngReadHelper.read(bais, new DefaultPngChunkReader<>(processor) {
                    @Override
                    public void readHeaderChunk(PngSource source, int dataLength) throws IOException, PngException {
                        PngHeader header = PngHeader.from(source.getDis());
                        if (header.height != 64) {
                            throw new IOException("Bad height, expected 64, actual " + header.height);
                        }
                        if (header.width != 64) {
                            throw new IOException("Bad width, expected 64, actual " + header.width);
                        }
                        seenHeader = true;
                        processor.processHeader(header);
                    }
                });

                WritableImage image = new WritableImage(64, 64);
                image.getPixelWriter().setPixels(0, 0, 64, 64,
                        PixelFormat.getIntArgbInstance(), bitmap.array(),
                        0, 64);

                return image;
            } catch (Exception e) {
                LOG.warning("Failed to decode server icon", e);
            }
        }
        return FXUtils.newBuiltinImage("/assets/img/unknown_server.png");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ServerListPageSkin();
    }

    public void showServerStatus(ServerListItem item) {
        if (gameInstance != null) {
            runInFX(item::openServerStatusPane);
        }
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        refresh(true);
    }

    private void updateServerList() {
        if (serverListEntries == null || gameInstance == null) {
            getItems().clear();
            return;
        }

        var stream = serverListEntries.stream();
        if (!showAll.get()) {
            stream = stream.filter(entry -> entry.storageEntry.getServersDatFile().getFile().equals(gameInstance.getServersDatFilePath()));
        }

        getItems().setAll(stream.toList());
    }

    private void refresh() {
        refresh(false);
    }

    private void refresh(boolean init) {
        HMCLGameInstance gameInstance = this.gameInstance;
        if (gameInstance == null) {
            return;
        }

        int currentRefresh = ++refreshCount;

        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            List<ServerListItem> result;
            result = dataStorage.withLock(() -> {
                dataStorage.reload();
                return dataStorage.serversDatFileMap.values().stream()
                        .flatMap(e -> e.getEntries().stream())
                        .filter(e -> !e.getServer().isHidden())
                        .map(e -> new ServerListItem(e, dataStorage.holdInstancesMap.getOrDefault(e.getServersDatFile().getFile(), Set.of())))
                        .toList();
            });
            return result;
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

            if (init) {
                if (dataStorage.failedExceptions.containsKey(gameInstance.getServersDatFilePath())) {
                    Controllers.dialog(new MessageDialogPane.Builder(i18n("server.manage.file.error.load"), null, MessageDialogPane.MessageType.ERROR)
                            .addCancel(i18n("button.continue"), () -> {
                                // NOP
                            })
                            .build()
                    );
                }
            }
        }).start();
    }

    public void addServerToInstance(Server server, @NotNull HMCLGameInstance toInstance, @NotNull ObservableServerStatus observableServerStatus) {
        Task.supplyAsync(Schedulers.io(), () -> dataStorage.withLock(() -> {
            ServersDatFile datFile = dataStorage.serversDatFileMap.get(toInstance.getServersDatFilePath());
            if (datFile == null) {
                // try reload
                dataStorage.reload();
                datFile = dataStorage.serversDatFileMap.get(toInstance.getServersDatFilePath());
                if (datFile == null) {
                    runInFX(() -> Controllers.dialog(new MessageDialogPane.Builder(i18n("server.manage.file.error.notexist"), null, MessageDialogPane.MessageType.ERROR)
                            .addCancel(i18n("button.refresh"), this::refresh)
                            .build()
                    ));
                    return null;
                }
            }
            ServersDatFile.ServerEntry serverEntry = datFile.addServer(server);
            Set<HMCLGameInstance> instances = dataStorage.holdInstancesMap.get(serverEntry.getServersDatFile().getFile());
            instances.add(toInstance);
            return new Pair<>(serverEntry, instances);
        })).whenComplete(Schedulers.javafx(), (resultPair, exception) -> {
            if (resultPair == null) return;

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

            serverListEntries.add(new ServerListItem(resultPair.key(), resultPair.value(), observableServerStatus.copy()));
            updateServerList();
        }).start();
    }

    public void editServer(ServerListItem item) {
        if (gameInstance != null) {
            runInFX(() -> Controllers.dialog(new EditServerDialog(EditServerDialog.Type.EDIT, item.getServer(), (newServer, statusResult) -> {
                if (!serverListEntries.contains(item)) {
                    return;
                }

                Server oldServer = item.getServer();
                Task.supplyAsync(Schedulers.io(), () -> {
                    dataStorage.withLock(() -> {
                        item.storageEntry.modify(s -> s
                                .withServerPackStatus(newServer.getServerPackStatus())
                                .withIp(newServer.getIp())
                                .withName(newServer.getName())
                                .withIcon(newServer.getIcon())
                        );
                        return null;
                    });
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
                        ObservableServerStatus observableServerStatus = new ObservableServerStatus(newServer.getIp());
                        if (statusResult != null) {
                            observableServerStatus.resultProperty.set(statusResult);
                        } else if (Objects.equals(oldServer.getIp(), newServer.getIp())) {
                            observableServerStatus.resultProperty.set(item.observableServerStatus.resultProperty.get());
                        }

                        serverListEntries.set(index, item.renew(observableServerStatus));
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
                    dataStorage.withLock(() -> {
                        for (ServerListItem item : items) {
                            try {
                                // ignore valid check.
                                item.storageEntry.delete();
                                result.add(item);
                            } catch (ServersDatFile.FileModifiedExternalException | IllegalStateException e) {
                                throw e;
                            } catch (IOException e) {
                                LOG.error("Failed to delete server entry.", e);
                            }
                        }
                        return null;
                    });
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

    private void showModifiedErrorDialog(@NotNull ServersDatFile.FileModifiedExternalException ignored) {
        Controllers.dialog(new MessageDialogPane.Builder(i18n("server.manage.file.error.external"), null, MessageDialogPane.MessageType.ERROR)
                .addCancel(i18n("button.refresh"), this::refresh)
                .build()
        );
    }

    private void showModifiedErrorDialog(@NotNull IllegalStateException ignored) {
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
            addServerToInstance(item.getServer(), gameInstance, item.observableServerStatus);
        }
    }

    public void generateLaunchScript(ServerListItem item) {
        if (gameInstance != null) {
            Instances.generateLaunchScriptForQuickConnectServer(gameInstance, item.getServer().getIp());
        }
    }

    private void addServer() {
        HMCLGameInstance gameInstance = this.gameInstance;
        if (gameInstance != null) {
            runInFX(() -> Controllers.dialog(new EditServerDialog(EditServerDialog.Type.ADD, null, (server, statusResult) -> {
                ObservableServerStatus observableServerStatus = new ObservableServerStatus(server.getIp());
                if (statusResult != null) {
                    observableServerStatus.resultProperty.set(statusResult);
                }

                addServerToInstance(server, gameInstance, observableServerStatus);
            })));
        }
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
        private final EditedCallback handleCallback;

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

        private final SimpleObjectProperty<ServerStatusResult.FailureResult> lastFailedStatusResultProperty = new SimpleObjectProperty<>();

        private boolean cancelled;

        public EditServerDialog(
                @NotNull Type type,
                @Nullable Server reference,
                @NotNull EditedCallback handleCallback
        ) {
            this.type = type;
            this.reference = reference;
            this.handleCallback = handleCallback;
            this.cancelled = false;

            getStyleClass().add("skin-pane");

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
            btnAccept.setOnAction(e -> onAdd());

            ChangeListener<String> contentChangeListener = (ignored0, ignored1, ignored2) -> {
                if (lastFailedStatusResultProperty.get() != null) {
                    clearFailedResult();
                }
            };
            serverIpField.textProperty().addListener(contentChangeListener);
        }

        private @NotNull Server buildServer() {
            String serverName;
            String serverIP = Objects.requireNonNullElse(serverIpField.getText(), "");
            Server.ServerPackStatus serverPackStatus = serverPackStatusField.getValue();
            if (serverNameField.getText() == null || serverNameField.getText().isEmpty()) {
                serverName = i18n("server.name.def");
            } else {
                serverName = serverNameField.getText();
            }

            if (reference != null) {
                return reference.withName(serverName).withIp(serverIP).withServerPackStatus(serverPackStatus);
            } else {
                return Server.createServer(serverPackStatus, serverName, serverIP);
            }
        }

        private void stillFailedText(String errorMessage) {
            lblErrorLabel.setText(errorMessage);
            btnAccept.setText(switch (type) {
                case EDIT -> i18n("server.manage.edit.still");
                case ADD -> i18n("server.manage.add.still");
            });
        }

        private void clearFailedResult() {
            lastFailedStatusResultProperty.set(null);
            lblErrorLabel.setText("");
            btnAccept.setText(switch (type) {
                case EDIT -> i18n("server.manage.edit.accept");
                case ADD -> i18n("server.manage.add.accept");
            });
        }

        private void onAdd() {
            ServerStatusResult.FailureResult lastFailedResult = lastFailedStatusResultProperty.get();
            if (lastFailedResult != null) {
                fireEvent(new DialogCloseEvent());
                handleCallback.callback(buildServer(), lastFailedResult);
                return;
            }

            if (reference != null) {
                String referenceIp = reference.getIp();
                if (Objects.equals(referenceIp, serverIpField.getText())) {
                    fireEvent(new DialogCloseEvent());
                    handleCallback.callback(buildServer(), null);
                    return;
                }
            }

            body.setDisable(true);
            spinner.showSpinner();

            Task.supplyAsync(Schedulers.io(), () ->
                    ServerStatusPinger.getStatus(Objects.requireNonNullElse(serverIpField.getText(), ""))
            ).whenComplete(Schedulers.javafx(), (result, e) -> {
                spinner.hideSpinner();
                body.setDisable(false);

                if (e != null) {
                    LOG.error("Failed to get server status", e);
                    return;
                }

                ServerStatus status = result.getIfSucceed();
                if (status == null) {
                    ServerStatusResult.FailureResult failedResult = (ServerStatusResult.FailureResult) result;
                    lastFailedStatusResultProperty.set(failedResult);
                    ServerStatusResult.FailureResult.Reason reason = failedResult.getReason();
                    stillFailedText(switch (reason) {
                        case EXCEPTION -> i18n("server.manage.status.error");
                        case UNKNOWN_HOST -> i18n("server.manage.status.error.unknownhost");
                        case BLOCKED_BY_MOJANG -> i18n("server.manage.status.error.blocked");
                    });
                } else {
                    if (getScene() == null || cancelled) {
                        return;
                    }
                    fireEvent(new DialogCloseEvent());
                    handleCallback.callback(buildServer(), result);
                }
            }).start();
        }

        private void onCancel() {
            this.cancelled = true;
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

        @FunctionalInterface
        public interface EditedCallback {
            void callback(@NotNull Server server, @Nullable ServerStatusResult statusResult);
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
                }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                    // expired.
                    if (currentRefreshId != refreshId.get()) return;

                    try {
                        if (exception == null) {
                            resultProperty.set(result);
                        }
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

        public ObservableServerStatus copy() {
            ObservableServerStatus observableServerStatus = new ObservableServerStatus(serverIp);
            observableServerStatus.resultProperty.set(resultProperty.get());
            return observableServerStatus;
        }
    }

    private final class ServerListPageSkin extends SkinBase<ServersPage> {
        private final JFXListView<ServersPage.ServerListItem> listView = new JFXListView<>();
        private final JFXTextField searchField = new JFXTextField();
        private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
        private final PauseTransition searchPause = new PauseTransition(Duration.millis(100));

        private final TransitionPane toolbarPane = new TransitionPane();
        private final HBox searchBar = new HBox();
        private final HBox toolbarNormal = new HBox();
        private final HBox toolbarSelecting = new HBox();

        ServerListPageSkin() {
            super(ServersPage.this);

            StackPane rootStackPane = new StackPane();
            getChildren().setAll(rootStackPane);
            rootStackPane.setPadding(new Insets(10));
            rootStackPane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            rootStackPane.getChildren().setAll(root);
            root.getStyleClass().add("no-padding");

            listView.getStyleClass().add("no-horizontal-scrollbar");
            listView.setCellFactory(param -> new ServerListCell(listView, ServersPage.this));
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
                            box.selectedProperty().bindBidirectional(ServersPage.this.showAll);
                        }),
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, ServersPage.this::refresh),
                        createToolbarButton2(i18n("server.manage.add"), SVG.ADD, ServersPage.this::addServer),
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
                    Bindings.bindContent(listView.getItems(), ServersPage.this.getItems());
                });
                onEscPressed(searchField, closeSearchBar::fire);
                searchBar.getChildren().setAll(
                        createCheckBox(box -> {
                            box.setText(i18n("server.manage.show_all"));
                            box.selectedProperty().bindBidirectional(ServersPage.this.showAll);
                        }),
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, ServersPage.this::refresh),
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
                            ServersPage.this.delete(listView.getSelectionModel().getSelectedItems().toArray(ServerListItem[]::new));
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

            Bindings.bindContent(listView.getItems(), ServersPage.this.getItems());
            ServersPage.this.getItems().addListener((ListChangeListener<? super ServerListItem>) c -> {
                if (isSearching.get()) {
                    search();
                }
            });

            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.loadingProperty().bind(ServersPage.this.loadingProperty());

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

            Bindings.unbindContent(listView.getItems(), ServersPage.this.getItems());

            String queryString = searchField.getText();
            if (StringUtils.isBlank(queryString)) {
                listView.getItems().setAll(ServersPage.this.getItems());
            } else {
                listView.getItems().clear();

                Predicate<@Nullable String> predicate;
                try {
                    predicate = StringUtils.compileQuery(queryString);
                } catch (Throwable e) {
                    LOG.warning("Illegal regular expression", e);
                    return;
                }

                for (ServerListItem item : ServersPage.this.getItems()) {
                    String serverIp = item.getServer().getIp();
                    String serverName = item.getServer().getName();

                    if (predicate.test(serverIp) || predicate.test(serverName)) {
                        listView.getItems().add(item);
                    }
                }
            }
        }
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
            this.observableServerStatus = Objects.requireNonNullElseGet(reference, () -> new ObservableServerStatus(server.getIp()));

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

                        Task.runAsync(Schedulers.javafx(), () -> {
                            if (!(Controllers.getGameInstancePage().getSelectedTab() instanceof ServersPage)) {
                                Controllers.getGameInstancePage().showServerList();
                            }

                            ServersPage serverListPage = Controllers.getGameInstancePage().getServersTab().getNode();
                            if (serverListPage != null) {
                                serverListPage.addServerToInstance(server, currentGameInstance, observableServerStatus);
                            }
                        }).start();
                    }
                });
                actions.getChildren().add(addToInstallBtn);
            }

            JFXButton launchBtn = new JFXButton(i18n("instance.launch"));
            launchBtn.getStyleClass().add("dialog-accept");
            launchBtn.setOnAction(e -> {
                fireEvent(new DialogCloseEvent());
                Instances.launchAndEnterServer(currentGameInstance, server.getIp());
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

        public static ServerStatusDialog fromDnD(Server server) {
            return new ServerStatusDialog(null, server, true, null);
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

            Image serverIcon = parseServerFaviconImageOrDefault(server.getIcon());
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
            title.setTitle(server.getName());
            titleContent.getChildren().add(title);

            ServerAddressMaskPane addressMaskPane = new ServerAddressMaskPane(server.getIp());
            addressMaskPane.labelAddStyleClass("subtitle");
            titleContent.getChildren().add(addressMaskPane);

            if (status != null) {
                VBox motdContentBox = new VBox();
                motdContentBox.setSpacing(5);
                motdContentBox.setAlignment(Pos.CENTER_LEFT);


                String[] motdLines = MinecraftChatComponentUtils
                        .toPlainText(status.description())
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

                addRealAddressInfo(status, rootContent);
                addPlayersInfo(status, rootContent);
                addServerTypeInfo(status, rootContent);
                addPlayVersionInfo(status, rootContent);

                rootContent.getChildren().add(createDescriptionRow(i18n("server.latency"), String.format("%,dms", status.networkLatency()), null, new Insets(0, 4, 0, 4)));

            }
        }

        private void addRealAddressInfo(@NotNull ServerStatus status, @NotNull VBox content) {
            ServerAddress originalServerAddress = status.originalServerAddress();
            InetSocketAddress resolvedAddress = status.resolvedAddress();
            boolean isOriginalAndResolvedSame = originalServerAddress.getHostAndPort().equals(resolvedAddress.getHostName(), resolvedAddress.getPort());
            if (isOriginalAndResolvedSame) return;
            String hostString = resolvedAddress.getHostString();
            if (hostString.endsWith(".")) {
                hostString = hostString.substring(0, hostString.length() - 1);
            }
            if (hostString.equals(originalServerAddress.getHostAndPort().host()) && resolvedAddress.getPort() == originalServerAddress.getHostAndPort().port())
                return;
            content.getChildren().add(createDescriptionRow(i18n("server.realaddress"), hostString + ":" + resolvedAddress.getPort(), null, new Insets(0, 4, 0, 4)));
        }

        private void addPlayVersionInfo(@NotNull ServerStatus status, @NotNull VBox content) {
            ServerStatus.ProtocolVersion protocolVersion = status.protocolVersion();
            String versionTypeStr = switch (protocolVersion.versionType()) {
                case RELEASE -> i18n("server.playversion.type.release");
                case SNAPSHOT -> i18n("server.playversion.type.snapshot");
                case LEGACY -> i18n("server.playversion.type.legacy");
                case UNKNOWN -> i18n("server.playversion.type.unknown");
            };
            if (protocolVersion.specifiedProtocolName() != null) {
                content.getChildren().add(createDescriptionRow(i18n("server.playversion"), i18n("server.playversion.full",
                        MinecraftChatComponentUtils.toPlainText(new JsonPrimitive(protocolVersion.specifiedProtocolName())),
                        versionTypeStr,
                        String.format("%,d", protocolVersion.protocolNumber())
                ), null, new Insets(0, 4, 0, 4)));
            } else {
                content.getChildren().add(createDescriptionRow(i18n("server.playversion"), i18n("server.playversion.protocolnumberonly",
                        versionTypeStr,
                        String.format("%,d", protocolVersion.protocolNumber())
                ), null, new Insets(0, 4, 0, 4)));
            }
        }

        private void addPlayersInfo(@NotNull ServerStatus status, @NotNull VBox content) {
            ServerStatus.PlayerList playerList = status.playerList();
            if (!playerList.numbered()) return;

            String playersTooltip = null;
            if (playerList.samples() != null && !playerList.samples().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.join("\n", playerList.samples().stream()
                        .map(sample -> {
                            if (sample.isAnonymous()) {
                                return i18n("server.anonymous_player");
                            }
                            return MinecraftChatComponentUtils.toPlainText(new JsonPrimitive(sample.name()));
                        })
                        .toList())
                );

                if (playerList.samples().size() < playerList.online()) {
                    sb.append("\n");
                    sb.append(i18n("server.and_more_players", String.format("%,d", playerList.online() - playerList.samples().size())));
                }

                playersTooltip = sb.toString();
            }

            content.getChildren().add(createDescriptionRow(i18n("server.onlineplayers"), String.format("%,d/%,d", playerList.online(), playerList.max()), playersTooltip, new Insets(0, 4, 0, 4)));
        }

        private void addServerTypeInfo(@NotNull ServerStatus status, @NotNull VBox content) {
            ServerStatus.ServerModList modList = status.modList();
            String modsTooltip = null;
            int showCount = 15;

            if (modList != null && !modList.modList().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.join("\n", modList.modList().stream()
                        .limit(showCount)
                        .map(it -> it.modId() + " - " + it.version())
                        .toList())
                );
                if (showCount < modList.modList().size()) {
                    sb.append("\n");
                    sb.append(i18n("server.and_more_mods", String.format("%,d", modList.modList().size() - showCount)));
                }

                modsTooltip = sb.toString();
            }
            content.getChildren().add(createDescriptionRow(i18n("server.type"), modList != null ? i18n("server.type.mod", modList.type(), modList.modList().size()) : i18n("server.type.vanilla"), modsTooltip, new Insets(0, 4, 0, 4)));
        }

        private void onClose() {
            fireEvent(new DialogCloseEvent());
        }
    }

    public final class ServerListItem {
        private final @NotNull ServersDatFile.ServerEntry storageEntry;
        private final @NotNull Set<HMCLGameInstance> holdInstances;
        private final @NotNull ObservableServerStatus observableServerStatus;
        private final @NotNull ReadOnlyObjectWrapper<Image> displayServerImageWrapper;

        public ServerListItem(
                @NotNull ServersDatFile.ServerEntry storageEntry,
                @NotNull Set<HMCLGameInstance> holdInstances
        ) {
            this(storageEntry, holdInstances, new ObservableServerStatus(storageEntry.getServer().getIp()));
        }

        public ServerListItem(
                @NotNull ServersDatFile.ServerEntry storageEntry,
                @NotNull Set<HMCLGameInstance> holdInstances,
                @NotNull ObservableServerStatus observableServerStatus
        ) {
            this.storageEntry = storageEntry;
            this.holdInstances = holdInstances;
            this.observableServerStatus = observableServerStatus;

            Image storageServerImage = parseServerFaviconImageOrDefault(getServer().getIcon());
            this.displayServerImageWrapper = new ReadOnlyObjectWrapper<>(storageServerImage);
            Subscription.subscribe(this.observableServerStatus.resultProperty(), serverStatusResult -> {
                if (serverStatusResult instanceof ServerStatusResult.SuccessResult successResult) {
                    displayServerImageWrapper.set(parseServerFaviconImageOrDefault(successResult.getServerStatus().favicon()));
                } else {
                    displayServerImageWrapper.set(storageServerImage);
                }
            });
        }

        public @NotNull Image getDisplayServerImage() {
            return displayServerImageWrapper.get();
        }

        public Server getServer() {
            return storageEntry.getServer();
        }

        public ServerListItem renew() {
            return new ServerListItem(storageEntry, holdInstances);
        }

        public ServerListItem renew(@NotNull ObservableServerStatus specifiedObservableServerStatus) {
            return new ServerListItem(storageEntry, holdInstances, specifiedObservableServerStatus);
        }

        public void openServerStatusPane() {
            Controllers.dialog(new ServerStatusDialog(observableServerStatus, getServer(), ServersPage.this.gameInstance));
        }

        public void checkAndSaveServerIcon(@NotNull ServerStatus status) {
            if (status.pinger() instanceof ModernServerStatusPinger &&
                    !Objects.equals(status.favicon(), getServer().getIcon())) {
                // save latest icon

                displayServerImageWrapper.set(parseServerFaviconImageOrDefault(status.favicon()));
                Task.supplyAsync(Schedulers.io(), () -> {
                    Server result;
                    result = dataStorage.withLock(() -> {
                        if (status.originalServerAddress().getRawAddress().equals(getServer().getIp())) {
                            if (storageEntry.isValid()) {
                                storageEntry.modify(s -> s.withIcon(status.favicon()));
                                return storageEntry.getServer();
                            } else {
                                return null;
                            }
                        } else {
                            return null;
                        }
                    });
                    return result;
                }).whenComplete(Schedulers.javafx(), (updated, exception) -> {
                    if (updated == null) return;

                    if (exception != null) {
                        LOG.warning("Failed to save server favicon.", exception);
                    }
                }).start();
            }
        }
    }

    private final class ServerListCell extends MDListCell<ServerListItem> {

        private final ServersPage page;

        private final ImageContainer serverIcon = new ImageContainer(32);
        private final TwoLineListItem contentLine1 = new TwoLineListItem();
        private final ServerAddressMaskPane contentLine2AddressMaskPane = new ServerAddressMaskPane("");

        private final ServerNetworkLatencyPane serverNetworkLatencyPane = new ServerNetworkLatencyPane();
        private final Tooltip statusBtnTooltip = new Tooltip();

        private Subscription serverStatusResultValueSubscription;
        private Subscription serverStatusPingingValueSubscription;

        public ServerListCell(JFXListView<ServerListItem> listView, ServersPage page) {
            super(listView);
            this.page = page;

            this.getStyleClass().add("server-list-cell");

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            VBox content = new VBox(contentLine1, contentLine2AddressMaskPane);
            HBox.setHgrow(content, Priority.ALWAYS);
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
                statusBtnTooltip.setText("");
                contentLine2AddressMaskPane.set("");
            } else {
                serverIcon.setImage(item.getDisplayServerImage());
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
                serverNetworkLatencyPane.pong(serverStatus.networkLatency());
                statusBtnTooltip.setText(i18n("server.manage.status.outside.pong", String.format("%,d", serverStatus.networkLatency())));
                serverIcon.setImage(item.getDisplayServerImage());

                item.checkAndSaveServerIcon(serverStatus);
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
}
