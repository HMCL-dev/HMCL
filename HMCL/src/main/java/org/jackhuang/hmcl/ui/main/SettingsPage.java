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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
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
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public SettingsPage() {
        FXUtils.smoothScrolling(scroll);

        // ==== Languages ====
        cboLanguage.getItems().setAll(SupportedLocale.getSupportedLocales());
        selectedItemPropertyFor(cboLanguage).bindBidirectional(config().localizationProperty());

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

    /// This method guarantees to close both `input` and the current zip entry.
    ///
    /// If no exception occurs, this method returns `true`;
    /// If an exception occurs while reading from `input`, this method returns `false`;
    /// If an exception occurs while writing to `output`, this method will throw it as is.
    private static boolean exportLogFile(ZipOutputStream output,
                                         Path file, // For logging
                                         String entryName,
                                         InputStream input,
                                         byte[] buffer) throws IOException {
        //noinspection TryFinallyCanBeTryWithResources
        try {
            output.putNextEntry(new ZipEntry(entryName));
            int read;
            while (true) {
                try {
                    read = input.read(buffer);
                    if (read <= 0)
                        return true;
                } catch (Throwable ex) {
                    LOG.warning("Failed to decompress log file " + file, ex);
                    return false;
                }

                output.write(buffer, 0, read);
            }
        } finally {
            try {
                input.close();
            } catch (Throwable ex) {
                LOG.warning("Failed to close log file " + file, ex);
            }
            output.closeEntry();
        }
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

                    byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                    try (var os = Files.newOutputStream(outputFile);
                         var zos = new ZipOutputStream(os)) {

                        for (Path path : recentLogFiles) {
                            String fileName = FileUtils.getName(path);
                            String extension = StringUtils.substringAfterLast(fileName, '.');

                            if ("gz".equals(extension) || "xz".equals(extension)) {
                                // If an exception occurs while decompressing the input file, we should
                                // ensure the input file and the current zip entry are closed,
                                // then copy the compressed file content as-is into a new entry in the zip file.

                                InputStream input = null;
                                try {
                                    input = Files.newInputStream(path);
                                    input = "gz".equals(extension)
                                            ? new GZIPInputStream(input)
                                            : new XZInputStream(input);
                                } catch (Throwable ex) {
                                    LOG.warning("Failed to open log file " + path, ex);
                                    IOUtils.closeQuietly(input, ex);
                                    input = null;
                                }

                                String entryName = StringUtils.substringBeforeLast(fileName, ".");
                                if (input != null && exportLogFile(zos, path, entryName, input, buffer))
                                    continue;
                            }

                            // Copy the log file content as-is into a new entry in the zip file.
                            // If an exception occurs while decompressing the input file, we should
                            // ensure the input file and the current zip entry are closed.

                            InputStream input;
                            try {
                                input = Files.newInputStream(path);
                            } catch (Throwable ex) {
                                LOG.warning("Failed to open log file " + path, ex);
                                continue;
                            }

                            exportLogFile(zos, path, fileName, input, buffer);
                        }

                        zos.putNextEntry(new ZipEntry("hmcl-latest.log"));
                        LOG.exportLogs(zos);
                        zos.closeEntry();
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
        String commonDirectory = Settings.instance().getCommonDirectory();
        if (commonDirectory != null) {
            FileUtils.cleanDirectoryQuietly(Path.of(commonDirectory, "cache"));
        }
    }
}
