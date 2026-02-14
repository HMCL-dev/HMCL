package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXSpinner;
import io.nayuki.qrcodegen.QrCode;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.QrCodeUtils;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MicrosoftAccountLoginPane extends JFXDialogLayout implements DialogAware {
    private final Account accountToRelogin;
    private final Consumer<AuthInfo> loginCallback;
    private final Runnable cancelCallback;

    @SuppressWarnings("FieldCanBeLocal")
    private final WeakListenerHolder holder = new WeakListenerHolder();

    private final ObjectProperty<Step> step = new SimpleObjectProperty<>();

    private TaskExecutor browserTaskExecutor;
    private TaskExecutor deviceTaskExecutor;

    private final JFXButton btnLogin;
    private final SpinnerPane loginButtonSpinner;

    public MicrosoftAccountLoginPane() {
        this(false);
    }

    public MicrosoftAccountLoginPane(boolean bodyonly) {
        this(null, null, null, bodyonly);
    }

    public MicrosoftAccountLoginPane(Account account, Consumer<AuthInfo> callback, Runnable onCancel, boolean bodyonly) {
        this.accountToRelogin = account;
        this.loginCallback = callback;
        this.cancelCallback = onCancel;

        getStyleClass().add("microsoft-login-dialog");
        if (bodyonly) {
            this.pseudoClassStateChanged(PseudoClass.getPseudoClass("bodyonly"), true);
        } else {
            Label heading = new Label(accountToRelogin != null ? i18n("account.login.refresh") : i18n("account.create.microsoft"));
            heading.getStyleClass().add("header-label");
            setHeading(heading);
        }

        this.setMaxWidth(650);

        onEscPressed(this, this::onCancel);

        btnLogin = new JFXButton(i18n("account.login"));
        btnLogin.getStyleClass().add("dialog-accept");

        loginButtonSpinner = new SpinnerPane();
        loginButtonSpinner.getStyleClass().add("small-spinner-pane");
        loginButtonSpinner.setContent(btnLogin);

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());

        setActions(loginButtonSpinner, btnCancel);

        holder.registerWeak(Accounts.OAUTH_CALLBACK.onOpenBrowserAuthorizationCode, event -> Platform.runLater(() -> {
            if (step.get() instanceof Step.StartAuthorizationCodeLogin)
                step.set(new Step.WaitForOpenBrowser(event.getUrl()));
        }));

        holder.registerWeak(Accounts.OAUTH_CALLBACK.onGrantDeviceCode, event -> Platform.runLater(() -> {
            if (step.get() instanceof Step.StartDeviceCodeLogin)
                step.set(new Step.WaitForScanQrCode(event.getUserCode(), event.getVerificationUri()));
        }));

        this.step.set(Accounts.OAUTH_CALLBACK.getClientId().isEmpty()
                ? new Step.Init()
                : new Step.StartAuthorizationCodeLogin());
        FXUtils.onChangeAndOperate(step, this::onStep);
    }

    private void onStep(Step currentStep) {
        VBox rootContainer = new VBox(10);
        setBody(rootContainer);
        rootContainer.setAlignment(Pos.TOP_CENTER);

        if (Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            var snapshotHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            snapshotHint.setSegment(i18n("account.methods.microsoft.snapshot"));
            rootContainer.getChildren().add(snapshotHint);
            btnLogin.setDisable(true);
            loginButtonSpinner.setLoading(false);
            return;
        }

        if (!IntegrityChecker.isOfficial()) {
            var unofficialHintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            unofficialHintPane.setSegment(i18n("unofficial.hint"));
            rootContainer.getChildren().add(unofficialHintPane);
        }

        if (currentStep instanceof Step.Init) {
            btnLogin.setOnAction(e -> this.step.set(new Step.StartAuthorizationCodeLogin()));
            loginButtonSpinner.setLoading(false);

            var hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.setText(i18n("account.methods.microsoft.hint"));
            rootContainer.getChildren().add(hintPane);
        } else if (currentStep instanceof Step.StartAuthorizationCodeLogin) {
            loginButtonSpinner.setLoading(true);
            cancelAllTasks();

            rootContainer.getChildren().add(new JFXSpinner());

            browserTaskExecutor = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.AUTHORIZATION_CODE))
                    .whenComplete(Schedulers.javafx(), this::onLoginCompleted)
                    .executor(true);
        } else if (currentStep instanceof Step.StartDeviceCodeLogin) {
            loginButtonSpinner.setLoading(true);
            cancelAllTasks();

            rootContainer.getChildren().add(new JFXSpinner());

            deviceTaskExecutor = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.DEVICE))
                    .whenComplete(Schedulers.javafx(), this::onLoginCompleted)
                    .executor(true);
        } else if (currentStep instanceof Step.WaitForOpenBrowser wait) {
            btnLogin.setOnAction(e -> {
                FXUtils.openLink(wait.url());
                loginButtonSpinner.setLoading(true);
            });
            loginButtonSpinner.setLoading(false);

            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.setSegment(
                    i18n("account.methods.microsoft.methods.browser.hint", StringUtils.escapeXmlAttribute(wait.url()), wait.url()),
                    FXUtils::copyText
            );

            rootContainer.getChildren().add(hintPane);
        } else if (currentStep instanceof Step.WaitForScanQrCode wait) {
            loginButtonSpinner.setLoading(true);

            var deviceHint = new HintPane(MessageDialogPane.MessageType.INFO);
            deviceHint.setSegment(i18n("account.methods.microsoft.methods.device.hint",
                    StringUtils.escapeXmlAttribute(wait.verificationUri()),
                    wait.verificationUri(),
                    wait.userCode()
            ));

            var qrCode = new SVGPath();
            qrCode.fillProperty().bind(Themes.colorSchemeProperty().getPrimary());
            qrCode.setContent(QrCodeUtils.toSVGPath(QrCode.encodeText(wait.verificationUri() + "?otc=" + wait.userCode(), QrCode.Ecc.MEDIUM)));
            qrCode.setScaleX(3);
            qrCode.setScaleY(3);

            var lblCode = new Label(wait.userCode());
            lblCode.getStyleClass().add("code-label");
            lblCode.setStyle("-fx-font-family: \"" + Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT) + "\";");

            var codeBox = new StackPane(lblCode);
            codeBox.getStyleClass().add("code-box");
            codeBox.setCursor(Cursor.HAND);
            FXUtils.onClicked(codeBox, () -> FXUtils.copyText(wait.userCode()));
            codeBox.setMaxWidth(USE_PREF_SIZE);

            rootContainer.getChildren().addAll(deviceHint, new Group(qrCode), codeBox);
        } else if (currentStep instanceof Step.LoginFailed failed) {
            btnLogin.setOnAction(e -> this.step.set(new Step.StartAuthorizationCodeLogin()));
            loginButtonSpinner.setLoading(false);
            cancelAllTasks();

            HintPane errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);
            errHintPane.setText(failed.message());
            rootContainer.getChildren().add(errHintPane);
        }

        var linkBox = new FlowPane(8, 8);
        linkBox.setAlignment(Pos.CENTER_LEFT);
        linkBox.setPrefWrapLength(500);

        if (currentStep instanceof Step.Init || currentStep instanceof Step.StartAuthorizationCodeLogin || currentStep instanceof Step.WaitForOpenBrowser) {
            JFXHyperlink useQrCode = new JFXHyperlink(i18n("account.methods.microsoft.methods.device"));
            useQrCode.setOnAction(e -> this.step.set(new Step.StartDeviceCodeLogin()));
            linkBox.getChildren().add(useQrCode);
        } else if (currentStep instanceof Step.StartDeviceCodeLogin || currentStep instanceof Step.WaitForScanQrCode) {
            JFXHyperlink userBrowser = new JFXHyperlink(i18n("account.methods.microsoft.methods.browser"));
            userBrowser.setOnAction(e -> this.step.set(new Step.StartAuthorizationCodeLogin()));
            linkBox.getChildren().add(userBrowser);
        }

        JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
        profileLink.setExternalLink("https://account.live.com/editprof.aspx");
        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);

        linkBox.getChildren().addAll(profileLink, purchaseLink);
        rootContainer.getChildren().add(linkBox);

        setBody(rootContainer);
    }

    private void cancelAllTasks() {
        if (browserTaskExecutor != null) browserTaskExecutor.cancel();
        if (deviceTaskExecutor != null) deviceTaskExecutor.cancel();
    }

    private void onCancel() {
        cancelAllTasks();
        if (cancelCallback != null) cancelCallback.run();
        fireEvent(new DialogCloseEvent());
    }

    private void onLoginCompleted(MicrosoftAccount account, Exception exception) {
        if (exception == null) {
            if (accountToRelogin != null) Accounts.getAccounts().remove(accountToRelogin);

            int oldIndex = Accounts.getAccounts().indexOf(account);
            if (oldIndex == -1) {
                Accounts.getAccounts().add(account);
            } else {
                Accounts.getAccounts().remove(oldIndex);
                Accounts.getAccounts().add(oldIndex, account);
            }

            Accounts.setSelectedAccount(account);

            if (loginCallback != null) {
                try {
                    loginCallback.accept(account.logIn());
                } catch (AuthenticationException e) {
                    this.step.set(new Step.LoginFailed(Accounts.localizeErrorMessage(e)));
                    return;
                }
            }
            fireEvent(new DialogCloseEvent());
        } else if (!(exception instanceof CancellationException)) {
            this.step.set(new Step.LoginFailed(Accounts.localizeErrorMessage(exception)));
        }
    }

    private sealed interface Step {
        final class Init implements Step {
        }

        final class StartAuthorizationCodeLogin implements Step {
        }

        record WaitForOpenBrowser(String url) implements Step {

        }

        final class StartDeviceCodeLogin implements Step {
        }

        record WaitForScanQrCode(String userCode, String verificationUri) implements Step {

        }

        record LoginFailed(String message) implements Step {
        }
    }

}

