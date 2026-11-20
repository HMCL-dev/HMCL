// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3RadioButton;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class AccountListItemSkin extends SkinBase<AccountListItem> {

    public AccountListItemSkin(AccountListItem skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();
        root.setCursor(Cursor.HAND);
        FXUtils.onClicked(root, skinnable::fire);

        M3RadioButton chkSelected = new M3RadioButton();
        chkSelected.setMouseTransparent(true);
        chkSelected.setFocusTraversable(false);
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
        BorderPane.setMargin(center, new Insets(0, 0, 0, 8));

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);

        M3IconButton btnMove = new M3IconButton();
        btnMove.setOnAction(e -> {
            Account account = skinnable.getAccount();
            if (!Accounts.canMoveAccount(account)) {
                Controllers.confirmBackupAndOverwrite(i18n("account.storage.read_only"), () -> {
                    Accounts.forceOverwriteAccountFiles();
                    moveAccount(skinnable);
                });
                return;
            }

            moveAccount(skinnable);
        });
        if (skinnable.getAccount().isPortable()) {
            btnMove.setGraphic(SVG.PUBLIC.createIcon());
            FXUtils.installFastTooltip(btnMove, i18n("account.move_to_global"));
        } else {
            btnMove.setGraphic(SVG.OUTPUT.createIcon());
            FXUtils.installFastTooltip(btnMove, i18n("account.move_to_portable"));
        }
        right.getChildren().add(btnMove);

        M3IconButton btnRefresh = new M3IconButton(SVG.REFRESH.createIcon());
        var refreshing = FXUtils.newButtonLoadingProperty(btnRefresh);
        if (skinnable.getAccount() instanceof MicrosoftAccount && Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            btnRefresh.setDisable(true);
            StackPane refreshHint = new StackPane(btnRefresh);
            FXUtils.installFastTooltip(refreshHint, i18n("account.methods.microsoft.snapshot.tooltip"));
            right.getChildren().add(refreshHint);
        } else {
            btnRefresh.disableProperty().bind(refreshing);
            right.getChildren().add(btnRefresh);
        }
        btnRefresh.setOnAction(e -> {
            refreshing.set(true);
            skinnable.refreshAsync()
                    .whenComplete(Schedulers.javafx(), ex -> {
                        refreshing.set(false);

                        if (ex != null) {
                            Controllers.showToast(Accounts.localizeErrorMessage(ex));
                        }
                    })
                    .start();
        });
        FXUtils.installFastTooltip(btnRefresh, i18n("button.refresh"));

        M3IconButton btnUpload = new M3IconButton(SVG.CHECKROOM.createIcon());
        var uploading = FXUtils.newButtonLoadingProperty(btnUpload);
        btnUpload.setOnAction(e -> {
            Task<?> uploadTask = skinnable.uploadSkin();
            if (uploadTask != null) {
                uploading.set(true);
                uploadTask
                        .whenComplete(Schedulers.javafx(), ex -> uploading.set(false))
                        .start();
            }
        });
        FXUtils.installFastTooltip(btnUpload, i18n("account.skin." + (skinnable.getAccount() instanceof OfflineAccount ? "edit" : "upload")));
        btnUpload.disableProperty().bind(uploading.or(Bindings.not(skinnable.canUploadSkin())));
        right.getChildren().add(btnUpload);

        M3IconButton btnCopyUUID = new M3IconButton(SVG.CONTENT_COPY.createIcon());
        btnCopyUUID.setOnAction(e -> FXUtils.copyText(skinnable.getAccount().getProfileID().toString()));
        FXUtils.installFastTooltip(btnCopyUUID, i18n("account.copy_uuid"));
        right.getChildren().add(btnCopyUUID);

        M3IconButton btnRemove = new M3IconButton(SVG.DELETE_FOREVER.createIcon());
        btnRemove.setOnAction(e -> Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), skinnable::remove, null));
        BorderPane.setAlignment(btnRemove, Pos.CENTER);
        FXUtils.installFastTooltip(btnRemove, i18n("button.delete"));
        right.getChildren().add(btnRemove);
        root.setRight(right);

        root.getStyleClass().add("card");
        root.setStyle("-fx-padding: 8 8 8 0;");

        getChildren().setAll(root);
    }

    /// Moves the account between local and user account files.
    private static void moveAccount(AccountListItem skinnable) {
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
    }
}
