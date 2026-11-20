// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3ComboBox;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3PasswordField;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3TextInputLayout;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccountFactory;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.authlibinjector.BoundAuthlibInjectorAccountFactory;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccountFactory;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static javafx.beans.binding.Bindings.bindContent;
import static javafx.beans.binding.Bindings.createBooleanBinding;
import static org.jackhuang.hmcl.setting.SettingsManager.getAuthlibInjectorServers;
import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class CreateAccountPane extends LauncherDialog {
    private static final Pattern USERNAME_CHECKER_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private boolean showMethodSwitcher;
    private AccountFactory<?> factory;

    private final Label lblErrorMessage;
    private final M3Button btnAccept;
    /// Tracks the pending account submission, including username confirmation.
    private final BooleanProperty submitting;
    private final Node body;
    /// Cancels account creation and any pending login task.
    private final M3Button btnCancel;
    private Node detailsPane; // AccountDetailsInputPane for Offline / Mojang / authlib-injector, Label for Microsoft
    private final Pane detailsContainer;

    private TaskExecutor loginTask;

    public CreateAccountPane() {
        this((AccountFactory<?>) null);
    }

    public CreateAccountPane(AccountFactory<?> factory) {
        if (factory == null) {
            if (AccountListPage.RESTRICTED.get()) {
                showMethodSwitcher = false;
                factory = Accounts.FACTORY_MICROSOFT;
            } else {
                showMethodSwitcher = true;
                String preferred = settings().preferredLoginTypeProperty().get();
                try {
                    factory = Accounts.getAccountFactory(preferred);
                } catch (IllegalArgumentException e) {
                    factory = Accounts.FACTORY_OFFLINE;
                }
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
            getDialogPane().setHeadline(i18n(title));
        }

        {
            lblErrorMessage = new Label();
            lblErrorMessage.setWrapText(true);
            lblErrorMessage.setMaxWidth(400);

            btnAccept = new M3Button(i18n("account.login"), M3ButtonVariant.TEXT);
            btnAccept.setOnAction(e -> {
                e.consume();
                onAccept();
            });

            submitting = FXUtils.newButtonLoadingProperty(btnAccept);

            btnCancel = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
            btnCancel.setOnAction(e -> onCancel());
            btnCancel.setCancelButton(true);

            lblErrorMessage.visibleProperty().bind(lblErrorMessage.textProperty().isNotEmpty());
            lblErrorMessage.managedProperty().bind(lblErrorMessage.visibleProperty());
        }

        if (showMethodSwitcher) {
            TabItem<?>[] tabs = new TabItem<?>[Accounts.FACTORIES.size()];
            var tabToFactory = new IdentityHashMap<TabItem<?>, AccountFactory<?>>();

            @Nullable TabItem<?> selected = null;
            for (int i = 0; i < tabs.length; i++) {
                AccountFactory<?> f = Accounts.FACTORIES.get(i);
                TabItem<Node> tab = new TabItem<>();
                tabs[i] = tab;

                tab.setText(Accounts.getLocalizedLoginTypeName(f));
                tabToFactory.put(tab, f);
                if (factory == f) {
                    selected = tab;
                }
            }

            TabHeader tabHeader = new TabHeader(tabs);
            tabHeader.getStyleClass().add("add-account-tab-header");
            tabHeader.setMinWidth(Region.USE_PREF_SIZE);
            tabHeader.setMaxWidth(Region.USE_PREF_SIZE);
            if (selected != null)
                tabHeader.getController().selectTab(selected);
            onChange(tabHeader.getController().selectedTabProperty(),
                    newItem -> {
                        if (newItem == null)
                            return;
                        AccountFactory<?> newMethod = tabToFactory.get(newItem);
                        settings().preferredLoginTypeProperty().set(Accounts.getLoginType(newMethod));
                        this.factory = newMethod;
                        initDetailsPane();
                    });

            detailsContainer = new StackPane();
            detailsContainer.setPadding(new Insets(15, 0, 0, 0));

            VBox boxBody = new VBox(tabHeader, detailsContainer);
            boxBody.setAlignment(Pos.CENTER);
            body = boxBody;

        } else {
            detailsContainer = new StackPane();
            detailsContainer.setPadding(new Insets(10, 0, 0, 0));
            body = detailsContainer;
        }
        initDetailsPane();

        getDialogPane().setPrefWidth(560);
        setFormContent(new VBox(8, body, lblErrorMessage));
        setOnShown(event -> {
            if (detailsPane instanceof AccountDetailsInputPane input)
                input.focus();
        });
    }

    public CreateAccountPane(AuthlibInjectorServer authServer) {
        this(Accounts.getAccountFactoryByAuthlibInjectorServer(authServer));
    }

    private void onAccept() {
        submitting.set(true);
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

        Runnable doCreate = () -> {
            loginTask = Task.supplyAsync(() -> factory.create(new DialogCharacterSelector(), username, password, null, additionalData))
                    .whenComplete(Schedulers.javafx(), account -> {
                        if (Accounts.isAccountFilesReadOnly(account)) {
                            body.setDisable(false);
                            submitting.set(false);
                            Controllers.confirmBackupAndOverwrite(i18n("account.storage.read_only"), () -> {
                                Accounts.forceOverwriteAccountFiles(account);
                                completeLogin(account);
                            });
                            return;
                        }

                        completeLogin(account);
                    }, exception -> {
                        if (exception instanceof NoSelectedCharacterException) {
                            closeDialog();
                        } else {
                            lblErrorMessage.setText(Accounts.localizeErrorMessage(exception));
                        }
                        body.setDisable(false);
                        submitting.set(false);
                    }).executor(true);
        };

        if (factory instanceof OfflineAccountFactory && username != null && (!USERNAME_CHECKER_PATTERN.matcher(username).matches() || username.length() > 16)) {
            Controllers.confirmWithCountdown(i18n("account.methods.offline.name.invalid"), i18n("message.warning"), 10,
                    MessageType.WARNING,
                    doCreate, () -> {
                        body.setDisable(false);
                        submitting.set(false);
                    });
        } else {
            doCreate.run();
        }
    }

    /// Adds the logged-in account, selects it, and closes the dialog.
    private void completeLogin(Account account) {
        int oldIndex = Accounts.getAccounts().indexOf(account);
        if (oldIndex == -1) {
            Accounts.getAccounts().add(account);
        } else {
            // Add an already-added account by replacing the existing entry with the new credentials.
            Accounts.getAccounts().remove(oldIndex);
            Accounts.getAccounts().add(oldIndex, account);
        }

        Accounts.setSelectedAccount(account);

        submitting.set(false);
        closeDialog();
    }

    private void onCancel() {
        if (loginTask != null) {
            loginTask.cancel();
        }
        closeDialog();
    }

    private void initDetailsPane() {
        if (detailsPane != null) {
            btnAccept.disableProperty().unbind();
            detailsContainer.getChildren().remove(detailsPane);
            lblErrorMessage.setText("");
        }

        if (factory == Accounts.FACTORY_MICROSOFT) {
            detailsPane = new MicrosoftAccountLoginPane(getDialogPane(), null, null, null);
        } else {
            detailsPane = new AccountDetailsInputPane(factory, btnAccept::fire);
            btnAccept.disableProperty().bind(submitting.or(((AccountDetailsInputPane) detailsPane).validProperty().not()));
            getDialogPane().getActions().setAll(btnCancel, btnAccept);
        }

        detailsContainer.getChildren().add(detailsPane);
    }

    private static class AccountDetailsInputPane extends GridPane {

        // ==== authlib-injector hyperlinks ====
        private static final String[] ALLOWED_LINKS = {"homepage", "register"};

        private static List<Hyperlink> createHyperlinks(AuthlibInjectorServer server) {
            if (server == null) {
                return emptyList();
            }

            Map<String, String> links = server.getLinks();
            List<Hyperlink> result = new ArrayList<>();
            for (String key : ALLOWED_LINKS) {
                String value = links.get(key);
                if (value != null) {
                    JFXHyperlink link = new JFXHyperlink(i18n("account.injector.link." + key));
                    FXUtils.installSlowTooltip(link, value);
                    link.setOnAction(e -> FXUtils.openLink(value));
                    result.add(link);
                }
            }
            return unmodifiableList(result);
        }
        // =====

        private final AccountFactory<?> factory;
        private @Nullable AuthlibInjectorServer server;
        private @Nullable M3ComboBox<AuthlibInjectorServer> cboServers;
        private @Nullable M3TextField txtUsername;
        /// Validates the username against the selected account server.
        private @Nullable M3TextInputLayout usernameLayout;
        /// Masked input for account types that require a password.
        private @Nullable M3PasswordField txtPassword;
        private @Nullable M3TextField txtUUID;
        private final BooleanBinding valid;

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

            if (!IntegrityChecker.isOfficial() && !(factory instanceof OfflineAccountFactory)) {
                HintPane hintPane = new HintPane(MessageType.WARNING);
                hintPane.setSegment(i18n("unofficial.hint"));
                GridPane.setColumnSpan(hintPane, 2);
                add(hintPane, 0, rowIndex);

                rowIndex++;
            }

            if (factory instanceof BoundAuthlibInjectorAccountFactory) {
                this.server = ((BoundAuthlibInjectorAccountFactory) factory).getServer();

                Label lblServers = new Label(i18n("account.injector.server"));
                setHalignment(lblServers, HPos.LEFT);
                add(lblServers, 0, rowIndex);

                Label lblServerName = new Label(this.server.getName());
                lblServerName.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(lblServerName, Priority.ALWAYS);

                HBox linksContainer = new HBox();
                linksContainer.setAlignment(Pos.CENTER);
                linksContainer.getChildren().setAll(createHyperlinks(this.server));
                linksContainer.setMinWidth(USE_PREF_SIZE);

                HBox boxServers = new HBox(lblServerName, linksContainer);
                boxServers.setAlignment(Pos.CENTER_LEFT);
                add(boxServers, 1, rowIndex);

                rowIndex++;
            } else if (factory instanceof AuthlibInjectorAccountFactory) {
                Label lblServers = new Label(i18n("account.injector.server"));
                setHalignment(lblServers, HPos.LEFT);
                add(lblServers, 0, rowIndex);

                cboServers = new M3ComboBox<>();
                cboServers.setConverter(it -> String.format("%s (%s)", it.getName(), it.getDisplayHostUrl()));
                bindContent(cboServers.getItems(), getAuthlibInjectorServers());
                Runnable selectFirstServer = () -> cboServers.setValue(
                        cboServers.getItems().isEmpty() ? null : cboServers.getItems().getFirst());
                cboServers.getItems().addListener(onInvalidating(selectFirstServer));
                selectFirstServer.run();

                Label noServersLabel = new Label(i18n("account.injector.empty"));
                BooleanBinding noServers = createBooleanBinding(cboServers.getItems()::isEmpty, cboServers.getItems());

                HBox.setHgrow(cboServers, Priority.ALWAYS);
                HBox.setMargin(cboServers, new Insets(0, 10, 0, 0));
                cboServers.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(noServersLabel, Priority.ALWAYS);
                HBox.setMargin(noServersLabel, new Insets(0, 10, 0, 0));
                noServersLabel.setMaxWidth(Double.MAX_VALUE);

                HBox linksContainer = new HBox();
                linksContainer.setAlignment(Pos.CENTER);
                onChangeAndOperate(cboServers.valueProperty(), server -> {
                    this.server = server;
                    linksContainer.getChildren().setAll(createHyperlinks(server));

                    if (usernameLayout != null)
                        usernameLayout.validate();
                });
                linksContainer.setMinWidth(USE_PREF_SIZE);

                M3IconButton btnAddServer = new M3IconButton(SVG.ADD.createIcon(20));
                btnAddServer.setOnAction(e -> {
                    Controllers.dialog(new AddAuthlibInjectorServerPane());
                });

                noServersLabel.visibleProperty().bind(noServers);
                noServersLabel.managedProperty().bind(noServers);
                cboServers.visibleProperty().bind(noServers.not());
                cboServers.managedProperty().bind(noServers.not());

                HBox boxServers = new HBox(cboServers, noServersLabel, linksContainer, btnAddServer);
                boxServers.setAlignment(Pos.CENTER_LEFT);
                add(boxServers, 1, rowIndex);

                rowIndex++;
            }

            if (factory.getLoginType().requiresUsername) {
                Label lblUsername = new Label(i18n("account.username"));
                setHalignment(lblUsername, HPos.LEFT);
                add(lblUsername, 0, rowIndex);

                txtUsername = FXUtils.newTextField();
                usernameLayout = FXUtils.newValidatedTextField(txtUsername,
                        new RequiredValidator(),
                        new Validator(i18n("input.email"), username -> {
                            if (requiresEmailAsUsername()) {
                                return username.contains("@");
                            } else {
                                return true;
                            }
                        }));
                txtUsername.setOnAction(e -> onAction.run());
                add(usernameLayout, 1, rowIndex);

                rowIndex++;
            }

            if (factory.getLoginType().requiresPassword) {
                Label lblPassword = new Label(i18n("account.password"));
                setHalignment(lblPassword, HPos.LEFT);
                add(lblPassword, 0, rowIndex);

                txtPassword = FXUtils.newPasswordField();
                lblPassword.setLabelFor(txtPassword);
                txtPassword.setOnAction(e -> onAction.run());
                add(FXUtils.newValidatedTextField(txtPassword, new RequiredValidator()), 1, rowIndex);

                rowIndex++;
            }

            if (factory instanceof OfflineAccountFactory) {
                txtUsername.setPromptText(i18n("account.methods.offline.name.special_characters"));
                FXUtils.installFastTooltip(txtUsername, i18n("account.methods.offline.name.special_characters"));

                JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
                purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);
                HBox linkPane = new HBox(purchaseLink);
                GridPane.setColumnSpan(linkPane, 2);
                add(linkPane, 0, rowIndex);

                rowIndex++;

                HBox box = new HBox();
                ExpandablePane advancedPane = new ExpandablePane();
                box.getChildren().setAll(advancedPane);
                advancedPane.setText(i18n("settings.advanced"));
                GridPane.setColumnSpan(box, 2);
                add(box, 0, rowIndex);

                GridPane advancedMenu = new GridPane();
                advancedMenu.setPadding(new Insets(8, 0, 0, 0));
                advancedMenu.setHgap(8);
                advancedMenu.setVgap(8);

                Label lblUUID = new Label(i18n("account.methods.offline.uuid"));
                setHalignment(lblUUID, HPos.LEFT);
                advancedMenu.add(lblUUID, 0, 0);

                txtUUID = FXUtils.newTextField();
                var uuidLayout = FXUtils.newValidatedTextField(txtUUID, (input, text) -> {
                    if (StringUtils.isBlank(text)) return null;
                    try {
                        UUIDs.parse(text);
                        return null;
                    } catch (IllegalArgumentException ignored) {
                        return i18n("account.methods.offline.uuid.malformed");
                    }
                });
                txtUUID.promptTextProperty().bind(BindingMapping.of(txtUsername.textProperty()).map(name -> OfflineAccountFactory.getUUIDFromUserName(name).toString()));
                txtUUID.setOnAction(e -> onAction.run());
                advancedMenu.add(uuidLayout, 1, 0);

                HintPane hintPane = new HintPane(MessageType.WARNING);
                hintPane.setText(i18n("account.methods.offline.uuid.hint"));
                GridPane.setColumnSpan(hintPane, 2);
                advancedMenu.add(hintPane, 0, 1);

                advancedPane.getContent().add(advancedMenu);
            }

            valid = new BooleanBinding() {
                {
                    if (cboServers != null)
                        bind(cboServers.valueProperty());
                    if (txtUsername != null)
                        bind(txtUsername.errorProperty());
                    if (txtPassword != null)
                        bind(txtPassword.errorProperty());
                    if (txtUUID != null)
                        bind(txtUUID.errorProperty());
                }

                @Override
                protected boolean computeValue() {
                    if (cboServers != null && cboServers.getValue() == null)
                        return false;
                    if (txtUsername != null && txtUsername.isError())
                        return false;
                    if (txtPassword != null && txtPassword.isError())
                        return false;
                    if (txtUUID != null && txtUUID.isError())
                        return false;
                    return true;
                }
            };
        }

        private boolean requiresEmailAsUsername() {
            if ((factory instanceof AuthlibInjectorAccountFactory) && this.server != null) {
                return !server.isNonEmailLogin();
            }
            if (factory instanceof BoundAuthlibInjectorAccountFactory bound) {
                return !bound.getServer().isNonEmailLogin();
            }
            return false;
        }

        public Object getAdditionalData() {
            if (factory instanceof AuthlibInjectorAccountFactory) {
                return getAuthServer();
            } else if (factory instanceof OfflineAccountFactory) {
                UUID uuid = txtUUID == null ? null : StringUtils.isBlank(txtUUID.getText()) ? null : UUIDs.parse(txtUUID.getText());
                return new OfflineAccountFactory.AdditionalData(uuid, null);
            } else {
                return null;
            }
        }

        public @Nullable AuthlibInjectorServer getAuthServer() {
            return this.server;
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

        public void focus() {
            if (txtUsername != null) {
                txtUsername.requestFocus();
            }
        }
    }

    /// Prompts for a Yggdrasil profile while the authentication worker waits for the selection.
    public static class DialogCharacterSelector implements CharacterSelector {
        /// Shows the profiles on the FX thread and dismisses the chooser if the worker is interrupted.
        @Override
        public GameProfile select(YggdrasilService service, List<GameProfile> profiles) throws NoSelectedCharacterException {
            var latch = new CountDownLatch(1);
            var selected = new AtomicReference<@Nullable GameProfile>();
            var chooser = new AtomicReference<@Nullable LauncherDialog>();
            Platform.runLater(() -> {
                var dialog = new LauncherDialog();
                chooser.set(dialog);
                var listBox = new AdvancedListBox();
                dialog.getDialogPane().setHeadline(i18n("account.choose"));
                dialog.setFormContent(listBox);
                var cancel = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
                cancel.setCancelButton(true);
                cancel.setOnAction(e -> latch.countDown());
                dialog.getDialogPane().getActions().add(cancel);

                for (GameProfile profile : profiles) {
                    Canvas portraitCanvas = new Canvas(32, 32);
                    TexturesLoader.bindAvatar(portraitCanvas, service, profile.getId());

                    IconedItem accountItem = new IconedItem(portraitCanvas, profile.getName());
                    FXUtils.onClicked(accountItem, () -> {
                        selected.set(profile);
                        latch.countDown();
                    });
                    listBox.add(accountItem);
                }
                Controllers.dialog(dialog);
            });

            try {
                latch.await();

                @Nullable GameProfile selectedProfile = selected.get();
                if (selectedProfile == null)
                    throw new NoSelectedCharacterException();

                return selectedProfile;
            } catch (InterruptedException ignored) {
                throw new NoSelectedCharacterException();
            } finally {
                Platform.runLater(() -> {
                    @Nullable LauncherDialog dialog = chooser.get();
                    if (dialog != null)
                        dialog.closeDialog();
                });
            }
        }
    }

    private static final String MICROSOFT_ACCOUNT_EDIT_PROFILE_URL = "https://support.microsoft.com/account-billing/837badbc-999e-54d2-2617-d19206b9540a";
}
