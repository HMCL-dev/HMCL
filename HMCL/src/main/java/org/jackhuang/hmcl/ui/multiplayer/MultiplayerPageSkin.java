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
import de.javawi.jstun.test.DiscoveryInfo;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MultiplayerPageSkin extends SkinBase<MultiplayerPage> {

    /**
     * Constructor for all SkinBase instances.
     *
     * @param control The control for which this Skin should attach to.
     */
    protected MultiplayerPageSkin(MultiplayerPage control) {
        super(control);

        BorderPane root = new BorderPane();
        getChildren().setAll(root);
        {
            VBox roomPane = new VBox();
            {
                AdvancedListItem createRoomItem = new AdvancedListItem();
                createRoomItem.setTitle(i18n("multiplayer.session.create"));
                createRoomItem.setLeftGraphic(wrap(SVG::plusCircleOutline));
                createRoomItem.setActionButtonVisible(false);
                createRoomItem.setOnAction(e -> control.createRoom());

                AdvancedListItem joinRoomItem = new AdvancedListItem();
                joinRoomItem.setTitle(i18n("multiplayer.session.join"));
                joinRoomItem.setLeftGraphic(wrap(SVG::accountArrowRightOutline));
                joinRoomItem.setActionButtonVisible(false);
                joinRoomItem.setOnAction(e -> control.joinRoom());

                AdvancedListItem copyLinkItem = new AdvancedListItem();
                copyLinkItem.setTitle(i18n("multiplayer.session.copy_room_code"));
                copyLinkItem.setLeftGraphic(wrap(SVG::accountArrowRightOutline));
                copyLinkItem.setActionButtonVisible(false);
                copyLinkItem.setOnAction(e -> control.copyInvitationCode());

                AdvancedListItem cancelItem = new AdvancedListItem();
                cancelItem.setTitle(i18n("button.cancel"));
                cancelItem.setLeftGraphic(wrap(SVG::closeCircle));
                cancelItem.setActionButtonVisible(false);
                cancelItem.setOnAction(e -> control.cancelRoom());

                AdvancedListItem quitItem = new AdvancedListItem();
                quitItem.setTitle(i18n("multiplayer.session.quit"));
                quitItem.setLeftGraphic(wrap(SVG::closeCircle));
                quitItem.setActionButtonVisible(false);
                quitItem.setOnAction(e -> control.quitRoom());

                AdvancedListItem closeRoomItem = new AdvancedListItem();
                closeRoomItem.setTitle(i18n("multiplayer.session.close"));
                closeRoomItem.setLeftGraphic(wrap(SVG::closeCircle));
                closeRoomItem.setActionButtonVisible(false);
                closeRoomItem.setOnAction(e -> control.closeRoom());

                FXUtils.onChangeAndOperate(getSkinnable().multiplayerStateProperty(), state -> {
                    if (state == MultiplayerManager.State.DISCONNECTED) {
                        roomPane.getChildren().setAll(createRoomItem, joinRoomItem);
                    } else if (state == MultiplayerManager.State.CONNECTING) {
                        roomPane.getChildren().setAll(cancelItem);
                    } else if (state == MultiplayerManager.State.MASTER) {
                        roomPane.getChildren().setAll(copyLinkItem, closeRoomItem);
                    } else if (state == MultiplayerManager.State.SLAVE) {
                        roomPane.getChildren().setAll(quitItem);
                    }
                });
            }

            AdvancedListBox sideBar = new AdvancedListBox()
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("version.launch"));
                        item.setLeftGraphic(wrap(SVG::rocketLaunchOutline));
                        item.setOnAction(e -> {
                            Profile profile = Profiles.getSelectedProfile();
                            Versions.launch(profile, profile.getSelectedVersion(), LauncherHelper::setKeep);
                        });
                    })
                    .startCategory(i18n("multiplayer.session"))
                    .add(roomPane)
                    .startCategory(i18n("help"))
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("help"));
                        item.setLeftGraphic(wrap(SVG::gamepad));
                        item.setOnAction(e -> FXUtils.openLink("https://hmcl.huangyuhui.net/help/launcher/multiplayer.html"));
                    })
                    .addNavigationDrawerItem(report -> {
                        report.setTitle(i18n("multiplayer.report"));
                        report.setLeftGraphic(wrap(SVG::bug));
                        report.setOnAction(e -> FXUtils.openLink(Metadata.EULA_URL));
                    });
            FXUtils.setLimitWidth(sideBar, 200);
            root.setLeft(sideBar);
        }

        {
            VBox content = new VBox(16);
            content.setPadding(new Insets(10));
            content.setFillWidth(true);
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            root.setCenter(scrollPane);

            HintPane hint = new HintPane(MessageDialogPane.MessageType.INFO);
            hint.setText(i18n("multiplayer.hint"));

            ComponentList roomPane = new ComponentList();
            {
                TransitionPane transitionPane = new TransitionPane();
                roomPane.getContent().setAll(transitionPane);

                VBox disconnectedPane = new VBox(8);
                {
                    HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
                    hintPane.setText(i18n("multiplayer.state.disconnected.hint"));

                    Label label = new Label(i18n("multiplayer.state.disconnected"));

                    disconnectedPane.getChildren().setAll(hintPane, label);
                }

                VBox connectingPane = new VBox(8);
                {
                    Label label = new Label(i18n("multiplayer.state.connecting"));

                    connectingPane.getChildren().setAll(label);
                }

                VBox masterPane = new VBox(8);
                {
                    Label label = new Label(i18n("multiplayer.state.master"));
                    label.textProperty().bind(Bindings.createStringBinding(() ->
                            i18n("multiplayer.state.master", control.getSession() == null ? "" : control.getSession().getName(), control.getPort()),
                            control.portProperty(), control.sessionProperty()));
                    masterPane.getChildren().setAll(label);
                }

                BorderPane slavePane = new BorderPane();
                {
                    HintPane slaveHintPane = new HintPane();
                    slaveHintPane.setText(i18n("multiplayer.state.slave.hint"));
                    slavePane.setTop(slaveHintPane);

                    Label label = new Label();
                    label.textProperty().bind(Bindings.createStringBinding(() ->
                            i18n("multiplayer.state.slave", control.getSession() == null ? "" : control.getSession().getName(), "0.0.0.0:" + control.getPort()),
                            control.sessionProperty(), control.portProperty()));
                    BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                    slavePane.setCenter(label);

                    JFXButton copyButton = new JFXButton(i18n("multiplayer.state.slave.copy"));
                    copyButton.setOnAction(e -> FXUtils.copyText("0.0.0.0:" + control.getPort()));
                    slavePane.setRight(copyButton);
                }

                FXUtils.onChangeAndOperate(getSkinnable().multiplayerStateProperty(), state -> {
                    if (state == MultiplayerManager.State.DISCONNECTED) {
                        transitionPane.setContent(disconnectedPane, ContainerAnimations.NONE.getAnimationProducer());
                    } else if (state == MultiplayerManager.State.CONNECTING) {
                        transitionPane.setContent(connectingPane, ContainerAnimations.NONE.getAnimationProducer());
                    } else if (state == MultiplayerManager.State.MASTER) {
                        transitionPane.setContent(masterPane, ContainerAnimations.NONE.getAnimationProducer());
                    } else if (state == MultiplayerManager.State.SLAVE) {
                        transitionPane.setContent(slavePane, ContainerAnimations.NONE.getAnimationProducer());
                    }
                });
            }

            ComponentList natDetectionPane = new ComponentList();
            {
                GridPane pane = new GridPane();
                ColumnConstraints title = new ColumnConstraints();
                ColumnConstraints value = new ColumnConstraints();
                pane.getColumnConstraints().setAll(title, value);
                value.setFillWidth(true);
                value.setHgrow(Priority.ALWAYS);
                pane.setHgap(16);
                pane.setVgap(8);

                HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
                hintPane.setText(i18n("multiplayer.nat.hint"));
                GridPane.setColumnSpan(hintPane, 2);
                pane.addRow(0, hintPane);

                Label natResult = new Label();
                natResult.textProperty().bind(BindingMapping.of(getSkinnable().natStateProperty())
                        .map(MultiplayerPageSkin::getNATType));
                pane.addRow(1, new Label(i18n("multiplayer.nat.type")), natResult);

                natDetectionPane.getContent().add(pane);
            }

            ComponentList thanksPane = new ComponentList();
            {
                Label label = new Label(i18n("multiplayer.powered_by"));

                thanksPane.getContent().add(label);
            }

            content.getChildren().setAll(
                    hint,
                    ComponentList.createComponentListTitle(i18n("multiplayer.session")),
                    roomPane,
                    ComponentList.createComponentListTitle(i18n("multiplayer.nat")),
                    natDetectionPane,
                    ComponentList.createComponentListTitle(i18n("about.thanks_to")),
                    thanksPane
            );
        }
    }

    private static String getNATType(DiscoveryInfo info) {
        if (info == null) {
            return i18n("multiplayer.nat.testing");
        } else if (info.isBlockedUDP()) {
            return i18n("multiplayer.nat.type.blocked_udp");
        } else if (info.isFullCone()) {
            return i18n("multiplayer.nat.type.full_cone");
        } else if (info.isOpenAccess()) {
            return i18n("multiplayer.nat.type.open_access");
        } else if (info.isPortRestrictedCone()) {
            return i18n("multiplayer.nat.type.port_restricted_cone");
        } else if (info.isRestrictedCone()) {
            return i18n("multiplayer.nat.type.restricted_cone");
        } else if (info.isSymmetric()) {
            return i18n("multiplayer.nat.type.symmetric");
        } else if (info.isSymmetricUDPFirewall()) {
            return i18n("multiplayer.nat.type.symmetric_udp_firewall");
        } else {
            return i18n("multiplayer.nat.type.unknown");
        }
    }
}
