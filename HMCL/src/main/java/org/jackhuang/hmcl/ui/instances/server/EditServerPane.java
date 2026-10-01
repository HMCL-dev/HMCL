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
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.server.ServerStatus;
import org.jackhuang.hmcl.server.ServerStatusPinger;
import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.DialogAware;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.setValidateWhileTextChanged;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class EditServerPane extends TransitionPane implements DialogAware {
    private final Consumer<ServerListPage.IconedServer> handleCallback;

    private final ServerListPage.IconedServer reference;
    private final Type type;
    private final GridPane body = new GridPane();
    private final JFXTextField txtServerName = new JFXTextField();
    private final JFXTextField txtServerIP = new JFXTextField();

    private final JFXButton btnAccept = new JFXButton();
    private final JFXButton btnCancel = new JFXButton();
    private final SpinnerPane spinner = new SpinnerPane();
    private final Label lblErrorMessage = new Label();

    public EditServerPane(Type type, @Nullable ServerListPage.IconedServer reference, Consumer<ServerListPage.IconedServer> handleCallback) {
        this.type = type;
        this.reference = reference;
        this.handleCallback = handleCallback;

        getStyleClass().add("skin-pane");
        if (reference != null) {
            initPaneContents(reference.getName(), reference.getIp());
        } else {
            initPaneContents("", "");
        }
    }

    private void initPaneContents(String serverName, String serverIP) {
        body.setDisable(false);
        body.getChildren().clear();
        body.getColumnConstraints().clear();
        lblErrorMessage.setText("");

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

            txtServerName.setPromptText(i18n("server.name.def"));
            txtServerName.setTextFormatter(new TextFormatter<>(change -> {
                if (change.getControlNewText().length() <= 32) {
                    return change;
                }
                return null;
            }));
            body.add(txtServerName, 1, 0);

            if (serverName != null) {
                txtServerName.setText(serverName);
            }
        }

        // server ip
        {
            Label label = new Label();
            label.setText(i18n("server.ip"));
            GridPane.setHalignment(label, HPos.LEFT);
            body.add(label, 0, 1);

            txtServerIP.setValidators(new RequiredValidator());
            setValidateWhileTextChanged(txtServerIP, true);
            body.add(txtServerIP, 1, 1);

            if (serverIP != null) {
                txtServerIP.setText(serverIP);
            }
        }

        bodyVbox.getChildren().add(body);
        rootLayout.setBody(bodyVbox);

        btnAccept.setText(switch (type) {
            case EDIT -> i18n("server.manage.edit.accept");
            case ADD -> i18n("server.manage.add.accept");
        });
        btnAccept.getStyleClass().add("dialog-accept");
        btnAccept.setOnAction(e -> onAdd());
        btnAccept.disableProperty().bind(new BooleanBinding() {
            {
                bind(txtServerIP.textProperty());
            }

            @Override
            protected boolean computeValue() {
                return !txtServerIP.validate();
            }
        });

        lblErrorMessage.setWrapText(true);
        lblErrorMessage.setMaxWidth(400);

        btnCancel.setText(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());
        onEscPressed(this, btnCancel::fire);

        spinner.getStyleClass().add("small-spinner-pane");
        spinner.setContent(btnAccept);

        HBox actions = new HBox(spinner, btnCancel);
        actions.setAlignment(Pos.CENTER_RIGHT);

        rootLayout.setActions(lblErrorMessage, actions);
    }

    private void onAdd() {
        spinner.showSpinner();
        String serverName;
        String serverIP = txtServerIP.getText();
        if (serverIP == null) return;
        if (txtServerName.getText() == null || txtServerName.getText().isEmpty()) {
            serverName = i18n("server.name.def");
        } else {
            serverName = txtServerName.getText();
        }

        body.setDisable(true);

        Task.supplyAsync(Schedulers.io(), () ->
                ServerStatusPinger.getStatus(serverIP)
        ).whenComplete(Schedulers.javafx(), (result, ignored) -> {

            ServerStatus status = result.getIfSucceed();
            if (status == null) {
                spinner.hideSpinner();
                btnAccept.setText(switch (type) {
                    case EDIT -> i18n("server.manage.edit.still");
                    case ADD -> i18n("server.manage.add.still");
                });
                btnAccept.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    if (reference != null) {
                        handleCallback.accept(reference.withIpAndName(serverIP, serverName));
                    } else {
                        handleCallback.accept(new ServerListPage.IconedServer(false, false, null, serverIP, serverName));
                    }
                });
                btnCancel.setOnAction(e -> {
                    initPaneContents(serverName, serverIP);
                });
                ServerStatusResult.FailureResult.Reason reason = result.getFailureReasonIfFailed();
                lblErrorMessage.setText(switch (Objects.requireNonNull(reason)) {
                    case EXCEPTION -> i18n("server.manage.status.error");
                    case UNKNOWN_HOST -> i18n("server.manage.status.error.unknownhost");
                    case BLOCKED_BY_MOJANG -> i18n("server.manage.status.error.blocked");
                });
            } else {
                fireEvent(new DialogCloseEvent());
                if (reference != null) {
                    handleCallback.accept(reference.withIpAndName(serverIP, serverName));
                } else {
                    handleCallback.accept(new ServerListPage.IconedServer(false, false, status.favicon(), serverIP, serverName));
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
        txtServerName.requestFocus();
    }
}
