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

import de.javawi.jstun.test.DiscoveryInfo;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
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
        root.setPadding(new Insets(10));
        getChildren().setAll(root);
        {
            VBox roomPane = new VBox();
            {
                AdvancedListItem createRoomItem = new AdvancedListItem();
                createRoomItem.setTitle(i18n("multiplayer.room.create"));
                createRoomItem.setLeftGraphic(wrap(SVG::plusCircleOutline));
                createRoomItem.setOnAction(e -> FXUtils.openLink(""));

                AdvancedListItem joinRoomItem = new AdvancedListItem();
                joinRoomItem.setTitle(i18n("multiplayer.room.join"));
                joinRoomItem.setLeftGraphic(wrap(SVG::accountArrowRightOutline));
                joinRoomItem.setOnAction(e -> FXUtils.openLink(""));

                AdvancedListItem copyLinkItem = new AdvancedListItem();
                copyLinkItem.setTitle(i18n("multiplayer.room.copy_room_code"));
                copyLinkItem.setLeftGraphic(wrap(SVG::accountArrowRightOutline));
                copyLinkItem.setOnAction(e -> FXUtils.openLink(""));

                AdvancedListItem quitItem = new AdvancedListItem();
                quitItem.setTitle(i18n("multiplayer.room.quit"));
                quitItem.setLeftGraphic(wrap(SVG::closeCircle));
                quitItem.setOnAction(e -> FXUtils.openLink(""));

                AdvancedListItem closeRoomItem = new AdvancedListItem();
                closeRoomItem.setTitle(i18n("multiplayer.room.quit"));
                closeRoomItem.setLeftGraphic(wrap(SVG::closeCircle));
                closeRoomItem.setOnAction(e -> FXUtils.openLink(""));

                FXUtils.onChangeAndOperate(getSkinnable().multiplayerStateProperty(), state -> {
                    if (state == MultiplayerManager.State.DISCONNECTED) {
                        roomPane.getChildren().setAll(createRoomItem, joinRoomItem);
                    } else if (state == MultiplayerManager.State.MASTER) {
                        roomPane.getChildren().setAll(copyLinkItem);
                        roomPane.getChildren().setAll(closeRoomItem);
                    } else if (state == MultiplayerManager.State.SLAVE) {
                        roomPane.getChildren().setAll(copyLinkItem);
                        roomPane.getChildren().setAll(quitItem);
                    }
                });
            }

            AdvancedListBox sideBar = new AdvancedListBox()
                    .startCategory("multiplayer.room")
                    .add(roomPane)
                    .startCategory("help")
                    .addNavigationDrawerItem(settingsItem -> {
                        settingsItem.setTitle(i18n("help"));
                        settingsItem.setLeftGraphic(wrap(SVG.gamepad(null, 20, 20)));
                        settingsItem.setOnAction(e -> FXUtils.openLink(""));
                    });
            FXUtils.setLimitWidth(sideBar, 200);
            root.setLeft(sideBar);
        }

        {
            VBox content = new VBox(16);
            content.setFillWidth(true);
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            root.setCenter(scrollPane);

            ComponentList roomPane = new ComponentList();
            {
                VBox pane = new VBox();

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

                HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFORMATION);
                hintPane.setText(i18n("multiplayer.nat.hint"));
                GridPane.setColumnSpan(hintPane, 2);
                pane.addRow(0, hintPane);

                Label natResult = new Label();
                natResult.textProperty().bind(BindingMapping.of(getSkinnable().natStateProperty())
                        .map(MultiplayerPageSkin::getNATType));
                pane.addRow(1, new Label(i18n("multiplayer.nat.type")), natResult);

//                Label natResult = new Label();
//                natResult.textProperty().bind(BindingMapping.of(getSkinnable().natStateProperty())
//                        .map(MultiplayerPageSkin::getNATType));
//                pane.addRow(1, new Label(i18n("multiplayer.nat.latency")), natResult);

                natDetectionPane.getContent().add(pane);
            }

            content.getChildren().setAll(
                    ComponentList.createComponentListTitle(i18n("multiplayer.room")),
                    roomPane,
                    ComponentList.createComponentListTitle(i18n("multiplayer.nat")),
                    natDetectionPane
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
