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
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.server.Server;
import org.jackhuang.hmcl.server.ServerStatus;
import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.server.pinger.ServerStatusPinger;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class EditServerPane extends TransitionPane implements DialogAware {
    private final Consumer<ServerListPage.IconedServer> handleCallback;

    private final ServerListPage.IconedServer reference;
    private final Type type;
    private final GridPane body = new GridPane();
    private final JFXTextField serverNameField = new JFXTextField();
    private final JFXTextField serverIpField = new JFXTextField();
    private final JFXComboBox<Server.ServerPackStatus> serverPackStatusField = new JFXComboBox<>();

    private final JFXButton btnAccept = new JFXButton();
    private final JFXButton btnCancel = new JFXButton();
    private final SpinnerPane spinner = new SpinnerPane();
    private final Label lblErrorLabel = new Label();

    private final SimpleBooleanProperty stillActionProgress = new SimpleBooleanProperty(false);

    public EditServerPane(Type type, @Nullable ServerListPage.IconedServer reference, Consumer<ServerListPage.IconedServer> handleCallback) {
        this.type = type;
        this.reference = reference;
        this.handleCallback = handleCallback;

        getStyleClass().add("skin-pane");

        String referenceServerName;
        String referenceServerIP;
        Server.ServerPackStatus referenceServerPackStatus;
        if (reference != null) {
            referenceServerName = reference.getName();
            referenceServerIP = reference.getIp();
            referenceServerPackStatus = reference.getServerPackStatus();
        } else {
            referenceServerName = null;
            referenceServerIP = null;
            referenceServerPackStatus = null;
        }

        JFXDialogLayout rootLayout = new JFXDialogLayout();
        getChildren().setAll(rootLayout);

        rootLayout.setHeading(new Label(switch (type) {
                case EDIT -> i18n("server.manage.edit.head");
                case ADD -> i18n("server.manage.add.head");
            }));

        VBox bodyVbox = new VBox();
        bodyVbox.setSpacing(8);

        VBox.setMargin(body, new Insets(20, 0, 0, 0));
        body.setVgap(22);
        body.setHgap(15);
        body.setAlignment(Pos.CENTER);

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(USE_PREF_SIZE);
        body.getColumnConstraints().add(col0);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        body.getColumnConstraints().add(col1);


        // server name
        {
            Label label = new Label();
            label.setText(i18n("server.name"));
            GridPane.setHalignment(label, HPos.LEFT);
            body.add(label, 0, 0);

            serverNameField.setPromptText(i18n("server.name.def"));
            serverNameField.setTextFormatter(new TextFormatter<>(change -> {
                if (change.getControlNewText().length() <= 32) {
                    return change;
                }
                return null;
            }));
            body.add(serverNameField, 1, 0);

            if (referenceServerName != null) {
                serverNameField.setText(referenceServerName);
            }
        }

        // server ip
        {
            Label label = new Label();
            label.setText(i18n("server.ip"));
            GridPane.setHalignment(label, HPos.LEFT);
            body.add(label, 0, 1);

            serverIpField.setValidators(
                    new RequiredValidator(),
                    new MinecraftServerAddressValidator()
            );
            setValidateWhileTextChanged(serverIpField, true);

            body.add(serverIpField, 1, 1);

            if (referenceServerIP != null) {
                serverIpField.setText(referenceServerIP);
            }

        }

        // serverPackStatusField
        {
            Label label = new Label();
            label.setText(i18n("server.resourcepack"));
            GridPane.setHalignment(label, HPos.LEFT);
            body.add(label, 0, 2);

            serverPackStatusField.setConverter(stringConverter(status -> i18n("server.resourcepack." + status.name().toLowerCase(Locale.ROOT))));
            serverPackStatusField.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null && oldVal != null) {
                    serverPackStatusField.setValue(oldVal);
                }
            });
            serverPackStatusField.getItems().setAll(Server.ServerPackStatus.values());
            body.add(serverPackStatusField, 1, 2);
            serverPackStatusField.setValue(
                    Objects.requireNonNullElse(referenceServerPackStatus, Server.ServerPackStatus.PROMPT)
            );
        }

        bodyVbox.getChildren().add(body);
        rootLayout.setBody(bodyVbox);

        btnAccept.setText(switch (type) {
            case EDIT -> i18n("server.manage.edit.accept");
            case ADD -> i18n("server.manage.add.accept");
        });
        btnAccept.getStyleClass().add("dialog-accept");
        btnAccept.disableProperty().bind(new BooleanBinding() {
            {
                bind(serverIpField.textProperty());
            }

            @Override
            protected boolean computeValue() {
                return !serverIpField.validate();
            }
        });

        lblErrorLabel.setWrapText(true);
        lblErrorLabel.setMaxWidth(400);

        btnCancel.setText(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        onEscPressed(this, btnCancel::fire);

        spinner.getStyleClass().add("small-spinner-pane");
        spinner.setContent(btnAccept);

        HBox actions = new HBox(spinner, btnCancel);
        actions.setAlignment(Pos.CENTER_RIGHT);

        rootLayout.setActions(lblErrorLabel, actions);

        btnCancel.setOnAction(e -> onCancel());
        btnAccept.setOnAction(ignored -> {
            String serverName;
            String serverIP = serverIpField.getText();
            Server.ServerPackStatus serverPackStatus = serverPackStatusField.getValue();
            if (serverIP == null) return;
            if (serverNameField.getText() == null || serverNameField.getText().isEmpty()) {
                serverName = i18n("server.name.def");
            } else {
                serverName = serverNameField.getText();
            }

            if (!stillActionProgress.get()) {
                onAdd(serverPackStatus, serverName, serverIP);
                return;
            }

            fireEvent(new DialogCloseEvent());
            if (reference != null) {
                handleCallback.accept(reference.withIpAndName(serverIP, serverName).withPackStatus(serverPackStatus));
            } else {
                handleCallback.accept(new ServerListPage.IconedServer(serverPackStatus, false, null, serverIP, serverName));
            }
        });

        ChangeListener<String> contentChangeListener = (ignored0, ignored1, ignored2) -> {
            if (stillActionProgress.get()) {
                hideStillAction();
            }
        };
//        serverNameField.textProperty().addListener(contentChangeListener);
        serverIpField.textProperty().addListener(contentChangeListener);
    }

    private void showStillAction(String errorMessage) {
        stillActionProgress.set(true);
        lblErrorLabel.setText(errorMessage);
        btnAccept.setText(switch (type) {
            case EDIT -> i18n("server.manage.edit.still");
            case ADD -> i18n("server.manage.add.still");
        });
    }

    private void hideStillAction() {
        stillActionProgress.set(false);
        lblErrorLabel.setText("");
        btnAccept.setText(switch (type) {
            case EDIT -> i18n("server.manage.edit.accept");
            case ADD -> i18n("server.manage.add.accept");
        });
    }

    private void onAdd(Server.ServerPackStatus packStatus, String serverName, String serverIP) {
        body.setDisable(true);
        spinner.showSpinner();

        Task.supplyAsync(Schedulers.io(), () ->
                ServerStatusPinger.getStatus(serverIP)
        ).whenComplete(Schedulers.javafx(), (result, ignored) -> {

            ServerStatus status = result.getIfSucceed();
            if (status == null) {
                spinner.hideSpinner();
                body.setDisable(false);

                ServerStatusResult.FailureResult.Reason reason = result.getFailureReasonIfFailed();
                showStillAction(switch (Objects.requireNonNull(reason)) {
                    case EXCEPTION -> i18n("server.manage.status.error");
                    case UNKNOWN_HOST -> i18n("server.manage.status.error.unknownhost");
                    case BLOCKED_BY_MOJANG -> i18n("server.manage.status.error.blocked");
                });
            } else {
                fireEvent(new DialogCloseEvent());
                if (reference != null) {
                    handleCallback.accept(reference.withIpAndName(serverIP, serverName).withPackStatus(packStatus));
                } else {
                    handleCallback.accept(new ServerListPage.IconedServer(packStatus, false, status.favicon(), serverIP, serverName));
                }
            }
        }).start();
    }

    private void onCancel() {
        fireEvent(new DialogCloseEvent());
    }

    public enum Type {
        EDIT,
        ADD
    }

    @Override
    public void onDialogShown() {
        serverNameField.requestFocus();
    }
}
