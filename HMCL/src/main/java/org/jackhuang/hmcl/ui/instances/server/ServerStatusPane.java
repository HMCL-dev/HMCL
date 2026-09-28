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

import com.google.gson.JsonPrimitive;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.server.ServerStatus;
import org.jackhuang.hmcl.server.ServerStatusGetter;
import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.util.MinecraftChatComponentUtils;

import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ServerStatusPane extends TransitionPane implements DialogAware {
    private final ServerListPage.IconedServer iconedServer;
    private final SpinnerPane refreshSpinner = new SpinnerPane();
    private final JFXDialogLayout rootLayout = new JFXDialogLayout();
    private final Label lblErrorMessage = new Label();
    private final ReadOnlyObjectWrapper<ServerStatusResult> serverStatusResultWrapper;
    private final ChangeListener<ServerStatusResult> listener = (ignored0, ignored1, newResult) -> {
        replaceBody();
    };

    public ServerStatusPane(ReadOnlyObjectWrapper<ServerStatusResult> reference, ServerListPage.IconedServer iconedServer) {
        this(reference, iconedServer, false);
    }

    public ServerStatusPane(ReadOnlyObjectWrapper<ServerStatusResult> reference, ServerListPage.IconedServer iconedServer, boolean fromDnD) {
        this.iconedServer = iconedServer;
        this.serverStatusResultWrapper = Objects.requireNonNullElseGet(reference, () -> new ReadOnlyObjectWrapper<>(null));

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

        lblErrorMessage.setWrapText(true);
        lblErrorMessage.setMaxWidth(400);
        rootLayout.getActions().addAll(lblErrorMessage, actions);

        replaceBody();
        serverStatusResultWrapper.addListener(new WeakChangeListener<>(listener));
        refreshIfNoStatus();
    }

    private static VBox createStatCell(String titleText, String valueText, boolean showDivider) {
        VBox cell = new VBox(3);
        cell.getStyleClass().add("server-status-stat-cell");
        if (!showDivider) {
            cell.getStyleClass().add("server-status-stat-cell-last");
        }
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setPadding(new Insets(10, 12, 10, 12));
        cell.setPrefWidth(140);

        Label title = new Label(titleText);
        title.getStyleClass().add("server-status-stat-title");

        Label value = new Label(valueText == null || valueText.isBlank() ? "—" : valueText);
        value.getStyleClass().add("server-status-stat-value");
        value.setWrapText(true);
        value.setMaxWidth(100);

        cell.getChildren().addAll(title, value);
        return cell;
    }

    public void replaceBody() {
        ServerStatusResult statusResult = serverStatusResultWrapper.get();
        ServerStatus status;

        if (statusResult == null) {
            status = null;
        } else {
            status = statusResult.getIfSuccess();
        }

        Image serverIcon = iconedServer.iconImage;
        if (status != null) {
            serverIcon = ServerListPage.IconedServer.parseImageOrDefault(status.favicon());
        }

        refreshSpinner.hideSpinner();
        if (statusResult != null) {
            if (statusResult.exceptionIfFailure() != null) {
                lblErrorMessage.setText(i18n("servers.manager.error.status"));
            } else {
                lblErrorMessage.setText("");
            }
        }

        VBox root = new VBox();
        root.setSpacing(10);
        HBox serverInfoBox = new HBox();
        VBox.setMargin(serverInfoBox, new Insets(10, 0, 0, 5));

        ImageContainer imageView = new ImageContainer(64);
        imageView.setImage(serverIcon);

        StackPane canvasPane = new StackPane(imageView);
        canvasPane.setPrefWidth(64);
        canvasPane.setPrefHeight(64);
        serverInfoBox.getChildren().add(canvasPane);

        GridPane textPane = new GridPane();
        textPane.setAlignment(Pos.CENTER_LEFT);

        textPane.setHgap(10);
        textPane.setVgap(10);

        serverInfoBox.getChildren().add(textPane);
        HBox.setMargin(textPane, new Insets(0, 0, 0, 20));

        Label label = new Label(i18n("servers.manager.server.name") + ":");
        textPane.add(label, 0, 0);
        textPane.add(new Label(iconedServer.getName()), 1, 0);

        textPane.add(new Label(i18n("servers.manager.server.ip") + ":"), 0, 1);
        textPane.add(new Label(iconedServer.getIp()), 1, 1);

        root.getChildren().add(serverInfoBox);
        if (status != null) {
            VBox motdBox = new VBox(5);
            motdBox.getStyleClass().add("server-status-motd-box");
            motdBox.setPadding(new Insets(10, 12, 10, 12));
            motdBox.setMaxWidth(620);

            Label motdTitle = new Label(i18n("servers.manager.server.motd"));
            motdTitle.getStyleClass().add("server-status-motd-title");
            motdBox.getChildren().add(motdTitle);

            String motd = MinecraftChatComponentUtils.toPlainStringFromChatComponent(status.description());
            String[] motdLines = motd.split("\\r?\\n");
            for (int i = 0; i < Math.min(motdLines.length, 2); i++) {
                String line = motdLines[i];
                if (line.length() > 80) {
                    line = line.substring(0, 77).trim() + "...";
                }
                Label lineLabel = new Label(line);
                lineLabel.getStyleClass().add("server-status-motd-line");
                lineLabel.setMaxWidth(560);
                lineLabel.setWrapText(true);
                motdBox.getChildren().add(lineLabel);
            }

            root.getChildren().add(motdBox);

            HBox detailRow = new HBox();
            detailRow.getStyleClass().add("server-status-detail-row");
            detailRow.setSpacing(0);
            detailRow.setMaxWidth(620);

            var cells = List.of(
                    createStatCell(i18n("servers.manager.server.type"), status.modInfo() != null ? i18n("servers.manager.server.modtypeinfo", status.modInfo().type(), status.modInfo().modList().size()) : i18n("servers.manager.server.vanilla"), true),
                    createStatCell(i18n("servers.manager.server.players"), String.format("%,d/%,d", status.players().online(), status.players().max()), true),
                    createStatCell(i18n("servers.manager.server.version"), MinecraftChatComponentUtils.toPlainStringFromChatComponent(new JsonPrimitive(status.version().name())) + "(" + status.version().version() + ")", true),
                    createStatCell(i18n("servers.manager.server.latency"), String.format("%,dms", status.networkLatency()), false)
            );
            detailRow.getChildren().addAll(cells);

            root.getChildren().add(detailRow);
        }

        rootLayout.setBody(root);
    }

    private void refreshIfNoStatus() {
        if (serverStatusResultWrapper.get() == null) refresh();
    }

    private void refresh() {
        lblErrorMessage.setText("");
        refreshSpinner.showSpinner();

        Task.supplyAsync(Schedulers.io(), () ->
                ServerStatusGetter.getStatus(iconedServer.getIp())
        ).whenComplete(Schedulers.javafx(), (result, ignored) -> {
            serverStatusResultWrapper.set(result);
        }).start();
    }

    private void onClose() {
        fireEvent(new DialogCloseEvent());
    }
}
