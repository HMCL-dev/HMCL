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

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.IconedItem;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Logging;
import static org.jackhuang.hmcl.ui.FXUtils.jfxListCellFactory;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

public class AddAccountPane extends StackPane {

    @FXML private JFXTextField txtUsername;
    @FXML private JFXPasswordField txtPassword;
    @FXML private Label lblCreationWarning;
    @FXML private Label lblPassword;
    @FXML private JFXComboBox<String> cboType;
    @FXML private JFXComboBox<AuthlibInjectorServer> cboServers;
    @FXML private Label lblInjectorServer;
    @FXML private Hyperlink linkManageInjectorServers;
    @FXML private JFXDialogLayout layout;
    @FXML private JFXButton btnAccept;
    @FXML private SpinnerPane acceptPane;

    public AddAccountPane() {
        FXUtils.loadFXML(this, "/assets/fxml/account-add.fxml");

        cboServers.setCellFactory(jfxListCellFactory(server -> new TwoLineListItem(server.getName(), server.getUrl())));
        cboServers.setConverter(stringConverter(AuthlibInjectorServer::getName));
        Bindings.bindContent(cboServers.getItems(), Settings.SETTINGS.authlibInjectorServers);
        cboServers.getItems().addListener(onInvalidating(this::selectDefaultServer));
        selectDefaultServer();

        cboType.getItems().setAll(i18n("account.methods.offline"), i18n("account.methods.yggdrasil"), i18n("account.methods.authlib_injector"));
        cboType.getSelectionModel().select(0);

        ReadOnlyIntegerProperty loginTypeIdProperty = cboType.getSelectionModel().selectedIndexProperty();

        txtPassword.visibleProperty().bind(loginTypeIdProperty.isNotEqualTo(0));
        lblPassword.visibleProperty().bind(txtPassword.visibleProperty());

        cboServers.visibleProperty().bind(loginTypeIdProperty.isEqualTo(2));
        lblInjectorServer.visibleProperty().bind(cboServers.visibleProperty());
        linkManageInjectorServers.visibleProperty().bind(cboServers.visibleProperty());

        txtUsername.getValidators().add(new Validator(i18n("input.email"), str -> !txtPassword.isVisible() || str.contains("@")));

        btnAccept.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !txtUsername.validate() || (loginTypeIdProperty.get() != 0 && !txtPassword.validate()),
                txtUsername.textProperty(), txtPassword.textProperty(), loginTypeIdProperty));
    }

    /**
     * Selects the first server if no server is selected.
     */
    private void selectDefaultServer() {
        if (!cboServers.getItems().isEmpty() && cboServers.getSelectionModel().isEmpty()) {
            cboServers.getSelectionModel().select(0);
        }
    }

    @FXML
    private void onCreationAccept() {
        if (btnAccept.isDisabled())
            return;

        String username = txtUsername.getText();
        String password = txtPassword.getText();
        Object addtionalData;

        int type = cboType.getSelectionModel().getSelectedIndex();
        AccountFactory<?> factory;
        switch (type) {
            case 0:
                factory = Accounts.ACCOUNT_FACTORY.get(Accounts.OFFLINE_ACCOUNT_KEY);
                addtionalData = null;
                break;
            case 1:
                factory = Accounts.ACCOUNT_FACTORY.get(Accounts.YGGDRASIL_ACCOUNT_KEY);
                addtionalData = null;
                break;
            case 2:
                factory = Accounts.ACCOUNT_FACTORY.get(Accounts.AUTHLIB_INJECTOR_ACCOUNT_KEY);
                Optional<AuthlibInjectorServer> server = Optional.ofNullable(cboServers.getSelectionModel().getSelectedItem());
                if (server.isPresent()) {
                    addtionalData = server.get();
                } else {
                    lblCreationWarning.setText(i18n("account.failed.no_selected_server"));
                    return;
                }
                break;
            default:
                throw new Error();
        }

        acceptPane.showSpinner();
        lblCreationWarning.setText("");
        setDisable(true);

        Task.ofResult("create_account", () -> factory.create(new Selector(), username, password, addtionalData, Settings.INSTANCE.getProxy()))
                .finalized(Schedulers.javafx(), variables -> {
                    Settings.INSTANCE.addAccount(variables.get("create_account"));
                    acceptPane.hideSpinner();
                    fireEvent(new DialogCloseEvent());
                }, exception -> {
                    if (exception instanceof NoSelectedCharacterException) {
                        fireEvent(new DialogCloseEvent());
                    } else {
                        lblCreationWarning.setText(accountException(exception));
                    }
                    setDisable(false);
                    acceptPane.hideSpinner();
                }).start();
    }

    @FXML
    private void onCreationCancel() {
        fireEvent(new DialogCloseEvent());
    }

    @FXML
    private void onManageInjecterServers() {
        fireEvent(new DialogCloseEvent());
        Controllers.navigate(Controllers.getServersPage());
    }

    private class Selector extends BorderPane implements CharacterSelector {
        private final AdvancedListBox listBox = new AdvancedListBox();
        private final JFXButton cancel = new JFXButton();

        private final CountDownLatch latch = new CountDownLatch(1);
        private GameProfile selectedProfile = null;

        {
            setStyle("-fx-padding: 8px;");

            cancel.setText(i18n("button.cancel"));
            StackPane.setAlignment(cancel, Pos.BOTTOM_RIGHT);
            cancel.setOnMouseClicked(e -> latch.countDown());

            listBox.startCategory(i18n("account.choose"));

            setCenter(listBox);

            HBox hbox = new HBox();
            hbox.setAlignment(Pos.CENTER_RIGHT);
            hbox.getChildren().add(cancel);
            setBottom(hbox);
        }

        @Override
        public GameProfile select(Account account, List<GameProfile> names) throws NoSelectedCharacterException {
            if (!(account instanceof YggdrasilAccount))
                return CharacterSelector.DEFAULT.select(account, names);
            YggdrasilAccount yggdrasilAccount = (YggdrasilAccount) account;

            for (GameProfile profile : names) {
                Image image;
                try {
                    image = AccountHelper.getSkinImmediately(yggdrasilAccount, profile, 4, Settings.INSTANCE.getProxy());
                } catch (Exception e) {
                    Logging.LOG.log(Level.WARNING, "Failed to get skin for " + profile.getName(), e);
                    image = null;
                }
                ImageView portraitView = new ImageView();
                portraitView.setSmooth(false);
                if (image == null) {
                    portraitView.setImage(Constants.DEFAULT_ICON.get());
                } else {
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

                if (selectedProfile == null)
                    throw new NoSelectedCharacterException(account);

                return selectedProfile;
            } catch (InterruptedException ignore) {
                throw new NoSelectedCharacterException(account);
            } finally {
                JFXUtilities.runInFX(() -> Selector.this.fireEvent(new DialogCloseEvent()));
            }
        }
    }

    public static String accountException(Exception exception) {
        if (exception instanceof NoCharacterException) {
            return i18n("account.failed.no_character");
        } else if (exception instanceof ServerDisconnectException) {
            return i18n("account.failed.connect_authentication_server");
        } else if (exception instanceof RemoteAuthenticationException) {
            RemoteAuthenticationException remoteException = (RemoteAuthenticationException) exception;
            String remoteMessage = remoteException.getRemoteMessage();
            if ("ForbiddenOperationException".equals(remoteException.getRemoteName()) && remoteMessage != null) {
                if (remoteMessage.contains("Invalid credentials"))
                    return i18n("account.failed.invalid_credentials");
                else if (remoteMessage.contains("Invalid token"))
                    return i18n("account.failed.invalid_token");
                else if (remoteMessage.contains("Invalid username or password"))
                    return i18n("account.failed.invalid_password");
            }
            return exception.getMessage();
        } else {
            return exception.getClass().getName() + ": " + exception.getLocalizedMessage();
        }
    }

    public static String accountType(Account account) {
        if (account instanceof OfflineAccount) return i18n("account.methods.offline");
        else if (account instanceof AuthlibInjectorAccount) return i18n("account.methods.authlib_injector");
        else if (account instanceof YggdrasilAccount) return i18n("account.methods.yggdrasil");
        else throw new Error(i18n("account.methods.no_method") + ": " + account);
    }
}
