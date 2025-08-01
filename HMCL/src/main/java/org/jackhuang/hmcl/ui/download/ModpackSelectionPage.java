/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.server.ServerModpackManifest;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.ui.download.LocalModpackPage.MODPACK_FILE;
import static org.jackhuang.hmcl.ui.download.LocalModpackPage.MODPACK_NAME;
import static org.jackhuang.hmcl.ui.download.RemoteModpackPage.MODPACK_SERVER_MANIFEST;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModpackSelectionPage extends VBox implements WizardPage {
    private final WizardController controller;

    public ModpackSelectionPage(WizardController controller) {
        this.controller = controller;

        Label title = new Label(i18n("install.modpack"));
        title.setPadding(new Insets(8));

        this.getStyleClass().add("jfx-list-view");
        this.setMaxSize(400, 150);
        this.setSpacing(8);
        this.getChildren().setAll(
                title,
                createButton("local", this::onChooseLocalFile),
                createButton("remote", this::onChooseRemoteFile),
                createButton("repository", this::onChooseRepository)
        );

        Optional<File> filePath = tryCast(controller.getSettings().get(MODPACK_FILE), File.class);
        if (filePath.isPresent()) {
            controller.getSettings().put(MODPACK_FILE, filePath.get());
            Platform.runLater(controller::onNext);
        }

        FXUtils.applyDragListener(this, ModpackHelper::isFileModpackByExtension, modpacks -> {
            File modpack = modpacks.get(0);
            controller.getSettings().put(MODPACK_FILE, modpack);
            controller.onNext();
        });
    }

    private JFXButton createButton(String type, Runnable action) {
        JFXButton button = new JFXButton();

        button.getStyleClass().add("card");
        button.setStyle("-fx-cursor: HAND;");
        button.prefWidthProperty().bind(this.widthProperty());
        button.setOnAction(e -> action.run());

        BorderPane graphic = new BorderPane();
        graphic.setMouseTransparent(true);
        graphic.setLeft(new TwoLineListItem(i18n("modpack.choose." + type), i18n("modpack.choose." + type + ".detail")));

        SVGPath arrow = new SVGPath();
        arrow.setContent(SVG.ARROW_FORWARD.getPath());
        BorderPane.setAlignment(arrow, Pos.CENTER);
        graphic.setRight(arrow);

        button.setGraphic(graphic);

        JFXDepthManager.setDepth(button, 1);

        return button;
    }

    private void onChooseLocalFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("modpack.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("modpack"), "*.zip", "*.mrpack"));
        File selectedFile = chooser.showOpenDialog(Controllers.getStage());
        if (selectedFile == null) {
            Platform.runLater(controller::onEnd);
            return;
        }

        controller.getSettings().put(MODPACK_FILE, selectedFile);
        controller.onNext();
    }

    private void onChooseRemoteFile() {
        Controllers.prompt(i18n("modpack.choose.remote.tooltip"), (urlString, resolve, reject) -> {
            try {
                URI url = URI.create(urlString);
                if (urlString.endsWith("server-manifest.json")) {
                    // if urlString ends with .json, we assume that the url is server-manifest.json
                    Controllers.taskDialog(new GetTask(url).whenComplete(Schedulers.javafx(), (result, e) -> {
                        ServerModpackManifest manifest = JsonUtils.fromMaybeMalformedJson(result, ServerModpackManifest.class);
                        if (manifest == null) {
                            reject.accept(i18n("modpack.type.server.malformed"));
                        } else if (e == null) {
                            resolve.run();
                            controller.getSettings().put(MODPACK_SERVER_MANIFEST, manifest);
                            controller.onNext();
                        } else {
                            reject.accept(e.getMessage());
                        }
                    }).executor(true), i18n("message.downloading"), TaskCancellationAction.NORMAL);
                } else {
                    // otherwise we still consider the file as modpack zip file
                    // since casually the url may not ends with ".zip"
                    Path modpack = Files.createTempFile("modpack", ".zip");
                    resolve.run();

                    Controllers.taskDialog(
                            new FileDownloadTask(url, modpack, null)
                                    .whenComplete(Schedulers.javafx(), e -> {
                                        if (e == null) {
                                            resolve.run();
                                            controller.getSettings().put(MODPACK_FILE, modpack.toFile());
                                            controller.onNext();
                                        } else {
                                            reject.accept(e.getMessage());
                                        }
                                    }).executor(true),
                            i18n("message.downloading"),
                            TaskCancellationAction.NORMAL
                    );
                }
            } catch (IOException e) {
                reject.accept(e.getMessage());
            }
        });
    }

    public void onChooseRepository() {
        String modPackName = (String) controller.getSettings().get(MODPACK_NAME);
        DownloadPage downloadPage = new DownloadPage(modPackName);
        downloadPage.showModpackDownloads();
        Controllers.navigate(downloadPage);
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }

    @Override
    public String getTitle() {
        return i18n("modpack.task.install");
    }
}
