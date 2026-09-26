/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances.server;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.server.ServerStatus;
import org.jackhuang.hmcl.server.ServerStatusGetter;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ServerStatusPane extends TransitionPane implements DialogAware {
    private final ServerListPage.IconedServer iconedServer;
    private final SpinnerPane refreshSpinner = new SpinnerPane();
    private final JFXDialogLayout rootLayout = new JFXDialogLayout();
    private final Label lblErrorMessage = new Label();

    private @Nullable ServerStatus status;

    public ServerStatusPane(ServerListPage.IconedServer iconedServer) {
        this(iconedServer, false);
    }

    public ServerStatusPane(ServerListPage.IconedServer iconedServer, boolean fromDnD) {
        this.iconedServer = iconedServer;

        getStyleClass().add("skin-pane");
        getChildren().setAll(rootLayout);
        rootLayout.setHeading(new Label(i18n("servers.manager.status.head")));

        {
            JFXButton refreshBtn = new JFXButton(i18n("button.refresh"));
            refreshBtn.getStyleClass().add("dialog-refresh");
            refreshBtn.setOnAction(e -> refresh());
            refreshSpinner.getStyleClass().add("small-spinner-pane");
            refreshSpinner.setContent(refreshBtn);
        }

        HBox actions = new HBox(refreshSpinner);
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (fromDnD) {
            // add to current instance

            JFXButton addToInstallBtn = new JFXButton(i18n("servers.dnd.add"));
            addToInstallBtn.getStyleClass().add("dialog-accept");
            addToInstallBtn.setOnAction(ignored0 -> {
                fireEvent(new DialogCloseEvent());
                // open server manager
                HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
                HMCLGameInstance selectedInstance = repository.getSelectedInstance();
                if (selectedInstance == null) {
                    JFXButton gotoDownload = new JFXButton(i18n("instance.empty.launch.goto_download"));
                    gotoDownload.getStyleClass().add("dialog-accept");
                    gotoDownload.setOnAction(ignored1 -> Controllers.navigate(Controllers.getDownloadPage()));

                    Controllers.confirmAction(i18n("servers.dnd.add.instanceempty.desc"), i18n("servers.dnd.add.instanceempty"),
                            MessageDialogPane.MessageType.ERROR,
                            gotoDownload,
                            null);
                } else {
                    Instances.modifyServerList(selectedInstance);
                    if (Controllers.getGameInstancePage().getSelectedTab() instanceof ServerListPage listPage) {
                        listPage.addServer(iconedServer);
                    }
                }
            });
            actions.getChildren().add(addToInstallBtn);
        }

        JFXButton launchBtn = new JFXButton(i18n("instance.launch"));
        launchBtn.getStyleClass().add("dialog-accept");
        launchBtn.setOnAction(e -> {
            fireEvent(new DialogCloseEvent());
            HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
            HMCLGameInstance selectedInstance = repository.getSelectedInstance();
            Instances.launchAndEnterServer(selectedInstance, iconedServer.getIp());
        });
        actions.getChildren().add(launchBtn);

        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.getStyleClass().add("dialog-cancel");
        cancelBtn.setOnAction(e -> onClose());
        onEscPressed(this, cancelBtn::fire);
        actions.getChildren().add(cancelBtn);

        replaceBody();

        lblErrorMessage.setWrapText(true);
        lblErrorMessage.setMaxWidth(400);
        rootLayout.getActions().addAll(lblErrorMessage, actions);


        refresh();
    }

    public void replaceBody() {
        HBox contentBox = new HBox();
        setMargin(contentBox, new Insets(20, 0, 0, 0));

        Image serverIcon = iconedServer.iconImage;
        if (status != null) {
            serverIcon = ServerListPage.IconedServer.parseImageOrDefault(status.favicon());
        }

        int iconScale = 4;
        ImageContainer imageView = new ImageContainer(32);
        imageView.setImage(serverIcon);

        imageView.setScaleX(iconScale);
        imageView.setScaleY(iconScale);

        StackPane canvasPane = new StackPane(imageView);
        canvasPane.setPrefWidth(iconScale * 32);
        canvasPane.setPrefHeight(iconScale * 32);
        contentBox.getChildren().add(canvasPane);

        GridPane textPane = new GridPane();
        textPane.setAlignment(Pos.TOP_LEFT);

        textPane.setHgap(10);
        textPane.setVgap(10);

        contentBox.getChildren().add(textPane);
        HBox.setMargin(textPane, new Insets(15, 0, 0, 20));

        int rows = 0;
        textPane.add(new Label(i18n("servers.manager.server.name") + ":"), 0, rows);
        textPane.add(new Label(iconedServer.getName()), 1, rows);

        rows++;
        textPane.add(new Label(i18n("servers.manager.server.ip") + ":"), 0, rows);
        textPane.add(new Label(iconedServer.getIp()), 1, rows);

        ServerStatus cachedServerStatus = status;
        if (cachedServerStatus != null) {

            rows++;
            textPane.add(new Label(i18n("servers.manager.server.latency") + ":"), 0, rows);
            textPane.add(new Label(String.format("%,dms", cachedServerStatus.networkLatency())), 1, rows);

            rows++;
            textPane.add(new Label(i18n("servers.manager.server.players") + ":"), 0, rows);
            textPane.add(new Label(String.format("%,d/%,d", cachedServerStatus.playerOnline(), cachedServerStatus.playerMax())), 1, rows);
        }

        rootLayout.setBody(contentBox);
    }

    private void refresh() {
        lblErrorMessage.setText("");
        refreshSpinner.showSpinner();
        Task.supplyAsync(Schedulers.io(), () ->
                ServerStatusGetter.getStatus(iconedServer.getIp())
        ).whenComplete(Schedulers.javafx(), (result, ignored) -> {
            refreshSpinner.hideSpinner();

            this.status = result.getIfSuccess();
            if (status == null) {
                lblErrorMessage.setText(i18n("servers.manager.error.status"));
            }

            replaceBody();
        }).start();
    }

    private void onClose() {
        fireEvent(new DialogCloseEvent());
    }
}
