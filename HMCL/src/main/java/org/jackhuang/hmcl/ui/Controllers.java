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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.java.JavaRepository;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.EnumCommonDirectory;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountListPage;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.DecoratorController;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.multiplayer.MultiplayerPage;
import org.jackhuang.hmcl.ui.versions.GameListPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Controllers {
    private static DoubleProperty stageWidth = new SimpleDoubleProperty();
    private static DoubleProperty stageHeight = new SimpleDoubleProperty();

    private static Scene scene;
    private static Stage stage;
    private static Lazy<VersionPage> versionPage = new Lazy<>(VersionPage::new);
    private static Lazy<GameListPage> gameListPage = new Lazy<>(() -> {
        GameListPage gameListPage = new GameListPage();
        gameListPage.selectedProfileProperty().bindBidirectional(Profiles.selectedProfileProperty());
        gameListPage.profilesProperty().bindContent(Profiles.profilesProperty());
        FXUtils.applyDragListener(gameListPage, it -> "zip".equals(FileUtils.getExtension(it)), modpacks -> {
            File modpack = modpacks.get(0);
            Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), modpack), i18n("install.modpack"));
        });
        return gameListPage;
    });
    private static Lazy<RootPage> rootPage = new Lazy<>(RootPage::new);
    private static DecoratorController decorator;
    private static Lazy<DownloadPage> downloadPage = new Lazy<>(DownloadPage::new);
    private static Lazy<AccountListPage> accountListPage = new Lazy<>(() -> {
        AccountListPage accountListPage = new AccountListPage();
        accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
        accountListPage.accountsProperty().bindContent(Accounts.accountsProperty());
        accountListPage.authServersProperty().bindContentBidirectional(config().getAuthlibInjectorServers());
        return accountListPage;
    });
    private static Lazy<MultiplayerPage> multiplayerPage = new Lazy<>(MultiplayerPage::new);
    private static Lazy<LauncherSettingsPage> settingsPage = new Lazy<>(LauncherSettingsPage::new);

    private Controllers() {
    }

    public static Scene getScene() {
        return scene;
    }

    public static Stage getStage() {
        return stage;
    }

    // FXThread
    public static VersionPage getVersionPage() {
        return versionPage.get();
    }

    // FXThread
    public static GameListPage getGameListPage() {
        return gameListPage.get();
    }

    // FXThread
    public static RootPage getRootPage() {
        return rootPage.get();
    }

    // FXThread
    public static MultiplayerPage getMultiplayerPage() {
        return multiplayerPage.get();
    }

    // FXThread
    public static LauncherSettingsPage getSettingsPage() {
        return settingsPage.get();
    }

    // FXThread
    public static AccountListPage getAccountListPage() {
        return accountListPage.get();
    }

    // FXThread
    public static DownloadPage getDownloadPage() {
        return downloadPage.get();
    }

    // FXThread
    public static DecoratorController getDecorator() {
        return decorator;
    }

    public static void onApplicationStop() {
        config().setHeight(stageHeight.get());
        config().setWidth(stageWidth.get());
        stageHeight = null;
        stageWidth = null;
    }

    public static void initialize(Stage stage) {
        Logging.LOG.info("Start initializing application");

        Controllers.stage = stage;

        stage.setHeight(config().getHeight());
        stageHeight.bind(stage.heightProperty());
        stage.setWidth(config().getWidth());
        stageWidth.bind(stage.widthProperty());

        stage.setOnCloseRequest(e -> Launcher.stopApplication());

        decorator = new DecoratorController(stage, getRootPage());

        if (config().getCommonDirType() == EnumCommonDirectory.CUSTOM &&
                !FileUtils.canCreateDirectory(config().getCommonDirectory())) {
            config().setCommonDirType(EnumCommonDirectory.DEFAULT);
            dialog(i18n("launcher.cache_directory.invalid"));
        }

        Task.runAsync(JavaVersion::initialize).thenRunAsync(JavaRepository::initialize).start();

        scene = new Scene(decorator.getDecorator());
        scene.setFill(Color.TRANSPARENT);
        stage.setMinHeight(450 + 2 + 40 + 16); // bg height + border width*2 + toolbar height + shadow width*2
        stage.setMinWidth(800 + 2 + 16); // bg width + border width*2 + shadow width*2
        decorator.getDecorator().prefWidthProperty().bind(scene.widthProperty());
        decorator.getDecorator().prefHeightProperty().bind(scene.heightProperty());
        scene.getStylesheets().setAll(config().getTheme().getStylesheets(config().getLauncherFontFamily()));

        stage.getIcons().add(newImage("/assets/img/icon.png"));
        stage.setTitle(Metadata.FULL_TITLE);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);

        if (globalConfig().getAgreementVersion() < 1) {
            JFXDialogLayout agreementPane = new JFXDialogLayout();
            agreementPane.setHeading(new Label(i18n("launcher.agreement")));
            agreementPane.setBody(new Label(i18n("launcher.agreement.hint")));
            JFXHyperlink agreementLink = new JFXHyperlink(i18n("launcher.agreement"));
            agreementLink.setOnAction(e -> FXUtils.openLink(Metadata.EULA_URL));
            JFXButton yesButton = new JFXButton(i18n("launcher.agreement.accept"));
            yesButton.getStyleClass().add("dialog-accept");
            yesButton.setOnAction(e -> {
                globalConfig().setAgreementVersion(1);
                agreementPane.fireEvent(new DialogCloseEvent());
            });
            JFXButton noButton = new JFXButton(i18n("launcher.agreement.decline"));
            noButton.getStyleClass().add("dialog-cancel");
            noButton.setOnAction(e -> {
                System.exit(1);
            });
            agreementPane.setActions(agreementLink, yesButton, noButton);
            Controllers.dialog(agreementPane);
        }
    }

    public static void dialog(Region content) {
        if (decorator != null)
            decorator.showDialog(content);
    }

    public static void dialog(String text) {
        dialog(text, null);
    }

    public static void dialog(String text, String title) {
        dialog(text, title, MessageType.INFO);
    }

    public static void dialog(String text, String title, MessageType type) {
        dialog(text, title, type, null);
    }

    public static void dialog(String text, String title, MessageType type, Runnable ok) {
        dialog(MessageDialogPane.ok(text, title, type, ok));
    }

    public static void confirm(String text, String title, Runnable yes, Runnable no) {
        confirm(text, title, MessageType.QUESTION, yes, no);
    }

    public static void confirm(String text, String title, MessageType type, Runnable yes, Runnable no) {
        dialog(MessageDialogPane.yesOrNo(text, title, type, yes, no));
    }

    public static void confirmAction(String text, String title, MessageType type, ButtonBase actionButton) {
        dialog(MessageDialogPane.actionOrCancel(text, title, type, actionButton, null));
    }

    public static void confirmAction(String text, String title, MessageType type, ButtonBase actionButton, Runnable cancel) {
        dialog(MessageDialogPane.actionOrCancel(text, title, type, actionButton, cancel));
    }

    public static CompletableFuture<String> prompt(String title, FutureCallback<String> onResult) {
        return prompt(title, onResult, "");
    }

    public static CompletableFuture<String> prompt(String title, FutureCallback<String> onResult, String initialValue) {
        InputDialogPane pane = new InputDialogPane(title, initialValue, onResult);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    public static CompletableFuture<List<PromptDialogPane.Builder.Question<?>>> prompt(PromptDialogPane.Builder builder) {
        PromptDialogPane pane = new PromptDialogPane(builder);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    public static TaskExecutorDialogPane taskDialog(TaskExecutor executor, String title) {
        return taskDialog(executor, title, null);
    }

    public static TaskExecutorDialogPane taskDialog(TaskExecutor executor, String title, Consumer<Region> onCancel) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setExecutor(executor);
        dialog(pane);
        return pane;
    }

    public static TaskExecutorDialogPane taskDialog(Task<?> task, String title, Consumer<Region> onCancel) {
        TaskExecutor executor = task.executor();
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setExecutor(executor);
        dialog(pane);
        executor.start();
        return pane;
    }

    public static void navigate(Node node) {
        decorator.navigate(node);
    }

    public static void showToast(String content) {
        decorator.showToast(content);
    }

    public static void onHyperlinkAction(String href) {
        if (href.startsWith("hmcl://")) {
            if ("hmcl://settings/feedback".equals(href)) {
                Controllers.getSettingsPage().showFeedback();
                Controllers.navigate(Controllers.getSettingsPage());
            }
        } else {
            FXUtils.openLink(href);
        }
    }

    public static boolean isStopped() {
        return decorator == null;
    }

    public static void shutdown() {
        rootPage = null;
        versionPage = null;
        gameListPage = null;
        settingsPage = null;
        decorator = null;
        stage = null;
        scene = null;
    }
}
