/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.multiplayer;

import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.FutureCallback;

import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class CreateMultiplayerRoomDialog extends DialogPane implements DialogAware {

    private final FutureCallback<CreationRequest> callback;
    private final LocalServerDetector lanServerDetectorThread;
    private final BooleanProperty allowAllJoinRequests = new SimpleBooleanProperty(true);

    private LocalServerDetector.PingResponse server;

    CreateMultiplayerRoomDialog(FutureCallback<CreationRequest> callback) {
        this.callback = callback;

        setTitle(i18n("multiplayer.session.create"));

        GridPane body = new GridPane();
        body.setMaxWidth(500);
        body.getColumnConstraints().addAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());
        body.setVgap(8);
        body.setHgap(16);
        body.setDisable(true);
        setBody(body);

        HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
        hintPane.setText(i18n("multiplayer.session.create.hint"));
        GridPane.setColumnSpan(hintPane, 2);

        body.addRow(0, hintPane);

        Label nameField = new Label();
        nameField.setText(Optional.ofNullable(Accounts.getSelectedAccount())
                .map(Account::getUsername)
                .map(username -> i18n("multiplayer.session.name.format", username))
                .orElse(""));
        body.addRow(1, new Label(i18n("multiplayer.session.create.name")), nameField);

        Label portLabel = new Label(i18n("multiplayer.nat.testing"));
        portLabel.setText(i18n("multiplayer.nat.testing"));
        body.addRow(2, new Label(i18n("multiplayer.session.create.port")), portLabel);

        JFXCheckBox allowAllJoinRequestsCheckBox = new JFXCheckBox(i18n("multiplayer.session.create.join.allow"));
        allowAllJoinRequestsCheckBox.selectedProperty().bindBidirectional(allowAllJoinRequests);
        GridPane.setColumnSpan(allowAllJoinRequestsCheckBox, 2);
        body.addRow(3, allowAllJoinRequestsCheckBox);

        setValid(false);

        JFXHyperlink noinLink = new JFXHyperlink();
        noinLink.setText("mcer.cn");
        noinLink.setOnAction(e -> FXUtils.openLink("https://mcer.cn/circle/cato"));

        setActions(warningLabel, noinLink, acceptPane, cancelButton);

        lanServerDetectorThread = new LocalServerDetector(3);
        lanServerDetectorThread.onDetectedLanServer().register(event -> {
            runInFX(() -> {
                if (event.getLanServer() != null && event.getLanServer().isValid()) {
                    nameField.setText(event.getLanServer().getMotd());
                    portLabel.setText(event.getLanServer().getAd().toString());
                    setValid(true);
                } else {
                    nameField.setText("");
                    portLabel.setText("");
                    onFailure(i18n("multiplayer.session.create.port.error"));
                    setValid(false);
                }
                server = event.getLanServer();
                body.setDisable(false);
                getProgressBar().setVisible(false);
            });
        });
    }

    @Override
    protected void onAccept() {
        setLoading();

        callback.call(new CreationRequest(
                Objects.requireNonNull(server),
                allowAllJoinRequests.get()
        ), () -> {
            runInFX(this::onSuccess);
        }, msg -> {
            runInFX(() -> onFailure(msg));
        });
    }

    @Override
    public void onDialogShown() {
        getProgressBar().setVisible(true);
        getProgressBar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        lanServerDetectorThread.start();
    }

    @Override
    public void onDialogClosed() {
        lanServerDetectorThread.interrupt();
    }

    public static class CreationRequest {
        private final LocalServerDetector.PingResponse server;
        private final boolean allowAllJoinRequests;

        public CreationRequest(LocalServerDetector.PingResponse server, boolean allowAllJoinRequests) {
            this.server = server;
            this.allowAllJoinRequests = allowAllJoinRequests;
        }

        public LocalServerDetector.PingResponse getServer() {
            return server;
        }

        public boolean isAllowAllJoinRequests() {
            return allowAllJoinRequests;
        }
    }
}
