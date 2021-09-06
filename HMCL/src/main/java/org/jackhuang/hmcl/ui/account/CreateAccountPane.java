/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccountFactory;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static javafx.beans.binding.Bindings.bindContent;
import static javafx.beans.binding.Bindings.createBooleanBinding;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.classPropertyFor;

public class CreateAccountPane extends JFXDialogLayout {

    private boolean showMethodSwitcher;
    private AccountFactory<?> factory;

    private Label lblErrorMessage;
    private JFXButton btnAccept;
    private SpinnerPane spinner;
    private JFXButton btnCancel;
    private Node body;

    private Node detailsPane; // AccountDetailsInputPane for Offline / Mojang / authlib-injector, Label for Microsoft
    private Pane detailsContainer;

    private TaskExecutor loginTask;

    public CreateAccountPane() {
        this((AccountFactory<?>) null);
    }

    public CreateAccountPane(AccountFactory<?> factory) {
        if (factory == null) {
            showMethodSwitcher = true;
            String preferred = config().getPreferredLoginType();
            try {
                factory = Accounts.getAccountFactory(preferred);
            } catch (IllegalArgumentException e) {
                factory = Accounts.FACTORY_OFFLINE;
            }
        } else {
            showMethodSwitcher = false;
        }
        this.factory = factory;

        {
            String title;
            if (showMethodSwitcher) {
                title = "account.create";
            } else {
                title = "account.create." + Accounts.getLoginType(factory);
            }
            setHeading(new Label(i18n(title)));
        }

        {
            lblErrorMessage = new Label();

            btnAccept = new JFXButton(i18n("button.ok"));
            btnAccept.getStyleClass().add("dialog-accept");
            btnAccept.setOnAction(e -> onAccept());

            spinner = new SpinnerPane();
            spinner.getStyleClass().add("small-spinner-pane");
            spinner.setContent(btnAccept);

            btnCancel = new JFXButton(i18n("button.cancel"));
            btnCancel.getStyleClass().add("dialog-cancel");
            btnCancel.setOnAction(e -> onCancel());
            onEscPressed(this, btnCancel::fire);

            setActions(lblErrorMessage, new HBox(spinner, btnCancel));
        }

        if (showMethodSwitcher) {
            TabControl.Tab<?>[] tabs = new TabControl.Tab[Accounts.FACTORIES.size()];
            TabControl.Tab<?> selected = null;
            for (int i = 0; i < tabs.length; i++) {
                AccountFactory<?> f = Accounts.FACTORIES.get(i);
                tabs[i] = new TabControl.Tab<>(Accounts.getLoginType(f), Accounts.getLocalizedLoginTypeName(f));
                tabs[i].setUserData(f);
                if (factory == f) {
                    selected = tabs[i];
                }
            }

            TabHeader tabHeader = new TabHeader(tabs);
            tabHeader.getStyleClass().add("add-account-tab-header");
            tabHeader.setMinWidth(USE_PREF_SIZE);
            tabHeader.setMaxWidth(USE_PREF_SIZE);
            tabHeader.getSelectionModel().select(selected);
            onChange(tabHeader.getSelectionModel().selectedItemProperty(),
                    newItem -> {
                        if (newItem == null)
                            return;
                        AccountFactory<?> newMethod = (AccountFactory<?>) newItem.getUserData();
                        config().setPreferredLoginType(Accounts.getLoginType(newMethod));
                        this.factory = newMethod;
                        initDetailsPane();
                    });

            detailsContainer = new StackPane();
            detailsContainer.setPadding(new Insets(15, 0, 0, 0));

            VBox boxBody = new VBox(tabHeader, detailsContainer);
            boxBody.setAlignment(Pos.CENTER);
            body = boxBody;
            setBody(body);

        } else {
            detailsContainer = new StackPane();
            detailsContainer.setPadding(new Insets(10, 0, 0, 0));
            body = detailsContainer;
            setBody(body);
        }
        initDetailsPane();

        setPrefWidth(560);
    }

    public CreateAccountPane(AuthlibInjectorServer authserver) {
        this(Accounts.FACTORY_AUTHLIB_INJECTOR);
        ((AccountDetailsInputPane) detailsPane).selectAuthServer(authserver);
    }

    private void onAccept() {
        spinner.showSpinner();
        lblErrorMessage.setText("");
        body.setDisable(true);

        String username;
        String password;
        Object additionalData;
        if (detailsPane instanceof AccountDetailsInputPane) {
            AccountDetailsInputPane details = (AccountDetailsInputPane) detailsPane;
            username = details.getUsername();
            password = details.getPassword();
            additionalData = details.getAuthServer();
        } else {
            username = null;
            password = null;
            additionalData = null;
        }

        loginTask = Task.supplyAsync(() -> factory.create(new DialogCharacterSelector(), username, password, null, additionalData))
                .whenComplete(Schedulers.javafx(), account -> {
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

                    spinner.hideSpinner();
                    fireEvent(new DialogCloseEvent());
                }, exception -> {
                    if (exception instanceof NoSelectedCharacterException) {
                        fireEvent(new DialogCloseEvent());
                    } else {
                        lblErrorMessage.setText(Accounts.localizeErrorMessage(exception));
                    }
                    body.setDisable(false);
                    spinner.hideSpinner();
                }).executor(true);
    }

    private void onCancel() {
        if (loginTask != null) {
            loginTask.cancel();
        }
        fireEvent(new DialogCloseEvent());
    }

    private void initDetailsPane() {
        if (detailsPane != null) {
            btnAccept.disableProperty().unbind();
            detailsContainer.getChildren().remove(detailsPane);
            lblErrorMessage.setText("");
        }
        if (factory == Accounts.FACTORY_MICROSOFT) {
            Label lblTip = new Label(i18n("account.methods.microsoft.manual")); // TODO
            lblTip.setWrapText(true);
            detailsPane = lblTip;
            btnAccept.setDisable(false);
        } else {
            detailsPane = new AccountDetailsInputPane(factory, btnAccept::fire);
            btnAccept.disableProperty().bind(((AccountDetailsInputPane) detailsPane).validProperty().not());
        }
        detailsContainer.getChildren().add(detailsPane);
    }

    private static class AccountDetailsInputPane extends GridPane {

        // ==== authlib-injector hyperlinks ====
        private static final String[] ALLOWED_LINKS = { "register" };

        private static List<Hyperlink> createHyperlinks(AuthlibInjectorServer server) {
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
        // =====

        private AccountFactory<?> factory;
        private @Nullable JFXComboBox<AuthlibInjectorServer> cboServers;
        private @Nullable JFXTextField txtUsername;
        private @Nullable JFXPasswordField txtPassword;
        private BooleanBinding valid;

        public AccountDetailsInputPane(AccountFactory<?> factory, Runnable onAction) {
            this.factory = factory;

            setVgap(15);
            setHgap(15);
            setAlignment(Pos.CENTER);

            ColumnConstraints col0 = new ColumnConstraints();
            col0.setMinWidth(USE_PREF_SIZE);
            getColumnConstraints().add(col0);
            ColumnConstraints col1 = new ColumnConstraints();
            col1.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(col1);

            int rowIndex = 0;

            if (factory instanceof AuthlibInjectorAccountFactory) {
                Label lblServers = new Label(i18n("account.injector.server"));
                setHalignment(lblServers, HPos.LEFT);
                add(lblServers, 0, rowIndex);

                cboServers = new JFXComboBox<>();
                cboServers.setCellFactory(jfxListCellFactory(server -> new TwoLineListItem(server.getName(), server.getUrl())));
                cboServers.setConverter(stringConverter(AuthlibInjectorServer::getName));
                bindContent(cboServers.getItems(), config().getAuthlibInjectorServers());
                cboServers.getItems().addListener(onInvalidating(
                        () -> Platform.runLater( // the selection will not be updated as expected if we call it immediately
                                cboServers.getSelectionModel()::selectFirst)));
                cboServers.getSelectionModel().selectFirst();
                cboServers.setPromptText(i18n("account.injector.empty"));
                BooleanBinding noServers = createBooleanBinding(cboServers.getItems()::isEmpty, cboServers.getItems());
                classPropertyFor(cboServers, "jfx-combo-box-warning").bind(noServers);
                classPropertyFor(cboServers, "jfx-combo-box").bind(noServers.not());
                HBox.setHgrow(cboServers, Priority.ALWAYS);
                HBox.setMargin(cboServers, new Insets(0, 10, 0, 0));
                cboServers.setMaxWidth(Double.MAX_VALUE);

                HBox linksContainer = new HBox();
                linksContainer.setAlignment(Pos.CENTER);
                onChangeAndOperate(cboServers.valueProperty(), server -> linksContainer.getChildren().setAll(createHyperlinks(server)));
                linksContainer.setMinWidth(USE_PREF_SIZE);

                JFXButton btnAddServer = new JFXButton();
                btnAddServer.setGraphic(SVG.plus(Theme.blackFillBinding(), 20, 20));
                btnAddServer.getStyleClass().add("toggle-icon4");
                btnAddServer.setOnAction(e -> {
                    Controllers.dialog(new AddAuthlibInjectorServerPane());
                });

                HBox boxServers = new HBox(cboServers, linksContainer, btnAddServer);
                add(boxServers, 1, rowIndex);

                rowIndex++;
            }

            if (factory.getLoginType().requiresUsername) {
                Label lblUsername = new Label(i18n("account.username"));
                setHalignment(lblUsername, HPos.LEFT);
                add(lblUsername, 0, rowIndex);

                txtUsername = new JFXTextField();
                txtUsername.setValidators(
                        new RequiredValidator(),
                        new Validator(i18n("input.email"), username -> {
                            if (requiresEmailAsUsername()) {
                                return username.contains("@");
                            } else {
                                return true;
                            }
                        }));
                setValidateWhileTextChanged(txtUsername, true);
                txtUsername.setOnAction(e -> onAction.run());
                add(txtUsername, 1, rowIndex);

                rowIndex++;
            }

            if (factory.getLoginType().requiresPassword) {
                Label lblPassword = new Label(i18n("account.password"));
                setHalignment(lblPassword, HPos.LEFT);
                add(lblPassword, 0, rowIndex);

                txtPassword = new JFXPasswordField();
                txtPassword.setValidators(new RequiredValidator());
                setValidateWhileTextChanged(txtPassword, true);
                txtPassword.setOnAction(e -> onAction.run());
                add(txtPassword, 1, rowIndex);

                rowIndex++;
            }

            valid = new BooleanBinding() {
                {
                    if (cboServers != null)
                        bind(cboServers.valueProperty());
                    if (txtUsername != null)
                        bind(txtUsername.textProperty());
                    if (txtPassword != null)
                        bind(txtPassword.textProperty());
                }

                @Override
                protected boolean computeValue() {
                    if (cboServers != null && cboServers.getValue() == null)
                        return false;
                    if (txtUsername != null && !txtUsername.validate())
                        return false;
                    if (txtPassword != null && !txtPassword.validate())
                        return false;
                    return true;
                }
            };
        }

        private boolean requiresEmailAsUsername() {
            if (factory instanceof YggdrasilAccountFactory) {
                return true;
            } else if ((factory instanceof AuthlibInjectorAccountFactory) && cboServers != null) {
                AuthlibInjectorServer server = cboServers.getValue();
                if (server != null && !server.isNonEmailLogin()) {
                    return true;
                }
            }
            return false;
        }

        public @Nullable AuthlibInjectorServer getAuthServer() {
            return cboServers == null ? null : cboServers.getValue();
        }

        public @Nullable String getUsername() {
            return txtUsername == null ? null : txtUsername.getText();
        }

        public @Nullable String getPassword() {
            return txtPassword == null ? null : txtPassword.getText();
        }

        public BooleanBinding validProperty() {
            return valid;
        }

        public void selectAuthServer(AuthlibInjectorServer authserver) {
            cboServers.getSelectionModel().select(authserver);
        }
    }

    private static class DialogCharacterSelector extends BorderPane implements CharacterSelector {

        private final AdvancedListBox listBox = new AdvancedListBox();
        private final JFXButton cancel = new JFXButton();

        private final CountDownLatch latch = new CountDownLatch(1);
        private GameProfile selectedProfile = null;

        public DialogCharacterSelector() {
            setStyle("-fx-padding: 8px;");

            cancel.setText(i18n("button.cancel"));
            StackPane.setAlignment(cancel, Pos.BOTTOM_RIGHT);
            cancel.setOnAction(e -> latch.countDown());

            listBox.startCategory(i18n("account.choose"));

            setCenter(listBox);

            HBox hbox = new HBox();
            hbox.setAlignment(Pos.CENTER_RIGHT);
            hbox.getChildren().add(cancel);
            setBottom(hbox);

            onEscPressed(this, cancel::fire);
        }

        @Override
        public GameProfile select(YggdrasilService service, List<GameProfile> profiles) throws NoSelectedCharacterException {
            Platform.runLater(() -> {
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
                Controllers.dialog(this);
            });

            try {
                latch.await();

                if (selectedProfile == null)
                    throw new NoSelectedCharacterException();

                return selectedProfile;
            } catch (InterruptedException ignored) {
                throw new NoSelectedCharacterException();
            } finally {
                Platform.runLater(() -> fireEvent(new DialogCloseEvent()));
            }
        }
    }
}
