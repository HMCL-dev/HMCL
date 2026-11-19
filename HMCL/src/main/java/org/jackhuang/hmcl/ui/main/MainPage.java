// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Cursor;
import javafx.scene.control.ButtonBase;
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
import org.glavo.m3fx.controls.M3ButtonSize;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3SplitButton;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogBuilder;
import org.jackhuang.hmcl.ui.construct.MessageType;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.instances.GameListPopupMenu;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.download.ComponentRemoteVersion.Type.RELEASE;
import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.ui.FXUtils.SINE;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Displays the launcher home controls for the currently selected game repository.
public final class MainPage extends StackPane implements DecoratorPage {
    private static final String ANNOUNCEMENT = "announcement";

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final ObjectProperty<@Nullable HMCLGameInstance> currentGame = new SimpleObjectProperty<>(this, "currentGame");
    private final BooleanProperty showUpdate = new SimpleBooleanProperty(this, "showUpdate");
    private final BooleanProperty showUpdateDialog = new SimpleBooleanProperty(this, "showUpdateDialog");
    private final ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>(this, "latestVersion");
    /// Mutable storage for visible instances from the selected repository's current snapshot.
    private final ObservableList<HMCLGameInstance> mutableInstances = FXCollections.observableArrayList();

    /// Read-only observable view of [#mutableInstances].
    private final @UnmodifiableView ObservableList<HMCLGameInstance> instances =
            FXCollections.unmodifiableObservableList(mutableInstances);

    /// Current snapshot of the repository selected by [GameDirectoryManager].
    private final ObservableValue<HMCLGameRepositorySnapshot> selectedRepositorySnapshot =
            BindingMapping.of(GameDirectoryManager.selectedRepositoryProperty())
                    .flatMap(HMCLGameRepository::snapshotProperty);

    private TransitionPane announcementPane;
    private final StackPane updatePane;
    /// The home page's default launch action, shared by pointer and keyboard input.
    private final M3SplitButton launchButton;

    private RemoteVersion lastShownVersion;

    {
        HBox titleNode = new HBox(8);
        titleNode.setPadding(new Insets(0, 0, 0, 2));
        titleNode.setAlignment(Pos.CENTER_LEFT);

        ImageView titleIcon = new ImageView(FXUtils.newBuiltinImage("/assets/img/icon-title.png"));
        Label titleLabel = new Label();
        titleLabel.textProperty().bind(Controllers.launcherTitleProperty());
        if (I18n.isUpsideDown()) {
            titleIcon.setRotate(180);
            titleLabel.setRotate(180);
        }
        titleLabel.getStyleClass().add("jfx-decorator-title");
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

            M3IconButton btnHide = new M3IconButton();
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

        updatePane = new StackPane();
        updatePane.setVisible(false);
        updatePane.getStyleClass().add("bubble");
        FXUtils.setLimitWidth(updatePane, 230);
        FXUtils.setLimitHeight(updatePane, 55);
        StackPane.setAlignment(updatePane, Pos.TOP_RIGHT);
        FXUtils.onClicked(updatePane, this::onUpgrade);
        updatePane.setCursor(Cursor.HAND);
        FXUtils.onChange(showUpdateProperty(), this::doAnimation);
        FXUtils.onChange(showUpdateDialogProperty(), this::showUpdateDialog);

        {
            HBox hBox = new HBox();
            hBox.setSpacing(12);
            hBox.setAlignment(Pos.CENTER_LEFT);
            StackPane.setAlignment(hBox, Pos.CENTER_LEFT);
            StackPane.setMargin(hBox, new Insets(9, 12, 9, 16));
            {
                TwoLineListItem prompt = new TwoLineListItem();
                prompt.setSubtitle(i18n("update.bubble.subtitle"));
                prompt.setPickOnBounds(false);
                prompt.titleProperty().bind(BindingMapping.of(latestVersionProperty()).map(latestVersion ->
                        latestVersion == null ? "" : i18n("update.bubble.title", latestVersion.version())));

                hBox.getChildren().setAll(SVG.UPDATE.createIcon(20), prompt);
            }

            M3IconButton closeUpdateButton = new M3IconButton();
            closeUpdateButton.setGraphic(SVG.CLOSE.createIcon(10));
            StackPane.setAlignment(closeUpdateButton, Pos.TOP_RIGHT);
            closeUpdateButton.getStyleClass().add("toggle-icon-tiny");
            StackPane.setMargin(closeUpdateButton, new Insets(5));
            closeUpdateButton.setOnAction(e -> closeUpdateBubble());

            updatePane.getChildren().setAll(new RipplerContainer(hBox), closeUpdateButton);
        }

        launchButton = createLaunchButton(currentGameProperty(), () -> {
            if (getCurrentGame() == null)
                launchNoGame();
            else
                launch();
        });
        GameListPopupMenu instanceMenu = new GameListPopupMenu();
        launchButton.getItems().add(instanceMenu);
        FXUtils.onChangeAndOperate(selectedRepositorySnapshot, ignored -> {
            mutableInstances.setAll(GameDirectoryManager.getSelectedRepository().getDisplayInstances().toList());
            instanceMenu.setInstances(instances);
        });
        FXUtils.onChange(launchButton.showingProperty(), showing -> {
            if (showing)
                instanceMenu.focusInstance(getCurrentGame());
        });
        FXUtils.onScroll(launchButton, instances, list -> {
            @Nullable HMCLGameInstance currentGame = getCurrentGame();
            @Nullable GameInstanceID currentId = currentGame != null ? currentGame.getId() : null;
            return Lang.indexWhere(list, instance -> instance.getId().equals(currentId));
        }, instance -> instance.getRepository().setSelectedInstance(instance));

        StackPane.setAlignment(launchButton, Pos.BOTTOM_RIGHT);
        getChildren().addAll(updatePane, launchButton);
    }

    /// Creates the home launch action and its instance-menu trigger.
    static M3SplitButton createLaunchButton(ObservableValue<@Nullable HMCLGameInstance> currentGame, Runnable launch) {
        M3SplitButton button = new M3SplitButton();
        button.setSize(M3ButtonSize.MEDIUM);
        button.setVariant(M3ButtonVariant.FILLED);
        button.getStyleClass().add("launch-button");

        ButtonBase primary = (ButtonBase) button.queryAccessibleAttribute(AccessibleAttribute.ITEM_AT_INDEX, 0);
        ButtonBase menu = (ButtonBase) button.queryAccessibleAttribute(AccessibleAttribute.ITEM_AT_INDEX, 1);
        menu.setAccessibleText(i18n("instance.switch"));
        FXUtils.installFastTooltip(menu, i18n("instance.switch"));

        Label instanceName = new Label();
        Tooltip emptyTooltip = new Tooltip(i18n("instance.launch.empty.tooltip"));
        FXUtils.onChangeAndOperate(currentGame, instance -> {
            button.setText(i18n(instance == null ? "instance.launch.empty" : "instance.launch"));
            if (instance == null) {
                button.setGraphic(null);
                primary.setAccessibleText(button.getText());
                FXUtils.installFastTooltip(primary, emptyTooltip);
            } else {
                instanceName.setText(instance.getId().toString());
                button.setGraphic(instanceName);
                primary.setAccessibleText(button.getText() + " " + instanceName.getText());
                Tooltip.uninstall(primary, emptyTooltip);
            }
        });

        PauseTransition cooldown = new PauseTransition(Motion.SHORT4);
        cooldown.setOnFinished(event -> primary.setDisable(false));
        button.setOnAction(event -> {
            // The menu segment's action also bubbles through the split button.
            if (event.getTarget() == button && !primary.isDisabled()) {
                primary.setDisable(true);
                cooldown.playFromStart();
                launch.run();
                event.consume();
            }
        });
        button.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.SECONDARY && event.getClickCount() == 1) {
                if (button.isShowing())
                    button.hideMenu();
                else
                    button.showMenu();
                event.consume();
            }
        });
        return button;
    }

    /// Activates the launch action with the same cooldown as a pointer click.
    void fireLaunchButton() {
        launchButton.fire();
    }

    private void showUpdateDialog(boolean show) {
        if (show && getLatestVersion() != null && !Objects.equals(getLatestVersion(), lastShownVersion)
                && !Objects.equals(state().getPromptedVersion(), getLatestVersion().version())
        ) {
            lastShownVersion = getLatestVersion();
            Controllers.dialogLater(new MessageDialogBuilder("", i18n("update.bubble.title", getLatestVersion().version()), MessageType.INFO)
                    .addAction(i18n("button.view"), () -> {
                        state().setPromptedVersion(getLatestVersion().version());
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
                            new KeyValue(updatePane.translateXProperty(), show ? 260 : 0, SINE)),
                    new KeyFrame(duration,
                            new KeyValue(updatePane.translateXProperty(), show ? 0 : 260, SINE)));
            if (show) nowAnimation.getKeyFrames().add(
                    new KeyFrame(Duration.ZERO, e -> updatePane.setVisible(true)));
            else nowAnimation.getKeyFrames().add(
                    new KeyFrame(duration, e -> updatePane.setVisible(false)));
            nowAnimation.play();
        } else {
            updatePane.setVisible(show);
        }
    }

    private void launch() {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        Instances.launch(repository.getSelectedInstance());
    }

    /// Installs the latest supported release in the currently selected repository, then launches
    /// that instance even if the selected repository or instance changes during installation.
    private void launchNoGame() {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();

        Task<?> task = downloadProvider.getVersionsAsync(GameComponentType.GAME, null, false)
                .thenApplyAsync(versions -> versions.stream()
                        .filter(it -> it.getVersionType() == RELEASE)
                        .filter(it -> NativePatcher.checkSupportedStatus(it.getGameVersion(), SystemInfo.SYSTEM_PLATFORM, SystemInfo.SYSTEM_VERSION) != NativePatcher.SupportStatus.UNSUPPORTED)
                        .sorted()
                        .findFirst()
                        .orElseThrow(() -> new IOException("No versions found")))
                .thenComposeAsync(version -> {
                    HMCLDependencyManager dependency = repository.getDependency();

                    String gameVersion = version.getGameVersion().toString();
                    GameInstanceID instanceId = new GameInstanceID(gameVersion);

                    try (HMCLGameBuilder builder = dependency.newGameBuilder(instanceId)) {
                        return builder
                                .component(GameComponentType.GAME, gameVersion)
                                .buildAsync()
                                .thenApplyAsync(ignored -> repository.getInstance(instanceId));
                    }
                })
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        repository.setSelectedInstance(result);
                        Instances.launch(result);
                    } else if (!(exception instanceof CancellationException)) {
                        LOG.warning("Failed to install game", exception);
                        Controllers.dialog(StringUtils.getStackTrace(exception),
                                i18n("install.failed"),
                                MessageType.WARNING);
                    }
                });
        Controllers.taskDialog(task, i18n("instance.launch.empty.installing"), TaskCancellationAction.NORMAL);
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

    /// Returns the instance shown by the launch controls.
    ///
    /// @return the current instance, or `null` when no instance is selected
    public @Nullable HMCLGameInstance getCurrentGame() {
        return currentGame.get();
    }

    /// Returns the property for the instance shown by the launch controls.
    ///
    /// @return the current-instance property
    public ObjectProperty<@Nullable HMCLGameInstance> currentGameProperty() {
        return currentGame;
    }

    /// Sets the instance shown by the launch controls.
    ///
    /// @param currentGame the instance to show, or `null` to show the empty state
    public void setCurrentGame(@Nullable HMCLGameInstance currentGame) {
        this.currentGame.set(currentGame);
    }

    /// Returns the observable instances displayed by launch-selection controls.
    ///
    /// The list is updated from the selected repository's published snapshot and contains no hidden
    /// instances. The returned view cannot be mutated.
    ///
    /// @return the observable launch-menu instances
    public @UnmodifiableView ObservableList<HMCLGameInstance> getInstances() {
        return instances;
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

    public void setShowUpdateDialog(boolean showUpdateDialog) {
        this.showUpdateDialog.set(showUpdateDialog);
    }

    public RemoteVersion getLatestVersion() {
        return latestVersion.get();
    }

    public ObjectProperty<RemoteVersion> latestVersionProperty() {
        return latestVersion;
    }

    public void setLatestVersion(RemoteVersion latestVersion) {
        this.latestVersion.set(latestVersion);
    }

}
