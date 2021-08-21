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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToggleGroup;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChannel;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.i18n.Locales;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.reversedSelectedPropertyFor;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;

public final class SettingsPage extends SettingsView {

    private InvalidationListener updateListener;

    public SettingsPage() {
        FXUtils.smoothScrolling(scroll);

        // ==== Download sources ====
        cboDownloadSource.getItems().setAll(DownloadProviders.providersById.keySet());
        selectedItemPropertyFor(cboDownloadSource).bindBidirectional(config().downloadTypeProperty());
        // ====

        // ==== Font ====
        cboFont.valueProperty().bindBidirectional(config().fontFamilyProperty());

        txtFontSize.textProperty().bindBidirectional(config().fontSizeProperty(),
                SafeStringConverter.fromFiniteDouble()
                        .restrict(it -> it > 0)
                        .fallbackTo(12.0)
                        .asPredicate(Validator.addTo(txtFontSize)));

        lblDisplay.fontProperty().bind(Bindings.createObjectBinding(
                () -> Font.font(config().getFontFamily(), config().getFontSize()),
                config().fontFamilyProperty(), config().fontSizeProperty()));
        // ====

        // ==== Languages ====
        cboLanguage.getItems().setAll(Locales.LOCALES);
        selectedItemPropertyFor(cboLanguage).bindBidirectional(config().localizationProperty());
        // ====

        // ==== Proxy ====
        txtProxyHost.textProperty().bindBidirectional(config().proxyHostProperty());
        txtProxyPort.textProperty().bindBidirectional(config().proxyPortProperty(),
                SafeStringConverter.fromInteger()
                        .restrict(it -> it >= 0 && it <= 0xFFFF)
                        .fallbackTo(0)
                        .asPredicate(Validator.addTo(txtProxyPort)));
        txtProxyUsername.textProperty().bindBidirectional(config().proxyUserProperty());
        txtProxyPassword.textProperty().bindBidirectional(config().proxyPassProperty());

        proxyPane.disableProperty().bind(chkDisableProxy.selectedProperty());
        authPane.disableProperty().bind(chkProxyAuthentication.selectedProperty().not());

        reversedSelectedPropertyFor(chkDisableProxy).bindBidirectional(config().hasProxyProperty());
        chkProxyAuthentication.selectedProperty().bindBidirectional(config().hasProxyAuthProperty());

        ToggleGroup proxyConfigurationGroup = new ToggleGroup();
        chkProxyHttp.setUserData(Proxy.Type.HTTP);
        chkProxyHttp.setToggleGroup(proxyConfigurationGroup);
        chkProxySocks.setUserData(Proxy.Type.SOCKS);
        chkProxySocks.setToggleGroup(proxyConfigurationGroup);
        selectedItemPropertyFor(proxyConfigurationGroup, Proxy.Type.class).bindBidirectional(config().proxyTypeProperty());
        // ====

        fileCommonLocation.loadChildren(Collections.singletonList(
                fileCommonLocation.createChildren(i18n("launcher.cache_directory.default"), EnumCommonDirectory.DEFAULT)
        ), EnumCommonDirectory.CUSTOM);
        fileCommonLocation.selectedDataProperty().bindBidirectional(config().commonDirTypeProperty());
        fileCommonLocation.customTextProperty().bindBidirectional(config().commonDirectoryProperty());
        fileCommonLocation.subtitleProperty().bind(
                Bindings.createObjectBinding(() -> Optional.ofNullable(Settings.instance().getCommonDirectory())
                                .orElse(i18n("launcher.cache_directory.disabled")),
                        config().commonDirectoryProperty(), config().commonDirTypeProperty()));

        // ==== Update ====
        FXUtils.installFastTooltip(btnUpdate, i18n("update.tooltip"));
        updateListener = any -> {
            btnUpdate.setVisible(UpdateChecker.isOutdated());

            if (UpdateChecker.isOutdated()) {
                lblUpdateSub.setText(i18n("update.newest_version", UpdateChecker.getLatestVersion().getVersion()));
                lblUpdateSub.getStyleClass().setAll("update-label");

                lblUpdate.setText(i18n("update.found"));
                lblUpdate.getStyleClass().setAll("update-label");
            } else if (UpdateChecker.isCheckingUpdate()) {
                lblUpdateSub.setText(i18n("update.checking"));
                lblUpdateSub.getStyleClass().setAll("subtitle-label");

                lblUpdate.setText(i18n("update"));
                lblUpdate.getStyleClass().setAll();
            } else {
                lblUpdateSub.setText(i18n("update.latest"));
                lblUpdateSub.getStyleClass().setAll("subtitle-label");

                lblUpdate.setText(i18n("update"));
                lblUpdate.getStyleClass().setAll();
            }
        };
        UpdateChecker.latestVersionProperty().addListener(new WeakInvalidationListener(updateListener));
        UpdateChecker.outdatedProperty().addListener(new WeakInvalidationListener(updateListener));
        UpdateChecker.checkingUpdateProperty().addListener(new WeakInvalidationListener(updateListener));
        updateListener.invalidated(null);

        lblUpdateNote.setWrappingWidth(470);

        ToggleGroup updateChannelGroup = new ToggleGroup();
        chkUpdateDev.setToggleGroup(updateChannelGroup);
        chkUpdateDev.setUserData(UpdateChannel.DEVELOPMENT);
        chkUpdateStable.setToggleGroup(updateChannelGroup);
        chkUpdateStable.setUserData(UpdateChannel.STABLE);
        selectedItemPropertyFor(updateChannelGroup, UpdateChannel.class).bindBidirectional(config().updateChannelProperty());
        // ====

        // ==== Background ====
        backgroundItem.loadChildren(Collections.singletonList(
                backgroundItem.createChildren(i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
        ), EnumBackgroundImage.CUSTOM);
        backgroundItem.customTextProperty().bindBidirectional(config().backgroundImageProperty());
        backgroundItem.selectedDataProperty().bindBidirectional(config().backgroundImageTypeProperty());
        backgroundItem.subtitleProperty().bind(
                new When(backgroundItem.selectedDataProperty().isEqualTo(EnumBackgroundImage.DEFAULT))
                        .then(i18n("launcher.background.default"))
                        .otherwise(config().backgroundImageProperty()));
        // ====

        // ==== Theme ====
        ColorPicker picker = new ColorPicker(Color.web(config().getTheme().getColor()));
        picker.getCustomColors().setAll(Theme.SUGGESTED_COLORS);
        picker.setOnAction(e -> {
            Theme theme = Theme.custom(Theme.getColorDisplayName(picker.getValue()));
            config().setTheme(theme);
            Controllers.getScene().getStylesheets().setAll(theme.getStylesheets());
        });
        themeColorPickerContainer.getChildren().setAll(picker);
        Platform.runLater(() -> JFXDepthManager.setDepth(picker, 0));
        // ====
    }

    @Override
    protected void onUpdate() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    @Override
    protected void onExportLogs() {
        // We cannot determine which file is JUL using.
        // So we write all the logs to a new file.
        thread(() -> {
            Path logFile = Paths.get("hmcl-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".log").toAbsolutePath();

            LOG.info("Exporting logs to " + logFile);
            try {
                Files.write(logFile, Logging.getRawLogs());
            } catch (IOException e) {
                Platform.runLater(() -> Controllers.dialog(i18n("settings.launcher.launcher_log.export.failed") + "\n" + e, null, MessageType.ERROR));
                LOG.log(Level.WARNING, "Failed to export logs", e);
                return;
            }

            Platform.runLater(() -> Controllers.dialog(i18n("settings.launcher.launcher_log.export.success", logFile)));
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().open(logFile.toFile());
                } catch (IOException ignored) {
                }
            }
        });
    }

    @Override
    protected void onHelp() {
        FXUtils.openLink(Metadata.HELP_URL);
    }

    @Override
    protected void onSponsor() {
        FXUtils.openLink("https://hmcl.huangyuhui.net/api/redirect/sponsor");
    }

    @Override
    protected void clearCacheDirectory() {
        FileUtils.cleanDirectoryQuietly(new File(Settings.instance().getCommonDirectory(), "cache"));
    }
}
