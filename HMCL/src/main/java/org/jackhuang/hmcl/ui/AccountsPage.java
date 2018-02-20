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
import javafx.scene.control.Hyperlink;
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
import org.jackhuang.hmcl.auth.yggdrasil.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.IconedItem;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public final class AccountsPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", Main.i18n("account"));

    @FXML
    private ScrollPane scrollPane;
    @FXML private JFXMasonryPane masonryPane;
    @FXML private JFXDialog dialog;
    @FXML private JFXTextField txtUsername;
    @FXML private JFXPasswordField txtPassword;
    @FXML private Label lblCreationWarning;
    @FXML private Label lblPassword;
    @FXML private JFXComboBox<String> cboType;
    @FXML private JFXComboBox<TwoLineListItem> cboServers;
    @FXML private JFXProgressBar progressBar;
    @FXML private Label lblAddInjectorServer;
    @FXML private Hyperlink linkAddInjectorServer;

    {
        FXUtils.loadFXML(this, "/assets/fxml/account.fxml");

        getChildren().remove(dialog);
        dialog.setDialogContainer(this);

        FXUtils.smoothScrolling(scrollPane);

        cboType.getItems().setAll(Main.i18n("account.methods.offline"), Main.i18n("account.methods.yggdrasil"), Main.i18n("account.methods.authlib_injector"));
        cboType.getSelectionModel().selectedIndexProperty().addListener((a, b, newValue) -> {
            txtPassword.setVisible(newValue.intValue() != 0);
            lblPassword.setVisible(newValue.intValue() != 0);
            cboServers.setVisible(newValue.intValue() == 2);
            linkAddInjectorServer.setVisible(newValue.intValue() == 2);
            lblAddInjectorServer.setVisible(newValue.intValue() == 2);
        });
        cboType.getSelectionModel().select(0);

        // These two lines can eliminate black, don't know why.
        cboServers.getItems().setAll(new TwoLineListItem("", ""));
        cboServers.getSelectionModel().select(0);

        txtPassword.setOnAction(e -> onCreationAccept());
        txtUsername.setOnAction(e -> onCreationAccept());
        txtUsername.getValidators().add(new Validator(Main.i18n("input.email"), str -> !txtPassword.isVisible() || str.contains("@")));

        FXUtils.onChangeAndOperate(Settings.INSTANCE.selectedAccountProperty(), account -> {
            for (Node node : masonryPane.getChildren())
                if (node instanceof AccountItem)
                    ((AccountItem) node).setSelected(account == ((AccountItem) node).getAccount());
        });

        loadAccounts();
        loadServers();

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

    public void loadServers() {
        Task.ofResult("list", () -> Settings.INSTANCE.getAuthlibInjectorServerURLs().parallelStream()
                .map(serverURL -> new TwoLineListItem(Accounts.getAuthlibInjectorServerName(serverURL), serverURL))
                .collect(Collectors.toList()))
                .subscribe(Task.of(Schedulers.javafx(), variables -> {
                    cboServers.getItems().setAll(variables.<Collection<TwoLineListItem>>get("list"));
                    if (!cboServers.getItems().isEmpty())
                        cboServers.getSelectionModel().select(0);
                }));
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
    private void onAddInjecterServer() {
        Controllers.navigate(Controllers.getServersPage());
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
                    case 0: account = Accounts.ACCOUNT_FACTORY.get(Accounts.OFFLINE_ACCOUNT_KEY).fromUsername(username); break;
                    case 1: account = Accounts.ACCOUNT_FACTORY.get(Accounts.YGGDRASIL_ACCOUNT_KEY).fromUsername(username, password); break;
                    case 2: account = Accounts.ACCOUNT_FACTORY.get(Accounts.AUTHLIB_INJECTOR_ACCOUNT_KEY).fromUsername(username, password, cboServers.getSelectionModel().getSelectedItem().getSubtitle()); break;
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
            } else if (account instanceof NoSelectedCharacterException) {
                dialog.close();
            } else if (account instanceof Exception) {
                lblCreationWarning.setText(accountException((Exception) account));
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

    public static String accountException(Exception exception) {
        if (exception instanceof InvalidCredentialsException) {
            return Main.i18n("account.failed.invalid_credentials");
        } else if (exception instanceof NoCharacterException) {
            return Main.i18n("account.failed.no_charactor");
        } else if (exception instanceof ServerDisconnectException) {
            return Main.i18n("account.failed.connect_authentication_server");
        } else if (exception instanceof InvalidTokenException) {
            return Main.i18n("account.failed.invalid_token");
        } else if (exception instanceof InvalidPasswordException) {
            return Main.i18n("account.failed.invalid_password");
        } else {
            return exception.getClass() + ": " + exception.getLocalizedMessage();
        }
    }

    public static String accountType(Account account) {
        if (account instanceof OfflineAccount) return Main.i18n("account.methods.offline");
        else if (account instanceof AuthlibInjectorAccount) return Main.i18n("account.methods.authlib_injector");
        else if (account instanceof YggdrasilAccount) return Main.i18n("account.methods.yggdrasil");
        else throw new Error(Main.i18n("account.methods.no_method") + ": " + account);
    }

    private static class CharacterSelector extends BorderPane implements MultiCharacterSelector {
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
