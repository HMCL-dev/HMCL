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
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.server.ServerModpackManifest;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.ui.download.LocalModpackPage.*;
import static org.jackhuang.hmcl.ui.download.RemoteModpackPage.MODPACK_SERVER_MANIFEST;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModpackSelectionPage extends StackPane implements WizardPage {
    private final WizardController controller;

    @FXML private JFXButton btnLocal;
    @FXML private JFXButton btnRemote;

    public ModpackSelectionPage(WizardController controller) {
        this.controller = controller;
        FXUtils.loadFXML(this, "/assets/fxml/download/modpack-source.fxml");

        Optional<File> filePath = tryCast(controller.getSettings().get(MODPACK_FILE), File.class);
        if (filePath.isPresent()) {
            controller.getSettings().put(MODPACK_FILE, filePath.get());
            Platform.runLater(controller::onNext);
        }

        FXUtils.applyDragListener(this, it -> "zip".equals(FileUtils.getExtension(it)), modpacks -> {
            File modpack = modpacks.get(0);
            controller.getSettings().put(MODPACK_FILE, modpack);
            controller.onNext();
        });
    }

    @FXML
    private void onChooseLocalFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("modpack.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("modpack"), "*.zip"));
        File selectedFile = chooser.showOpenDialog(Controllers.getStage());
        if (selectedFile == null) {
            Platform.runLater(controller::onEnd);
            return;
        }

        controller.getSettings().put(MODPACK_FILE, selectedFile);
        controller.onNext();
    }

    @FXML
    private void onChooseRemoteFile() {
        Controllers.inputDialog(i18n("modpack.choose.remote.tooltip"), (urlString, resolve, reject) -> {
            try {
                URL url = new URL(urlString);
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
                    }).executor(true), i18n("message.downloading"));
                } else {
                    // otherwise we still consider the file as modpack zip file
                    // since casually the url may not ends with ".zip"
                    Path modpack = Files.createTempFile("modpack", ".zip");
                    resolve.run();

                    Controllers.taskDialog(
                            new FileDownloadTask(url, modpack.toFile(), null)
                                    .whenComplete(Schedulers.javafx(), e -> {
                                        if (e == null) {
                                            resolve.run();
                                            controller.getSettings().put(MODPACK_FILE, modpack.toFile());
                                            controller.onNext();
                                        } else {
                                            reject.accept(e.getMessage());
                                        }
                                    }).executor(true),
                            i18n("message.downloading")
                    );
                }
            } catch (IOException e) {
                reject.accept(e.getMessage());
            }
        });
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }

    @Override
    public String getTitle() {
        return i18n("modpack.task.install");
    }
}
