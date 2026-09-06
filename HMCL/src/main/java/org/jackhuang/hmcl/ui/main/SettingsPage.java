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
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChannel;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.LauncherLogExporter;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class SettingsPage extends ScrollPane {
    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationListener updateListener;

    public SettingsPage() {
        this.setFitToWidth(true);

        VBox rootPane = new VBox(10);
        rootPane.setPadding(new Insets(10));
        this.setContent(rootPane);
        FXUtils.smoothScrolling(this);

        {
            ComponentList updatePaneList = new ComponentList();
            {
                ObjectProperty<UpdateChannel> updateChannel;
                {

                    JFXButton updateButton = FXUtils.newToggleButton4(SVG.UPDATE, 20);
                    updateButton.setOnAction(e -> onUpdate());
                    updateButton.setPadding(Insets.EMPTY);
                    FXUtils.installFastTooltip(updateButton, i18n("update.tooltip"));

                    var updatePane = new LineSelectButton<UpdateChannel>() {

                        {
                            getStyleClass().add("update-pane");
                            setNode(IDX_TRAILING, updateButton);
                        }

                        @Override
                        protected int getTrailingTextIndex() {
                            return LineComponent.IDX_TRAILING + 1;
                        }
                    };
                    updateChannel = updatePane.valueProperty();
                    updatePane.setTitle(i18n("update"));
                    updatePane.setValue(UpdateChannel.getChannel());

                    updatePane.setNullSafeConverter(channel -> i18n("update.channel." + channel.channelName));
                    updatePane.setItems(List.of(UpdateChannel.STABLE, UpdateChannel.DEVELOPMENT));
                    updatePane.setDescriptionConverter(channel -> i18n("update.note." + channel.channelName));

                    final StringProperty lblUpdateSubProperty = updatePane.subtitleProperty();

                    {
                        updateListener = any -> {
                            boolean outdated = UpdateChecker.isOutdated();

                            updateButton.setVisible(outdated);
                            updateButton.setManaged(outdated);
                            updatePane.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), outdated);

                            if (UpdateChecker.isOutdated()) {
                                lblUpdateSubProperty.set(i18n("update.newest_version", UpdateChecker.getLatestVersion().version()));
                            } else if (UpdateChecker.isCheckingUpdate()) {
                                lblUpdateSubProperty.set(i18n("update.checking"));
                            } else {
                                lblUpdateSubProperty.set(i18n("update.latest"));
                            }
                        };
                        UpdateChecker.latestVersionProperty().addListener(new WeakInvalidationListener(updateListener));
                        UpdateChecker.outdatedProperty().addListener(new WeakInvalidationListener(updateListener));
                        UpdateChecker.checkingUpdateProperty().addListener(new WeakInvalidationListener(updateListener));
                        updateListener.invalidated(null);
                    }

                    updatePaneList.getContent().add(updatePane);
                }

                {
                    LineToggleButton previewPane = new LineToggleButton();
                    previewPane.setTitle(i18n("update.preview"));
                    previewPane.setSubtitle(i18n("update.preview.subtitle"));
                    previewPane.selectedProperty().bindBidirectional(settings().acceptPreviewUpdateProperty());

                    InvalidationListener checkUpdateListener = e -> {
                        UpdateChecker.requestCheckUpdate(updateChannel.get(), previewPane.isSelected());
                    };
                    updateChannel.addListener(checkUpdateListener);
                    previewPane.selectedProperty().addListener(checkUpdateListener);

                    updatePaneList.getContent().add(previewPane);
                }

                {
                    LineToggleButton disableAutoShowUpdateDialogPane = new LineToggleButton();
                    disableAutoShowUpdateDialogPane.setTitle(i18n("update.disable_auto_show_update_dialog"));
                    disableAutoShowUpdateDialogPane.setSubtitle(i18n("update.disable_auto_show_update_dialog.subtitle"));
                    disableAutoShowUpdateDialogPane.selectedProperty().bindBidirectional(settings().disableAutoShowUpdateDialogProperty());
                    updatePaneList.getContent().add(disableAutoShowUpdateDialogPane);
                }

                rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("update")), updatePaneList);
            }

            {
                ComponentList languagePaneList = new ComponentList();

                {
                    var chooseLanguagePane = new LineSelectButton<SupportedLocale>();
                    chooseLanguagePane.setTitle(i18n("settings.launcher.language"));
                    chooseLanguagePane.setSubtitle(i18n("settings.take_effect_after_restart"));

                    SupportedLocale currentLocale = I18n.getLocale();
                    chooseLanguagePane.setNullSafeConverter(locale -> {
                        if (locale.isDefault())
                            return locale.getDisplayName(currentLocale);
                        else if (locale.isSameLanguage(currentLocale))
                            return locale.getDisplayName(locale);
                        else
                            return locale.getDisplayName(currentLocale) + " - " + locale.getDisplayName(locale);
                    });
                    chooseLanguagePane.setItems(SupportedLocale.getSupportedLocales());
                    chooseLanguagePane.valueProperty().bindBidirectional(settings().languageProperty());

                    languagePaneList.getContent().add(chooseLanguagePane);

                }

                rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.language")), languagePaneList);
            }

            {
                ComponentList miscPaneList = new ComponentList();

                {
                    LineToggleButton disableAprilFools = new LineToggleButton();
                    disableAprilFools.setTitle(i18n("settings.launcher.disable_april_fools"));
                    disableAprilFools.setSubtitle(i18n("settings.take_effect_after_restart"));
                    disableAprilFools.selectedProperty().bindBidirectional(settings().disableAprilFoolsProperty());
                    miscPaneList.getContent().add(disableAprilFools);
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

                    SpinnerPane exportLogPane = new SpinnerPane();

                    JFXButton logButton = FXUtils.newBorderButton(i18n("settings.launcher.launcher_log.export"));
                    exportLogPane.setContent(logButton);
                    logButton.setOnAction(e -> {
                        exportLogPane.showSpinner();
                        CompletableFuture.supplyAsync(Lang.wrap(LauncherLogExporter::exportLogsAsZip), Schedulers.io()).whenCompleteAsync((result, exception) -> {
                            exportLogPane.hideSpinner();
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

                    HBox buttonBox = new HBox();
                    buttonBox.setSpacing(10);
                    buttonBox.getChildren().addAll(openLogFolderButton, exportLogPane);
                    BorderPane.setAlignment(buttonBox, Pos.CENTER_RIGHT);
                    debugPane.setRight(buttonBox);

                    miscPaneList.getContent().add(debugPane);
                }

                rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.misc")), miscPaneList);
            }
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
}
