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
package org.jackhuang.hmcl.ui.download;

import com.google.gson.JsonParseException;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ManuallyCreatedModpackException;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseManifest;
import org.jackhuang.hmcl.mod.modrinth.ModrinthManifest;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LocalModpackPage extends ModpackPage {

    private final BooleanProperty installAsVersion = new SimpleBooleanProperty(true);
    private Modpack manifest = null;
    private Charset charset;

    public LocalModpackPage(WizardController controller) {
        super(controller);

        Profile profile = (Profile) controller.getSettings().get("PROFILE");

        Optional<String> name = tryCast(controller.getSettings().get(MODPACK_NAME), String.class);
        if (name.isPresent()) {
            txtModpackName.setText(name.get());
            txtModpackName.setDisable(true);
        } else {
            FXUtils.onChangeAndOperate(installAsVersion, installAsVersion -> {
                if (installAsVersion) {
                    txtModpackName.getValidators().setAll(
                            new RequiredValidator(),
                            new Validator(i18n("install.new_game.already_exists"), str -> !profile.getRepository().versionIdConflicts(str)),
                            new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId));
                } else {
                    txtModpackName.getValidators().setAll(
                            new RequiredValidator(),
                            new Validator(i18n("install.new_game.already_exists"), str -> !ModpackHelper.isExternalGameNameConflicts(str) && Profiles.getProfiles().stream().noneMatch(p -> p.getName().equals(str))),
                            new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId));
                }
            });
        }

        File selectedFile;
        Optional<File> filePath = tryCast(controller.getSettings().get(MODPACK_FILE), File.class);
        if (filePath.isPresent()) {
            selectedFile = filePath.get();
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n("modpack.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("modpack"), "*.zip"));
            selectedFile = chooser.showOpenDialog(Controllers.getStage());
            if (selectedFile == null) {
                controller.onEnd();
                return;
            }

            controller.getSettings().put(MODPACK_FILE, selectedFile);
        }

        showSpinner();
        Task.supplyAsync(() -> CompressingUtils.findSuitableEncoding(selectedFile.toPath()))
                .thenApplyAsync(encoding -> {
                    charset = encoding;
                    manifest = ModpackHelper.readModpackManifest(selectedFile.toPath(), encoding);
                    return manifest;
                })
                .thenApplyAsync(Schedulers.javafx(), (manifest) -> {
                    hideSpinner();
                    controller.getSettings().put(MODPACK_MANIFEST, manifest);
                    lblName.setText(manifest.getName());
                    lblVersion.setText(manifest.getVersion());
                    lblAuthor.setText(manifest.getAuthor());

                    lblModpackLocation.setText(selectedFile.getAbsolutePath());

                    if (!name.isPresent()) {
                        // trim: https://github.com/huanghongxun/HMCL/issues/962
                        txtModpackName.setText(manifest.getName().trim());
                    }

                    if (manifest.getManifest() instanceof ModrinthManifest) {
                        optionalFiles.updateOptionalFileList(((ModrinthManifest) manifest.getManifest()).getFiles());
                    } else if (manifest.getManifest() instanceof CurseManifest) {
                        waitingForOptionalFiles.set(true);
                    } else {
                        optionalFiles.updateOptionalFileList(Collections.emptyList());
                    }

                    return manifest;
                }).thenApplyAsync((manifest) -> {
                    if (manifest.getManifest() instanceof CurseManifest) {
                        CurseManifest manifest1 = (CurseManifest) manifest.getManifest(); // Fetch optional files in advance so that we can display the optional mods
                        return manifest.setManifest(manifest1.setFiles(
                                manifest1.getFiles().parallelStream()
                                        .map(file -> {
                                            if ((StringUtils.isBlank(file.getFileName()) || file.getUrl() == null) && file.isOptional()) {
                                                try {
                                                    RemoteMod.File remoteFile = CurseForgeRemoteModRepository.MODS.getModFile(Integer.toString(file.getProjectID()), Integer.toString(file.getFileID()));
                                                    return file.withFileName(remoteFile.getFilename()).withURL(remoteFile.getUrl());
                                                } catch (FileNotFoundException fof) {
                                                    Logging.LOG.log(Level.WARNING, "Could not query api.curseforge.com for deleted mods: " + file.getProjectID() + ", " + file.getFileID(), fof);
                                                    return file;
                                                } catch (IOException | JsonParseException e) {
                                                    Logging.LOG.log(Level.WARNING, "Unable to fetch the file name projectID=" + file.getProjectID() + ", fileID=" + file.getFileID(), e);
                                                    return file;
                                                }
                                            } else {
                                                return file;
                                            }
                                        })
                                        .collect(Collectors.toList())));
                    } else {
                        return manifest;
                    }
                }).whenComplete(Schedulers.javafx(), (manifest, exception) -> {
                    if (exception instanceof ManuallyCreatedModpackException) {
                        hideSpinner();
                        lblName.setText(selectedFile.getName());
                        installAsVersion.set(false);
                        lblModpackLocation.setText(selectedFile.getAbsolutePath());

                        if (!name.isPresent()) {
                            // trim: https://github.com/huanghongxun/HMCL/issues/962
                            txtModpackName.setText(FileUtils.getNameWithoutExtension(selectedFile));
                        }

                        Controllers.confirm(i18n("modpack.type.manual.warning"), i18n("install.modpack"), MessageDialogPane.MessageType.WARNING,
                                () -> {},
                                controller::onEnd);

                        controller.getSettings().put(MODPACK_MANUALLY_CREATED, true);
                    } else if (exception != null) {
                        LOG.log(Level.WARNING, "Failed to read modpack manifest", exception);
                        Controllers.dialog(i18n("modpack.task.install.error"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        Platform.runLater(controller::onEnd);
                    } else if (manifest.getManifest() instanceof CurseManifest) {
                        optionalFiles.updateOptionalFileList(((CurseManifest) manifest.getManifest()).getFiles());
                        waitingForOptionalFiles.set(false);
                    }
                }).start();
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(MODPACK_FILE);
    }

    protected void onInstall() {
        if (!txtModpackName.validate()) return;
        controller.getSettings().put(MODPACK_NAME, txtModpackName.getText());
        controller.getSettings().put(MODPACK_CHARSET, charset);
        controller.getSettings().put(MODPACK_SELECTED_FILES, optionalFiles.getSelected());
        controller.onFinish();
    }

    protected void onDescribe() {
        if (manifest != null) {
            FXUtils.showWebDialog(i18n("modpack.description"), manifest.getDescription());
        }
    }

    public static final String MODPACK_FILE = "MODPACK_FILE";
    public static final String MODPACK_NAME = "MODPACK_NAME";
    public static final String MODPACK_MANIFEST = "MODPACK_MANIFEST";
    public static final String MODPACK_CHARSET = "MODPACK_CHARSET";
    public static final String MODPACK_MANUALLY_CREATED = "MODPACK_MANUALLY_CREATED";

    public static final String MODPACK_SELECTED_FILES = "MODPACK_SELECTED_FILES";
}
