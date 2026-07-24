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

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ManuallyCreatedModpackException;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WebPage;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LocalModpackPage extends ModpackPage {

    private final BooleanProperty installAsVersion = new SimpleBooleanProperty(true);
    /// The extracted bundled modpack while this page owns its lifecycle.
    private volatile @Nullable Path temporaryModpackFile;
    /// Whether the outer wizard page has been closed.
    private volatile boolean disposed;
    private Modpack manifest = null;
    private Charset charset;

    public LocalModpackPage(WizardController controller) {
        super(controller);

        if (controller.getDisplayer() instanceof Node displayer) {
            displayer.addEventHandler(Navigator.NavigationEvent.EXITED, event -> {
                disposed = true;
                cleanup(controller.getSettings());
            });
        }

        HMCLGameRepository repository = controller.getSettings().get(ModpackPage.REPOSITORY);

        String name = controller.getSettings().get(MODPACK_NAME);
        if (name != null) {
            txtModpackName.setText(name);
            txtModpackName.setDisable(true);
        } else {
            FXUtils.onChangeAndOperate(installAsVersion, installAsVersion -> {
                if (installAsVersion) {
                    txtModpackName.getValidators().setAll(
                            new RequiredValidator(),
                            new Validator(i18n("install.new_game.already_exists"), str -> !repository.versionIdConflicts(str)),
                            new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId));
                } else {
                    txtModpackName.getValidators().setAll(
                            new RequiredValidator(),
                            new Validator(i18n("install.new_game.already_exists"), str -> !ModpackHelper.isExternalGameNameConflicts(str)
                                    && GameDirectoryManager.getGameDirectories().stream()
                                            .noneMatch(existingProfile ->
                                                    str.equals(GameDirectoryManager.getGameDirectoryCustomName(existingProfile)))),
                            new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId));
                }
            });
        }

        btnDescription.setVisible(false);

        Path selectedFile;
        Path filePath = controller.getSettings().get(MODPACK_FILE);
        if (filePath != null) {
            selectedFile = filePath;
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n("modpack.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("modpack"), "*.zip"));
            selectedFile = Controllers.showOpenDialog(chooser);
            if (selectedFile == null) {
                controller.onEnd();
                return;
            }

            controller.getSettings().put(MODPACK_FILE, selectedFile);
        }

        controller.getSettings().put(MODPACK_FILE_OWNER, this);

        showSpinner();
        Task.supplyAsync(() -> CompressingUtils.findSuitableEncoding(selectedFile))
                .thenApplyAsync(encoding -> {
                    Optional<Path> bundledModpack = ModpackHelper.extractBundledModpack(selectedFile, encoding);
                    Path modpackFile = bundledModpack.orElse(selectedFile);
                    if (bundledModpack.isPresent()) {
                        temporaryModpackFile = modpackFile;
                        charset = CompressingUtils.findSuitableEncoding(modpackFile);
                    } else {
                        charset = encoding;
                    }
                    manifest = ModpackHelper.readModpackManifest(modpackFile, charset);
                    return manifest;
                })
                .whenComplete(Schedulers.javafx(), (manifest, exception) -> {
                    if (disposed || !controller.getPages().contains(this)
                            || controller.getSettings().get(MODPACK_FILE_OWNER) != this) {
                        cleanup(controller.getSettings());
                        return;
                    }

                    controller.getSettings().put(MODPACK_FILE,
                            temporaryModpackFile != null ? temporaryModpackFile : selectedFile);

                    if (exception instanceof ManuallyCreatedModpackException) {
                        hideSpinner();
                        nameProperty.set(FileUtils.getName(selectedFile));
                        installAsVersion.set(false);

                        if (name == null) {
                            // trim: https://github.com/HMCL-dev/HMCL/issues/962
                            txtModpackName.setText(FileUtils.getNameWithoutExtension(selectedFile));
                        }

                        Controllers.confirm(i18n("modpack.type.manual.warning"), i18n("install.modpack"), MessageDialogPane.MessageType.WARNING,
                                () -> {},
                                controller::onEnd);

                        controller.getSettings().put(MODPACK_MANUALLY_CREATED, true);
                    } else if (exception != null) {
                        cleanup(controller.getSettings());
                        LOG.warning("Failed to read modpack manifest", exception);
                        Controllers.dialog(i18n("modpack.task.install.error"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        Platform.runLater(controller::onEnd);
                    } else {
                        hideSpinner();
                        controller.getSettings().put(MODPACK_MANIFEST, manifest);
                        nameProperty.set(manifest.getName());
                        versionProperty.set(manifest.getVersion());
                        authorProperty.set(manifest.getAuthor());

                        if (name == null) {
                            // trim: https://github.com/HMCL-dev/HMCL/issues/962
                            txtModpackName.setText(manifest.getName().trim());
                        }

                        btnDescription.setVisible(StringUtils.isNotBlank(manifest.getDescription()));
                    }
                }).start();
    }

    @Override
    public void cleanup(SettingsMap settings) {
        deleteTemporaryModpackFile(temporaryModpackFile);
        if (settings.get(MODPACK_FILE_OWNER) == this) {
            settings.remove(MODPACK_FILE_OWNER);
            settings.remove(MODPACK_FILE);
            deleteTemporaryModpackFile(settings.remove(TEMPORARY_MODPACK_FILE));
        }
    }

    /// Deletes a temporary bundled modpack and logs a failed attempt.
    ///
    /// @param file the temporary file, or `null` when no file was extracted
    static void deleteTemporaryModpackFile(@Nullable Path file) {
        if (file == null) {
            return;
        }

        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warning("Failed to delete temporary bundled modpack " + file, e);
        }
    }

    /// Restores ownership when installation does not create a task.
    ///
    /// @param file the temporary bundled modpack
    void restoreTemporaryModpackFile(Path file) {
        temporaryModpackFile = file;
    }

    protected void onInstall() {
        String name = txtModpackName.getText();

        // Check for non-ASCII characters.
        if (!StringUtils.isASCII(name)) {
            Controllers.dialog(new MessageDialogPane.Builder(
                    i18n("install.name.invalid"),
                    i18n("message.warning"),
                    MessageDialogPane.MessageType.QUESTION)
                    .yesOrNo(() -> {
                        controller.getSettings().put(MODPACK_NAME, name);
                        controller.getSettings().put(MODPACK_CHARSET, charset);
                        if (temporaryModpackFile != null) {
                            controller.getSettings().put(TEMPORARY_MODPACK_FILE, temporaryModpackFile);
                            temporaryModpackFile = null;
                        }
                        controller.onFinish();
                    }, () -> {
                        // The user selects Cancel and does nothing.
                    })
                    .build());
        } else {
            controller.getSettings().put(MODPACK_NAME, name);
            controller.getSettings().put(MODPACK_CHARSET, charset);
            if (temporaryModpackFile != null) {
                controller.getSettings().put(TEMPORARY_MODPACK_FILE, temporaryModpackFile);
                temporaryModpackFile = null;
            }
            controller.onFinish();
        }
    }

    protected void onDescribe() {
        if (manifest != null)
            Controllers.navigate(new WebPage(i18n("modpack.description"), manifest.getDescription()));
    }

    public static final SettingsMap.Key<Path> MODPACK_FILE = new SettingsMap.Key<>("MODPACK_FILE");
    public static final SettingsMap.Key<String> MODPACK_NAME = new SettingsMap.Key<>("MODPACK_NAME");
    public static final SettingsMap.Key<Modpack> MODPACK_MANIFEST = new SettingsMap.Key<>("MODPACK_MANIFEST");
    public static final SettingsMap.Key<Charset> MODPACK_CHARSET = new SettingsMap.Key<>("MODPACK_CHARSET");
    public static final SettingsMap.Key<Boolean> MODPACK_MANUALLY_CREATED = new SettingsMap.Key<>("MODPACK_MANUALLY_CREATED");
    public static final SettingsMap.Key<String> MODPACK_ICON_URL = new SettingsMap.Key<>("MODPACK_ICON_URL");
    /// The local page that currently owns the selected modpack settings.
    static final SettingsMap.Key<LocalModpackPage> MODPACK_FILE_OWNER = new SettingsMap.Key<>("MODPACK_FILE_OWNER");
    /// The bundled modpack temporary file transferred to the installation task.
    static final SettingsMap.Key<Path> TEMPORARY_MODPACK_FILE = new SettingsMap.Key<>("TEMPORARY_MODPACK_FILE");
}
