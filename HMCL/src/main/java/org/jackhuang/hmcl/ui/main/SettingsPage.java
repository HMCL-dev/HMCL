// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3FormRow;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3ListPane;
import org.glavo.m3fx.controls.M3ListSectionHeader;
import org.glavo.m3fx.controls.M3ListStyle;
import org.glavo.m3fx.controls.M3SelectSettingItem;
import org.glavo.m3fx.controls.M3SwitchSettingItem;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ScrollUtils;
import org.jackhuang.hmcl.ui.construct.MessageType;
import org.jackhuang.hmcl.upgrade.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class SettingsPage extends ScrollPane {
    /// Keeps the weakly registered update status listener alive while this page is in use.
    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationListener updateListener;

    /// Creates the update, language, and miscellaneous settings groups.
    public SettingsPage() {
        this(settings());
    }

    /// Binds this page to the supplied launcher preferences.
    SettingsPage(LauncherSettings preferences) {
        this.setFitToWidth(true);

        VBox rootPane = new VBox(10);
        rootPane.setPadding(new Insets(10));
        this.setContent(rootPane);
        ScrollUtils.installSmoothScrolling(this);

        var updateButton = new M3IconButton(SVG.UPDATE.createIcon(20));
        updateButton.setAccessibleText(i18n("update.tooltip"));
        updateButton.setOnAction(e -> onUpdate());
        FXUtils.installFastTooltip(updateButton, i18n("update.tooltip"));

        var channel = new M3SelectSettingItem<UpdateChannel>(i18n("update"));
        channel.getStyleClass().add("update-pane");
        channel.setHeadlineTrailing(updateButton);
        channel.setConverter(value -> i18n("update.channel." + value.channelName));
        channel.setDescriptionConverter(value -> i18n("update.note." + value.channelName));
        channel.getItems().setAll(UpdateChannel.STABLE, UpdateChannel.DEVELOPMENT);
        channel.setValue(UpdateChannel.getChannel());

        updateListener = ignored -> {
            boolean outdated = UpdateChecker.isOutdated();
            updateButton.setVisible(outdated);
            updateButton.setManaged(outdated);
            channel.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), outdated);
            channel.setSupportingText(updateStatusText());
        };
        var weakUpdateListener = new WeakInvalidationListener(updateListener);
        UpdateChecker.latestVersionProperty().addListener(weakUpdateListener);
        UpdateChecker.outdatedProperty().addListener(weakUpdateListener);
        UpdateChecker.checkingUpdateProperty().addListener(weakUpdateListener);
        UpdateChecker.errorProperty().addListener(weakUpdateListener);
        updateListener.invalidated(null);

        var preview = new M3SwitchSettingItem(i18n("update.preview"));
        preview.setSupportingText(i18n("update.preview.subtitle"));
        preview.selectedProperty().bindBidirectional(preferences.acceptPreviewUpdateProperty());
        InvalidationListener checkUpdate = ignored -> UpdateChecker.requestCheckUpdate(channel.getValue(), preview.isSelected());
        channel.valueProperty().addListener(checkUpdate);
        preview.selectedProperty().addListener(checkUpdate);

        var disableAutoShow = new M3SwitchSettingItem(i18n("update.disable_auto_show_update_dialog"));
        disableAutoShow.setSupportingText(i18n("update.disable_auto_show_update_dialog.subtitle"));
        disableAutoShow.selectedProperty().bindBidirectional(preferences.disableAutoShowUpdateDialogProperty());
        addSection(rootPane, i18n("update"), channel, preview, disableAutoShow);

        var language = new M3SelectSettingItem<SupportedLocale>(i18n("settings.launcher.language"));
        language.setSupportingText(i18n("settings.take_effect_after_restart"));
        SupportedLocale currentLocale = I18n.getLocale();
        language.setConverter(locale -> {
            if (locale.isDefault())
                return locale.getDisplayName(currentLocale);
            if (locale.isSameLanguage(currentLocale))
                return locale.getDisplayName(locale);
            return locale.getDisplayName(currentLocale) + " - " + locale.getDisplayName(locale);
        });
        language.getItems().setAll(SupportedLocale.getSupportedLocales());
        language.valueProperty().bindBidirectional(preferences.languageProperty());
        addSection(rootPane, i18n("settings.launcher.language"), language);

        var disableAprilFools = new M3SwitchSettingItem(i18n("settings.launcher.disable_april_fools"));
        disableAprilFools.setSupportingText(i18n("settings.take_effect_after_restart"));
        disableAprilFools.selectedProperty().bindBidirectional(preferences.disableAprilFoolsProperty());
        addSection(rootPane, i18n("settings.launcher.misc"), disableAprilFools, createDebugRow());
    }

    /// Groups related settings under a heading with segmented list styling.
    private static void addSection(VBox root, String title, Node... rows) {
        var list = new M3ListPane();
        list.setListStyle(M3ListStyle.SEGMENTED);
        list.getItems().setAll(rows);
        root.getChildren().addAll(new M3ListSectionHeader(title), list);
    }

    /// Returns the status shown below the update channel selector.
    private static String updateStatusText() {
        if (UpdateChecker.isCheckingUpdate())
            return i18n("update.checking");
        if (UpdateChecker.isOutdated())
            return i18n("update.newest_version", UpdateChecker.getLatestVersion().version());
        @Nullable Throwable error = UpdateChecker.errorProperty().get();
        if (error instanceof SelfVerificationException)
            return i18n("update.unverified");
        if (error != null)
            return i18n("update.check_failed", error.getClass().getSimpleName() + ": " + error.getLocalizedMessage());
        return i18n("update.latest");
    }

    /// Creates the independent log actions, which wrap when the content column is narrow.
    private M3FormRow createDebugRow() {
        M3Button openLogFolderButton = new M3Button(i18n("settings.launcher.launcher_log.reveal"), M3ButtonVariant.OUTLINED);
        openLogFolderButton.setOnAction(e -> openLogFolder());
        if (LOG.getLogFile() == null)
            openLogFolderButton.setDisable(true);

        M3Button logButton = new M3Button(i18n("settings.launcher.launcher_log.export"), M3ButtonVariant.OUTLINED);
        var exporting = FXUtils.newButtonLoadingProperty(logButton);
        logButton.disableProperty().bind(exporting);
        logButton.setOnAction(e -> {
            exporting.set(true);
            onExportLogs().whenCompleteAsync((result, exception) -> {
                exporting.set(false);
                if (exception == null) {
                    Controllers.dialog(i18n("settings.launcher.launcher_log.export.success", result));
                    FXUtils.showFileInExplorer(result);
                } else {
                    LOG.warning("Failed to export logs", exception);
                    Controllers.dialog(
                            i18n("settings.launcher.launcher_log.export.failed") + "\n" + StringUtils.getStackTrace(exception),
                            null,
                            MessageType.ERROR
                    );
                }
            }, Schedulers.javafx());
        });

        var actions = new FlowPane(10, 8, openLogFolderButton, logButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return new M3FormRow(i18n("settings.launcher.debug"), actions);
    }

    private void openLogFolder() {
        FXUtils.openFolder(LOG.getLogFile().getParent());
    }

    private void onUpdate() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    private static String getEntryName(Set<String> entryNames, String name) {
        if (entryNames.add(name)) {
            return name;
        }

        for (long i = 1; ; i++) {
            String newName = name + "." + i;
            if (entryNames.add(newName)) {
                return newName;
            }
        }
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

    private CompletableFuture<Path> onExportLogs() {
        return CompletableFuture.supplyAsync(Lang.wrap(() -> {
            String nameBase = "hmcl-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"));
            List<Path> recentLogFiles = LOG.findRecentLogFiles(5);

            Path outputFile;
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

                    Set<String> entryNames = new HashSet<>();

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

                            String entryName = getEntryName(entryNames, StringUtils.substringBeforeLast(fileName, "."));
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

                        exportLogFile(zos, path, getEntryName(entryNames, fileName), input, buffer);
                    }

                    zos.putNextEntry(new ZipEntry(getEntryName(entryNames, "hmcl-latest.log")));
                    LOG.exportLogs(zos);
                    zos.closeEntry();
                }
            }

            return outputFile;
        }), Schedulers.io());
    }
}
