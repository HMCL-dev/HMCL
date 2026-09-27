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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ManuallyCreatedModpackException;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackFile;
import org.jackhuang.hmcl.modpack.ModpackManifest;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WebPage;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LocalModpackPage extends ModpackPage {

    private final BooleanProperty installAsVersion = new SimpleBooleanProperty(true);
    private Modpack manifest = null;
    private Charset charset;
    private final ObservableList<ModpackFile> allFiles = FXCollections.observableList(new ArrayList<>());
    private final ObservableSet<String> excludedFiles = FXCollections.observableSet(new HashSet<>());
    private final BooleanProperty loadingOptionalFiles = new SimpleBooleanProperty(false);
    private final BooleanProperty loadedOptionalFiles = new SimpleBooleanProperty(true);

    public LocalModpackPage(WizardController controller) {
        super(controller);

        btnOptionalFiles.setOnAction(ev -> controller.onNext(new OptionalFilesPage(
                this::onInstall,
                this::loadOptionalFiles,
                loadingOptionalFiles,
                loadedOptionalFiles,
                new FilteredList<>(allFiles, ModpackFile::optional),
                excludedFiles)));

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
                            new Validator(i18n("install.new_game.already_exists"), str -> !repository.instanceIdConflicts(str)),
                            new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidInstanceId));
                } else {
                    txtModpackName.getValidators().setAll(
                            new RequiredValidator(),
                            new Validator(i18n("install.new_game.already_exists"), str -> !ModpackHelper.isExternalGameNameConflicts(str)
                                    && GameDirectoryManager.getGameDirectories().stream()
                                            .noneMatch(existingProfile ->
                                                    str.equals(GameDirectoryManager.getGameDirectoryCustomName(existingProfile)))),
                            new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidInstanceId));
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

        showSpinner();
        Task.supplyAsync(() -> CompressingUtils.findSuitableEncoding(selectedFile))
                .thenApplyAsync(encoding -> {
                    charset = encoding;
                    return ModpackHelper.readModpackManifest(selectedFile, encoding);
                })
                .whenComplete(Schedulers.javafx(), (manifest, exception) -> {
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
                        LOG.warning("Failed to read modpack manifest", exception);
                        Controllers.dialog(i18n("modpack.task.install.error"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        Platform.runLater(controller::onEnd);
                    } else if (manifest == null) {
                        Controllers.dialog(i18n("modpack.task.install.error"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        Platform.runLater(controller::onEnd);
                    } else {
                        this.manifest = manifest;
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

                        if (manifest.getManifest() instanceof ModpackManifest.SupportOptional supportOptional) {
                            allFiles.setAll(supportOptional.getFiles());
                            if (allFiles.stream().anyMatch(ModpackFile::optional)) {
                                loadOptionalFiles();
                                btnOptionalFiles.setVisible(true);
                                btnOptionalFiles.setManaged(true);
                            }
                        }
                    }
                }).start();
    }

    private void loadOptionalFiles() {
        Objects.requireNonNull(manifest);
        loadingOptionalFiles.set(true);
        loadedOptionalFiles.set(false);
        Task.supplyAsync(() -> manifest.getManifest().getProvider().loadFiles(DownloadProviders.getDownloadProvider(), manifest.getManifest()))
                .whenComplete(Schedulers.javafx(), (manifest1, exception) -> {
                    loadingOptionalFiles.set(false);
                    List<? extends ModpackFile> files = ((ModpackManifest.SupportOptional) manifest1).getFiles();
                    manifest.setManifest(manifest1);
                    allFiles.setAll(files);
                    if (files.stream().anyMatch(s -> s.optional() && (!s.addonQueried() || s.fileName() == null))) {
                        LOG.warning("Failed to load optional files");
                    } else {
                        loadedOptionalFiles.set(true);
                    }
                }).start();
    }

    @Override
    public void cleanup(SettingsMap settings) {
        settings.remove(MODPACK_FILE);
    }

    protected void onInstall() {
        String name = txtModpackName.getText();

        // Check for non-ASCII characters.
        if (!StringUtils.isASCII(name)) {
            Controllers.dialog(new MessageDialogPane.Builder(
                    i18n("install.name.invalid"),
                    i18n("message.warning"),
                    MessageDialogPane.MessageType.QUESTION)
                    .yesOrNo(() -> finishInstall(name), () -> {
                        // The user selects Cancel and does nothing.
                    })
                    .build());
        } else {
            finishInstall(name);
        }
    }

    private void finishInstall(String name) {
        controller.getSettings().put(MODPACK_NAME, name);
        controller.getSettings().put(MODPACK_CHARSET, charset);
        controller.getSettings().put(MODPACK_EXCLUDED_FILES, getExcludedFiles());
        controller.onFinish();
    }

    private @Nullable Set<String> getExcludedFiles() {
        if (allFiles.isEmpty())
            return null;
        return Set.copyOf(excludedFiles);
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
    public static final SettingsMap.Key<Set<String>> MODPACK_EXCLUDED_FILES = new SettingsMap.Key<>("MODPACK_EXCLUDED_FILES");
}
