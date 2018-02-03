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

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.InvalidCredentialsException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.IconedItem;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class AccountsPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", Main.i18n("account"));

    @FXML
    private ScrollPane scrollPane;
    @FXML private JFXMasonryPane masonryPane;
    @FXML private JFXDialog dialog;
    @FXML private JFXTextField txtUsername;
    @FXML private JFXPasswordField txtPassword;
    @FXML private Label lblCreationWarning;
    @FXML private JFXComboBox<String> cboType;
    @FXML private JFXProgressBar progressBar;

    {
        FXUtils.loadFXML(this, "/assets/fxml/account.fxml");

        getChildren().remove(dialog);
        dialog.setDialogContainer(this);

        FXUtils.smoothScrolling(scrollPane);
        FXUtils.setValidateWhileTextChanged(txtUsername);
        FXUtils.setValidateWhileTextChanged(txtPassword);

        cboType.getItems().setAll(Main.i18n("account.methods.offline"), Main.i18n("account.methods.yggdrasil"));
        cboType.getSelectionModel().selectedIndexProperty().addListener((a, b, newValue) -> {
            txtPassword.setVisible(newValue.intValue() != 0);
        });
        cboType.getSelectionModel().select(0);

        txtPassword.setOnAction(e -> onCreationAccept());
        txtUsername.setOnAction(e -> onCreationAccept());
        txtUsername.getValidators().add(new Validator(Main.i18n("input.email"), str -> !txtPassword.isVisible() || str.contains("@")));

        FXUtils.onChangeAndOperate(Settings.INSTANCE.selectedAccountProperty(), account -> {
            for (Node node : masonryPane.getChildren())
                if (node instanceof AccountItem)
                    ((AccountItem) node).setSelected(account == ((AccountItem) node).getAccount());
        });

        loadAccounts();

        if (Settings.INSTANCE.getAccounts().isEmpty())
            addNewAccount();
    }

    public void loadAccounts() {
        List<Node> children = new LinkedList<>();
        int i = 0;
        ToggleGroup group = new ToggleGroup();
        for (Account account : Settings.INSTANCE.getAccounts()) {
            children.add(buildNode(++i, account, group));
        }
        group.selectedToggleProperty().addListener((a, b, newValue) -> {
            if (newValue != null)
                Settings.INSTANCE.setSelectedAccount((Account) newValue.getProperties().get("account"));
        });
        FXUtils.resetChildren(masonryPane, children);
        Platform.runLater(() -> {
            masonryPane.requestLayout();
            scrollPane.requestLayout();
        });
    }

    private Node buildNode(int i, Account account, ToggleGroup group) {
        AccountItem item = new AccountItem(i, account, group);
        item.setOnDeleteButtonMouseClicked(e -> {
            Settings.INSTANCE.deleteAccount(account);
            Platform.runLater(this::loadAccounts);
        });
        return item;
    }

    @FXML
    private void addNewAccount() {
        txtUsername.setText("");
        txtPassword.setText("");
        dialog.show();
    }

    @FXML
    private void onCreationAccept() {
        int type = cboType.getSelectionModel().getSelectedIndex();
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        progressBar.setVisible(true);
        lblCreationWarning.setText("");
        Task.ofResult("create_account", () -> {
            try {
                Account account;
                switch (type) {
                    case 0: account = OfflineAccountFactory.INSTANCE.fromUsername(username); break;
                    case 1: account = YggdrasilAccountFactory.INSTANCE.fromUsername(username, password); break;
                    default: throw new Error();
                }

                AuthInfo info = account.logIn(new CharacterSelector(), Settings.INSTANCE.getProxy());
                Accounts.setCurrentCharacter(account, info.getUsername());
                return account;
            } catch (Exception e) {
                return e;
            }
        }).subscribe(Schedulers.javafx(), variables -> {
            Object account = variables.get("create_account");
            if (account instanceof Account) {
                Settings.INSTANCE.addAccount((Account) account);
                dialog.close();
                loadAccounts();
            } else if (account instanceof InvalidCredentialsException) {
                lblCreationWarning.setText(Main.i18n("account.failed.wrong_password"));
            } else if (account instanceof NoCharacterException) {
                lblCreationWarning.setText(Main.i18n("account.failed.no_charactor"));
            } else if (account instanceof ServerDisconnectException) {
                lblCreationWarning.setText(Main.i18n("account.failed.connect_authentication_server"));
            } else if (account instanceof InvalidTokenException) {
                lblCreationWarning.setText(Main.i18n("account.failed.invalid_token"));
            } else if (account instanceof InvalidPasswordException) {
                lblCreationWarning.setText(Main.i18n("account.failed.invalid_password"));
            } else if (account instanceof NoSelectedCharacterException) {
                dialog.close();
            } else if (account instanceof Exception) {
                lblCreationWarning.setText(account.getClass() + ": " + ((Exception) account).getLocalizedMessage());
            }
            progressBar.setVisible(false);
        });
    }

    @FXML
    private void onCreationCancel() {
        dialog.close();
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public static String accountType(Account account) {
        if (account instanceof OfflineAccount) return Main.i18n("account.methods.offline");
        else if (account instanceof YggdrasilAccount) return Main.i18n("account.methods.yggdrasil");
        else throw new Error(Main.i18n("account.methods.no_method") + ": " + account);
    }

    class CharacterSelector extends BorderPane implements MultiCharacterSelector {
        private AdvancedListBox listBox = new AdvancedListBox();
        private JFXButton cancel = new JFXButton();

        private CountDownLatch latch = new CountDownLatch(1);
        private GameProfile selectedProfile = null;

        {
            setStyle("-fx-padding: 8px;");

            cancel.setText(Main.i18n("button.cancel"));
            StackPane.setAlignment(cancel, Pos.BOTTOM_RIGHT);
            cancel.setOnMouseClicked(e -> latch.countDown());

            listBox.startCategory(Main.i18n("account.choose"));

            setCenter(listBox);

            HBox hbox = new HBox();
            hbox.setAlignment(Pos.CENTER_RIGHT);
            hbox.getChildren().add(cancel);
            setBottom(hbox);
        }

        @Override
        public GameProfile select(Account account, List<GameProfile> names) throws NoSelectedCharacterException {
            if (!(account instanceof YggdrasilAccount))
                return MultiCharacterSelector.DEFAULT.select(account, names);
            YggdrasilAccount yggdrasilAccount = (YggdrasilAccount) account;

            for (GameProfile profile : names) {
                Image image;
                try {
                    image = AccountHelper.getSkinImmediately(yggdrasilAccount, profile, 4, Settings.INSTANCE.getProxy());
                } catch (Exception e) {
                    image = FXUtils.DEFAULT_ICON;
                }
                ImageView portraitView = new ImageView();
                portraitView.setSmooth(false);
                if (image == FXUtils.DEFAULT_ICON)
                    portraitView.setImage(FXUtils.DEFAULT_ICON);
                else {
                    portraitView.setImage(image);
                    portraitView.setViewport(AccountHelper.getViewport(4));
                }
                FXUtils.limitSize(portraitView, 32, 32);

                IconedItem accountItem = new IconedItem(portraitView, profile.getName());
                accountItem.setOnMouseClicked(e -> {
                    selectedProfile = profile;
                    latch.countDown();
                });
                listBox.add(accountItem);
            }

            JFXUtilities.runInFX(() -> Controllers.dialog(this));

            try {
                latch.await();

                JFXUtilities.runInFX(Controllers::closeDialog);

                if (selectedProfile == null)
                    throw new NoSelectedCharacterException(account);

                return selectedProfile;
            } catch (InterruptedException ignore) {
                throw new NoSelectedCharacterException(account);
            }
        }
    }
}
