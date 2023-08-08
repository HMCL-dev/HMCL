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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.*;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.net.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.reversedSelectedPropertyFor;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;

public class DownloadSettingsPage extends StackPane {

    public DownloadSettingsPage() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);

        {
            VBox downloadSource = new VBox(8);
            downloadSource.getStyleClass().add("card-non-transparent");
            {

                VBox chooseWrapper = new VBox();
                chooseWrapper.setPadding(new Insets(8, 0, 8, 0));
                JFXCheckBox chkAutoChooseDownloadSource = new JFXCheckBox(i18n("settings.launcher.download_source.auto"));
                chkAutoChooseDownloadSource.selectedProperty().bindBidirectional(config().autoChooseDownloadTypeProperty());
                chooseWrapper.getChildren().setAll(chkAutoChooseDownloadSource);

                BorderPane versionListSourcePane = new BorderPane();
                versionListSourcePane.setPadding(new Insets(0, 0, 8, 30));
                versionListSourcePane.disableProperty().bind(chkAutoChooseDownloadSource.selectedProperty().not());
                {
                    Label label = new Label(i18n("settings.launcher.version_list_source"));
                    BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                    versionListSourcePane.setLeft(label);

                    JFXComboBox<String> cboVersionListSource = new JFXComboBox<>();
                    cboVersionListSource.setConverter(stringConverter(key -> i18n("download.provider." + key)));
                    versionListSourcePane.setRight(cboVersionListSource);
                    FXUtils.setLimitWidth(cboVersionListSource, 400);

                    cboVersionListSource.getItems().setAll(DownloadProviders.providersById.keySet());
                    selectedItemPropertyFor(cboVersionListSource).bindBidirectional(config().versionListSourceProperty());
                }

                BorderPane downloadSourcePane = new BorderPane();
                downloadSourcePane.setPadding(new Insets(0, 0, 8, 30));
                downloadSourcePane.disableProperty().bind(chkAutoChooseDownloadSource.selectedProperty());
                {
                    Label label = new Label(i18n("settings.launcher.download_source"));
                    BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                    downloadSourcePane.setLeft(label);

                    JFXComboBox<String> cboDownloadSource = new JFXComboBox<>();
                    cboDownloadSource.setConverter(stringConverter(key -> i18n("download.provider." + key)));
                    downloadSourcePane.setRight(cboDownloadSource);
                    FXUtils.setLimitWidth(cboDownloadSource, 420);

                    cboDownloadSource.getItems().setAll(DownloadProviders.rawProviders.keySet());
                    selectedItemPropertyFor(cboDownloadSource).bindBidirectional(config().downloadTypeProperty());
                }

                downloadSource.getChildren().setAll(chooseWrapper, versionListSourcePane, downloadSourcePane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.version_list_source")), downloadSource);
        }

        {
            VBox downloadThreads = new VBox(16);
            downloadThreads.getStyleClass().add("card-non-transparent");
            {
                {
                    JFXCheckBox chkAutoDownloadThreads = new JFXCheckBox(i18n("settings.launcher.download.threads.auto"));
                    VBox.setMargin(chkAutoDownloadThreads, new Insets(8, 0, 0, 0));
                    chkAutoDownloadThreads.selectedProperty().bindBidirectional(config().autoDownloadThreadsProperty());
                    downloadThreads.getChildren().add(chkAutoDownloadThreads);

                    chkAutoDownloadThreads.selectedProperty().addListener((a, b, newValue) -> {
                        if (newValue) {
                            config().downloadThreadsProperty().set(FetchTask.DEFAULT_CONCURRENCY);
                        }
                    });
                }

                {
                    HBox hbox = new HBox(8);
                    hbox.setAlignment(Pos.CENTER);
                    hbox.setPadding(new Insets(0, 0, 0, 30));
                    hbox.disableProperty().bind(config().autoDownloadThreadsProperty());
                    Label label = new Label(i18n("settings.launcher.download.threads"));

                    JFXSlider slider = new JFXSlider(1, 256, 64);
                    HBox.setHgrow(slider, Priority.ALWAYS);

                    JFXTextField threadsField = new JFXTextField();
                    FXUtils.setLimitWidth(threadsField, 60);
                    FXUtils.bindInt(threadsField, config().downloadThreadsProperty());

                    AtomicBoolean changedByTextField = new AtomicBoolean(false);
                    FXUtils.onChangeAndOperate(config().downloadThreadsProperty(), value -> {
                        changedByTextField.set(true);
                        slider.setValue(value.intValue());
                        changedByTextField.set(false);
                    });
                    slider.valueProperty().addListener((value, oldVal, newVal) -> {
                        if (changedByTextField.get()) return;
                        config().downloadThreadsProperty().set(value.getValue().intValue());
                    });

                    hbox.getChildren().setAll(label, slider, threadsField);
                    downloadThreads.getChildren().add(hbox);
                }

                {
                    HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
                    VBox.setMargin(hintPane, new Insets(0, 0, 0, 30));
                    hintPane.disableProperty().bind(config().autoDownloadThreadsProperty());
                    hintPane.setText(i18n("settings.launcher.download.threads.hint"));
                    downloadThreads.getChildren().add(hintPane);
                }
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("download")), downloadThreads);
        }

        {
            VBox proxyList = new VBox(10);
            proxyList.getStyleClass().add("card-non-transparent");

            VBox proxyPane = new VBox();
            {
                JFXCheckBox chkDisableProxy = new JFXCheckBox(i18n("settings.launcher.proxy.disable"));
                VBox.setMargin(chkDisableProxy, new Insets(8, 0, 0, 0));
                proxyList.getChildren().add(chkDisableProxy);
                reversedSelectedPropertyFor(chkDisableProxy).bindBidirectional(config().hasProxyProperty());
                proxyPane.disableProperty().bind(chkDisableProxy.selectedProperty());
            }

            {
                proxyPane.setPadding(new Insets(0, 0, 0, 30));

                ColumnConstraints colHgrow = new ColumnConstraints();
                colHgrow.setHgrow(Priority.ALWAYS);

                JFXRadioButton chkProxyNone;
                JFXRadioButton chkProxyHttp;
                JFXRadioButton chkProxySocks;
                {
                    HBox hBox = new HBox();
                    chkProxyNone = new JFXRadioButton(i18n("settings.launcher.proxy.none"));
                    chkProxyHttp = new JFXRadioButton(i18n("settings.launcher.proxy.http"));
                    chkProxySocks = new JFXRadioButton(i18n("settings.launcher.proxy.socks"));
                    hBox.getChildren().setAll(chkProxyNone, chkProxyHttp, chkProxySocks);
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
                        JFXTextField txtProxyHost = new JFXTextField();
                        GridPane.setRowIndex(txtProxyHost, 1);
                        GridPane.setColumnIndex(txtProxyHost, 1);
                        gridPane.getChildren().add(txtProxyHost);
                        FXUtils.bindString(txtProxyHost, config().proxyHostProperty());
                    }

                    {
                        Label port = new Label(i18n("settings.launcher.proxy.port"));
                        GridPane.setRowIndex(port, 2);
                        GridPane.setColumnIndex(port, 0);
                        GridPane.setHalignment(port, HPos.RIGHT);
                        gridPane.getChildren().add(port);
                    }

                    {
                        JFXTextField txtProxyPort = new JFXTextField();
                        GridPane.setRowIndex(txtProxyPort, 2);
                        GridPane.setColumnIndex(txtProxyPort, 1);
                        FXUtils.setValidateWhileTextChanged(txtProxyPort, true);
                        gridPane.getChildren().add(txtProxyPort);

                        FXUtils.bind(txtProxyPort, config().proxyPortProperty(), SafeStringConverter.fromInteger()
                                .restrict(it -> it >= 0 && it <= 0xFFFF)
                                .fallbackTo(0)
                                .asPredicate(Validator.addTo(txtProxyPort)));
                    }
                    proxyPane.getChildren().add(gridPane);
                }

                GridPane authPane = new GridPane();
                {
                    VBox vBox = new VBox();
                    vBox.setStyle("-fx-padding: 20 0 20 5;");

                    JFXCheckBox chkProxyAuthentication = new JFXCheckBox(i18n("settings.launcher.proxy.authentication"));
                    vBox.getChildren().setAll(chkProxyAuthentication);
                    authPane.disableProperty().bind(chkProxyAuthentication.selectedProperty().not());
                    chkProxyAuthentication.selectedProperty().bindBidirectional(config().hasProxyAuthProperty());

                    proxyPane.getChildren().add(vBox);
                }

                {
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
                        JFXTextField txtProxyUsername = new JFXTextField();
                        GridPane.setRowIndex(txtProxyUsername, 0);
                        GridPane.setColumnIndex(txtProxyUsername, 1);
                        authPane.getChildren().add(txtProxyUsername);
                        FXUtils.bindString(txtProxyUsername, config().proxyUserProperty());
                    }

                    {
                        Label password = new Label(i18n("settings.launcher.proxy.password"));
                        GridPane.setRowIndex(password, 1);
                        GridPane.setColumnIndex(password, 0);
                        authPane.getChildren().add(password);
                    }

                    {
                        JFXPasswordField txtProxyPassword = new JFXPasswordField();
                        GridPane.setRowIndex(txtProxyPassword, 1);
                        GridPane.setColumnIndex(txtProxyPassword, 1);
                        authPane.getChildren().add(txtProxyPassword);
                        txtProxyPassword.textProperty().bindBidirectional(config().proxyPassProperty());
                    }

                    ToggleGroup proxyConfigurationGroup = new ToggleGroup();
                    chkProxyNone.setUserData(Proxy.Type.DIRECT);
                    chkProxyNone.setToggleGroup(proxyConfigurationGroup);
                    chkProxyHttp.setUserData(Proxy.Type.HTTP);
                    chkProxyHttp.setToggleGroup(proxyConfigurationGroup);
                    chkProxySocks.setUserData(Proxy.Type.SOCKS);
                    chkProxySocks.setToggleGroup(proxyConfigurationGroup);
                    selectedItemPropertyFor(proxyConfigurationGroup, Proxy.Type.class).bindBidirectional(config().proxyTypeProperty());

                    proxyPane.getChildren().add(authPane);
                }
                proxyList.getChildren().add(proxyPane);
            }
            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.proxy")), proxyList);
        }

    }
}
