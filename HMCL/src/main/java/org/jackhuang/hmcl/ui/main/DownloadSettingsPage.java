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
import javafx.scene.Node;
import javafx.scene.control.Label;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.setting.EnumCommonDirectory;
import org.jackhuang.hmcl.setting.ProxyType;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
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
                Function<DownloadSource, String> converter = source -> switch (source) {
                    case DEFAULT -> i18n("settings.launcher.download_source.auto");
                    case OFFICIAL -> i18n("download.provider.official");
                    case MIRROR -> i18n("download.provider.mirror");
                };
                Function<DownloadSource, String> descriptionConverter = source -> {
                    String bundleKey = switch (source) {
                        case DEFAULT -> "download.provider.balanced.desc";
                        case OFFICIAL -> "download.provider.official.desc";
                        case MIRROR -> "download.provider.mirror.desc";
                    };
                    return I18n.hasKey(bundleKey) ? i18n(bundleKey) : null;
                };

                var versionListSourcePane = new LineSelectButton<DownloadSource>();
                versionListSourcePane.setTitle(i18n("settings.launcher.version_list_source"));
                versionListSourcePane.setNullSafeConverter(converter);
                versionListSourcePane.setDescriptionConverter(descriptionConverter);
                versionListSourcePane.setItems(DownloadSource.values());
                versionListSourcePane.valueProperty().bindBidirectional(settings().versionListSourceProperty());

                var downloadSourcePane = new LineSelectButton<DownloadSource>();
                downloadSourcePane.setTitle(i18n("settings.launcher.download_source"));
                downloadSourcePane.setNullSafeConverter(converter);
                downloadSourcePane.setDescriptionConverter(descriptionConverter);
                downloadSourcePane.setItems(DownloadSource.values());
                downloadSourcePane.valueProperty().bindBidirectional(settings().fileDownloadSourceProperty());

                var defaultAddonSourcePane = new LineSelectButton<String>();
                defaultAddonSourcePane.setTitle(i18n("settings.launcher.default_addon_source"));
                defaultAddonSourcePane.setNullSafeConverter(key -> I18n.i18n("addon." + key));
                defaultAddonSourcePane.setItems("modrinth", "curseforge");
                defaultAddonSourcePane.valueProperty().bindBidirectional(settings().defaultAddonSourceProperty());

                downloadSource.getContent().setAll(versionListSourcePane, downloadSourcePane, defaultAddonSourcePane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.download_source")), downloadSource);
        }

        {
            var downloadList = new ComponentList();

            ComponentSublist fileCommonLocationSublist = new ComponentSublist(() -> {
                MultiFileItem<EnumCommonDirectory> fileCommonLocation = new MultiFileItem<>();
                fileCommonLocation.loadChildren(Arrays.asList(
                        new MultiFileItem.Option<>(i18n("launcher.cache_directory.default"), EnumCommonDirectory.DEFAULT),
                        new MultiFileItem.FileOption<>(i18n("settings.custom"), EnumCommonDirectory.CUSTOM)
                                .setChooserTitle(i18n("launcher.cache_directory.choose"))
                                .setSelectionMode(FileSelector.SelectionMode.DIRECTORY)
                                .bindBidirectional(settings().commonDirectoryProperty())
                ));
                fileCommonLocation.selectedDataProperty().bindBidirectional(settings().commonDirectoryTypeProperty());
                return List.of(fileCommonLocation);
            });
            fileCommonLocationSublist.setTitle(i18n("launcher.cache_directory"));
            fileCommonLocationSublist.setHasSubtitle(true);
            fileCommonLocationSublist.descriptionProperty().bind(
                    Bindings.createObjectBinding(() -> Optional.ofNullable(settings().getResolvedCommonDirectory())
                                    .orElse(i18n("launcher.cache_directory.disabled")),
                            settings().commonDirectoryProperty(), settings().commonDirectoryTypeProperty()));

            JFXButton cleanButton = FXUtils.newBorderButton(i18n("launcher.cache_directory.clean"));
            cleanButton.setOnAction(e -> clearCacheDirectory());
            fileCommonLocationSublist.setHeaderRight(cleanButton);

            ComponentSublist downloadThreadsSublist = new ComponentSublist(() -> {
                var downloadThreadsList = new RadioChoiceList<Boolean>();
                downloadThreadsList.setChoices(
                        new RadioChoiceList.Choice<>(i18n("settings.launcher.download.threads.auto"), true),
                        new RadioChoiceList.Choice<>(i18n("settings.launcher.download.threads.custom"), false) {
                            @Override
                            protected Node createRightNode() {
                                HBox hbox = new HBox(8);
                                hbox.setViewOrder(-1);
                                hbox.setAlignment(Pos.CENTER);
                                // hbox.setPadding(new Insets(0, 0, 0, 30));
                                hbox.disableProperty().bind(settings().autoDownloadThreadsProperty());

                                JFXSlider slider = new JFXSlider(1, 256, 64);
                                HBox.setHgrow(slider, Priority.ALWAYS);

                                JFXTextField threadsField = new JFXTextField();
                                FXUtils.setLimitWidth(threadsField, 60);
                                FXUtils.bind(threadsField, settings().downloadThreadsProperty(), SafeStringConverter.fromInteger()
                                        .restrict(it -> it > 0)
                                        .fallbackTo(FetchTask.DEFAULT_CONCURRENCY)
                                        .asPredicate(Validator.addTo(threadsField)));

                                var changedByTextField = new Holder<>(false);
                                FXUtils.onChangeAndOperate(settings().downloadThreadsProperty(), value -> {
                                    changedByTextField.value = true;
                                    slider.setValue(value.intValue());
                                    changedByTextField.value = false;
                                });
                                slider.valueProperty().addListener((value, oldVal, newVal) -> {
                                    if (changedByTextField.value) return;
                                    settings().downloadThreadsProperty().set(value.getValue().intValue());
                                });

                                hbox.getChildren().setAll(slider, threadsField);
                                return hbox;
                            }
                        }
                );

                downloadThreadsList.selectedValueProperty().bindBidirectional(settings().autoDownloadThreadsProperty());

                return List.of(downloadThreadsList);
            });
            downloadThreadsSublist.setTitle(i18n("settings.launcher.download.threads"));
            downloadThreadsSublist.descriptionProperty().bind(Bindings.createStringBinding(() -> {
                if (settings().autoDownloadThreadsProperty().get()) {
                    return i18n("settings.launcher.download.threads.auto");
                } else {
                    return Integer.toString(settings().downloadThreadsProperty().get());
                }
            }, settings().autoDownloadThreadsProperty(), settings().downloadThreadsProperty()));

            downloadList.getContent().addAll(fileCommonLocationSublist, downloadThreadsSublist);
            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("download")), downloadList);
        }

        {
            VBox proxyList = new VBox(10);
            proxyList.getStyleClass().add("card-non-transparent");

            HBox proxyTypePane = new HBox();
            {
                proxyTypePane.setPadding(new Insets(10, 0, 0, 0));

                ToggleGroup proxyConfigurationGroup = new ToggleGroup();

                JFXRadioButton chkProxySystem = new JFXRadioButton(i18n("settings.launcher.proxy.default"));
                chkProxySystem.setUserData(ProxyType.SYSTEM);
                chkProxySystem.setToggleGroup(proxyConfigurationGroup);

                JFXRadioButton chkProxyNone = new JFXRadioButton(i18n("settings.launcher.proxy.none"));
                chkProxyNone.setUserData(ProxyType.DIRECT);
                chkProxyNone.setToggleGroup(proxyConfigurationGroup);

                JFXRadioButton chkProxyHttp = new JFXRadioButton(i18n("settings.launcher.proxy.http"));
                chkProxyHttp.setUserData(ProxyType.HTTP);
                chkProxyHttp.setToggleGroup(proxyConfigurationGroup);


                JFXRadioButton chkProxySocks = new JFXRadioButton(i18n("settings.launcher.proxy.socks"));
                chkProxySocks.setUserData(ProxyType.SOCKS);
                chkProxySocks.setToggleGroup(proxyConfigurationGroup);

                switch (settings().proxyTypeProperty().get()) {
                    case DIRECT -> chkProxyNone.setSelected(true);
                    case HTTP -> chkProxyHttp.setSelected(true);
                    case SOCKS -> chkProxySocks.setSelected(true);
                    case SYSTEM -> chkProxySystem.setSelected(true);
                }

                holder.add(FXUtils.onWeakChange(proxyConfigurationGroup.selectedToggleProperty(), toggle -> {
                    settings().proxyTypeProperty().set(toggle != null
                            ? (ProxyType) toggle.getUserData()
                            : ProxyType.SYSTEM);
                }));

                proxyTypePane.getChildren().setAll(chkProxySystem, chkProxyNone, chkProxyHttp, chkProxySocks);
                proxyList.getChildren().add(proxyTypePane);
            }

            VBox proxyPane = new VBox();
            {
                proxyPane.disableProperty().bind(
                        Bindings.createBooleanBinding(() ->
                                        !settings().proxyTypeProperty().get().usesCustomAddress(),
                                settings().proxyTypeProperty()));

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
                        FXUtils.bindString(txtProxyHost, settings().proxyHostProperty());
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

                        FXUtils.bind(txtProxyPort, settings().proxyPortProperty(), SafeStringConverter.fromInteger()
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
                    chkProxyAuthentication.selectedProperty().bindBidirectional(settings().hasProxyAuthProperty());

                    proxyPane.getChildren().add(chkProxyAuthenticationPane);
                }

                GridPane authPane = new GridPane();
                {
                    authPane.setPadding(new Insets(0, 0, 0, 30));
                    authPane.setHgap(20);
                    authPane.setVgap(10);
                    authPane.getColumnConstraints().setAll(new ColumnConstraints(), colHgrow);
                    authPane.getRowConstraints().setAll(new RowConstraints(), new RowConstraints());
                    authPane.disableProperty().bind(settings().hasProxyAuthProperty().not());

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
                        FXUtils.bindString(txtProxyUsername, settings().proxyUserProperty());
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
                        txtProxyPassword.textProperty().bindBidirectional(settings().proxyPasswordProperty());
                    }

                    proxyPane.getChildren().add(authPane);
                    proxyList.getChildren().add(proxyPane);
                }
            }
            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.proxy")), proxyList);
        }

    }

    private void clearCacheDirectory() {
        String commonDirectory = settings().getResolvedCommonDirectory();
        if (commonDirectory != null) {
            FileUtils.cleanDirectoryQuietly(Path.of(commonDirectory, "cache"));
        }
    }
}
