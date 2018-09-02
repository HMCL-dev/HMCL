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

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountList;
import org.jackhuang.hmcl.ui.account.AuthlibInjectorServersPage;
import org.jackhuang.hmcl.ui.construct.InputDialogPane;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorController;
import org.jackhuang.hmcl.ui.profile.ProfileList;
import org.jackhuang.hmcl.ui.versions.GameList;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.JavaVersion;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

import java.util.function.Consumer;

public final class Controllers {

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
        if (accountListPage == null)
            accountListPage = new AccountList();
        return accountListPage;
    }

    // FXThread
    public static ProfileList getProfileListPage() {
        if (profileListPage == null)
            profileListPage = new ProfileList();
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
        if (mainPage == null)
            mainPage = new MainPage();
        return mainPage;
    }

    public static LeftPaneController getLeftPaneController() {
        return leftPaneController;
    }

    public static void initialize(Stage stage) {
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
