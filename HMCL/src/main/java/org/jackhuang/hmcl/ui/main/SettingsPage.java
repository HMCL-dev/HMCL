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

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleGroup;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChannel;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.Restarter;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.Locales;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.logging.Level;
import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class SettingsPage extends SettingsView {

    private InvalidationListener updateListener;

    private boolean ignoreLanguageChange = false;

    public SettingsPage() {
        FXUtils.smoothScrolling(scroll);

        // ==== Languages ====
        cboLanguage.getItems().setAll(Locales.LOCALES);
        selectedItemPropertyFor(cboLanguage).bindBidirectional(config().localizationProperty());

        cboLanguage.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (ignoreLanguageChange) return;

            if (oldValue != null && newValue != null && !oldValue.equals(newValue)) {
                JFXButton restartButton = new JFXButton(i18n("button.restart"));
                restartButton.getStyleClass().add("dialog-success");
                restartButton.setOnAction(e -> {
                    try {
                        Restarter.restartWithLocale(newValue);
                    } catch (IOException ex) {
                        LOG.log(Level.WARNING, "Failed to restart", ex);
                        ignoreLanguageChange = true;
                        cboLanguage.getSelectionModel().select(oldValue);
                        ignoreLanguageChange = false;
                    }
                });

                Runnable cancelAction = () -> {
                    ignoreLanguageChange = true;
                    cboLanguage.getSelectionModel().select(newValue);
                    ignoreLanguageChange = false;
                };

                Controllers.confirmAction(
                        i18n("settings.launcher.language.restart_message"),
                        i18n("settings.launcher.language.restart_title"),
                        MessageType.INFO,
                        restartButton,
                        cancelAction
                );
            }
        });

        disableAutoGameOptionsPane.selectedProperty().bindBidirectional(config().disableAutoGameOptionsProperty());
        // ====

        fileCommonLocation.selectedDataProperty().bindBidirectional(config().commonDirTypeProperty());
        fileCommonLocationSublist.subtitleProperty().bind(
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

        ToggleGroup updateChannelGroup = new ToggleGroup();
        chkUpdateDev.setToggleGroup(updateChannelGroup);
        chkUpdateDev.setUserData(UpdateChannel.DEVELOPMENT);
        chkUpdateStable.setToggleGroup(updateChannelGroup);
        chkUpdateStable.setUserData(UpdateChannel.STABLE);
        ObjectProperty<UpdateChannel> updateChannel = selectedItemPropertyFor(updateChannelGroup, UpdateChannel.class);
        updateChannel.set(UpdateChannel.getChannel());
        updateChannel.addListener((a, b, newValue) -> {
            UpdateChecker.requestCheckUpdate(newValue);
        });
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
        thread(() -> {
            String nameBase = "hmcl-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"));
            List<Path> recentLogFiles = LOG.findRecentLogFiles(5);

            Path outputFile;
            try {
                if (recentLogFiles.isEmpty()) {
                    outputFile = Metadata.CURRENT_DIRECTORY.resolve(nameBase + ".log");

                    LOG.info("Exporting latest logs to " + outputFile);
                    try (OutputStream output = Files.newOutputStream(outputFile)) {
                        LOG.exportLogs(output);
                    }
                } else {
                    outputFile = Metadata.CURRENT_DIRECTORY.resolve(nameBase + ".zip");

                    LOG.info("Exporting latest logs to " + outputFile);

                    Path tempFile = Files.createTempFile("hmcl-decompress-log-", ".txt");
                    try (var tempChannel = FileChannel.open(tempFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
                         var os = Files.newOutputStream(outputFile);
                         var zos = new ZipOutputStream(os)) {

                        for (Path path : recentLogFiles) {
                            String extension = FileUtils.getExtension(path);
                            decompress:
                            if ("gz".equalsIgnoreCase(extension) || "xz".equalsIgnoreCase(extension)) {
                                try (InputStream fis = Files.newInputStream(path);
                                     InputStream uncompressed = "gz".equalsIgnoreCase(extension)
                                             ? new GZIPInputStream(fis)
                                             : new XZInputStream(fis)) {
                                    uncompressed.transferTo(Channels.newOutputStream(tempChannel));
                                } catch (IOException e) {
                                    LOG.warning("Failed to decompress log: " + path, e);
                                    break decompress;
                                }

                                zos.putNextEntry(new ZipEntry(StringUtils.substringBeforeLast(FileUtils.getName(path), '.')));
                                Channels.newInputStream(tempChannel).transferTo(zos);
                                zos.closeEntry();
                                tempChannel.truncate(0);
                                continue;
                            }

                            zos.putNextEntry(new ZipEntry(FileUtils.getName(path)));
                            Files.copy(path, zos);
                            zos.closeEntry();
                        }

                        zos.putNextEntry(new ZipEntry("hmcl-latest.log"));
                        LOG.exportLogs(zos);
                        zos.closeEntry();
                    } finally {
                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (IOException ignored) {
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warning("Failed to export logs", e);
                Platform.runLater(() -> Controllers.dialog(i18n("settings.launcher.launcher_log.export.failed") + "\n" + StringUtils.getStackTrace(e), null, MessageType.ERROR));
                return;
            }

            Platform.runLater(() -> Controllers.dialog(i18n("settings.launcher.launcher_log.export.success", outputFile)));
            FXUtils.showFileInExplorer(outputFile);
        });
    }

    @Override
    protected void onSponsor() {
        FXUtils.openLink("https://github.com/HMCL-dev/HMCL");
    }

    @Override
    protected void clearCacheDirectory() {
        FileUtils.cleanDirectoryQuietly(new File(Settings.instance().getCommonDirectory(), "cache"));
    }
}
