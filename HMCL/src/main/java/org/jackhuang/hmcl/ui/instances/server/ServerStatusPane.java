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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.server.ServerStatus;
import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.util.MinecraftChatComponentUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ServerStatusPane extends TransitionPane implements DialogAware {
    private final ServerListPage.IconedServer iconedServer;
    private final SpinnerPane refreshSpinner = new SpinnerPane();
    private final JFXDialogLayout rootLayout = new JFXDialogLayout();
    private final Label lblErrorMessage = new Label();
    private final ObservableServerStatus observableServerStatus;
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    public ServerStatusPane(ObservableServerStatus reference, ServerListPage.IconedServer iconedServer) {
        this(reference, iconedServer, false);
    }

    public ServerStatusPane(ObservableServerStatus reference, ServerListPage.IconedServer iconedServer, boolean fromDnD) {
        this.iconedServer = iconedServer;
        this.observableServerStatus = Objects.requireNonNullElseGet(reference, () -> new ObservableServerStatus(iconedServer.getIp()));

        getStyleClass().add("skin-pane");
        getChildren().setAll(rootLayout);
        rootLayout.setHeading(new Label(i18n("server.manage.status.head")));

        {
            JFXButton refreshBtn = new JFXButton(i18n("button.refresh"));
            refreshBtn.getStyleClass().add("dialog-refresh");
            refreshBtn.setOnAction(e -> observableServerStatus.refreshAsync(true));
            refreshSpinner.getStyleClass().add("small-spinner-pane");
            refreshSpinner.setContent(refreshBtn);
        }

        HBox actions = new HBox(refreshSpinner);
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (fromDnD) {
            // add to current instance

            JFXButton addToInstallBtn = new JFXButton(i18n("server.dnd.add"));
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

                    Controllers.confirmAction(i18n("server.dnd.add.instanceempty.desc"), i18n("server.dnd.add.instanceempty"),
                            MessageDialogPane.MessageType.ERROR,
                            gotoDownload,
                            null);
                } else {
                    if (Controllers.getGameInstancePage().getSelectedTab() instanceof ServerListPage listPage) {
                        listPage.addServer(iconedServer);
                    } else {
                        Instances.modifyServerList(selectedInstance);
                        if (Controllers.getGameInstancePage().getSelectedTab() instanceof ServerListPage listPage) {
                            listPage.addServer(iconedServer);
                        }
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

        applyResult(observableServerStatus.resultProperty().get());
        listenerHolder.add(FXUtils.onWeakChangeAndOperate(observableServerStatus.resultProperty(), this::applyResult));

        applyPinging(observableServerStatus.pingingProperty().get());
        listenerHolder.add(FXUtils.onWeakChangeAndOperate(observableServerStatus.pingingProperty(), this::applyPinging));

        observableServerStatus.refreshIfNoResultOrFailedAsync(true);
    }

    public void applyPinging(boolean pinging) {
        if (pinging) {
            lblErrorMessage.setText("");
            refreshSpinner.showSpinner();
        } else {
            refreshSpinner.hideSpinner();
        }
    }

    private static Node createDescriptionRow(String title, String content, @Nullable String tooltip, Insets inVBoxInsets) {
        BorderPane row = new BorderPane();
        VBox.setMargin(row, inVBoxInsets);
        row.setLeft(new Label(title));
        Label right = new Label(content);
        if (tooltip != null) {
            FXUtils.installFastTooltip(right, tooltip);
        }
        row.setRight(right);
        return row;
    }

    public void applyResult(ServerStatusResult statusResult) {
        ServerStatus status;

        if (statusResult == null) {
            // pinging..., continue
            status = null;
        } else {
            // ping failed
            status = statusResult.getIfSucceed();
            if (status == null) {
                ServerStatusResult.FailureResult.Reason reason = statusResult.getFailureReasonIfFailed();
                lblErrorMessage.setText(switch (Objects.requireNonNull(reason)) {
                    case EXCEPTION -> i18n("server.manage.status.error");
                    case UNKNOWN_HOST -> i18n("server.manage.status.error.unknownhost");
                    case BLOCKED_BY_MOJANG -> i18n("server.manage.status.error.blocked");
                });
            }
        }

        Image serverIcon = iconedServer.iconImage;
        if (status != null) {
            serverIcon = ServerListPage.IconedServer.parseImageOrDefault(status.favicon());
        }

        VBox rootContent = new VBox();
        rootContent.setSpacing(10);
        rootLayout.setBody(rootContent);

        HBox titleContainer = new HBox();
        titleContainer.setSpacing(8);
        titleContainer.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(titleContainer, new Insets(12, 0, 0, 0));
        rootContent.getChildren().add(titleContainer);

        ImageContainer imageView = new ImageContainer(64);
        imageView.setImage(serverIcon);
        titleContainer.getChildren().add(imageView);

        VBox titleContent = new VBox();
        titleContent.setAlignment(Pos.CENTER_LEFT);
        titleContent.setSpacing(8);
        HBox.setMargin(titleContent, new Insets(0, 0, 0, 12));
        titleContainer.getChildren().add(titleContent);

        TwoLineListItem title = new TwoLineListItem();
        title.getTitleLabel().setWrapText(true);
        title.setTitle(iconedServer.getName());
        titleContent.getChildren().add(title);

        ServerAddressMaskPane addressMaskPane = new ServerAddressMaskPane(iconedServer.getIp());
        addressMaskPane.labelAddStyleClass("subtitle");
        titleContent.getChildren().add(addressMaskPane);

        if (status != null) {
            VBox motdContentBox = new VBox();
            motdContentBox.setSpacing(5);
            motdContentBox.setAlignment(Pos.CENTER_LEFT);

            VBox.setMargin(motdContentBox, new Insets(4, 0, 0, 4));
            rootContent.getChildren().add(motdContentBox);

            String[] motdLines = MinecraftChatComponentUtils
                    .toPlainStringFromChatComponent(status.description())
                    .split("\\r?\\n");
            for (int i = 0; i < Math.min(motdLines.length, 2); i++) {
                String line = motdLines[i].trim();
                if (line.length() > 80) {
                    line = line.substring(0, 77).trim() + "...";
                }
                Label lineLabel = new Label(line);
                lineLabel.setMaxWidth(560);
                lineLabel.setWrapText(true);
                motdContentBox.getChildren().add(lineLabel);
            }

            Separator titleEndSeparator = new Separator();
            titleEndSeparator.setMaxWidth(Double.MAX_VALUE);
            titleEndSeparator.setPadding(new Insets(4, 0, 0, 0));
            rootContent.getChildren().add(titleEndSeparator);

            String playersTooltip = null;
            if (!status.players().samples().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.join("\n", status.players().samples().stream()
                        .map(sample -> {
                            if (ServerStatus.Players.Sample.ANONYMOUS_PLAYER.equals(sample)) {
                                return i18n("server.anonymous_player");
                            }
                            return MinecraftChatComponentUtils.toPlainStringFromChatComponent(new JsonPrimitive(sample.name()));
                        })
                        .toList())
                );

                if (status.players().samples().size() < status.players().online()) {
                    sb.append("\n");
                    sb.append(i18n("server.and_more_players", String.valueOf(status.players().online() - status.players().samples().size())));
                }

                playersTooltip = sb.toString();
            }

            String modsTooltip = null;
            int showCount = 15;
            if (status.modInfo() != null && !status.modInfo().modList().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.join("\n", status.modInfo().modList().stream()
                        .limit(showCount)
                        .map(it -> it.modId() + " - " + it.version())
                        .toList())
                );
                if (showCount < status.modInfo().modList().size()) {
                    sb.append("\n");
                    sb.append(i18n("server.and_more_mods", String.valueOf(status.modInfo().modList().size() - showCount)));
                }

                modsTooltip = sb.toString();
            }

            rootContent.getChildren().add(createDescriptionRow(i18n("server.type"), status.modInfo() != null ? i18n("server.type.mod", status.modInfo().type(), status.modInfo().modList().size()) : i18n("server.type.vanilla"), modsTooltip, new Insets(0, 4, 0, 4)));
            rootContent.getChildren().add(createDescriptionRow(i18n("server.onlineplayers"), String.format("%,d/%,d", status.players().online(), status.players().max()), playersTooltip, new Insets(0, 4, 0, 4)));
            if (status.version() != null) {
                rootContent.getChildren().add(createDescriptionRow(i18n("server.playversion"), MinecraftChatComponentUtils.toPlainStringFromChatComponent(new JsonPrimitive(status.version().name())) + "(" + status.version().version() + ")", null, new Insets(0, 4, 0, 4)));
            }
            rootContent.getChildren().add(createDescriptionRow(i18n("server.latency"), String.format("%,dms", status.networkLatency()), null, new Insets(0, 4, 0, 4)));
        }

    }

    private void onClose() {
        fireEvent(new DialogCloseEvent());
    }
}
