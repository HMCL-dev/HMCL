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
import com.jfoenix.validation.base.ValidatorBase;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccountFactory;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccountFactory;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static javafx.beans.binding.Bindings.bindContent;
import static javafx.beans.binding.Bindings.createBooleanBinding;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.classPropertyFor;

public class CreateAccountPane extends JFXDialogLayout implements DialogAware {

    private boolean showMethodSwitcher;
    private AccountFactory<?> factory;

    private final Label lblErrorMessage;
    private final JFXButton btnAccept;
    private final SpinnerPane spinner;
    private final Node body;

    private Node detailsPane; // AccountDetailsInputPane for Offline / Mojang / authlib-injector, Label for Microsoft
    private final Pane detailsContainer;

    private final BooleanProperty logging = new SimpleBooleanProperty();
    private final ObjectProperty<OAuthServer.GrantDeviceCodeEvent> deviceCode = new SimpleObjectProperty<>();
    private final WeakListenerHolder holder = new WeakListenerHolder();

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
            lblErrorMessage.setWrapText(true);
            lblErrorMessage.setMaxWidth(400);

            btnAccept = new JFXButton(i18n("account.login"));
            btnAccept.getStyleClass().add("dialog-accept");
            btnAccept.setOnAction(e -> onAccept());

            spinner = new SpinnerPane();
            spinner.getStyleClass().add("small-spinner-pane");
            spinner.setContent(btnAccept);

            JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
            btnCancel.getStyleClass().add("dialog-cancel");
            btnCancel.setOnAction(e -> onCancel());
            onEscPressed(this, btnCancel::fire);

            HBox hbox = new HBox(spinner, btnCancel);
            hbox.setAlignment(Pos.CENTER_RIGHT);

            setActions(lblErrorMessage, hbox);
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

        if (!(factory instanceof MicrosoftAccountFactory)) {
            body.setDisable(true);
        }

        String username;
        String password;
        Object additionalData;
        if (detailsPane instanceof AccountDetailsInputPane) {
            AccountDetailsInputPane details = (AccountDetailsInputPane) detailsPane;
            username = details.getUsername();
            password = details.getPassword();
            additionalData = details.getAdditionalData();
        } else {
            username = null;
            password = null;
            additionalData = null;
        }

        logging.set(true);
        deviceCode.set(null);

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
            VBox vbox = new VBox(8);
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            FXUtils.onChangeAndOperate(deviceCode, deviceCode -> {
                if (deviceCode != null) {
                    FXUtils.copyText(deviceCode.getUserCode());
                    hintPane.setSegment(i18n("account.methods.microsoft.manual", deviceCode.getUserCode(), deviceCode.getVerificationUri()));
                } else {
                    hintPane.setSegment(i18n("account.methods.microsoft.hint"));
                }
            });
            hintPane.setOnMouseClicked(e -> {
                if (deviceCode.get() != null) {
                    FXUtils.copyText(deviceCode.get().getUserCode());
                }
            });

            holder.add(Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(value -> {
                runInFX(() -> deviceCode.set(value));
            }));

            HBox box = new HBox(8);
            JFXHyperlink birthLink = new JFXHyperlink(i18n("account.methods.microsoft.birth"));
            birthLink.setOnAction(e -> FXUtils.openLink("https://support.microsoft.com/zh-cn/account-billing/如何更改-microsoft-帐户上的出生日期-837badbc-999e-54d2-2617-d19206b9540a"));
            JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
            profileLink.setOnAction(e -> FXUtils.openLink("https://account.live.com/editprof.aspx"));
            JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.yggdrasil.purchase"));
            purchaseLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.PURCHASE_URL));
            box.getChildren().setAll(profileLink, birthLink, purchaseLink);
            GridPane.setColumnSpan(box, 2);

            vbox.getChildren().setAll(hintPane, box);

            detailsPane = vbox;
            btnAccept.setDisable(false);
        } else if (factory == Accounts.FACTORY_MOJANG) {
            VBox vbox = new VBox(8);
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            hintPane.setText(i18n("account.methods.yggdrasil.migration.hint"));

            HBox linkPane = new HBox(8);

            JFXHyperlink migrationLink = new JFXHyperlink(i18n("account.methods.yggdrasil.migration"));
            migrationLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.PROFILE_URL));

            JFXHyperlink migrationHowLink = new JFXHyperlink(i18n("account.methods.yggdrasil.migration.how"));
            migrationHowLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.MIGRATION_FAQ_URL));

            linkPane.getChildren().setAll(migrationLink, migrationHowLink);

            vbox.getChildren().setAll(hintPane, linkPane);
            detailsPane = vbox;
            btnAccept.setDisable(true);
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
        private @Nullable JFXTextField txtUUID;
        private BooleanBinding valid;

        public AccountDetailsInputPane(AccountFactory<?> factory, Runnable onAction) {
            this.factory = factory;

            setVgap(22);
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

            if (factory instanceof YggdrasilAccountFactory) {
                HBox box = new HBox();
                GridPane.setColumnSpan(box, 2);

                JFXHyperlink migrationLink = new JFXHyperlink(i18n("account.methods.yggdrasil.migration"));
                migrationLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.PROFILE_URL));

                JFXHyperlink migrationHowLink = new JFXHyperlink(i18n("account.methods.yggdrasil.migration.how"));
                migrationHowLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.MIGRATION_FAQ_URL));

                JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.yggdrasil.purchase"));
                purchaseLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.PURCHASE_URL));

                box.getChildren().setAll(migrationLink, migrationHowLink, purchaseLink);
                add(box, 0, rowIndex);

                rowIndex++;
            }

            if (factory instanceof OfflineAccountFactory) {
                JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.yggdrasil.purchase"));
                purchaseLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.PURCHASE_URL));
                HBox linkPane = new HBox(purchaseLink);
                GridPane.setColumnSpan(linkPane, 2);
                add(linkPane, 0, rowIndex);

                rowIndex++;

                HBox box = new HBox();
                MenuUpDownButton advancedButton = new MenuUpDownButton();
                box.getChildren().setAll(advancedButton);
                advancedButton.setText(i18n("settings.advanced"));
                GridPane.setColumnSpan(box, 2);
                add(box, 0, rowIndex);

                rowIndex++;

                Label lblUUID = new Label(i18n("account.methods.offline.uuid"));
                lblUUID.managedProperty().bind(advancedButton.selectedProperty());
                lblUUID.visibleProperty().bind(advancedButton.selectedProperty());
                setHalignment(lblUUID, HPos.LEFT);
                add(lblUUID, 0, rowIndex);

                txtUUID = new JFXTextField();
                txtUUID.managedProperty().bind(advancedButton.selectedProperty());
                txtUUID.visibleProperty().bind(advancedButton.selectedProperty());
                txtUUID.setValidators(new UUIDValidator());
                txtUUID.promptTextProperty().bind(BindingMapping.of(txtUsername.textProperty()).map(name -> OfflineAccountFactory.getUUIDFromUserName(name).toString()));
                txtUUID.setOnAction(e -> onAction.run());
                add(txtUUID, 1, rowIndex);

                rowIndex++;

                HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
                hintPane.managedProperty().bind(advancedButton.selectedProperty());
                hintPane.visibleProperty().bind(advancedButton.selectedProperty());
                hintPane.setText(i18n("account.methods.offline.uuid.hint"));
                GridPane.setColumnSpan(hintPane, 2);
                add(hintPane, 0, rowIndex);

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
                    if (txtUUID != null)
                        bind(txtUUID.textProperty());
                }

                @Override
                protected boolean computeValue() {
                    if (cboServers != null && cboServers.getValue() == null)
                        return false;
                    if (txtUsername != null && !txtUsername.validate())
                        return false;
                    if (txtPassword != null && !txtPassword.validate())
                        return false;
                    if (txtUUID != null && !txtUUID.validate())
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

        public Object getAdditionalData() {
            if (factory instanceof AuthlibInjectorAccountFactory) {
                return getAuthServer();
            } else if (factory instanceof OfflineAccountFactory) {
                UUID uuid = txtUUID == null ? null : StringUtils.isBlank(txtUUID.getText()) ? null : UUIDTypeAdapter.fromString(txtUUID.getText());
                return new OfflineAccountFactory.AdditionalData(uuid, null);
            } else {
                return null;
            }
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

        public void focus() {
            if (txtUsername != null) {
                txtUsername.requestFocus();
            }
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

    @Override
    public void onDialogShown() {
        if (detailsPane instanceof AccountDetailsInputPane) {
            ((AccountDetailsInputPane) detailsPane).focus();
        }
    }

    private static class UUIDValidator extends ValidatorBase {

        public UUIDValidator() {
            this(i18n("account.methods.offline.uuid.malformed"));
        }

        public UUIDValidator(@NamedArg("message") String message) {
            super(message);
        }

        @Override
        protected void eval() {
            if (srcControl.get() instanceof TextInputControl) {
                evalTextInputField();
            }
        }

        private void evalTextInputField() {
            TextInputControl textField = ((TextInputControl) srcControl.get());
            if (StringUtils.isBlank(textField.getText())) {
                hasErrors.set(false);
                return;
            }

            try {
                UUIDTypeAdapter.fromString(textField.getText());
                hasErrors.set(false);
            } catch (IllegalArgumentException ignored) {
                hasErrors.set(true);
            }
        }
    }

    private static final String MICROSOFT_ACCOUNT_EDIT_PROFILE_URL = "https://support.microsoft.com/zh-cn/account-billing/%E5%A6%82%E4%BD%95%E6%9B%B4%E6%94%B9-microsoft-%E5%B8%90%E6%88%B7%E4%B8%8A%E7%9A%84%E5%87%BA%E7%94%9F%E6%97%A5%E6%9C%9F-837badbc-999e-54d2-2617-d19206b9540a";
}
