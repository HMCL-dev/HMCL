/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;
import org.jackhuang.hmcl.ui.construct.DialogAware;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.loadFXML;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AddAuthlibInjectorServerPane extends StackPane implements DialogAware {

    @FXML private StackPane addServerContainer;
    @FXML private Label lblServerUrl;
    @FXML private Label lblServerName;
    @FXML private Label lblCreationWarning;
    @FXML private Label lblServerWarning;
    @FXML private JFXTextField txtServerUrl;
    @FXML private JFXDialogLayout addServerPane;
    @FXML private JFXDialogLayout confirmServerPane;
    @FXML private SpinnerPane nextPane;
    @FXML private JFXButton btnAddNext;

    private TransitionHandler transitionHandler;

    private AuthlibInjectorServer serverBeingAdded;

    public AddAuthlibInjectorServerPane(String url) {
        this();
        txtServerUrl.setText(url);
        onAddNext();
    }

    public AddAuthlibInjectorServerPane() {
        loadFXML(this, "/assets/fxml/authlib-injector-server-add.fxml");
        transitionHandler = new TransitionHandler(addServerContainer);
        transitionHandler.setContent(addServerPane, ContainerAnimations.NONE.getAnimationProducer());

        btnAddNext.disableProperty().bind(txtServerUrl.textProperty().isEmpty());
        nextPane.hideSpinner();
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

    @FXML
    private void onAddCancel() {
        fireEvent(new DialogCloseEvent());
    }

    @FXML
    private void onAddNext() {
        if (btnAddNext.isDisabled())
            return;

        lblCreationWarning.setText("");

        String url = txtServerUrl.getText();

        nextPane.showSpinner();
        addServerPane.setDisable(true);

        Task.of(() -> {
            serverBeingAdded = AuthlibInjectorServer.locateServer(url);
        }).finalized(Schedulers.javafx(), (variables, isDependentsSucceeded) -> {
            addServerPane.setDisable(false);
            nextPane.hideSpinner();

            if (isDependentsSucceeded) {
                lblServerName.setText(serverBeingAdded.getName());
                lblServerUrl.setText(serverBeingAdded.getUrl());

                lblServerWarning.setVisible("http".equals(NetworkUtils.toURL(serverBeingAdded.getUrl()).getProtocol()));

                transitionHandler.setContent(confirmServerPane, ContainerAnimations.SWIPE_LEFT.getAnimationProducer());
            } else {
                lblCreationWarning.setText(resolveFetchExceptionMessage(variables.<Exception>get("lastException")));
            }
        }).start();

    }

    @FXML
    private void onAddPrev() {
        transitionHandler.setContent(addServerPane, ContainerAnimations.SWIPE_RIGHT.getAnimationProducer());
    }

    @FXML
    private void onAddFinish() {
        if (!config().getAuthlibInjectorServers().contains(serverBeingAdded)) {
            config().getAuthlibInjectorServers().add(serverBeingAdded);
        }
        fireEvent(new DialogCloseEvent());
    }

}
