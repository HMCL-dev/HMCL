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
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.HMCLService;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.i18n.Locales;

import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
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
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("multiplayer.help.1"));
                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html#%E9%9B%B6%E4%BD%BF%E7%94%A8%E7%AE%A1%E7%90%86%E5%91%98%E6%9D%83%E9%99%90%E5%90%AF%E5%8A%A8-hmcl"));
                    })
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
                        item.setTitle(i18n("multiplayer.help.text"));
                        item.setLeftGraphic(wrap(SVG::rocketLaunchOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/text.html"));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("multiplayer.help.5"));
                        item.setLeftGraphic(wrap(SVG::helpCircleOutline));
                        item.setOnAction(e -> FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html#3%E5%A6%82%E4%BD%95%E5%85%B3%E9%97%AD%E9%98%B2%E7%81%AB%E5%A2%99"));
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
                    HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
                    hintPane.setText(i18n("multiplayer.hint"));

                    HintPane hintPane0 = new HintPane(MessageDialogPane.MessageType.WARNING);
                    hintPane0.setText(i18n("multiplayer.off.hint"));

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
                        tokenField.setPromptText(i18n("multiplayer.token.prompt"));

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

                        startPane.getChildren().setAll(startButton);
                        startPane.setAlignment(Pos.CENTER_RIGHT);
                    }

                    offPane.getContent().setAll(hintPane, hintPane0, tokenPane, startPane);
                }

                ComponentList onPane = new ComponentList();
                {     
                    BorderPane roothintPane = new BorderPane();
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
                        
                        HintPane hintPane2 = new HintPane(MessageDialogPane.MessageType.INFO);
                        GridPane.setColumnSpan(hintPane2, 3);
                        hintPane2.setText(i18n("multiplayer.master.hint2"));

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
                                slavePane.getChildren().setAll(titlePane, hintPane3, hintPane4, hintPane5, notBroadcastingPane);
                            } else {
                                slavePane.getChildren().setAll(titlePane, hintPane3, hintPane4, hintPane5, broadcastingPane);
                            }
                        });
                    }

                    FXUtils.onChangeAndOperate(control.expireTimeProperty(), t -> {
                        if (t == null) {
                            onPane.getContent().setAll(masterPane, slavePane);
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
                        //placeholder,
                        FXUtils.segmentToTextFlow(i18n("multiplayer.powered_by"), Controllers::onHyperlinkAction));

                thanksPane.getContent().addAll(pane);
            }

            content.getChildren().setAll(
                    mainPane,
                    ComponentList.createComponentListTitle(i18n("about")),
                    thanksPane
            );
        }
    }

}
