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
import com.jfoenix.controls.JFXRadioButton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.EnumCommonDirectory;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChannel;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
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
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class SettingsPage extends ScrollPane {

    @SuppressWarnings("FieldCanBeLocal")
    private final ToggleGroup updateChannelGroup;
    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationListener updateListener;

    public SettingsPage() {
        this.setFitToWidth(true);

        VBox rootPane = new VBox();
        rootPane.setPadding(new Insets(10));
        this.setContent(rootPane);
        FXUtils.smoothScrolling(this);

        ComponentList settingsPane = new ComponentList();
        {
            {
                StackPane sponsorPane = new StackPane();
                sponsorPane.setCursor(Cursor.HAND);
                FXUtils.onClicked(sponsorPane, this::onSponsor);
                sponsorPane.setPadding(new Insets(8, 0, 8, 0));

                GridPane gridPane = new GridPane();

                ColumnConstraints col = new ColumnConstraints();
                col.setHgrow(Priority.SOMETIMES);
                col.setMaxWidth(Double.POSITIVE_INFINITY);

                gridPane.getColumnConstraints().setAll(col);

                RowConstraints row = new RowConstraints();
                row.setMinHeight(Double.NEGATIVE_INFINITY);
                row.setValignment(VPos.TOP);
                row.setVgrow(Priority.SOMETIMES);
                gridPane.getRowConstraints().setAll(row);

                {
                    Label label = new Label(i18n("sponsor.hmcl"));
                    label.setWrapText(true);
                    label.setTextAlignment(TextAlignment.JUSTIFY);
                    GridPane.setRowIndex(label, 0);
                    GridPane.setColumnIndex(label, 0);
                    gridPane.getChildren().add(label);
                }

                sponsorPane.getChildren().setAll(gridPane);
                settingsPane.getContent().add(sponsorPane);
            }

            {
                ComponentSublist updatePane = new ComponentSublist();
                updatePane.setTitle(i18n("update"));
                updatePane.setHasSubtitle(true);

                final Label lblUpdate;
                final Label lblUpdateSub;
                {
                    VBox headerLeft = new VBox();

                    lblUpdate = new Label(i18n("update"));
                    lblUpdate.getStyleClass().add("title-label");
                    lblUpdateSub = new Label();
                    lblUpdateSub.getStyleClass().add("subtitle-label");

                    headerLeft.getChildren().setAll(lblUpdate, lblUpdateSub);
                    updatePane.setHeaderLeft(headerLeft);
                }

                {
                    JFXButton btnUpdate = new JFXButton();
                    btnUpdate.setOnAction(e -> onUpdate());
                    btnUpdate.getStyleClass().add("toggle-icon4");
                    btnUpdate.setGraphic(SVG.UPDATE.createIcon(20));
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
                            lblUpdate.getStyleClass().setAll("title-label");
                        } else {
                            lblUpdateSub.setText(i18n("update.latest"));
                            lblUpdateSub.getStyleClass().setAll("subtitle-label");

                            lblUpdate.setText(i18n("update"));
                            lblUpdate.getStyleClass().setAll("title-label");
                        }
                    };
                    UpdateChecker.latestVersionProperty().addListener(new WeakInvalidationListener(updateListener));
                    UpdateChecker.outdatedProperty().addListener(new WeakInvalidationListener(updateListener));
                    UpdateChecker.checkingUpdateProperty().addListener(new WeakInvalidationListener(updateListener));
                    updateListener.invalidated(null);

                    updatePane.setHeaderRight(btnUpdate);
                }

                {
                    VBox content = new VBox(12);
                    content.setPadding(new Insets(8, 0, 0, 0));

                    updateChannelGroup = new ToggleGroup();

                    JFXRadioButton chkUpdateStable = new JFXRadioButton(i18n("update.channel.stable"));
                    chkUpdateStable.setUserData(UpdateChannel.STABLE);
                    chkUpdateStable.setToggleGroup(updateChannelGroup);

                    JFXRadioButton chkUpdateDev = new JFXRadioButton(i18n("update.channel.dev"));
                    chkUpdateDev.setUserData(UpdateChannel.DEVELOPMENT);
                    chkUpdateDev.setToggleGroup(updateChannelGroup);

                    Label noteWrapper = new Label(i18n("update.note"));
                    VBox.setMargin(noteWrapper, new Insets(8, 0, 0, 0));

                    content.getChildren().setAll(chkUpdateStable, chkUpdateDev, noteWrapper);

                    updatePane.getContent().add(content);
                }
                settingsPane.getContent().add(updatePane);
            }

            {
                LineToggleButton previewPane = new LineToggleButton();
                previewPane.setTitle(i18n("update.preview"));
                previewPane.setSubtitle(i18n("update.preview.subtitle"));
                previewPane.selectedProperty().bindBidirectional(config().acceptPreviewUpdateProperty());

                ObjectProperty<UpdateChannel> updateChannel = selectedItemPropertyFor(updateChannelGroup, UpdateChannel.class);
                updateChannel.set(UpdateChannel.getChannel());
                InvalidationListener checkUpdateListener = e -> {
                    UpdateChecker.requestCheckUpdate(updateChannel.get(), previewPane.isSelected());
                };
                updateChannel.addListener(checkUpdateListener);
                previewPane.selectedProperty().addListener(checkUpdateListener);

                settingsPane.getContent().add(previewPane);
            }

            {
                MultiFileItem<EnumCommonDirectory> fileCommonLocation = new MultiFileItem<>();
                fileCommonLocation.loadChildren(Arrays.asList(
                        new MultiFileItem.Option<>(i18n("launcher.cache_directory.default"), EnumCommonDirectory.DEFAULT),
                        new MultiFileItem.FileOption<>(i18n("settings.custom"), EnumCommonDirectory.CUSTOM)
                                .setChooserTitle(i18n("launcher.cache_directory.choose"))
                                .setDirectory(true)
                                .bindBidirectional(config().commonDirectoryProperty())
                ));
                fileCommonLocation.selectedDataProperty().bindBidirectional(config().commonDirTypeProperty());

                ComponentSublist fileCommonLocationSublist = new ComponentSublist();
                fileCommonLocationSublist.getContent().add(fileCommonLocation);
                fileCommonLocationSublist.setTitle(i18n("launcher.cache_directory"));
                fileCommonLocationSublist.setHasSubtitle(true);
                fileCommonLocationSublist.subtitleProperty().bind(
                        Bindings.createObjectBinding(() -> Optional.ofNullable(Settings.instance().getCommonDirectory())
                                        .orElse(i18n("launcher.cache_directory.disabled")),
                                config().commonDirectoryProperty(), config().commonDirTypeProperty()));

                JFXButton cleanButton = FXUtils.newBorderButton(i18n("launcher.cache_directory.clean"));
                cleanButton.setOnAction(e -> clearCacheDirectory());
                fileCommonLocationSublist.setHeaderRight(cleanButton);

                settingsPane.getContent().add(fileCommonLocationSublist);
            }

            {
                var chooseLanguagePane = new LineSelectButton<SupportedLocale>();
                chooseLanguagePane.setTitle(i18n("settings.launcher.language"));
                chooseLanguagePane.setSubtitle(i18n("settings.take_effect_after_restart"));

                SupportedLocale currentLocale = I18n.getLocale();
                chooseLanguagePane.setConverter(locale -> {
                    if (locale.isDefault())
                        return locale.getDisplayName(currentLocale);
                    else if (locale.isSameLanguage(currentLocale))
                        return locale.getDisplayName(locale);
                    else
                        return locale.getDisplayName(currentLocale) + " - " + locale.getDisplayName(locale);
                });
                chooseLanguagePane.setItems(SupportedLocale.getSupportedLocales());
                chooseLanguagePane.valueProperty().bindBidirectional(config().localizationProperty());

                settingsPane.getContent().add(chooseLanguagePane);
            }

            {
                LineToggleButton disableAutoGameOptionsPane = new LineToggleButton();
                disableAutoGameOptionsPane.setTitle(i18n("settings.launcher.disable_auto_game_options"));
                disableAutoGameOptionsPane.selectedProperty().bindBidirectional(config().disableAutoGameOptionsProperty());

                settingsPane.getContent().add(disableAutoGameOptionsPane);
            }

            {
                BorderPane debugPane = new BorderPane();

                Label left = new Label(i18n("settings.launcher.debug"));
                BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                debugPane.setLeft(left);

                JFXButton openLogFolderButton = new JFXButton(i18n("settings.launcher.launcher_log.reveal"));
                openLogFolderButton.setOnAction(e -> openLogFolder());
                openLogFolderButton.getStyleClass().add("jfx-button-border");
                if (LOG.getLogFile() == null)
                    openLogFolderButton.setDisable(true);

                JFXButton logButton = FXUtils.newBorderButton(i18n("settings.launcher.launcher_log.export"));
                logButton.setOnAction(e -> onExportLogs());

                HBox buttonBox = new HBox();
                buttonBox.setSpacing(10);
                buttonBox.getChildren().addAll(openLogFolderButton, logButton);
                BorderPane.setAlignment(buttonBox, Pos.CENTER_RIGHT);
                debugPane.setRight(buttonBox);

                settingsPane.getContent().add(debugPane);
            }

            rootPane.getChildren().add(settingsPane);
        }
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

    private void onExportLogs() {
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
            } catch (IOException e) {
                LOG.warning("Failed to export logs", e);
                Platform.runLater(() -> Controllers.dialog(i18n("settings.launcher.launcher_log.export.failed") + "\n" + StringUtils.getStackTrace(e), null, MessageType.ERROR));
                return;
            }

            Platform.runLater(() -> Controllers.dialog(i18n("settings.launcher.launcher_log.export.success", outputFile)));
            FXUtils.showFileInExplorer(outputFile);
        });
    }

    private void onSponsor() {
        FXUtils.openLink("https://github.com/HMCL-dev/HMCL");
    }

    private void clearCacheDirectory() {
        String commonDirectory = Settings.instance().getCommonDirectory();
        if (commonDirectory != null) {
            FileUtils.cleanDirectoryQuietly(Path.of(commonDirectory, "cache"));
        }
    }
}
