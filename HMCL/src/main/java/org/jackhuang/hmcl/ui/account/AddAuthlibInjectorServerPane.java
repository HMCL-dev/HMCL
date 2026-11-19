// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3TextField;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Lang;

import javax.net.ssl.SSLException;
import java.io.IOException;

import static org.jackhuang.hmcl.setting.SettingsManager.getAuthlibInjectorServers;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class AddAuthlibInjectorServerPane extends TransitionPane implements DialogAware {

    private final Label lblServerUrl;
    private final Label lblServerName;
    private final Label lblCreationWarning;
    private final Label lblServerWarning;
    private final M3TextField txtServerUrl;
    private final JFXDialogLayout addServerPane;
    private final JFXDialogLayout confirmServerPane;
    /// Tracks the pending server metadata request.
    private final BooleanProperty checkingServer;
    private final M3Button btnAddNext;

    private AuthlibInjectorServer serverBeingAdded;

    public AddAuthlibInjectorServerPane(String url) {
        this();
        txtServerUrl.setText(url);
        onAddNext();
    }

    public AddAuthlibInjectorServerPane() {
        addServerPane = new JFXDialogLayout();
        addServerPane.setHeading(new Label(i18n("account.injector.add")));
        {
            txtServerUrl = FXUtils.newTextField();
            txtServerUrl.setPromptText(i18n("account.injector.server_url"));
            txtServerUrl.setOnAction(e -> onAddNext());

            lblCreationWarning = new Label();
            lblCreationWarning.setWrapText(true);
            HBox actions = new HBox();
            {
                M3Button cancel = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
                cancel.setOnAction(e -> onAddCancel());

                btnAddNext = new M3Button(i18n("wizard.next"), M3ButtonVariant.TEXT);
                btnAddNext.setOnAction(e -> onAddNext());
                checkingServer = FXUtils.newButtonLoadingProperty(btnAddNext);

                actions.getChildren().setAll(cancel, btnAddNext);
            }

            VBox body = new VBox();
            body.setSpacing(8);

            lblCreationWarning.setMinWidth(0);
            lblCreationWarning.setPrefWidth(0);
            lblCreationWarning.maxWidthProperty().bind(body.widthProperty());
            lblCreationWarning.visibleProperty().bind(lblCreationWarning.textProperty().isNotEmpty());
            lblCreationWarning.managedProperty().bind(lblCreationWarning.visibleProperty());

            body.getChildren().addAll(
                FXUtils.newValidatedTextField(txtServerUrl, new RequiredValidator(), new URLValidator()),
                lblCreationWarning
            );

            addServerPane.setBody(body);
            addServerPane.setActions(actions);

            btnAddNext.disableProperty().bind(checkingServer.or(txtServerUrl.errorProperty()));
        }

        confirmServerPane = new JFXDialogLayout();
        confirmServerPane.setHeading(new Label(i18n("account.injector.add")));
        {
            GridPane body = new GridPane();
            body.setStyle("-fx-padding: 15 0 0 0;");
            body.setVgap(15);
            body.setHgap(15);
            {
                body.getColumnConstraints().setAll(
                        Lang.apply(new ColumnConstraints(), c -> c.setMaxWidth(100)),
                        new ColumnConstraints()
                );

                lblServerUrl = new Label();
                GridPane.setColumnIndex(lblServerUrl, 1);
                GridPane.setRowIndex(lblServerUrl, 0);

                lblServerName = new Label();
                GridPane.setColumnIndex(lblServerName, 1);
                GridPane.setRowIndex(lblServerName, 1);

                lblServerWarning = new Label(i18n("account.injector.http"));
                lblServerWarning.setStyle("-fx-text-fill: -monet-error;");
                GridPane.setColumnIndex(lblServerWarning, 0);
                GridPane.setRowIndex(lblServerWarning, 2);
                lblServerWarning.managedProperty().bind(lblServerWarning.visibleProperty());
                GridPane.setColumnSpan(lblServerWarning, 2);

                body.getChildren().setAll(
                        Lang.apply(new Label(i18n("account.injector.server_url")), l -> {
                            GridPane.setColumnIndex(l, 0);
                            GridPane.setRowIndex(l, 0);
                        }),
                        Lang.apply(new Label(i18n("account.injector.server_name")), l -> {
                            GridPane.setColumnIndex(l, 0);
                            GridPane.setRowIndex(l, 1);
                        }),
                        lblServerUrl, lblServerName, lblServerWarning
                );
            }

            M3Button prevButton = new M3Button(i18n("wizard.prev"), M3ButtonVariant.TEXT);
            prevButton.setOnAction(e -> onAddPrev());

            M3Button cancelButton = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
            cancelButton.setOnAction(e -> onAddCancel());

            M3Button finishButton = new M3Button(i18n("wizard.finish"), M3ButtonVariant.TEXT);
            finishButton.setOnAction(e -> onAddFinish());

            confirmServerPane.setBody(body);
            confirmServerPane.setActions(prevButton, cancelButton, finishButton);
        }

        this.setContent(addServerPane, ContainerAnimations.NONE);

        onEscPressed(this, this::onAddCancel);
    }

    @Override
    public void onDialogShown() {
        txtServerUrl.requestFocus();
    }

    private String resolveFetchExceptionMessage(Throwable exception) {
        if (exception instanceof SSLException) {
            if (exception.getMessage() != null && exception.getMessage().contains("Remote host terminated")) {
                return i18n("account.failed.connect_injector_server");
            }
            if (exception.getMessage() != null && (exception.getMessage().contains("No name matching") || exception.getMessage().contains("No subject alternative DNS name matching"))) {
                return i18n("account.failed.dns");
            }
            return i18n("account.failed.ssl");
        } else if (exception instanceof IOException) {
            return i18n("account.failed.connect_injector_server");
        } else {
            return exception.getClass().getName() + ": " + exception.getLocalizedMessage();
        }
    }

    private void onAddCancel() {
        fireEvent(new DialogCloseEvent());
    }

    private void onAddNext() {
        if (btnAddNext.isDisabled())
            return;

        lblCreationWarning.setText("");

        String url = txtServerUrl.getText();

        checkingServer.set(true);
        addServerPane.setDisable(true);

        Task.runAsync(() -> {
            serverBeingAdded = AuthlibInjectorServer.locateServer(url);
        }).whenComplete(Schedulers.javafx(), exception -> {
            addServerPane.setDisable(false);
            checkingServer.set(false);

            if (exception == null) {
                lblServerName.setText(serverBeingAdded.getName());
                lblServerUrl.setText(serverBeingAdded.getUrl());

                //noinspection HttpUrlsUsage
                lblServerWarning.setVisible(serverBeingAdded.getUrl().startsWith("http://"));

                this.setContent(confirmServerPane, ContainerAnimations.SWIPE_LEFT);
            } else {
                LOG.warning("Failed to resolve auth server: " + url, exception);
                lblCreationWarning.setText(resolveFetchExceptionMessage(exception));
            }
        }).start();

    }

    private void onAddPrev() {
        this.setContent(addServerPane, ContainerAnimations.SWIPE_RIGHT);
    }

    private void onAddFinish() {
        if (SettingsManager.isAuthlibInjectorServersReadOnly()) {
            Controllers.confirmBackupAndOverwrite(i18n("account.injector.server.storage.read_only"), () -> {
                SettingsManager.forceOverwriteAuthlibInjectorServers();
                addServerAndClose();
            });
            return;
        }

        addServerAndClose();
    }

    /// Adds the resolved authlib-injector server and closes the dialog.
    private void addServerAndClose() {
        if (!getAuthlibInjectorServers().contains(serverBeingAdded)) {
            getAuthlibInjectorServers().add(serverBeingAdded);
        }
        fireEvent(new DialogCloseEvent());
    }

}
