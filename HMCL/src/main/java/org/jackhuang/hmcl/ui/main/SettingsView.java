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

import com.jfoenix.controls.*;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.setting.EnumCommonDirectory;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.i18n.Locales.SupportedLocale;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class SettingsView extends StackPane {
    protected final JFXTextField txtProxyHost;
    protected final JFXTextField txtProxyPort;
    protected final JFXTextField txtProxyUsername;
    protected final JFXPasswordField txtProxyPassword;
    protected final JFXTextField txtFontSize;
    protected final JFXComboBox<SupportedLocale> cboLanguage;
    protected final JFXComboBox<String> cboDownloadSource;
    protected final FontComboBox cboFont;
    protected final MultiFileItem<EnumCommonDirectory> fileCommonLocation;
    protected final Label lblDisplay;
    protected final Label lblUpdate;
    protected final Label lblUpdateSub;
    protected final Text lblUpdateNote;
    protected final JFXRadioButton chkUpdateStable;
    protected final JFXRadioButton chkUpdateDev;
    protected final JFXButton btnUpdate;
    protected final ScrollPane scroll;
    protected final MultiFileItem<EnumBackgroundImage> backgroundItem;
    protected final StackPane themeColorPickerContainer;
    protected final JFXCheckBox chkDisableProxy;
    protected final JFXRadioButton chkProxyHttp;
    protected final JFXRadioButton chkProxySocks;
    protected final JFXCheckBox chkProxyAuthentication;
    protected final GridPane authPane;
    protected final Pane proxyPane;

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
                        sponsorPane.setOnMouseClicked(e -> onSponsor());

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
                        btnUpdate.setOnMouseClicked(e -> onUpdate());
                        btnUpdate.getStyleClass().add("toggle-icon4");
                        btnUpdate.setGraphic(SVG.update(Theme.blackFillBinding(), 20, 20));

                        updatePane.setHeaderRight(btnUpdate);
                    }

                    {
                        VBox content = new VBox();
                        content.setSpacing(8);

                        chkUpdateStable = new JFXRadioButton(i18n("update.channel.stable"));
                        chkUpdateDev = new JFXRadioButton(i18n("update.channel.dev"));

                        VBox noteWrapper = new VBox();
                        noteWrapper.setStyle("-fx-padding: 10 0 0 0;");
                        lblUpdateNote = new Text(i18n("update.note"));
                        noteWrapper.getChildren().setAll(lblUpdateNote);

                        content.getChildren().setAll(chkUpdateStable, chkUpdateDev, noteWrapper);

                        updatePane.getContent().add(content);
                    }
                    settingsPane.getContent().add(updatePane);
                }

                {
                    BorderPane docPane = new BorderPane();
                    {
                        VBox headerLeft = new VBox();

                        Label help = new Label(i18n("help"));
                        Label helpSubtitle = new Label(i18n("help.detail"));
                        helpSubtitle.getStyleClass().add("subtitle-label");

                        headerLeft.getChildren().setAll(help, helpSubtitle);
                        docPane.setLeft(headerLeft);
                    }

                    {
                        JFXButton btnExternal = new JFXButton();
                        btnExternal.setOnMouseClicked(e -> onHelp());
                        btnExternal.getStyleClass().add("toggle-icon4");
                        btnExternal.setGraphic(SVG.openInNew(Theme.blackFillBinding(), -1, -1));

                        docPane.setRight(btnExternal);
                    }
                    settingsPane.getContent().add(docPane);
                }

                {
                    fileCommonLocation = new MultiFileItem<>(true);
                    fileCommonLocation.setTitle(i18n("launcher.cache_directory"));
                    fileCommonLocation.setDirectory(true);
                    fileCommonLocation.setChooserTitle(i18n("launcher.cache_directory.choose"));
                    fileCommonLocation.setHasSubtitle(true);
                    fileCommonLocation.setCustomText("settings.custom");

                    {
                        JFXButton cleanButton = new JFXButton(i18n("launcher.cache_directory.clean"));
                        cleanButton.setOnMouseClicked(e -> clearCacheDirectory());
                        cleanButton.getStyleClass().add("jfx-button-border");

                        fileCommonLocation.setHeaderRight(cleanButton);
                    }

                    settingsPane.getContent().add(fileCommonLocation);
                }

                {
                    backgroundItem = new MultiFileItem<>(true);
                    backgroundItem.setTitle(i18n("launcher.background"));
                    backgroundItem.setChooserTitle(i18n("launcher.background.choose"));
                    backgroundItem.setHasSubtitle(true);
                    backgroundItem.setCustomText(i18n("settings.custom"));

                    settingsPane.getContent().add(backgroundItem);
                }

                {
                    BorderPane downloadSourcePane = new BorderPane();
                    {
                        Label label = new Label(i18n("settings.launcher.download_source"));
                        BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                        downloadSourcePane.setLeft(label);
                    }

                    {
                        cboDownloadSource = new JFXComboBox<>();
                        cboDownloadSource.setConverter(stringConverter(key -> i18n("download.provider." + key)));
                        downloadSourcePane.setRight(cboDownloadSource);
                    }
                    settingsPane.getContent().add(downloadSourcePane);
                }

                {
                    BorderPane languagePane = new BorderPane();

                    Label left = new Label(i18n("settings.launcher.language"));
                    BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                    languagePane.setLeft(left);

                    cboLanguage = new JFXComboBox<>();
                    cboLanguage.setConverter(stringConverter(locale -> locale.getName(config().getLocalization().getResourceBundle())));
                    FXUtils.setLimitWidth(cboLanguage, 400);
                    languagePane.setRight(cboLanguage);

                    settingsPane.getContent().add(languagePane);
                }

                {
                    ComponentList proxyList = new ComponentList();
                    proxyList.setTitle(i18n("settings.launcher.proxy"));

                    VBox proxyWrapper = new VBox();
                    proxyWrapper.setSpacing(10);

                    {
                        chkDisableProxy = new JFXCheckBox(i18n("settings.launcher.proxy.disable"));
                        proxyWrapper.getChildren().add(chkDisableProxy);
                    }

                    {
                        proxyPane = new VBox();
                        proxyPane.setStyle("-fx-padding: 0 0 0 30;");

                        ColumnConstraints colHgrow = new ColumnConstraints();
                        colHgrow.setHgrow(Priority.ALWAYS);

                        {
                            HBox hBox = new HBox();
                            chkProxyHttp = new JFXRadioButton(i18n("settings.launcher.proxy.http"));
                            chkProxySocks = new JFXRadioButton(i18n("settings.launcher.proxy.socks"));
                            hBox.getChildren().setAll(chkProxyHttp, chkProxySocks);
                            proxyPane.getChildren().add(hBox);
                        }

                        {
                            GridPane gridPane = new GridPane();
                            gridPane.setHgap(20);
                            gridPane.setVgap(10);
                            gridPane.setStyle("-fx-padding: 0 0 0 15;");
                            gridPane.getColumnConstraints().setAll(new ColumnConstraints(), colHgrow);
                            gridPane.getRowConstraints().setAll(new RowConstraints(), new RowConstraints());

                            {
                                Label host = new Label(i18n("settings.launcher.proxy.host"));
                                GridPane.setRowIndex(host, 1);
                                GridPane.setColumnIndex(host, 0);
                                GridPane.setHalignment(host, HPos.RIGHT);
                                gridPane.getChildren().add(host);
                            }

                            {
                                txtProxyHost = new JFXTextField();
                                GridPane.setRowIndex(txtProxyHost, 1);
                                GridPane.setColumnIndex(txtProxyHost, 1);
                                gridPane.getChildren().add(txtProxyHost);
                            }

                            {
                                Label port = new Label(i18n("settings.launcher.proxy.port"));
                                GridPane.setRowIndex(port, 2);
                                GridPane.setColumnIndex(port, 0);
                                GridPane.setHalignment(port, HPos.RIGHT);
                                gridPane.getChildren().add(port);
                            }

                            {
                                txtProxyPort = new JFXTextField();
                                GridPane.setRowIndex(txtProxyPort, 2);
                                GridPane.setColumnIndex(txtProxyPort, 1);
                                FXUtils.setValidateWhileTextChanged(txtProxyPort, true);
                                txtProxyHost.getValidators().setAll(new NumberValidator(i18n("input.number"), false));
                                gridPane.getChildren().add(txtProxyPort);
                            }
                            proxyPane.getChildren().add(gridPane);
                        }

                        {
                            VBox vBox = new VBox();
                            vBox.setStyle("-fx-padding: 20 0 20 5;");

                            chkProxyAuthentication = new JFXCheckBox(i18n("settings.launcher.proxy.authentication"));
                            vBox.getChildren().setAll(chkProxyAuthentication);

                            proxyPane.getChildren().add(vBox);
                        }

                        {
                            authPane = new GridPane();
                            authPane.setHgap(20);
                            authPane.setVgap(10);
                            authPane.setStyle("-fx-padding: 0 0 0 15;");
                            authPane.getColumnConstraints().setAll(new ColumnConstraints(), colHgrow);
                            authPane.getRowConstraints().setAll(new RowConstraints(), new RowConstraints());

                            {
                                Label username = new Label(i18n("settings.launcher.proxy.username"));
                                GridPane.setRowIndex(username, 0);
                                GridPane.setColumnIndex(username, 0);
                                authPane.getChildren().add(username);
                            }

                            {
                                txtProxyUsername = new JFXTextField();
                                GridPane.setRowIndex(txtProxyUsername, 0);
                                GridPane.setColumnIndex(txtProxyUsername, 1);
                                authPane.getChildren().add(txtProxyUsername);
                            }

                            {
                                Label password = new Label(i18n("settings.launcher.proxy.password"));
                                GridPane.setRowIndex(password, 1);
                                GridPane.setColumnIndex(password, 0);
                                authPane.getChildren().add(password);
                            }

                            {
                                txtProxyPassword = new JFXPasswordField();
                                GridPane.setRowIndex(txtProxyPassword, 1);
                                GridPane.setColumnIndex(txtProxyPassword, 1);
                                authPane.getChildren().add(txtProxyPassword);
                            }

                            proxyPane.getChildren().add(authPane);
                        }
                        proxyWrapper.getChildren().add(proxyPane);
                    }
                    proxyList.getContent().add(proxyWrapper);
                    settingsPane.getContent().add(proxyList);
                }

                {
                    BorderPane themePane = new BorderPane();

                    Label left = new Label(i18n("settings.launcher.theme"));
                    BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                    themePane.setLeft(left);

                    themeColorPickerContainer = new StackPane();
                    themeColorPickerContainer.setMinHeight(30);
                    themePane.setRight(themeColorPickerContainer);

                    settingsPane.getContent().add(themePane);
                }

                {
                    ComponentSublist logPane = new ComponentSublist();
                    logPane.setTitle(i18n("settings.launcher.log"));

                    {
                        JFXButton logButton = new JFXButton(i18n("settings.launcher.launcher_log.export"));
                        logButton.setOnMouseClicked(e -> onExportLogs());
                        logButton.getStyleClass().add("jfx-button-border");

                        logPane.setHeaderRight(logButton);
                    }

                    {
                        VBox fontPane = new VBox();
                        fontPane.setSpacing(5);

                        {
                            BorderPane borderPane = new BorderPane();
                            fontPane.getChildren().add(borderPane);
                            {
                                Label left = new Label(i18n("settings.launcher.log.font"));
                                BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                                borderPane.setLeft(left);
                            }

                            {
                                HBox hBox = new HBox();
                                hBox.setSpacing(3);

                                cboFont = new FontComboBox(12);
                                txtFontSize = new JFXTextField();
                                FXUtils.setLimitWidth(txtFontSize, 50);
                                hBox.getChildren().setAll(cboFont, txtFontSize);

                                borderPane.setRight(hBox);
                            }
                        }

                        lblDisplay = new Label("[23:33:33] [Client Thread/INFO] [WaterPower]: Loaded mod WaterPower.");
                        fontPane.getChildren().add(lblDisplay);

                        logPane.getContent().add(fontPane);
                    }
                    settingsPane.getContent().add(logPane);
                }

                {
                    StackPane aboutPane = new StackPane();
                    GridPane gridPane = new GridPane();
                    gridPane.setHgap(20);
                    gridPane.setVgap(10);

                    ColumnConstraints col1 = new ColumnConstraints();
                    col1.setHgrow(Priority.SOMETIMES);
                    col1.setMaxWidth(Double.NEGATIVE_INFINITY);
                    col1.setMinWidth(Double.NEGATIVE_INFINITY);

                    ColumnConstraints col2 = new ColumnConstraints();
                    col2.setHgrow(Priority.SOMETIMES);
                    col2.setMinWidth(20);
                    col2.setMaxWidth(Double.POSITIVE_INFINITY);

                    gridPane.getColumnConstraints().setAll(col1, col2);

                    RowConstraints row = new RowConstraints();
                    row.setMinHeight(Double.NEGATIVE_INFINITY);
                    row.setValignment(VPos.TOP);
                    row.setVgrow(Priority.SOMETIMES);
                    gridPane.getRowConstraints().setAll(row, row, row, row, row, row);

                    {
                        Label label = new Label(i18n("about.copyright"));
                        GridPane.setRowIndex(label, 0);
                        GridPane.setColumnIndex(label, 0);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.copyright.statement"));
                        label.setWrapText(true);
                        GridPane.setRowIndex(label, 0);
                        GridPane.setColumnIndex(label, 1);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.author"));
                        GridPane.setRowIndex(label, 1);
                        GridPane.setColumnIndex(label, 0);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.author.statement"));
                        label.setWrapText(true);
                        GridPane.setRowIndex(label, 1);
                        GridPane.setColumnIndex(label, 1);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.thanks_to"));
                        GridPane.setRowIndex(label, 2);
                        GridPane.setColumnIndex(label, 0);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.thanks_to.statement"));
                        label.setWrapText(true);
                        GridPane.setRowIndex(label, 2);
                        GridPane.setColumnIndex(label, 1);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.dependency"));
                        GridPane.setRowIndex(label, 3);
                        GridPane.setColumnIndex(label, 0);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.dependency.statement"));
                        label.setWrapText(true);
                        GridPane.setRowIndex(label, 3);
                        GridPane.setColumnIndex(label, 1);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.claim"));
                        GridPane.setRowIndex(label, 4);
                        GridPane.setColumnIndex(label, 0);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.claim.statement"));
                        label.setWrapText(true);
                        label.setTextAlignment(TextAlignment.JUSTIFY);
                        GridPane.setRowIndex(label, 4);
                        GridPane.setColumnIndex(label, 1);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.open_source"));
                        GridPane.setRowIndex(label, 5);
                        GridPane.setColumnIndex(label, 0);
                        gridPane.getChildren().add(label);
                    }
                    {
                        Label label = new Label(i18n("about.open_source.statement"));
                        label.setWrapText(true);
                        GridPane.setRowIndex(label, 5);
                        GridPane.setColumnIndex(label, 1);
                        gridPane.getChildren().add(label);
                    }
                    aboutPane.getChildren().setAll(gridPane);
                    settingsPane.getContent().add(aboutPane);
                }
                rootPane.getChildren().add(settingsPane);
            }
            scroll.setContent(rootPane);
        }
    }

    protected abstract void onUpdate();
    protected abstract void onHelp();
    protected abstract void onExportLogs();
    protected abstract void onSponsor();
    protected abstract void clearCacheDirectory();
}
