/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import com.jfoenix.controls.JFXDialog;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.construct.InputDialogPane;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.JavaVersion;

import java.util.function.Consumer;

public final class Controllers {

    private static Scene scene;
    private static Stage stage;
    private static MainPage mainPage = new MainPage();
    private static SettingsPage settingsPage = null;
    private static VersionPage versionPage = null;
    private static LeftPaneController leftPaneController;
    private static Decorator decorator;

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
    public static VersionPage getVersionPage() {
        if (versionPage == null)
            versionPage = new VersionPage();
        return versionPage;
    }

    public static Decorator getDecorator() {
        return decorator;
    }

    public static MainPage getMainPage() {
        return mainPage;
    }

    public static LeftPaneController getLeftPaneController() {
        return leftPaneController;
    }

    public static void initialize(Stage stage) {
        Controllers.stage = stage;

        decorator = new Decorator(stage, mainPage, Main.TITLE, false, true);
        decorator.showPage(null);
        leftPaneController = new LeftPaneController(decorator.getLeftPane());

        Settings.INSTANCE.onProfileLoading();
        Task.of(JavaVersion::initialize).start();

        decorator.setCustomMaximize(false);

        scene = new Scene(decorator, 804, 521);
        scene.getStylesheets().addAll(FXUtils.STYLESHEETS);
        stage.setMinWidth(804);
        stage.setMaxWidth(804);
        stage.setMinHeight(521);
        stage.setMaxHeight(521);

        stage.getIcons().add(new Image("/assets/img/icon.png"));
        stage.setTitle(Main.TITLE);
    }

    public static Region getDialogContent() {
        return decorator.getDialog().getContent();
    }

    public static JFXDialog dialog(Region content) {
        // TODO: temp fix
        decorator.showDialog(new Region());
        return decorator.showDialog(content);
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
        dialog(new MessageDialogPane(text, title, decorator.getDialog(), type, onAccept));
    }

    public static void confirmDialog(String text, String title, Runnable onAccept, Runnable onCancel) {
        dialog(new MessageDialogPane(text, title, decorator.getDialog(), onAccept, onCancel));
    }

    public static void inputDialog(String text, Consumer<String> onResult) {
        dialog(new InputDialogPane(text, decorator.getDialog(), onResult));
    }

    public static void taskDialog(TaskExecutor executor, String title, String subtitle, Runnable onCancel) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setSubtitle(subtitle);
        pane.setExecutor(executor);
        executor.start();
        dialog(pane);
    }

    public static void closeDialog() {
        decorator.getDialog().close();
    }

    public static void navigate(Node node) {
        if (decorator.getNowPage() == node)
            decorator.showPage(null);
        else
            decorator.showPage(node);
    }

    public static void showUpdate() {
        getDecorator().showUpdate();
    }
}
