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
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXRadioButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.setting.EnumCommonDirectory;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.ComponentSublist;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;

import java.util.Arrays;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class SettingsView extends StackPane {
    protected final JFXComboBox<SupportedLocale> cboLanguage;
    protected final OptionToggleButton disableAutoGameOptionsPane;
    protected final MultiFileItem<EnumCommonDirectory> fileCommonLocation;
    protected final ComponentSublist fileCommonLocationSublist;
    protected final Label lblUpdate;
    protected final Label lblUpdateSub;
    protected final JFXRadioButton chkUpdateStable;
    protected final JFXRadioButton chkUpdateDev;
    protected final JFXButton btnUpdate;
    protected final OptionToggleButton previewPane;
    protected final ScrollPane scroll;

    public SettingsView() {
        scroll = new ScrollPane();
        getChildren().setAll(scroll);
        scroll.setStyle("-fx-font-size: 14;");
        scroll.setFitToWidth(true);

        {
            VBox rootPane = new VBox();
            rootPane.setPadding(new Insets(32, 10, 32, 10));
            {
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
                }

                {
                    ComponentSublist updatePane = new ComponentSublist();
                    updatePane.setTitle(i18n("update"));
                    updatePane.setHasSubtitle(true);
                    {
                        VBox headerLeft = new VBox();

                        lblUpdate = new Label(i18n("update"));
                        lblUpdateSub = new Label();
                        lblUpdateSub.getStyleClass().add("subtitle-label");

                        headerLeft.getChildren().setAll(lblUpdate, lblUpdateSub);
                        updatePane.setHeaderLeft(headerLeft);
                    }

                    {
                        btnUpdate = new JFXButton();
                        btnUpdate.setOnAction(e -> onUpdate());
                        btnUpdate.getStyleClass().add("toggle-icon4");
                        btnUpdate.setGraphic(SVG.UPDATE.createIcon(Theme.blackFill(), 20));

                        updatePane.setHeaderRight(btnUpdate);
                    }

                    {
                        VBox content = new VBox();
                        content.setSpacing(8);

                        chkUpdateStable = new JFXRadioButton(i18n("update.channel.stable"));
                        chkUpdateDev = new JFXRadioButton(i18n("update.channel.dev"));

                        TextFlow noteWrapper = new TextFlow(new Text(i18n("update.note")));
                        VBox.setMargin(noteWrapper, new Insets(10, 0, 0, 0));

                        content.getChildren().setAll(chkUpdateStable, chkUpdateDev, noteWrapper);

                        updatePane.getContent().add(content);
                    }
                    settingsPane.getContent().add(updatePane);
                }

                {
                    previewPane = new OptionToggleButton();
                    previewPane.setTitle(i18n("update.preview"));
                    previewPane.selectedProperty().bindBidirectional(config().acceptPreviewUpdateProperty());
                    FXUtils.installFastTooltip(previewPane, i18n("update.preview.tooltip"));

                    settingsPane.getContent().add(previewPane);
                }

                {
                    fileCommonLocation = new MultiFileItem<>();
                    fileCommonLocationSublist = new ComponentSublist();
                    fileCommonLocationSublist.getContent().add(fileCommonLocation);
                    fileCommonLocationSublist.setTitle(i18n("launcher.cache_directory"));
                    fileCommonLocationSublist.setHasSubtitle(true);
                    fileCommonLocation.loadChildren(Arrays.asList(
                            new MultiFileItem.Option<>(i18n("launcher.cache_directory.default"), EnumCommonDirectory.DEFAULT),
                            new MultiFileItem.FileOption<>(i18n("settings.custom"), EnumCommonDirectory.CUSTOM)
                                    .setChooserTitle(i18n("launcher.cache_directory.choose"))
                                    .setDirectory(true)
                                    .bindBidirectional(config().commonDirectoryProperty())
                    ));

                    {
                        JFXButton cleanButton = FXUtils.newBorderButton(i18n("launcher.cache_directory.clean"));
                        cleanButton.setOnAction(e -> clearCacheDirectory());

                        fileCommonLocationSublist.setHeaderRight(cleanButton);
                    }

                    settingsPane.getContent().add(fileCommonLocationSublist);
                }

                {
                    BorderPane languagePane = new BorderPane();

                    Label left = new Label(i18n("settings.launcher.language"));
                    BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                    languagePane.setLeft(left);

                    SupportedLocale currentLocale = I18n.getLocale();
                    cboLanguage = new JFXComboBox<>();
                    cboLanguage.setConverter(stringConverter(locale -> {
                        if (locale.isDefault())
                            return locale.getDisplayName(currentLocale);
                        else if (locale.isSameLanguage(currentLocale))
                            return locale.getDisplayName(locale);
                        else
                            return locale.getDisplayName(currentLocale) + " - " + locale.getDisplayName(locale);
                    }));

                    FXUtils.setLimitWidth(cboLanguage, 300);
                    languagePane.setRight(cboLanguage);

                    settingsPane.getContent().add(languagePane);
                }

                {
                    disableAutoGameOptionsPane = new OptionToggleButton();
                    disableAutoGameOptionsPane.setTitle(i18n("settings.launcher.disable_auto_game_options"));

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
            scroll.setContent(rootPane);
        }
    }

    public void openLogFolder() {
        FXUtils.openFolder(LOG.getLogFile().getParent());
    }

    protected abstract void onUpdate();

    protected abstract void onExportLogs();

    protected abstract void onSponsor();

    protected abstract void clearCacheDirectory();
}
