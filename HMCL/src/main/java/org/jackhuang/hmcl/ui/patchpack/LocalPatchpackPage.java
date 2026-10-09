/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.patchpack;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.patchpack.PatchpackHelper;
import org.jackhuang.hmcl.patchpack.PatchpackInfo;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineTextPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class LocalPatchpackPage extends SpinnerPane implements WizardPage {
    static final SettingsMap.Key<Charset> PATCH_PACK_CHARSET = new SettingsMap.Key<>("PATCH_PACK_CHARSET");

    private final WizardController controller;

    public LocalPatchpackPage(WizardController controller) {
        this.controller = controller;

        JFXButton btnInstall = FXUtils.newRaisedButton(i18n("button.install"));
        btnInstall.setOnAction(e -> onInstall());
        btnInstall.setDisable(true);

        JFXButton btnURL = FXUtils.newBorderButton(i18n("patchpack.url"));
        btnURL.setVisible(false);
        btnURL.setManaged(false);

        ComponentList componentList = new ComponentList();

        VBox pane = new VBox();
        pane.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(pane, 500);

        BorderPane buttons = new BorderPane();
        buttons.setLeft(btnURL);
        buttons.setRight(btnInstall);

        Path file = controller.getSettings().get(PatchpackInstallWizardProvider.PATCH_PACK_FILE);
        if (file == null) {
            Controllers.dialog(i18n("patchpack.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            FXUtils.runInFX(controller::onEnd);
            pane.getChildren().setAll(componentList);
            setContent(pane);
            return;
        }

        showSpinner();
        Task.supplyAsync(() -> PatchpackHelper.findSuitableEncoding(file)).thenApplyAsync(encoding -> new LoadedPatchpack(encoding, PatchpackHelper.readPatchPackInfo(file, encoding))).whenComplete(Schedulers.javafx(), (loaded, exception) -> {
            hideSpinner();

            if (exception != null) {
                LOG.warning("Failed to read patch pack " + file, exception);
                Controllers.dialog(i18n("patchpack.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                Platform.runLater(controller::onEnd);
                return;
            }

            controller.getSettings().put(PATCH_PACK_CHARSET, loaded.charset());
            controller.getSettings().put(PatchpackInstallWizardProvider.PATCH_PACK_INFO, loaded.info());

            PatchpackInfo info = loaded.info();
            componentList.getContent().add(createTextPane(i18n("patchpack.name"), info.name()));
            if (StringUtils.isNotBlank(info.description()))
                componentList.getContent().add(createTextPane(i18n("patchpack.description"), info.description()));
            if (info.authors() != null && !info.authors().isEmpty())
                componentList.getContent().add(createTextPane(i18n("archive.author"), String.join(", ", info.authors())));

            var config = readModpackConfiguration(controller);

            @Nullable LineTextPane namePane = createNamePane(info, config != null ? config.getName() : this.controller.getSettings().get(PatchpackInstallWizardProvider.INSTANCE_ID).id());
            if (namePane != null) componentList.getContent().add(namePane);

            @Nullable LineTextPane versionPane = createVersionPane(info, config != null ? config.getVersion() : null);
            if (versionPane != null) componentList.getContent().add(versionPane);

            componentList.getContent().add(buttons);

            if (StringUtils.isNotBlank(info.url())) {
                btnURL.setVisible(true);
                btnURL.setManaged(true);
                btnURL.setOnAction(e -> FXUtils.openLink(info.url()));
            }

            btnInstall.setDisable(false);
        }).start();

        pane.getChildren().setAll(componentList);
        setContent(pane);
    }

    private static LineTextPane createTextPane(String title, String text) {
        LineTextPane pane = new LineTextPane();
        pane.setTitle(title);
        pane.setText(text);
        return pane;
    }

    private static @Nullable LineTextPane createVersionPane(PatchpackInfo info, @Nullable String instanceVersion) {
        if (info.modpack() == null || StringUtils.isBlank(info.modpack().versionRange())) {
            return null;
        }

        if (instanceVersion == null) instanceVersion = i18n("message.unknown");

        LineTextPane pane = new LineTextPane();
        pane.setTitle(i18n("patchpack.modpack_version_range"));
        pane.setText(i18n("patchpack.difference", info.modpack().versionRange(), instanceVersion));
        if (info.isOutOfRange(instanceVersion)) {
            pane.getRightLabel().setStyle("-fx-text-fill: -monet-tertiary;");
        }

        var translated = translateVersionRange(info.modpack().versionRange());
        if (translated != null) {
            pane.setText(i18n("patchpack.difference", String.format(translated, i18n("patchpack.target_version")), instanceVersion));
        }

        return pane;
    }

    private static String sanitizeName(String s) {
        return s.toLowerCase(Locale.ROOT).replace(" ", "").replace(":", "").replace("'", "");
    }

    private static @Nullable LineTextPane createNamePane(PatchpackInfo info, String currentName) {
        if (info.modpack() == null || StringUtils.isBlank(info.modpack().name())) {
            return null;
        }

        LineTextPane pane = new LineTextPane();
        pane.setTitle(i18n("patchpack.modpack_name"));


        var cleanModpackName = sanitizeName(info.modpack().name());
        var cleanCurrentName = sanitizeName(currentName);

        if (!cleanModpackName.equals(cleanCurrentName)) {
            pane.setText(i18n("patchpack.difference", info.modpack().name(), currentName));
            pane.getRightLabel().setStyle("-fx-text-fill: -monet-tertiary;");
        } else {
            pane.setText(info.modpack().name());
        }

        return pane;
    }

    private static @Nullable ModpackConfiguration<?> readModpackConfiguration(WizardController controller) {
        @Nullable GameInstanceID instanceId = controller.getSettings().get(PatchpackInstallWizardProvider.INSTANCE_ID);
        HMCLGameRepository repository = controller.getSettings().get(PatchpackInstallWizardProvider.REPOSITORY);
        if (instanceId == null || repository == null) return null;

        @Nullable HMCLGameInstance instance = repository.findInstance(instanceId);
        if (instance == null) return null;

        try {
            @Nullable ModpackConfiguration<?> configuration = instance.readModpackConfiguration();
            return configuration;
        } catch (IOException e) {
            LOG.warning("Failed to read modpack configuration of " + instanceId, e);
            return null;
        }
    }

    private void onInstall() {
        if (controller.getSettings().get(PatchpackInstallWizardProvider.PATCH_PACK_INFO) == null) return;

        controller.onFinish();
    }

    @Override
    public String getTitle() {
        return i18n("patchpack.task.import");
    }

    private static final Pattern VERSION_RANGE_PATTERN = Pattern.compile("([\\[(])([^,]*),?([^,]*)([\\])])");

    private static @Nullable String translateVersionRange(String range) {
        Matcher m = VERSION_RANGE_PATTERN.matcher(range.trim());
        if (!m.matches()) {
            return null;
        }
        String lower = m.group(2).trim();
        String upper = m.group(3).trim();

        if (upper.isEmpty() && !range.contains(",")) {
            return "%s == " + lower;
        }
        String leftOp = m.group(1).equals("[") ? "≤" : "<";
        String rightOp = m.group(4).equals("]") ? "≤" : "<";

        if (!lower.isEmpty() && !upper.isEmpty()) return lower + " " + leftOp + " %s " + rightOp + " " + upper;
        if (!lower.isEmpty()) return "%s " + (m.group(1).equals("[") ? "≥ " : "> ") + lower;
        if (!upper.isEmpty()) return "%s " + (m.group(4).equals("]") ? "≤ " : "< ") + upper;

        return null;
    }

    private record LoadedPatchpack(Charset charset, PatchpackInfo info) {
    }
}
