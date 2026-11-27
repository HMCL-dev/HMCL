// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackFile;
import org.jackhuang.hmcl.modpack.ModpackManifest;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.modpack.server.ServerModpackManifest;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.construct.URLValidator;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Objects;

import static org.jackhuang.hmcl.ui.download.LocalModpackPage.MODPACK_FILE;
import static org.jackhuang.hmcl.ui.download.LocalModpackPage.MODPACK_NAME;
import static org.jackhuang.hmcl.ui.download.RemoteModpackPage.MODPACK_SERVER_MANIFEST;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModpackSelectionPage extends VBox implements WizardPage {
    private final WizardController controller;

    /// Offers modpack sources and, for an installed update target, optional-file changes.
    public ModpackSelectionPage(WizardController controller) {
        this.controller = controller;

        Label title = new Label(i18n("install.modpack"));
        title.setPadding(new Insets(8));

        this.getStyleClass().add("jfx-list-view");
        this.setMaxSize(400, Region.USE_PREF_SIZE);
        this.setSpacing(8);
        this.getChildren().setAll(
                title,
                createButton("local", this::onChooseLocalFile),
                createButton("remote", this::onChooseRemoteFile),
                createButton("repository", this::onChooseRepository)
        );

        @Nullable Path filePath = controller.getSettings().get(MODPACK_FILE);
        if (filePath != null) {
            controller.getSettings().put(MODPACK_FILE, filePath);
            Platform.runLater(controller::onNext);
        }

        @Nullable String name = controller.getSettings().get(MODPACK_NAME);
        if (name != null) {
            GameInstanceID instanceId = new GameInstanceID(name);
            Task.supplyAsync(() -> readInstalledModpack(instanceId))
                    .whenComplete(Schedulers.javafx(), (modpack, exception) -> {
                        if (exception != null) {
                            LOG.warning("Failed to read installed modpack", exception);
                            Controllers.dialog(i18n("modpack.invalid"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        } else if (modpack != null) {
                            getChildren().add(1, createButton("optional_files", () -> {
                                controller.getSettings().remove(LocalModpackPage.MODPACK_MANUALLY_CREATED);
                                controller.getSettings().put(CHANGE_OPTIONAL_FILES, true);
                                controller.getSettings().put(LocalModpackPage.MODPACK_MANIFEST, modpack);
                                HMCLGameRepository repository = Objects.requireNonNull(controller.getSettings().get(ModpackPage.REPOSITORY));
                                controller.getSettings().put(MODPACK_FILE, repository.getLayout().getModpackConfigurationFile(instanceId));
                                controller.onNext();
                            }));
                        }
                    }).start();
        }

        FXUtils.applyDragListener(this, ModpackHelper::isFileModpackByExtension, modpacks -> {
            Path modpack = modpacks.get(0);
            controller.getSettings().put(MODPACK_FILE, modpack);
            controller.onNext();
        });
    }

    /// Asks the format provider for installed metadata and checks whether optional files are available.
    /// Returns `null` when the original format or manifest has no optional files.
    private @Nullable Modpack readInstalledModpack(GameInstanceID instanceId) throws IOException {
        HMCLGameRepository repository = Objects.requireNonNull(controller.getSettings().get(ModpackPage.REPOSITORY));
        Path root = repository.getLayout().getInstanceRoot(instanceId);
        var configuration = ModpackHelper.readModpackConfiguration(repository.getLayout().getModpackConfigurationFile(instanceId));
        @Nullable ModpackProvider provider = ModpackHelper.getProviderByType(configuration.getType());
        if (!(provider instanceof ModpackProvider.SupportOptional supportOptional))
            return null;
        Modpack modpack = supportOptional.readInstalledModpack(root, configuration);
        return modpack.getManifest() instanceof ModpackManifest.SupportOptional manifest
                && manifest.getFiles().stream().anyMatch(ModpackFile::optional) ? modpack : null;
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

        SVGPath arrow = SVG.ARROW_FORWARD.createIcon();
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
        Path selectedFile = Controllers.showOpenDialog(chooser);
        if (selectedFile == null) {
            return;
        }

        controller.getSettings().put(MODPACK_FILE, selectedFile);
        controller.onNext();
    }

    private void onChooseRemoteFile() {
        Controllers.prompt(i18n("modpack.choose.remote.tooltip"), (url, handler) -> {
            try {
                if (url.endsWith("server-manifest.json")) {
                    // if urlString ends with .json, we assume that the url is server-manifest.json
                    Controllers.taskDialog(new GetTask(WebURL.parse(url)).whenComplete(Schedulers.javafx(), (result, e) -> {
                        ServerModpackManifest manifest = JsonUtils.fromMaybeMalformedJson(result, ServerModpackManifest.class);
                        if (manifest == null) {
                            handler.reject(i18n("modpack.type.server.malformed"));
                        } else if (e == null) {
                            handler.resolve();
                            controller.getSettings().put(MODPACK_SERVER_MANIFEST, manifest);
                            controller.onNext();
                        } else {
                            handler.reject(e.getMessage());
                        }
                    }), i18n("message.downloading"), TaskCancellationAction.NORMAL);
                } else {
                    // otherwise we still consider the file as modpack zip file
                    // since casually the url may not ends with ".zip"
                    Path modpack = Files.createTempFile("modpack", ".zip");
                    Controllers.taskDialog(
                            new FileDownloadTask(url, modpack)
                                    .whenComplete(Schedulers.javafx(), e -> {
                                        if (e == null) {
                                            handler.resolve();
                                            controller.getSettings().put(MODPACK_FILE, modpack);
                                            controller.onNext();
                                        } else {
                                            handler.reject(e.getMessage());
                                        }
                                    }),
                            i18n("message.downloading"),
                            TaskCancellationAction.NORMAL
                    );
                }
            } catch (Exception e) {
                handler.reject(i18n("message.failed"));
            }
        }, "", new URLValidator());
    }

    public void onChooseRepository() {
        String modPackName = controller.getSettings().get(MODPACK_NAME);
        DownloadPage downloadPage = new DownloadPage(modPackName != null ? new GameInstanceID(modPackName) : null);
        downloadPage.showModpackDownloads();
        Controllers.navigate(downloadPage);
    }

    @Override
    public void cleanup(SettingsMap settings) {
    }

    @Override
    public String getTitle() {
        return i18n("modpack.task.install");
    }

    /// Selects original-manifest loading and an optional-only update instead of archive installation.
    public static final SettingsMap.Key<Boolean> CHANGE_OPTIONAL_FILES = new SettingsMap.Key<>("CHANGE_OPTIONAL_FILES");
}
