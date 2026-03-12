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
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.net.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DownloadSettingsPage extends StackPane {

    private final WeakListenerHolder holder = new WeakListenerHolder();

    public DownloadSettingsPage() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);

        {
            var downloadSource = new ComponentList();
            downloadSource.getStyleClass().add("card-non-transparent");
            {

                var autoChooseDownloadSource = new LineToggleButton();
                autoChooseDownloadSource.setTitle(i18n("settings.launcher.download_source.auto"));
                autoChooseDownloadSource.selectedProperty().bindBidirectional(config().autoChooseDownloadTypeProperty());

                Function<String, String> converter = key -> i18n("download.provider." + key);
                Function<String, String> descriptionConverter = key -> {
                    String bundleKey = "download.provider." + key + ".desc";
                    return I18n.hasKey(bundleKey) ? i18n(bundleKey) : null;
                };

                var versionListSourcePane = new LineSelectButton<String>();
                versionListSourcePane.disableProperty().bind(autoChooseDownloadSource.selectedProperty().not());
                versionListSourcePane.setTitle(i18n("settings.launcher.version_list_source"));
                versionListSourcePane.setConverter(converter);
                versionListSourcePane.setDescriptionConverter(descriptionConverter);
                versionListSourcePane.setItems(DownloadProviders.AUTO_PROVIDERS.keySet());
                versionListSourcePane.valueProperty().bindBidirectional(config().versionListSourceProperty());

                var downloadSourcePane = new LineSelectButton<String>();
                downloadSourcePane.disableProperty().bind(autoChooseDownloadSource.selectedProperty());
                downloadSourcePane.setTitle(i18n("settings.launcher.download_source"));
                downloadSourcePane.setConverter(converter);
                downloadSourcePane.setDescriptionConverter(descriptionConverter);
                downloadSourcePane.setItems(DownloadProviders.DIRECT_PROVIDERS.keySet());
                downloadSourcePane.valueProperty().bindBidirectional(config().downloadTypeProperty());

                downloadSource.getContent().setAll(autoChooseDownloadSource, versionListSourcePane, downloadSourcePane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.download_source")), downloadSource);
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
                    hbox.setStyle("-fx-view-order: -1;"); // prevent the indicator from being covered by the hint
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

            HBox proxyTypePane = new HBox();
            {
                proxyTypePane.setPadding(new Insets(10, 0, 0, 0));

                ToggleGroup proxyConfigurationGroup = new ToggleGroup();

                JFXRadioButton chkProxyDefault = new JFXRadioButton(i18n("settings.launcher.proxy.default"));
                chkProxyDefault.setUserData(null);
                chkProxyDefault.setToggleGroup(proxyConfigurationGroup);

                JFXRadioButton chkProxyNone = new JFXRadioButton(i18n("settings.launcher.proxy.none"));
                chkProxyNone.setUserData(Proxy.Type.DIRECT);
                chkProxyNone.setToggleGroup(proxyConfigurationGroup);

                JFXRadioButton chkProxyHttp = new JFXRadioButton(i18n("settings.launcher.proxy.http"));
                chkProxyHttp.setUserData(Proxy.Type.HTTP);
                chkProxyHttp.setToggleGroup(proxyConfigurationGroup);


                JFXRadioButton chkProxySocks = new JFXRadioButton(i18n("settings.launcher.proxy.socks"));
                chkProxySocks.setUserData(Proxy.Type.SOCKS);
                chkProxySocks.setToggleGroup(proxyConfigurationGroup);

                if (config().hasProxy()) {
                    Proxy.Type proxyType = config().getProxyType();
                    if (proxyType == Proxy.Type.DIRECT) {
                        chkProxyNone.setSelected(true);
                    } else if (proxyType == Proxy.Type.HTTP) {
                        chkProxyHttp.setSelected(true);
                    } else if (proxyType == Proxy.Type.SOCKS) {
                        chkProxySocks.setSelected(true);
                    } else {
                        chkProxyNone.setSelected(true);
                    }
                } else {
                    chkProxyDefault.setSelected(true);
                }

                holder.add(FXUtils.onWeakChange(proxyConfigurationGroup.selectedToggleProperty(), toggle -> {
                    Proxy.Type proxyType = toggle != null ? (Proxy.Type) toggle.getUserData() : null;

                    if (proxyType == null) {
                        config().setHasProxy(false);
                        config().setProxyType(null);
                    } else {
                        config().setHasProxy(true);
                        config().setProxyType(proxyType);
                    }
                }));

                proxyTypePane.getChildren().setAll(chkProxyDefault, chkProxyNone, chkProxyHttp, chkProxySocks);
                proxyList.getChildren().add(proxyTypePane);
            }

            VBox proxyPane = new VBox();
            {
                proxyPane.disableProperty().bind(
                        Bindings.createBooleanBinding(() ->
                                        !config().hasProxy() || config().getProxyType() == null || config().getProxyType() == Proxy.Type.DIRECT,
                                config().hasProxyProperty(),
                                config().proxyTypeProperty()));

                ColumnConstraints colHgrow = new ColumnConstraints();
                colHgrow.setHgrow(Priority.ALWAYS);

                {
                    GridPane gridPane = new GridPane();
                    gridPane.setPadding(new Insets(0, 0, 0, 30));
                    gridPane.setHgap(20);
                    gridPane.setVgap(10);
                    gridPane.getColumnConstraints().setAll(new ColumnConstraints(), colHgrow);
                    gridPane.getRowConstraints().setAll(new RowConstraints(), new RowConstraints());

                    {
                        Label host = new Label(i18n("settings.launcher.proxy.host"));
                        GridPane.setRowIndex(host, 1);
                        GridPane.setColumnIndex(host, 0);
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
                        gridPane.getChildren().add(port);
                    }

                    {
                        JFXTextField txtProxyPort = new JFXTextField();
                        GridPane.setFillWidth(txtProxyPort, false);
                        txtProxyPort.setMaxWidth(200);
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

                VBox chkProxyAuthenticationPane = new VBox();
                {
                    chkProxyAuthenticationPane.setPadding(new Insets(20, 0, 20, 5));

                    JFXCheckBox chkProxyAuthentication = new JFXCheckBox(i18n("settings.launcher.proxy.authentication"));
                    chkProxyAuthenticationPane.getChildren().add(chkProxyAuthentication);
                    chkProxyAuthentication.selectedProperty().bindBidirectional(config().hasProxyAuthProperty());

                    proxyPane.getChildren().add(chkProxyAuthenticationPane);
                }

                GridPane authPane = new GridPane();
                {
                    authPane.setPadding(new Insets(0, 0, 0, 30));
                    authPane.setHgap(20);
                    authPane.setVgap(10);
                    authPane.getColumnConstraints().setAll(new ColumnConstraints(), colHgrow);
                    authPane.getRowConstraints().setAll(new RowConstraints(), new RowConstraints());
                    authPane.disableProperty().bind(config().hasProxyAuthProperty().not());

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

                    proxyPane.getChildren().add(authPane);
                    proxyList.getChildren().add(proxyPane);
                }
            }
            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.proxy")), proxyList);
        }

    }
}
