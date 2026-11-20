// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3TextInputVariant;
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
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class AddAuthlibInjectorServerPane extends LauncherDialog {

    private final Label lblServerUrl;
    private final Label lblServerName;
    private final Label lblCreationWarning;
    private final Label lblServerWarning;
    private final M3TextField txtServerUrl;
    /// Animates navigation between the URL form and the resolved server details.
    private final TransitionPane pages = new TransitionPane();
    /// Editable URL and validation feedback.
    private final VBox addServerPane = new VBox(8);
    /// Metadata displayed before the server is added.
    private final GridPane confirmServerPane = new GridPane();
    /// Cancels either step.
    private final M3Button cancel = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
    /// Returns to the URL form.
    private final M3Button prevButton = new M3Button(i18n("wizard.prev"), M3ButtonVariant.TEXT);
    /// Saves the confirmed server.
    private final M3Button finishButton = new M3Button(i18n("wizard.finish"), M3ButtonVariant.TEXT);
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
        getDialogPane().setHeadline(i18n("account.injector.add"));
        setFormContent(pages);
        cancel.setCancelButton(true);
        {
            txtServerUrl = FXUtils.newTextField(M3TextInputVariant.FILLED);
            txtServerUrl.setPromptText(i18n("account.injector.server_url"));
            txtServerUrl.setOnAction(e -> onAddNext());

            lblCreationWarning = new Label();
            lblCreationWarning.setWrapText(true);
            btnAddNext = new M3Button(i18n("wizard.next"), M3ButtonVariant.TEXT);
            btnAddNext.setOnAction(event -> {
                event.consume();
                onAddNext();
            });
            checkingServer = FXUtils.newButtonLoadingProperty(btnAddNext);

            lblCreationWarning.setMinWidth(0);
            lblCreationWarning.setPrefWidth(0);
            lblCreationWarning.maxWidthProperty().bind(addServerPane.widthProperty());
            lblCreationWarning.visibleProperty().bind(lblCreationWarning.textProperty().isNotEmpty());
            lblCreationWarning.managedProperty().bind(lblCreationWarning.visibleProperty());

            addServerPane.getChildren().addAll(
                FXUtils.newValidatedTextField(txtServerUrl, new RequiredValidator(), new URLValidator()),
                lblCreationWarning
            );

            btnAddNext.disableProperty().bind(checkingServer.or(txtServerUrl.errorProperty()));
        }

        {
            GridPane body = confirmServerPane;
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

            prevButton.setOnAction(event -> {
                event.consume();
                onAddPrev();
            });
            finishButton.setOnAction(event -> {
                event.consume();
                onAddFinish();
            });
        }

        pages.setContent(addServerPane, ContainerAnimations.NONE);
        getDialogPane().getActions().setAll(cancel, btnAddNext);
        setOnShown(event -> txtServerUrl.requestFocus());
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

                pages.setContent(confirmServerPane, ContainerAnimations.SWIPE_LEFT);
                getDialogPane().getActions().setAll(prevButton, cancel, finishButton);
            } else {
                LOG.warning("Failed to resolve auth server: " + url, exception);
                lblCreationWarning.setText(resolveFetchExceptionMessage(exception));
            }
        }).start();

    }

    private void onAddPrev() {
        pages.setContent(addServerPane, ContainerAnimations.SWIPE_RIGHT);
        getDialogPane().getActions().setAll(cancel, btnAddNext);
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
        closeDialog();
    }

}
