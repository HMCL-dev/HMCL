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
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.DialogAware;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.setValidateWhileTextChanged;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AddServerPane extends TransitionPane implements DialogAware {
    private final Runnable failed;
    private final Consumer<ServerListPage.IconedServer> success;

    private final GridPane body;
    private final JFXTextField txtServerName;
    private final JFXTextField txtServerIP;

    public AddServerPane(Runnable failed, Consumer<ServerListPage.IconedServer> success) {
        this.failed = failed;
        this.success = success;

        getStyleClass().add("skin-pane");

        JFXDialogLayout rootLayout = new JFXDialogLayout();
        getChildren().setAll(rootLayout);
        rootLayout.setHeading(new Label(i18n("servers.manager.add.head")));

        VBox bodyVbox = new VBox();
        bodyVbox.setSpacing(8);
        body = new GridPane();

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
            label.setText(i18n("servers.manager.add.server.name"));
            GridPane.setHalignment(label, HPos.LEFT);
            body.add(label, 0, 0);

            txtServerName = new JFXTextField();
            txtServerName.setPromptText(i18n("servers.manager.add.server.name.def"));
            body.add(txtServerName, 1, 0);
        }

        // server ip
        {
            Label label = new Label();
            label.setText(i18n("servers.manager.add.server.ip"));
            GridPane.setHalignment(label, HPos.LEFT);
            body.add(label, 0, 1);

            txtServerIP = new JFXTextField();
            txtServerIP.setValidators(new RequiredValidator());
            setValidateWhileTextChanged(txtServerIP, true);
            body.add(txtServerIP, 1, 1);
        }

        bodyVbox.getChildren().add(body);
        rootLayout.setBody(bodyVbox);

        JFXButton btnAccept = new JFXButton(i18n("servers.manager.add.accept"));
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

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());
        onEscPressed(this, btnCancel::fire);

        SpinnerPane spinner = new SpinnerPane();
        spinner.getStyleClass().add("small-spinner-pane");
        spinner.setContent(btnAccept);

        rootLayout.setActions(btnAccept, btnCancel);
    }

    private void onCancel() {
        fireEvent(new DialogCloseEvent());
        failed.run();
    }

    private void onAdd() {
        String serverName = txtServerName.getText();
        String serverIP = txtServerIP.getText();
        if (serverIP == null) return;
        if (serverName == null || serverName.isEmpty()) {
            serverName = i18n("servers.manager.add.server.name.def");
        }

        body.setDisable(true);

        fireEvent(new DialogCloseEvent());
        // todo online check
        success.accept(new ServerListPage.IconedServer(true, false, null, serverName, serverIP));
    }

    @Override
    public void onDialogShown() {
        txtServerName.requestFocus();
    }
}
