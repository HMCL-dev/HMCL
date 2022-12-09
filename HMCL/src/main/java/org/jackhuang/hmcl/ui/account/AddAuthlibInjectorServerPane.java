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
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.DialogAware;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class AddAuthlibInjectorServerPane extends TransitionPane implements DialogAware {

    private final Label lblServerUrl;
    private final Label lblServerName;
    private final Label lblCreationWarning;
    private final Label lblServerWarning;
    private final JFXTextField txtServerUrl;
    private final JFXDialogLayout addServerPane;
    private final JFXDialogLayout confirmServerPane;
    private final SpinnerPane nextPane;
    private final JFXButton btnAddNext;

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
            txtServerUrl = new JFXTextField();
            txtServerUrl.setPromptText(i18n("account.injector.server_url"));
            txtServerUrl.setOnAction(e -> onAddNext());

            lblCreationWarning = new Label();
            lblCreationWarning.setWrapText(true);
            HBox actions = new HBox();
            {
                JFXButton cancel = new JFXButton(i18n("button.cancel"));
                cancel.getStyleClass().add("dialog-accept");
                cancel.setOnAction(e -> onAddCancel());

                nextPane = new SpinnerPane();
                nextPane.getStyleClass().add("small-spinner-pane");
                btnAddNext = new JFXButton(i18n("wizard.next"));
                btnAddNext.getStyleClass().add("dialog-accept");
                btnAddNext.setOnAction(e -> onAddNext());
                nextPane.setContent(btnAddNext);

                actions.getChildren().setAll(cancel, nextPane);
            }

            addServerPane.setBody(txtServerUrl);
            addServerPane.setActions(lblCreationWarning, actions);
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
                lblServerWarning.setStyle("-fx-text-fill: red;");
                GridPane.setColumnIndex(lblServerWarning, 0);
                GridPane.setRowIndex(lblServerWarning, 2);
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

            JFXButton prevButton = new JFXButton(i18n("wizard.prev"));
            prevButton.getStyleClass().add("dialog-cancel");
            prevButton.setOnAction(e -> onAddPrev());

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.getStyleClass().add("dialog-cancel");
            cancelButton.setOnAction(e -> onAddCancel());

            JFXButton finishButton = new JFXButton(i18n("wizard.finish"));
            finishButton.getStyleClass().add("dialog-accept");
            finishButton.setOnAction(e -> onAddFinish());

            confirmServerPane.setBody(body);
            confirmServerPane.setActions(prevButton, cancelButton, finishButton);
        }

        this.setContent(addServerPane, ContainerAnimations.NONE.getAnimationProducer());

        lblCreationWarning.maxWidthProperty().bind(((FlowPane) lblCreationWarning.getParent()).widthProperty());
        btnAddNext.disableProperty().bind(txtServerUrl.textProperty().isEmpty());
        nextPane.hideSpinner();

        onEscPressed(this, this::onAddCancel);
    }

    @Override
    public void onDialogShown() {
        txtServerUrl.requestFocus();
    }

    private String resolveFetchExceptionMessage(Throwable exception) {
        if (exception instanceof IOException) {
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

        nextPane.showSpinner();
        addServerPane.setDisable(true);

        Task.runAsync(() -> {
            serverBeingAdded = AuthlibInjectorServer.locateServer(url);
        }).whenComplete(Schedulers.javafx(), exception -> {
            addServerPane.setDisable(false);
            nextPane.hideSpinner();

            if (exception == null) {
                lblServerName.setText(serverBeingAdded.getName());
                lblServerUrl.setText(serverBeingAdded.getUrl());

                lblServerWarning.setVisible("http".equals(NetworkUtils.toURL(serverBeingAdded.getUrl()).getProtocol()));

                this.setContent(confirmServerPane, ContainerAnimations.SWIPE_LEFT.getAnimationProducer());
            } else {
                LOG.log(Level.WARNING, "Failed to resolve auth server: " + url, exception);
                lblCreationWarning.setText(resolveFetchExceptionMessage(exception));
            }
        }).start();

    }

    private void onAddPrev() {
        this.setContent(addServerPane, ContainerAnimations.SWIPE_RIGHT.getAnimationProducer());
    }

    private void onAddFinish() {
        if (!config().getAuthlibInjectorServers().contains(serverBeingAdded)) {
            config().getAuthlibInjectorServers().add(serverBeingAdded);
        }
        fireEvent(new DialogCloseEvent());
    }

}
