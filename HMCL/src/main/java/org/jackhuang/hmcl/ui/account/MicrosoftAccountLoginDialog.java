package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MicrosoftAccountLoginDialog extends JFXDialogLayout implements DialogAware {
    private final JFXButton btnLogin;
    private final SpinnerPane spinner;
    private final HintPane hintPane;
    private final HintPane errHintPane;

    private final BooleanProperty logging = new SimpleBooleanProperty();
    private final ObjectProperty<OAuthServer.GrantDeviceCodeEvent> deviceCode = new SimpleObjectProperty<>();
    private final WeakListenerHolder holder = new WeakListenerHolder();

    private final Account accountToRelogin;
    private final Consumer<AuthInfo> loginCallback;
    private final Runnable cancelCallback;

    private TaskExecutor loginTask;

    public MicrosoftAccountLoginDialog() {
        this(null, null, null);
    }

    public MicrosoftAccountLoginDialog(Account account, Consumer<AuthInfo> callback, Runnable onCancel) {
        this.accountToRelogin = account;
        this.loginCallback = callback;
        this.cancelCallback = onCancel;

        hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
        errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);

        if (accountToRelogin != null) {
            setHeading(new Label(i18n("account.login.refresh")));
        } else {
            setHeading(new Label(i18n("account.create.microsoft")));
        }

        btnLogin = new JFXButton(i18n("account.login"));
        btnLogin.getStyleClass().add("dialog-accept");
        btnLogin.setOnAction(e -> onLogin());

        spinner = new SpinnerPane();
        spinner.getStyleClass().add("small-spinner-pane");
        spinner.setContent(btnLogin);

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());
        onEscPressed(this, btnCancel::fire);

        HBox actionsBox = new HBox(spinner, btnCancel);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        setActions(actionsBox);

        VBox bodyPane = new VBox(8);
        bodyPane.setPadding(new Insets(10, 0, 0, 0));

        if (Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            hintPane.setSegment(i18n("account.methods.microsoft.snapshot"));
            bodyPane.getChildren().add(hintPane);
            btnLogin.setDisable(true);
            return;
        }

        if (!IntegrityChecker.isOfficial()) {
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            hintPane.setSegment(i18n("unofficial.hint"));
            bodyPane.getChildren().add(hintPane);
        }

        VBox codeBox = new VBox(8);
        Label hint = new Label(i18n("account.methods.microsoft.code"));
        Label code = new Label();
        code.setMouseTransparent(true);
        code.setStyle("-fx-font-size: 24");
        codeBox.getChildren().addAll(hint, code);
        codeBox.setAlignment(Pos.CENTER);
        bodyPane.getChildren().add(codeBox);

        errHintPane.setVisible(false);
        errHintPane.setManaged(false);

        codeBox.setVisible(false);
        codeBox.setManaged(false);

        FXUtils.onChangeAndOperate(deviceCode, deviceCode -> {
            if (deviceCode != null) {
                FXUtils.copyText(deviceCode.getUserCode());
                code.setText(deviceCode.getUserCode());
                hintPane.setSegment(i18n("account.methods.microsoft.manual", deviceCode.getVerificationUri()));
                codeBox.setVisible(true);
                codeBox.setManaged(true);
            } else {
                hintPane.setSegment(i18n("account.methods.microsoft.hint"));
                codeBox.setVisible(false);
                codeBox.setManaged(false);
            }
        });

        hintPane.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasError = !newVal.isEmpty();
            errHintPane.setSegment(newVal);
            errHintPane.setVisible(hasError);
            errHintPane.setManaged(hasError);

            hintPane.setVisible(!hasError);
            hintPane.setManaged(!hasError);
            boolean showCode = !hasError && deviceCode.get() != null;
            codeBox.setVisible(showCode);
            codeBox.setManaged(showCode);
        });

        FXUtils.onClicked(codeBox, () -> {
            if (deviceCode.get() != null) FXUtils.copyText(deviceCode.get().getUserCode());
        });

        holder.add(Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(value -> runInFX(() -> deviceCode.set(value))));

        HBox linkBox = new HBox();
        JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
        profileLink.setExternalLink("https://account.live.com/editprof.aspx");
        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);
        JFXHyperlink deauthorizeLink = new JFXHyperlink(i18n("account.methods.microsoft.deauthorize"));
        deauthorizeLink.setExternalLink("https://account.live.com/consent/Edit?client_id=000000004C794E0A");
        JFXHyperlink forgotpasswordLink = new JFXHyperlink(i18n("account.methods.forgot_password"));
        forgotpasswordLink.setExternalLink("https://account.live.com/ResetPassword.aspx");
        linkBox.getChildren().setAll(profileLink, purchaseLink, deauthorizeLink, forgotpasswordLink);

        bodyPane.getChildren().addAll(hintPane, errHintPane, linkBox);

        setBody(bodyPane);

        setPrefWidth(560);
    }

    private void onLogin() {
        spinner.showSpinner();
        logging.set(true);
        deviceCode.set(null);

        if (accountToRelogin != null) {
            loginTask = Task.supplyAsync(accountToRelogin::logIn).whenComplete(Schedulers.javafx(), authInfo -> {
                if (loginCallback != null) {
                    loginCallback.accept(authInfo);
                }
                spinner.hideSpinner();
                fireEvent(new DialogCloseEvent());
            }, exception -> {
                errHintPane.setText(Accounts.localizeErrorMessage(exception));
                spinner.hideSpinner();
                logging.set(false);
            }).executor(true);
        } else {
            loginTask = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, null)).whenComplete(Schedulers.javafx(), account -> {
                int oldIndex = Accounts.getAccounts().indexOf(account);
                if (oldIndex == -1) {
                    Accounts.getAccounts().add(account);
                } else {
                    Accounts.getAccounts().remove(oldIndex);
                    Accounts.getAccounts().add(oldIndex, account);
                }

                Accounts.setSelectedAccount(account);

                spinner.hideSpinner();
                fireEvent(new DialogCloseEvent());
            }, exception -> {
                errHintPane.setText(Accounts.localizeErrorMessage(exception));
                spinner.hideSpinner();
                logging.set(false);
            }).executor(true);
        }
    }

    private void onCancel() {
        if (loginTask != null) {
            loginTask.cancel();
        }
        if (cancelCallback != null) {
            cancelCallback.run();
        }
        fireEvent(new DialogCloseEvent());
    }

    @Override
    public void onDialogShown() {
        btnLogin.requestFocus();
    }
}
