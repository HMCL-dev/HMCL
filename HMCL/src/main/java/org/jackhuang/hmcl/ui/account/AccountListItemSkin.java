/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class AccountListItemSkin extends SkinBase<AccountListItem> {

    public AccountListItemSkin(AccountListItem skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();
        root.setCursor(Cursor.HAND);
        FXUtils.onClicked(root, skinnable::fire);

        JFXRadioButton chkSelected = new JFXRadioButton();
        chkSelected.setMouseTransparent(true);
        BorderPane.setAlignment(chkSelected, Pos.CENTER);
        chkSelected.selectedProperty().bind(skinnable.selectedProperty());
        root.setLeft(chkSelected);

        HBox center = new HBox();
        center.setSpacing(8);
        center.setAlignment(Pos.CENTER_LEFT);

        Canvas canvas = new Canvas(32, 32);
        TexturesLoader.bindAvatar(canvas, skinnable.getAccount());

        Label title = new Label();
        title.getStyleClass().add("title");
        title.textProperty().bind(skinnable.titleProperty());
        Label subtitle = new Label();
        subtitle.getStyleClass().add("subtitle");
        subtitle.textProperty().bind(skinnable.subtitleProperty());
        if (skinnable.getAccount() instanceof AuthlibInjectorAccount) {
            Tooltip tooltip = new Tooltip();
            AuthlibInjectorServer server = ((AuthlibInjectorAccount) skinnable.getAccount()).getServer();
            tooltip.textProperty().bind(BindingMapping.of(server, AuthlibInjectorServer::toString));
            FXUtils.installSlowTooltip(subtitle, tooltip);
        }
        VBox item = new VBox(title, subtitle);
        item.getStyleClass().add("two-line-list-item");
        BorderPane.setAlignment(item, Pos.CENTER);

        center.getChildren().setAll(canvas, item);
        root.setCenter(center);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);

        JFXButton btnMove = new JFXButton();
        SpinnerPane spinnerMove = new SpinnerPane();
        spinnerMove.getStyleClass().add("small-spinner-pane");
        btnMove.setOnAction(e -> {
            Account account = skinnable.getAccount();
            Accounts.getAccounts().remove(account);
            if (account.isPortable()) {
                account.setPortable(false);
                if (!Accounts.getAccounts().contains(account))
                    Accounts.getAccounts().add(account);
            } else {
                account.setPortable(true);
                if (!Accounts.getAccounts().contains(account)) {
                    int idx = 0;
                    for (int i = Accounts.getAccounts().size() - 1; i >= 0; i--) {
                        if (Accounts.getAccounts().get(i).isPortable()) {
                            idx = i + 1;
                            break;
                        }
                    }
                    Accounts.getAccounts().add(idx, account);
                }
            }
        });
        btnMove.getStyleClass().add("toggle-icon4");
        if (skinnable.getAccount().isPortable()) {
            btnMove.setGraphic(SVG.PUBLIC.createIcon());
            FXUtils.installFastTooltip(btnMove, i18n("account.move_to_global"));
        } else {
            btnMove.setGraphic(SVG.OUTPUT.createIcon());
            FXUtils.installFastTooltip(btnMove, i18n("account.move_to_portable"));
        }
        spinnerMove.setContent(btnMove);
        right.getChildren().add(spinnerMove);

        JFXButton btnRefresh = new JFXButton();
        SpinnerPane spinnerRefresh = new SpinnerPane();
        spinnerRefresh.getStyleClass().setAll("small-spinner-pane");
        if (skinnable.getAccount() instanceof MicrosoftAccount && Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            btnRefresh.setDisable(true);
            FXUtils.installFastTooltip(spinnerRefresh, i18n("account.methods.microsoft.snapshot.tooltip"));
        }
        btnRefresh.setOnAction(e -> {
            spinnerRefresh.showSpinner();
            skinnable.refreshAsync()
                    .whenComplete(Schedulers.javafx(), ex -> {
                        spinnerRefresh.hideSpinner();

                        if (ex != null) {
                            Controllers.showToast(Accounts.localizeErrorMessage(ex));
                        }
                    })
                    .start();
        });
        btnRefresh.getStyleClass().add("toggle-icon4");
        btnRefresh.setGraphic(SVG.REFRESH.createIcon());
        FXUtils.installFastTooltip(btnRefresh, i18n("button.refresh"));
        spinnerRefresh.setContent(btnRefresh);
        right.getChildren().add(spinnerRefresh);

        JFXButton btnUpload = new JFXButton();
        SpinnerPane spinnerUpload = new SpinnerPane();
        btnUpload.setOnAction(e -> {
            Task<?> uploadTask = skinnable.uploadSkin();
            if (uploadTask != null) {
                spinnerUpload.showSpinner();
                uploadTask
                        .whenComplete(Schedulers.javafx(), ex -> spinnerUpload.hideSpinner())
                        .start();
            }
        });
        btnUpload.getStyleClass().add("toggle-icon4");
        btnUpload.setGraphic(SVG.CHECKROOM.createIcon());
        FXUtils.installFastTooltip(btnUpload, i18n("account.skin.upload"));
        btnUpload.disableProperty().bind(Bindings.not(skinnable.canUploadSkin()));
        spinnerUpload.setContent(btnUpload);
        spinnerUpload.getStyleClass().add("small-spinner-pane");
        right.getChildren().add(spinnerUpload);

        JFXButton btnCopyUUID = new JFXButton();
        SpinnerPane spinnerCopyUUID = new SpinnerPane();
        spinnerCopyUUID.getStyleClass().add("small-spinner-pane");
        btnUpload.getStyleClass().add("toggle-icon4");
        btnCopyUUID.setOnAction(e -> FXUtils.copyText(skinnable.getAccount().getUUID().toString()));
        btnCopyUUID.setGraphic(SVG.CONTENT_COPY.createIcon());
        FXUtils.installFastTooltip(btnCopyUUID, i18n("account.copy_uuid"));
        spinnerCopyUUID.setContent(btnCopyUUID);
        right.getChildren().add(spinnerCopyUUID);

        JFXButton btnRemove = new JFXButton();
        btnRemove.setOnAction(e -> Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), skinnable::remove, null));
        btnRemove.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnRemove, Pos.CENTER);
        btnRemove.setGraphic(SVG.DELETE.createIcon());
        FXUtils.installFastTooltip(btnRemove, i18n("button.delete"));
        right.getChildren().add(btnRemove);
        root.setRight(right);

        root.getStyleClass().add("card");
        root.setStyle("-fx-padding: 8 8 8 0;");
        JFXDepthManager.setDepth(root, 1);

        getChildren().setAll(root);
    }
}
