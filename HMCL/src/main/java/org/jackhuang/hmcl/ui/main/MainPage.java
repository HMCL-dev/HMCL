/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXSpinner;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.EnumUpdateMode;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.UpgradeDialog;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.GameListPopupMenu;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.HMCLDownloadTask;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.download.RemoteVersion.Type.RELEASE;
import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.ui.FXUtils.SINE;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MainPage extends StackPane implements DecoratorPage {
    private static final String ANNOUNCEMENT = "announcement";

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final SimpleObjectProperty<UpdateBubble.State> updateState = new SimpleObjectProperty<>(UpdateBubble.State.NOTIFY);
    private final SimpleObjectProperty<Exception> exception = new SimpleObjectProperty<>();
    private Path downloadedHmcl = null;
    private final StringProperty currentGame = new SimpleStringProperty(this, "currentGame");
    private final BooleanProperty showUpdate = new SimpleBooleanProperty(this, "showUpdate");
    private final BooleanProperty showUpdateDialog = new SimpleBooleanProperty(this, "showUpdateDialog");
    private final ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>(this, "latestVersion");
    private final ObservableList<Version> versions = FXCollections.observableArrayList();
    private Profile profile;

    private TransitionPane announcementPane;
    private final UpdateBubble updateBubble = new UpdateBubble();
    private final JFXButton menuButton;

    private RemoteVersion lastShownVersion;

    {
        latestVersion.bind(UpdateChecker.latestVersionProperty());
        FXUtils.onChange(showUpdateProperty(), MainPage.this::doAnimation);
        FXUtils.onChange(showUpdateProperty(), MainPage.this::doUpdateIfNeeded);
        FXUtils.onChange(showUpdateDialogProperty(), MainPage.this::showUpdateDialog);

        HBox titleNode = new HBox(8);
        titleNode.setPadding(new Insets(0, 0, 0, 2));
        titleNode.setAlignment(Pos.CENTER_LEFT);

        ImageView titleIcon = new ImageView(FXUtils.newBuiltinImage("/assets/img/icon-title.png"));
        Label titleLabel = new Label(Metadata.FULL_TITLE);
        if (I18n.isUpsideDown()) {
            titleIcon.setRotate(180);
            titleLabel.setRotate(180);
        }
        titleLabel.getStyleClass().add("jfx-decorator-title");
        titleLabel.textFillProperty().bind(Themes.titleFillProperty());
        titleNode.getChildren().setAll(titleIcon, titleLabel);

        state.setValue(new State(null, titleNode, false, false, true));

        setPadding(new Insets(20));

        if (Metadata.isNightly() || (Metadata.isDev() && !Objects.equals(Metadata.VERSION, state().getShownTips().get(ANNOUNCEMENT)))) {
            String title;
            String content;
            if (Metadata.isNightly()) {
                title = i18n("update.channel.nightly.title");
                content = i18n("update.channel.nightly.hint");
            } else {
                title = i18n("update.channel.dev.title");
                content = i18n("update.channel.dev.hint");
            }

            VBox announcementCard = new VBox();

            BorderPane titleBar = new BorderPane();
            titleBar.getStyleClass().add("title");
            titleBar.setLeft(new Label(title));

            JFXButton btnHide = new JFXButton();
            btnHide.setOnAction(e -> {
                announcementPane.setContent(new StackPane(), ContainerAnimations.FADE);
                if (Metadata.isDev()) {
                    state().getShownTips().put(ANNOUNCEMENT, Metadata.VERSION);
                }
            });
            btnHide.getStyleClass().add("announcement-close-button");
            btnHide.setGraphic(SVG.CLOSE.createIcon(20));
            titleBar.setRight(btnHide);

            TextFlow body = FXUtils.segmentToTextFlow(content, Controllers::onHyperlinkAction);
            body.setLineSpacing(4);

            announcementCard.getChildren().setAll(titleBar, body);
            announcementCard.setSpacing(16);
            announcementCard.getStyleClass().addAll("card", "announcement");

            VBox announcementBox = new VBox(16);
            announcementBox.setPadding(new Insets(15));
            announcementBox.getChildren().add(announcementCard);

            announcementPane = new TransitionPane();
            announcementPane.setContent(announcementBox, ContainerAnimations.NONE);

            StackPane.setMargin(announcementPane, new Insets(-15));
            getChildren().add(announcementPane);
        }


        HBox launchPane = new HBox();
        launchPane.getStyleClass().add("launch-pane");
        FXUtils.onScroll(launchPane, versions, list -> {
            String currentId = getCurrentGame();
            return Lang.indexWhere(list, instance -> instance.getId().equals(currentId));
        }, it -> Profiles.setSelectedInstance(profile, it.getId()));

        StackPane.setAlignment(launchPane, Pos.BOTTOM_RIGHT);
        {
            JFXButton launchButton = new JFXButton();
            launchButton.getStyleClass().add("launch-button");
            launchButton.setDefaultButton(true);
            {
                VBox graphic = new VBox();
                graphic.setAlignment(Pos.CENTER);
                Label launchLabel = new Label();
                launchLabel.setStyle("-fx-font-size: 16px;");
                Label currentLabel = new Label();
                currentLabel.setStyle("-fx-font-size: 12px;");

                FXUtils.onChangeAndOperate(currentGameProperty(), new Consumer<>() {
                    private Tooltip tooltip;

                    @Override
                    public void accept(String currentGame) {
                        if (currentGame == null) {
                            launchLabel.setText(i18n("version.launch.empty"));
                            currentLabel.setText(null);
                            graphic.getChildren().setAll(launchLabel);
                            FXUtils.setOnActionWithCooldown(launchButton, MainPage.this::launchNoGame);
                            if (tooltip == null)
                                tooltip = new Tooltip(i18n("version.launch.empty.tooltip"));
                            FXUtils.installFastTooltip(launchButton, tooltip);
                        } else {
                            launchLabel.setText(i18n("version.launch"));
                            currentLabel.setText(currentGame);
                            graphic.getChildren().setAll(launchLabel, currentLabel);
                            FXUtils.setOnActionWithCooldown(launchButton, MainPage.this::launch);
                            if (tooltip != null)
                                Tooltip.uninstall(launchButton, tooltip);
                        }
                    }
                });

                launchButton.setGraphic(graphic);
            }

            menuButton = new JFXButton();
            menuButton.getStyleClass().add("menu-button");
            menuButton.setOnAction(e -> GameListPopupMenu.show(
                    menuButton,
                    JFXPopup.PopupVPosition.BOTTOM,
                    JFXPopup.PopupHPosition.RIGHT,
                    0,
                    -menuButton.getHeight(),
                    profile, versions
            ));
            FXUtils.installFastTooltip(menuButton, i18n("version.switch"));
            menuButton.setGraphic(SVG.ARROW_DROP_UP.createIcon(30));

            EventHandler<MouseEvent> secondaryClickHandle = event -> {
                if (event.getButton() == MouseButton.SECONDARY && event.getClickCount() == 1) {
                    menuButton.fire();
                    event.consume();
                }
            };
            launchButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);
            menuButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);

            launchPane.getChildren().setAll(launchButton, menuButton);
        }

        getChildren().addAll(updateBubble, launchPane);

    }

    private void doUpdateIfNeeded(Boolean show) {
        if (!show) return;

        var mode = settings().updateModeProperty().get();
        if (mode == EnumUpdateMode.NOTIFY) return;

        updateState.set(UpdateBubble.State.DOWNLOADING);

        try {
            downloadedHmcl = Files.createTempFile("hmcl-update-", ".jar");
            var downloadTask = new HMCLDownloadTask(latestVersion.get(), downloadedHmcl);
            downloadTask.whenComplete((e) -> {
                if (e != null) {
                    javafx.application.Platform.runLater(() -> {
                        exception.set(e);
                    });
                } else {
                    javafx.application.Platform.runLater(() -> {
                        updateState.set(UpdateBubble.State.SUCCESS);
                    });

                    if (mode == EnumUpdateMode.SILENT) {
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                UpdateHandler.finishUpdate(downloadedHmcl, true);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }));
                    } else if (mode == EnumUpdateMode.AUTO) {
                        try {
                            UpdateHandler.finishUpdate(downloadedHmcl, false);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                }
            }).start();
        } catch (IOException e) {
            LOG.warning("Failed to update", e);
            exception.set(e);
        }
    }

    private void showUpdateDialog(boolean show) {
        if (show && latestVersion.get() != null && !Objects.equals(latestVersion.get(), lastShownVersion)
                && !Objects.equals(state().getPromptedVersion(), latestVersion.get().version())
        ) {
            lastShownVersion = latestVersion.get();
            Controllers.dialogLater(new MessageDialogPane.Builder("", i18n("update.bubble.title", latestVersion.get().version()), MessageDialogPane.MessageType.INFO)
                    .addAction(i18n("button.view"), () -> {
                        state().setPromptedVersion(latestVersion.get().version());
                        onUpgrade();
                    })
                    .addCancel(null)
                    .build());
        }
    }

    private void doAnimation(boolean show) {
        if (AnimationUtils.isAnimationEnabled()) {
            Duration duration = Duration.millis(320);
            Timeline nowAnimation = new Timeline();
            nowAnimation.getKeyFrames().addAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(updateBubble.translateXProperty(), show ? 260 : 0, SINE)),
                    new KeyFrame(duration,
                            new KeyValue(updateBubble.translateXProperty(), show ? 0 : 260, SINE)));
            if (show) nowAnimation.getKeyFrames().add(
                    new KeyFrame(Duration.ZERO, e -> updateBubble.setVisible(true)));
            else nowAnimation.getKeyFrames().add(
                    new KeyFrame(duration, e -> updateBubble.setVisible(false)));
            nowAnimation.play();
        } else {
            updateBubble.setVisible(show);
        }
    }

    private void launch() {
        Profile profile = Profiles.getSelectedProfile();
        Versions.launch(profile, Profiles.getSelectedInstance(profile));
    }

    private void launchNoGame() {
        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
        VersionList<?> versionList = downloadProvider.getVersionListById("game");

        Holder<String> gameVersionHolder = new Holder<>();
        Task<?> task = versionList.refreshAsync("")
                .thenSupplyAsync(() -> versionList.getVersions("").stream()
                        .filter(it -> it.getVersionType() == RELEASE)
                        .filter(it -> NativePatcher.checkSupportedStatus(GameVersionNumber.asGameVersion(it.getGameVersion()), Platform.SYSTEM_PLATFORM, OperatingSystem.SYSTEM_VERSION) != NativePatcher.SupportStatus.UNSUPPORTED)
                        .sorted()
                        .findFirst()
                        .orElseThrow(() -> new IOException("No versions found")))
                .thenComposeAsync(version -> {
                    Profile profile = Profiles.getSelectedProfile();
                    DefaultDependencyManager dependency = profile.getDependency();
                    String gameVersion = gameVersionHolder.value = version.getGameVersion();

                    return dependency.gameBuilder()
                            .name(gameVersion)
                            .gameVersion(gameVersion)
                            .buildAsync();
                })
                .whenComplete(any -> profile.getRepository().refreshVersions())
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        Profiles.setSelectedInstance(profile, gameVersionHolder.value);
                        launch();
                    } else if (exception instanceof CancellationException) {
                        Controllers.showToast(i18n("message.cancelled"));
                    } else {
                        LOG.warning("Failed to install game", exception);
                        Controllers.dialog(StringUtils.getStackTrace(exception),
                                i18n("install.failed"),
                                MessageDialogPane.MessageType.WARNING);
                    }
                });
        Controllers.taskDialog(task, i18n("version.launch.empty.installing"), TaskCancellationAction.NORMAL);
    }

    private void onUpgrade() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    private void closeUpdateBubble() {
        showUpdate.unbind();
        showUpdate.set(false);
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getCurrentGame() {
        return currentGame.get();
    }

    public StringProperty currentGameProperty() {
        return currentGame;
    }

    public void setCurrentGame(String currentGame) {
        this.currentGame.set(currentGame);
    }

    public ObservableList<Version> getVersions() {
        return versions;
    }

    public boolean isShowUpdate() {
        return showUpdate.get();
    }

    public BooleanProperty showUpdateProperty() {
        return showUpdate;
    }

    public void setShowUpdate(boolean showUpdate) {
        this.showUpdate.set(showUpdate);
    }

    public boolean isShowUpdateDialog() {
        return showUpdateDialog.get();
    }

    public BooleanProperty showUpdateDialogProperty() {
        return showUpdateDialog;
    }

    public void initVersions(Profile profile, List<Version> versions) {
        FXUtils.checkFxUserThread();
        this.profile = profile;
        this.versions.setAll(versions);
    }

    private class UpdateBubble extends StackPane {
        public UpdateBubble() {
            this.setVisible(false);
            this.getStyleClass().add("bubble");
            FXUtils.setLimitWidth(this, 260);
            FXUtils.setLimitHeight(this, 55);
            StackPane.setAlignment(this, Pos.TOP_RIGHT);

            HBox hBox = new HBox();
            hBox.setSpacing(12);
            hBox.setAlignment(Pos.CENTER_LEFT);
            StackPane.setAlignment(hBox, Pos.CENTER_LEFT);
            StackPane.setMargin(hBox, new Insets(9, 12, 9, 16));

            TwoLineListItem item = new TwoLineListItem();
            item.setPickOnBounds(false);

            JFXButton closeUpdateButton = new JFXButton();
            closeUpdateButton.setGraphic(SVG.CLOSE.createIcon(20));
            StackPane.setAlignment(closeUpdateButton, Pos.TOP_RIGHT);
            closeUpdateButton.getStyleClass().add("toggle-icon-tiny");
            StackPane.setMargin(closeUpdateButton, new Insets(10));
            closeUpdateButton.setOnAction(e -> closeUpdateBubble());

            FXUtils.onClicked(this, this::onClink);

            var invalidationListener = (InvalidationListener) observable -> {
                item.titleProperty().unbind();
                if (updateState.get() == State.NOTIFY) {
                    item.setSubtitle(i18n("update.bubble.notify.subtitle"));
                    item.titleProperty().bind(BindingMapping.of(latestVersion).map(latestVersion ->
                            latestVersion == null ? "" : i18n("update.bubble.notify.title", latestVersion.version())));
                    hBox.getChildren().setAll(SVG.UPDATE.createIcon(32), item);
                    this.getChildren().setAll(hBox, closeUpdateButton);
                } else if (updateState.get() == State.DOWNLOADING) {
                    item.setSubtitle(i18n("update.bubble.downloading.subtitle"));
                    item.setTitle(i18n("update.bubble.downloading.title"));
                    hBox.getChildren().setAll(new JFXSpinner(), item);

                    this.getChildren().setAll(hBox);
                } else if (updateState.get() == State.SUCCESS) {
                    var mode = settings().updateModeProperty().get();

                    if (mode == EnumUpdateMode.SILENT) {
                        item.setSubtitle(i18n("update.bubble.success.subtitle.silent"));
                    } else if (mode == EnumUpdateMode.AUTO) {
                        item.setSubtitle(i18n("update.bubble.success.subtitle.auto"));
                    } else {
                        item.setSubtitle(i18n("update.bubble.success.subtitle.download"));
                    }

                    item.setTitle(i18n("update.bubble.success.title"));
                    hBox.getChildren().setAll(SVG.CHECK_CIRCLE.createIcon(32), item);
                    this.getChildren().setAll(hBox);
                } else if (updateState.get() == State.FAILED) {
                    item.setSubtitle(i18n("update.bubble.failed.subtitle"));
                    item.setTitle(i18n("update.failed"));
                    hBox.getChildren().setAll(SVG.ERROR.createIcon(32), item);
                    this.getChildren().setAll(hBox, closeUpdateButton);
                }


            };
            invalidationListener.invalidated(null);
            updateState.addListener(invalidationListener);
        }

        public enum State {
            NOTIFY,
            DOWNLOADING,
            SUCCESS,
            FAILED,
        }

        public void onClink() {
            if (updateState.get() == State.NOTIFY) {
                onUpgrade();
            } else if (updateState.get() == State.DOWNLOADING) {
                Controllers.dialog(new UpgradeDialog(latestVersion.get(), null));
            } else if (updateState.get() == State.SUCCESS) {
                Task.runAsync(() -> {
                    try {
                        UpdateHandler.finishUpdate(downloadedHmcl, settings().updateModeProperty().get() == EnumUpdateMode.SILENT);
                    } catch (IOException e) {
                        LOG.warning("Failed to apply update", e);
                        javafx.application.Platform.runLater(() -> {
                            Controllers.dialog(StringUtils.getStackTrace(e), i18n("update.failed"), MessageDialogPane.MessageType.ERROR);
                        });
                    }
                }).start();
            } else if (updateState.get() == State.FAILED) {
                var e = StringUtils.getStackTrace(exception.get());
                Controllers.dialog(e, i18n("update.failed"), MessageDialogPane.MessageType.ERROR);
            }
        }
    }
}
