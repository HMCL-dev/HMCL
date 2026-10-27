// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class AccountListPopupMenu extends StackPane {
    public static void show(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                            double initOffsetX, double initOffsetY) {
        var menu = new AccountListPopupMenu();
        JFXPopup popup = new JFXPopup(menu);
        popup.show(owner, vAlign, hAlign, initOffsetX, initOffsetY);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final BooleanBinding isEmpty = Bindings.isEmpty(Accounts.getAccounts());
    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationListener listener;

    public AccountListPopupMenu() {
        AdvancedListBox box = new AdvancedListBox();
        box.getStyleClass().add("no-padding");
        box.setPrefWidth(220);
        box.setPrefHeight(-1);
        box.setMaxHeight(260);

        listener = o -> {
            box.clear();

            for (Account account : Accounts.getAccounts()) {
                AccountAdvancedListItem item = new AccountAdvancedListItem(account);
                if (Accounts.getSelectedAccount() == account) item.setActive(true);
                item.setOnAction(e -> {
                    Accounts.setSelectedAccount(account);
                    if (getScene().getWindow() instanceof JFXPopup popup)
                        popup.hide();
                });
                box.add(item);
            }

            // https://github.com/HMCL-dev/HMCL/issues/5343
            Platform.runLater(() -> box.setVvalue(0.0));
        };
        listener.invalidated(null);
        Accounts.getAccounts().addListener(new WeakInvalidationListener(listener));

        Label placeholder = new Label(i18n("account.empty"));
        placeholder.setStyle("-fx-padding: 10px; -fx-text-fill: -monet-on-surface-variant; -fx-font-style: italic;");

        FXUtils.onChangeAndOperate(isEmpty, empty -> {
            getChildren().setAll(empty ? placeholder : box);
        });
    }

}
