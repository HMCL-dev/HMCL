/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXProgressBar;
import com.jfoenix.controls.JFXRadioButton;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;

public final class AccountItem extends StackPane {

    private final Account account;

    @FXML
    private Pane icon;
    @FXML private VBox content;
    @FXML private StackPane header;
    @FXML private StackPane body;
    @FXML private JFXButton btnDelete;
    @FXML private JFXButton btnRefresh;
    @FXML private Label lblUser;
    @FXML private Label lblType;
    @FXML private Label lblEmail;
    @FXML private Label lblCurrentAccount;
    @FXML private JFXRadioButton chkSelected;
    @FXML private JFXProgressBar pgsSkin;
    @FXML private ImageView portraitView;
    @FXML private HBox buttonPane;

    public AccountItem(int i, Account account, ToggleGroup toggleGroup) {
        this.account = account;

        FXUtils.loadFXML(this, "/assets/fxml/account-item.fxml");

        setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 5.0, 0.12, -0.5, 1.0));

        chkSelected.setToggleGroup(toggleGroup);
        btnDelete.setGraphic(SVG.delete("black", 15, 15));
        btnRefresh.setGraphic(SVG.refresh("black", 15, 15));

        // create content
        String headerColor = getDefaultColor(i % 12);
        header.setStyle("-fx-background-radius: 2 2 0 0; -fx-background-color: " + headerColor);

        // create image view
        icon.translateYProperty().bind(Bindings.createDoubleBinding(() -> header.getBoundsInParent().getHeight() - icon.getHeight() / 2 - 32.0, header.boundsInParentProperty(), icon.heightProperty()));

        chkSelected.getProperties().put("account", account);
        setSelected(Settings.INSTANCE.getSelectedAccount() == account);

        lblUser.setText(Accounts.getCurrentCharacter(account));
        lblType.setText(AccountsPage.accountType(account));
        lblEmail.setText(account.getUsername());

        if (account instanceof YggdrasilAccount) {
            btnRefresh.setOnMouseClicked(e -> {
                pgsSkin.setVisible(true);
                AccountHelper.refreshSkinAsync((YggdrasilAccount) account)
                        .subscribe(Schedulers.javafx(), this::loadSkin);
            });
            AccountHelper.loadSkinAsync((YggdrasilAccount) account)
                    .subscribe(Schedulers.javafx(), this::loadSkin);
        } else
            loadSkin();

        if (account instanceof OfflineAccount) { // Offline Account cannot be refreshed,
            buttonPane.getChildren().remove(btnRefresh);
        }
    }

    private void loadSkin() {
        pgsSkin.setVisible(false);
        portraitView.setViewport(AccountHelper.getViewport(4));
        if (account instanceof YggdrasilAccount)
            portraitView.setImage(AccountHelper.getSkin((YggdrasilAccount) account, 4));
        else
            portraitView.setImage(AccountHelper.getDefaultSkin(account, 4));
        FXUtils.limitSize(portraitView, 32, 32);
    }

    private String getDefaultColor(int i) {
        switch (i) {
            case 0: return "#8F3F7E";
            case 1: return "#B5305F";
            case 2: return "#CE584A";
            case 3: return "#DB8D5C";
            case 4: return "#DA854E";
            case 5: return "#E9AB44";
            case 6: return "#FEE435";
            case 7: return "#99C286";
            case 8: return "#01A05E";
            case 9: return "#4A8895";
            case 10: return "#16669B";
            case 11: return "#2F65A5";
            case 12: return "#4E6A9C";
            default: return "#FFFFFF";
        }
    }

    public Account getAccount() {
        return account;
    }

    public void setSelected(boolean selected) {
        lblCurrentAccount.setVisible(selected);
        chkSelected.setSelected(selected);
    }

    public void setOnDeleteButtonMouseClicked(EventHandler<? super MouseEvent> eventHandler) {
        btnDelete.setOnMouseClicked(eventHandler);
    }
}
