/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.concurrency.JFXUtilities;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountList;
import org.jackhuang.hmcl.ui.account.AuthlibInjectorServersPage;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorController;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.profile.ProfileList;
import org.jackhuang.hmcl.ui.versions.GameItem;
import org.jackhuang.hmcl.ui.versions.GameList;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MultiStepBinding;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Controllers {
    public static final int UI_VERSION = 1;

    private static Scene scene;
    private static Stage stage;
    private static MainPage mainPage = null;
    private static SettingsPage settingsPage = null;
    private static VersionPage versionPage = null;
    private static GameList gameListPage = null;
    private static AccountList accountListPage = null;
    private static ProfileList profileListPage = null;
    private static AuthlibInjectorServersPage serversPage = null;
    private static LeftPaneController leftPaneController;
    private static DecoratorController decorator;

    public static Scene getScene() {
        return scene;
    }

    public static Stage getStage() {
        return stage;
    }

    // FXThread
    public static SettingsPage getSettingsPage() {
        if (settingsPage == null)
            settingsPage = new SettingsPage();
        return settingsPage;
    }

    // FXThread
    public static GameList getGameListPage() {
        if (gameListPage == null)
            gameListPage = new GameList();
        return gameListPage;
    }

    // FXThread
    public static AccountList getAccountListPage() {
        if (accountListPage == null) {
            accountListPage = new AccountList();
            accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
            accountListPage.accountsProperty().bindContent(Accounts.accountsProperty());
        }
        return accountListPage;
    }

    // FXThread
    public static ProfileList getProfileListPage() {
        if (profileListPage == null) {
            profileListPage = new ProfileList();
            profileListPage.selectedProfileProperty().bindBidirectional(Profiles.selectedProfileProperty());
            profileListPage.profilesProperty().bindContent(Profiles.profilesProperty());
        }
        return profileListPage;
    }

    // FXThread
    public static VersionPage getVersionPage() {
        if (versionPage == null)
            versionPage = new VersionPage();
        return versionPage;
    }

    // FXThread
    public static AuthlibInjectorServersPage getServersPage() {
        if (serversPage == null)
            serversPage = new AuthlibInjectorServersPage();
        return serversPage;
    }

    // FXThread
    public static DecoratorController getDecorator() {
        return decorator;
    }

    public static MainPage getMainPage() {
        if (mainPage == null) {
            mainPage = new MainPage();
            mainPage.setOnDragOver(event -> {
                if (event.getGestureSource() != mainPage && event.getDragboard().hasFiles()) {
                    if (event.getDragboard().getFiles().stream().anyMatch(it -> "zip".equals(FileUtils.getExtension(it))))
                        event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                }
                event.consume();
            });

            mainPage.setOnDragDropped(event -> {
                List<File> files = event.getDragboard().getFiles();
                if (files != null) {
                    List<File> modpacks = files.stream()
                            .filter(it -> "zip".equals(FileUtils.getExtension(it)))
                            .collect(Collectors.toList());
                    if (!modpacks.isEmpty()) {
                        File modpack = modpacks.get(0);
                        Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(modpack), i18n("install.modpack"));
                        event.setDropCompleted(true);
                    }
                }
                event.consume();
            });

            FXUtils.onChangeAndOperate(Profiles.selectedVersionProperty(), version -> {
                if (version != null) {
                    mainPage.setCurrentGame(version);
                } else {
                    mainPage.setCurrentGame(i18n("version.empty"));
                }
            });
            mainPage.showUpdateProperty().bind(UpdateChecker.outdatedProperty());
            mainPage.latestVersionProperty().bind(
                    MultiStepBinding.of(UpdateChecker.latestVersionProperty())
                            .map(version -> version == null ? "" : i18n("update.bubble.title", version.getVersion())));

            Profiles.registerVersionsListener(profile -> {
                HMCLGameRepository repository = profile.getRepository();
                List<Node> children = repository.getVersions().parallelStream()
                        .filter(version -> !version.isHidden())
                        .sorted(Comparator.comparing((Version version) -> version.getReleaseTime() == null ? new Date(0L) : version.getReleaseTime())
                                .thenComparing(a -> VersionNumber.asVersion(a.getId())))
                        .map(version -> {
                            Node node = PopupMenu.wrapPopupMenuItem(new GameItem(profile, version.getId()));
                            node.setOnMouseClicked(e -> profile.setSelectedVersion(version.getId()));
                            return node;
                        })
                        .collect(Collectors.toList());
                JFXUtilities.runInFX(() -> {
                    if (profile == Profiles.getSelectedProfile())
                        mainPage.getVersions().setAll(children);
                });
            });
        }
        return mainPage;
    }

    public static LeftPaneController getLeftPaneController() {
        return leftPaneController;
    }

    public static void initialize(Stage stage) {
        Logging.LOG.info("Start initializing application");

        Controllers.stage = stage;

        stage.setOnCloseRequest(e -> Launcher.stopApplication());

        decorator = new DecoratorController(stage, getMainPage());
        leftPaneController = new LeftPaneController();
        decorator.getDecorator().drawerProperty().setAll(leftPaneController);

        Task.of(JavaVersion::initialize).start();

        scene = new Scene(decorator.getDecorator(), 800, 519);
        scene.getStylesheets().setAll(config().getTheme().getStylesheets());

        stage.getIcons().add(new Image("/assets/img/icon.png"));
        stage.setTitle(Metadata.TITLE);
    }

    public static void dialog(Region content) {
        if (decorator != null)
            decorator.showDialog(content);
    }

    public static void dialog(String text) {
        dialog(text, null);
    }

    public static void dialog(String text, String title) {
        dialog(text, title, MessageBox.INFORMATION_MESSAGE);
    }

    public static void dialog(String text, String title, int type) {
        dialog(text, title, type, null);
    }

    public static void dialog(String text, String title, int type, Runnable onAccept) {
        dialog(new MessageDialogPane(text, title, type, onAccept));
    }

    public static void confirmDialog(String text, String title, Runnable onAccept, Runnable onCancel) {
        dialog(new MessageDialogPane(text, title, onAccept, onCancel));
    }

    public static InputDialogPane inputDialog(String text, FutureCallback<String> onResult) {
        InputDialogPane pane = new InputDialogPane(text, onResult);
        dialog(pane);
        return pane;
    }

    public static Region taskDialog(TaskExecutor executor, String title, String subtitle) {
        return taskDialog(executor, title, subtitle, null);
    }

    public static Region taskDialog(TaskExecutor executor, String title, String subtitle, Consumer<Region> onCancel) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setSubtitle(subtitle);
        pane.setExecutor(executor);
        dialog(pane);
        return pane;
    }

    public static void navigate(Node node) {
        decorator.getNavigator().navigate(node);
    }

    public static boolean isStopped() {
        return decorator == null;
    }

    public static void shutdown() {
        mainPage = null;
        settingsPage = null;
        versionPage = null;
        serversPage = null;
        decorator = null;
        stage = null;
        scene = null;
        gameListPage = null;
        accountListPage = null;
        profileListPage = null;
    }
}
