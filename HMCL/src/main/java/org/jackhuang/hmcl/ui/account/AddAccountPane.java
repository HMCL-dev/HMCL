/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.*;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDownloadException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.javafx.MultiStepBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AddAccountPane extends StackPane {

    @FXML private JFXTextField txtUsername;
    @FXML private JFXPasswordField txtPassword;
    @FXML private Label lblCreationWarning;
    @FXML private Label lblPassword;
    @FXML private JFXComboBox<AccountFactory<?>> cboType;
    @FXML private JFXComboBox<AuthlibInjectorServer> cboServers;
    @FXML private Label lblInjectorServer;
    @FXML private JFXButton btnAccept;
    @FXML private JFXButton btnAddServer;
    @FXML private JFXButton btnManageServer;
    @FXML private SpinnerPane acceptPane;
    @FXML private HBox linksContainer;

    private ListProperty<Hyperlink> links = new SimpleListProperty<>();;

    public AddAccountPane() {
        FXUtils.loadFXML(this, "/assets/fxml/account-add.fxml");

        cboServers.setCellFactory(jfxListCellFactory(server -> new TwoLineListItem(server.getName(), server.getUrl())));
        cboServers.setConverter(stringConverter(AuthlibInjectorServer::getName));
        Bindings.bindContent(cboServers.getItems(), config().getAuthlibInjectorServers());
        cboServers.getItems().addListener(onInvalidating(this::selectDefaultServer));
        selectDefaultServer();

        cboType.getItems().setAll(Accounts.FACTORY_OFFLINE, Accounts.FACTORY_MOJANG, Accounts.FACTORY_AUTHLIB_INJECTOR);
        cboType.setConverter(stringConverter(Accounts::getLocalizedLoginTypeName));
        // try selecting the preferred login type
        cboType.getSelectionModel().select(
                cboType.getItems().stream()
                        .filter(type -> Accounts.getLoginType(type).equals(config().getPreferredLoginType()))
                        .findFirst()
                        .orElse(Accounts.FACTORY_OFFLINE));

        btnAddServer.visibleProperty().bind(cboServers.visibleProperty());
        btnManageServer.visibleProperty().bind(cboServers.visibleProperty());

        cboServers.getItems().addListener(onInvalidating(this::checkIfNoServer));
        checkIfNoServer();

        ReadOnlyObjectProperty<AccountFactory<?>> loginType = cboType.getSelectionModel().selectedItemProperty();

        // remember the last used login type
        loginType.addListener((observable, oldValue, newValue) -> config().setPreferredLoginType(Accounts.getLoginType(newValue)));

        txtPassword.visibleProperty().bind(loginType.isNotEqualTo(Accounts.FACTORY_OFFLINE));
        lblPassword.visibleProperty().bind(txtPassword.visibleProperty());

        cboServers.visibleProperty().bind(loginType.isEqualTo(Accounts.FACTORY_AUTHLIB_INJECTOR));
        lblInjectorServer.visibleProperty().bind(cboServers.visibleProperty());

        txtUsername.getValidators().add(new Validator(i18n("input.email"), str -> !txtPassword.isVisible() || str.contains("@")));

        btnAccept.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !( // consider the opposite situation: input is valid
                        txtUsername.validate() &&
                        // invisible means the field is not needed, neither should it be validated
                        (!txtPassword.isVisible() || txtPassword.validate()) &&
                        (!cboServers.isVisible() || cboServers.getSelectionModel().getSelectedItem() != null)
                ),
                txtUsername.textProperty(),
                txtPassword.textProperty(), txtPassword.visibleProperty(),
                cboServers.getSelectionModel().selectedItemProperty(), cboServers.visibleProperty()));

        // authlib-injector links
        links.bind(MultiStepBinding.of(cboServers.getSelectionModel().selectedItemProperty())
                .map(AddAccountPane::createHyperlinks)
                .map(FXCollections::observableList));
        Bindings.bindContent(linksContainer.getChildren(), links);
        linksContainer.visibleProperty().bind(cboServers.visibleProperty());
    }

    private static final String[] ALLOWED_LINKS = { "register" };

    public static List<Hyperlink> createHyperlinks(AuthlibInjectorServer server) {
        if (server == null) {
            return emptyList();
        }

        Map<String, String> links = server.getLinks();
        List<Hyperlink> result = new ArrayList<>();
        for (String key : ALLOWED_LINKS) {
            String value = links.get(key);
            if (value != null) {
                Hyperlink link = new Hyperlink(i18n("account.injector.link." + key));
                FXUtils.installSlowTooltip(link, value);
                link.setOnAction(e -> FXUtils.openLink(value));
                result.add(link);
            }
        }
        return unmodifiableList(result);
    }

    /**
     * Selects the first server if no server is selected.
     */
    private void selectDefaultServer() {
        if (!cboServers.getItems().isEmpty() && cboServers.getSelectionModel().isEmpty()) {
            cboServers.getSelectionModel().select(0);
        }
    }

    private void checkIfNoServer() {
        if (cboServers.getItems().isEmpty())
            cboServers.getStyleClass().setAll("jfx-combo-box-warning");
        else
            cboServers.getStyleClass().setAll("jfx-combo-box");
    }

    /**
     * Gets the additional data that needs to be passed into {@link AccountFactory#create(CharacterSelector, String, String, Object)}.
     */
    private Object getAuthAdditionalData() {
        AccountFactory<?> factory = cboType.getSelectionModel().getSelectedItem();
        if (factory == Accounts.FACTORY_AUTHLIB_INJECTOR) {
            return requireNonNull(cboServers.getSelectionModel().getSelectedItem(), "selected server cannot be null");
        }
        return null;
    }

    @FXML
    private void onCreationAccept() {
        if (btnAccept.isDisabled())
            return;

        acceptPane.showSpinner();
        lblCreationWarning.setText("");
        setDisable(true);

        String username = txtUsername.getText();
        String password = txtPassword.getText();
        AccountFactory<?> factory = cboType.getSelectionModel().getSelectedItem();
        Object additionalData = getAuthAdditionalData();

        Task.ofResult(() -> factory.create(new Selector(), username, password, additionalData))
                .finalizedResult(Schedulers.javafx(), account -> {
                    int oldIndex = Accounts.getAccounts().indexOf(account);
                    if (oldIndex == -1) {
                        Accounts.getAccounts().add(account);
                    } else {
                        // adding an already-added account
                        // instead of discarding the new account, we first remove the existing one then add the new one
                        Accounts.getAccounts().remove(oldIndex);
                        Accounts.getAccounts().add(oldIndex, account);
                    }

                    // select the new account
                    Accounts.setSelectedAccount(account);

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

    @FXML
    private void onAddInjecterServer() {
        Controllers.dialog(new AddAuthlibInjectorServerPane());
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
        public GameProfile select(YggdrasilService service, List<GameProfile> profiles) throws NoSelectedCharacterException {
            for (GameProfile profile : profiles) {
                ImageView portraitView = new ImageView();
                portraitView.setSmooth(false);
                portraitView.imageProperty().bind(TexturesLoader.fxAvatarBinding(service, profile.getId(), 32));
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
                    throw new NoSelectedCharacterException();

                return selectedProfile;
            } catch (InterruptedException ignore) {
                throw new NoSelectedCharacterException();
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
        } else if (exception instanceof ServerResponseMalformedException) {
            return i18n("account.failed.server_response_malformed");
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
        } else if (exception instanceof AuthlibInjectorDownloadException) {
            return i18n("account.failed.injector_download_failure");
        } else if (exception.getClass() == AuthenticationException.class) {
            return exception.getLocalizedMessage();
        } else {
            return exception.getClass().getName() + ": " + exception.getLocalizedMessage();
        }
    }
}
