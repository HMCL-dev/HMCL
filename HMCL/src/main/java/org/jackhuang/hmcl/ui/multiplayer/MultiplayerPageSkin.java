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
package org.jackhuang.hmcl.ui.multiplayer;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.HMCLService;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.Locales;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MultiplayerPageSkin extends DecoratorAnimatedPage.DecoratorAnimatedPageSkin<MultiplayerPage> {

    private ObservableList<Node> clients;

    /**
     * Constructor for all SkinBase instances.
     *
     * @param control The control for which this Skin should attach to.
     */
    protected MultiplayerPageSkin(MultiplayerPage control) {
        super(control);

        {
            AdvancedListBox sideBar = new AdvancedListBox()
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("version.launch"));
                        item.setLeftGraphic(wrap(SVG::rocketLaunchOutline));
                        item.setOnAction(e -> {
                            Profile profile = Profiles.getSelectedProfile();
                            Versions.launch(profile, profile.getSelectedVersion(), LauncherHelper::setKeep);
                        });
                    })
                    .startCategory(i18n("help"))
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("help"));
                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer"));
                    })
//                    .addNavigationDrawerItem(item -> {
//                        item.setTitle(i18n("multiplayer.help.1"));
//                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
//                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/admin.html"));
//                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("multiplayer.help.2"));
                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html"));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("multiplayer.help.3"));
                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html#%E5%88%9B%E5%BB%BA%E6%96%B9"));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("multiplayer.help.4"));
                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html#%E5%8F%82%E4%B8%8E%E8%80%85"));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("multiplayer.help.5"));
                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html#3%E5%A6%82%E4%BD%95%E5%85%B3%E9%97%AD%E9%98%B2%E7%81%AB%E5%A2%99"));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("multiplayer.help.6"));
                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html#%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98"));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("multiplayer.help.text"));
                        item.setLeftGraphic(wrap(SVG::rocketLaunchOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/text.html"));
                    })
                    .addNavigationDrawerItem(report -> {
                        report.setTitle(i18n("feedback"));
                        report.setLeftGraphic(wrap(SVG::messageAlertOutline));
                        report.setOnAction(e -> HMCLService.openRedirectLink("multiplayer-feedback"));
                    });
            FXUtils.setLimitWidth(sideBar, 200);
            setLeft(sideBar);
        }

        {
            VBox content = new VBox(16);
            content.setPadding(new Insets(10));
            content.setFillWidth(true);
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            setCenter(scrollPane);

            VBox mainPane = new VBox(16);
            {
                ComponentList offPane = new ComponentList();
                {
                    HintPane hintPane0 = new HintPane(MessageDialogPane.MessageType.WARNING);
                    hintPane0.setText(i18n("multiplayer.off.hint"));

                    HintPane hintPane00 = new HintPane(MessageDialogPane.MessageType.WARNING);
                    hintPane00.setText(i18n("multiplayer.token.prompt"));

                    BorderPane tokenPane = new BorderPane();
                    {
                        Label tokenTitle = new Label(i18n("multiplayer.token"));
                        BorderPane.setAlignment(tokenTitle, Pos.CENTER_LEFT);
                        tokenPane.setLeft(tokenTitle);
                        // Token acts like password, we hide it here preventing users from accidentally leaking their token when taking screenshots.
                        JFXPasswordField tokenField = new JFXPasswordField();
                        BorderPane.setAlignment(tokenField, Pos.CENTER_LEFT);
                        BorderPane.setMargin(tokenField, new Insets(0, 8, 0, 8));
                        tokenPane.setCenter(tokenField);
                        tokenField.textProperty().bindBidirectional(globalConfig().multiplayerTokenProperty());
                        tokenField.setPromptText(i18n("multiplayer.token.prompt2"));

                        Validator validator = new Validator("multiplayer.token.format_invalid", StringUtils::isAlphabeticOrNumber);
                        InvalidationListener listener = any -> tokenField.validate();
                        validator.getProperties().put(validator, listener);
                        tokenField.textProperty().addListener(new WeakInvalidationListener(listener));
                        tokenField.getValidators().add(validator);

                        JFXHyperlink applyLink = new JFXHyperlink(i18n("multiplayer.token.apply"));
                        BorderPane.setAlignment(applyLink, Pos.CENTER_RIGHT);
                        applyLink.setOnAction(e -> HMCLService.openRedirectLink("multiplayer-static-token"));
                        tokenPane.setRight(applyLink);
                    }

                    HBox startPane = new HBox();
                    {
                        JFXButton startButton = new JFXButton(i18n("multiplayer.off.start"));
                        startButton.getStyleClass().add("jfx-button-raised");
                        startButton.setButtonType(JFXButton.ButtonType.RAISED);
                        startButton.setOnMouseClicked(e -> control.start());
                        startButton.disableProperty().bind(MultiplayerManager.tokenInvalid);

                        startPane.getChildren().setAll(startButton);
                        startPane.setAlignment(Pos.CENTER_RIGHT);
                    }

                    if (!MultiplayerManager.IS_ADMINISTRATOR)
                        offPane.getContent().add(hintPane0);
                    offPane.getContent().addAll(tokenPane, hintPane00, startPane);
                }

                ComponentList onPane = new ComponentList();
                {     
                    HintPane roothintPane = new HintPane(MessageDialogPane.MessageType.INFO);
                    roothintPane.setText(i18n("multiplayer.hint"));

                    BorderPane expirationPane = new BorderPane();
                    expirationPane.setLeft(new Label(i18n("multiplayer.session.expiration")));
                    Label expirationLabel = new Label();
                    expirationLabel.textProperty().bind(Bindings.createStringBinding(() ->
                                    control.getExpireTime() == null ? "" : Locales.SIMPLE_DATE_FORMAT.get().format(control.getExpireTime()),
                            control.expireTimeProperty()));
                    expirationPane.setCenter(expirationLabel);

                    GridPane masterPane = new GridPane();
                    masterPane.setVgap(8);
                    masterPane.setHgap(16);
                    ColumnConstraints titleColumn = new ColumnConstraints();
                    ColumnConstraints valueColumn = new ColumnConstraints();
                    ColumnConstraints rightColumn = new ColumnConstraints();
                    masterPane.getColumnConstraints().setAll(titleColumn, valueColumn, rightColumn);
                    valueColumn.setFillWidth(true);
                    valueColumn.setHgrow(Priority.ALWAYS);
                    {
                        BorderPane titlePane = new BorderPane();
                        GridPane.setColumnSpan(titlePane, 3);
                        Label title = new Label(i18n("multiplayer.master"));
                        titlePane.setLeft(title);

                        JFXHyperlink tutorial = new JFXHyperlink(i18n("multiplayer.master.video_tutorial"));
                        titlePane.setRight(tutorial);
                        tutorial.setOnAction(e -> HMCLService.openRedirectLink("multiplayer-tutorial-master"));
                        masterPane.addRow(0, titlePane);

                        HintPane hintPane1 = new HintPane(MessageDialogPane.MessageType.INFO);
                        GridPane.setColumnSpan(hintPane1, 3);
                        hintPane1.setText(i18n("multiplayer.master.hint"));
                        masterPane.addRow(1, hintPane1);

                        Label portTitle = new Label(i18n("multiplayer.master.port"));
                        BorderPane.setAlignment(portTitle, Pos.CENTER_LEFT);

                        JFXTextField portTextField = new JFXTextField();
                        GridPane.setColumnSpan(portTextField, 2);
                        FXUtils.setValidateWhileTextChanged(portTextField, true);
                        portTextField.getValidators().add(new Validator(i18n("multiplayer.master.port.validate"), (text) -> {
                            Integer value = Lang.toIntOrNull(text);
                            return value != null && 0 <= value && value <= 65535;
                        }));
                        portTextField.textProperty().bindBidirectional(control.portProperty(), new StringConverter<Number>() {
                            @Override
                            public String toString(Number object) {
                                return Integer.toString(object.intValue());
                            }

                            @Override
                            public Number fromString(String string) {
                                return Lang.parseInt(string, 0);
                            }
                        });
                        masterPane.addRow(2, portTitle, portTextField);

                        Label serverAddressTitle = new Label(i18n("multiplayer.master.server_address"));
                        BorderPane.setAlignment(serverAddressTitle, Pos.CENTER_LEFT);
                        Label serverAddressLabel = new Label();
                        BorderPane.setAlignment(serverAddressLabel, Pos.CENTER_LEFT);
                        serverAddressLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                            return (control.getAddress() == null ? "" : control.getAddress()) + ":" + control.getPort();
                        }, control.addressProperty(), control.portProperty()));
                        JFXButton copyButton = new JFXButton(i18n("multiplayer.master.server_address.copy"));
                        copyButton.setOnAction(e -> FXUtils.copyText(serverAddressLabel.getText()));
                        masterPane.addRow(3, serverAddressTitle, serverAddressLabel, copyButton);
                    }

                    VBox slavePane = new VBox(8);
                    {
                        BorderPane titlePane = new BorderPane();
                        Label title = new Label(i18n("multiplayer.slave"));
                        titlePane.setLeft(title);

                        JFXHyperlink tutorial = new JFXHyperlink(i18n("multiplayer.slave.video_tutorial"));
                        tutorial.setOnAction(e -> HMCLService.openRedirectLink("multiplayer-tutorial-slave"));
                        titlePane.setRight(tutorial);

                        HintPane hintPane3 = new HintPane(MessageDialogPane.MessageType.INFO);
                        GridPane.setColumnSpan(hintPane3, 3);
                        hintPane3.setText(i18n("multiplayer.slave.hint"));
                        slavePane.getChildren().add(hintPane3);

                        HintPane hintPane4 = new HintPane(MessageDialogPane.MessageType.WARNING);
                        GridPane.setColumnSpan(hintPane4, 3);
                        hintPane4.setText(i18n("multiplayer.slave.hint2"));
                        slavePane.getChildren().add(hintPane4);

                        HintPane hintPane5 = new HintPane(MessageDialogPane.MessageType.INFO);
                        GridPane.setColumnSpan(hintPane5, 3);
                        hintPane5.setText(i18n("multiplayer.slave.server_address.hint"));
                        slavePane.getChildren().add(hintPane5);

                        GridPane notBroadcastingPane = new GridPane();
                        {
                            notBroadcastingPane.setVgap(8);
                            notBroadcastingPane.setHgap(16);
                            notBroadcastingPane.getColumnConstraints().setAll(titleColumn, valueColumn, rightColumn);

                            Label addressTitle = new Label(i18n("multiplayer.slave.server_address"));

                            JFXTextField addressField = new JFXTextField();
                            FXUtils.setValidateWhileTextChanged(addressField, true);
                            addressField.getValidators().add(new ServerAddressValidator());

                            JFXButton startButton = new JFXButton(i18n("multiplayer.slave.server_address.start"));
                            startButton.setOnAction(e -> control.broadcast(addressField.getText()));
                            notBroadcastingPane.addRow(0, addressTitle, addressField, startButton);
                        }

                        GridPane broadcastingPane = new GridPane();
                        {
                            broadcastingPane.setVgap(8);
                            broadcastingPane.setHgap(16);
                            broadcastingPane.getColumnConstraints().setAll(titleColumn, valueColumn, rightColumn);

                            Label addressTitle = new Label(i18n("multiplayer.slave.server_address"));
                            Label addressLabel = new Label();
                            addressLabel.textProperty().bind(Bindings.createStringBinding(() ->
                                            control.getBroadcaster() != null ? control.getBroadcaster().getAddress() : "",
                                    control.broadcasterProperty()));

                            JFXButton stopButton = new JFXButton(i18n("multiplayer.slave.server_address.stop"));
                            stopButton.setOnAction(e -> control.stopBroadcasting());
                            broadcastingPane.addRow(0, addressTitle, addressLabel, stopButton);
                        }

                        FXUtils.onChangeAndOperate(control.broadcasterProperty(), broadcaster -> {
                            if (broadcaster == null) {
                                slavePane.getChildren().setAll(titlePane, hintPane3);//, hintPane4, hintPane5, notBroadcastingPane);
                            } else {
                                slavePane.getChildren().setAll(titlePane, hintPane3);//, hintPane4, hintPane5, broadcastingPane);
                            }
                        });
                    }

                    FXUtils.onChangeAndOperate(control.expireTimeProperty(), t -> {
                        if (t == null) {
                            onPane.getContent().setAll(roothintPane, masterPane, slavePane);
                        } else {
                            onPane.getContent().setAll(roothintPane, expirationPane, masterPane, slavePane);
                        }
                    });
                }

                FXUtils.onChangeAndOperate(getSkinnable().sessionProperty(), session -> {
                    if (session == null) {
                        mainPane.getChildren().setAll(offPane);
                    } else {
                        mainPane.getChildren().setAll(onPane);
                    }
                });
            }

            ComponentList persistencePane = new ComponentList();
            {
                HintPane hintPane = new HintPane(MessageType.INFO);
                hintPane.setText(i18n("multiplayer.persistence.hint"));

                BorderPane importPane = new BorderPane();
                {
                    Label left = new Label(i18n("multiplayer.persistence.import"));
                    BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                    importPane.setLeft(left);

                    JFXButton importButton = new JFXButton(i18n("multiplayer.persistence.import.button"));
                    importButton.setOnMouseClicked(e -> {
                        Path targetPath = MultiplayerManager.getConfigPath(globalConfig().getMultiplayerToken());
                        if (Files.exists(targetPath)) {
                            LOG.warning("License file " + targetPath + " already exists");
                            Controllers.dialog(i18n("multiplayer.persistence.import.file_already_exists"), null, MessageType.ERROR);
                            return;
                        }

                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle(i18n("multiplayer.persistence.import.title"));
                        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("multiplayer.persistence.license_file"), "*.yml"));

                        File file = fileChooser.showOpenDialog(Controllers.getStage());
                        if (file == null)
                            return;

                        CompletableFuture<Boolean> future = new CompletableFuture<>();
                        if (file.getName().matches("[a-z0-9]{40}.yml") && !targetPath.getFileName().toString().equals(file.getName())) {
                            Controllers.confirm(i18n("multiplayer.persistence.import.token_not_match"), null, MessageType.QUESTION,
                                    () -> future.complete(true),
                                    () -> future.complete(false)) ;
                        } else {
                            future.complete(true);
                        }
                        future.thenAcceptAsync(Lang.wrapConsumer(c -> {
                            if (c) Files.copy(file.toPath(), targetPath);
                        })).exceptionally(exception -> {
                            LOG.log(Level.WARNING, "Failed to import license file", exception);
                            Platform.runLater(() -> Controllers.dialog(i18n("multiplayer.persistence.import.failed"), null, MessageType.ERROR));
                            return null;
                        });
                    });
                    importButton.disableProperty().bind(MultiplayerManager.tokenInvalid);
                    importButton.getStyleClass().add("jfx-button-border");
                    importPane.setRight(importButton);
                }

                BorderPane exportPane = new BorderPane();
                {
                    Label left = new Label(i18n("multiplayer.persistence.export"));
                    BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                    exportPane.setLeft(left);

                    JFXButton exportButton = new JFXButton(i18n("multiplayer.persistence.export.button"));
                    exportButton.setOnMouseClicked(e -> {
                        String token = globalConfig().getMultiplayerToken();
                        Path configPath = MultiplayerManager.getConfigPath(token);

                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle(i18n("multiplayer.persistence.export.title"));
                        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("multiplayer.persistence.license_file"), "*.yml"));
                        fileChooser.setInitialFileName(configPath.getFileName().toString());

                        File file = fileChooser.showSaveDialog(Controllers.getStage());
                        if (file == null)
                            return;

                        CompletableFuture.runAsync(Lang.wrap(() -> MultiplayerManager.downloadHiperConfig(token, configPath)), Schedulers.io())
                                .handleAsync((ignored, exception) -> {
                                    if (exception != null) {
                                        LOG.log(Level.INFO, "Unable to download hiper config file", e);
                                    }

                                    if (!Files.isRegularFile(configPath)) {
                                        LOG.warning("License file " + configPath + " not exists");
                                        Platform.runLater(() -> Controllers.dialog(i18n("multiplayer.persistence.export.file_not_exists"), null, MessageType.ERROR));
                                        return null;
                                    }

                                    try {
                                        Files.copy(configPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    } catch (IOException ioException) {
                                        LOG.log(Level.WARNING, "Failed to export license file", ioException);
                                        Platform.runLater(() -> Controllers.dialog(i18n("multiplayer.persistence.export.failed"), null, MessageType.ERROR));
                                    }

                                    return null;
                                });

                    });
                    exportButton.disableProperty().bind(MultiplayerManager.tokenInvalid);
                    exportButton.getStyleClass().add("jfx-button-border");
                    exportPane.setRight(exportButton);
                }

                persistencePane.getContent().setAll(hintPane, importPane, exportPane);
            }


            ComponentList thanksPane = new ComponentList();
            {
                HBox pane = new HBox();
                pane.setAlignment(Pos.CENTER_LEFT);

                //JFXHyperlink aboutLink = new JFXHyperlink(i18n("about"));
                //aboutLink.setOnAction(e -> HMCLService.openRedirectLink("multiplayer-about"));

                HBox placeholder = new HBox();
                HBox.setHgrow(placeholder, Priority.ALWAYS);

                pane.getChildren().setAll(
                        //aboutLink,
                        placeholder,
                        FXUtils.segmentToTextFlow(i18n("multiplayer.powered_by"), Controllers::onHyperlinkAction));

                thanksPane.getContent().addAll(pane);
            }

            content.getChildren().setAll(
                    mainPane,
                    ComponentList.createComponentListTitle(i18n("multiplayer.persistence")),
                    persistencePane,
                    ComponentList.createComponentListTitle(i18n("about")),
                    thanksPane
            );
        }
    }

}
