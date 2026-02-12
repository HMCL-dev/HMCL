package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import io.nayuki.qrcodegen.QrCode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.QrCodeUtils;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MicrosoftAccountLoginPane extends JFXDialogLayout implements DialogAware {
    private final Account accountToRelogin;
    private final Consumer<AuthInfo> loginCallback;
    private final Runnable cancelCallback;

    @SuppressWarnings("FieldCanBeLocal")
    private final WeakListenerHolder holder = new WeakListenerHolder();

    private final ObjectProperty<Step> step = new SimpleObjectProperty<>(new Step.Init());

    private TaskExecutor browserTaskExecutor;

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
        if (!bodyonly) {
            Label heading = new Label(accountToRelogin != null ? i18n("account.login.refresh") : i18n("account.create.microsoft"));
            heading.getStyleClass().add("header-label");
            setHeading(heading);
        } else {
            setStyle("-fx-padding: 0px 0px 0px 0px;");
        }

        onEscPressed(this, this::onCancel);

        btnLogin = new JFXButton(i18n("account.login"));
        btnLogin.getStyleClass().add("dialog-accept");
        btnLogin.setOnAction(e -> step.set(new Step.StartLogin()));

        loginButtonSpinner = new SpinnerPane();
        loginButtonSpinner.getStyleClass().add("small-spinner-pane");
        loginButtonSpinner.setContent(btnLogin);

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());

        HBox actions = new HBox(10, loginButtonSpinner, btnCancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        setActions(actions);

        Accounts.OAUTH_CALLBACK.onOpenBrowserAuthorizationCode.registerWeak(event -> runInFX(() ->
                step.set(new Step.WaitForOpenBrowser(event.getUrl()))));

        FXUtils.onChangeAndOperate(step, this::onStep);
    }

    private void onStep(Step currentStep) {
        VBox rootContainer = new VBox(10);
        setBody(rootContainer);
        rootContainer.setPadding(new Insets(5, 0, 0, 0));
        rootContainer.setAlignment(Pos.TOP_CENTER);

        HBox linkBox = new HBox(15);
        linkBox.setAlignment(Pos.CENTER_RIGHT);
        linkBox.setPadding(new Insets(5, 0, 0, 0));

        if (currentStep instanceof Step.Init) {
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.setText(i18n("account.methods.microsoft.hint"));
            rootContainer.getChildren().add(hintPane);
        }

        if (Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            HintPane snapshotHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            snapshotHint.setSegment(i18n("account.methods.microsoft.snapshot"));
            rootContainer.getChildren().add(snapshotHint);
            btnLogin.setDisable(true);
            loginButtonSpinner.setLoading(false);
            return;
        }

        if (currentStep instanceof Step.Init) {
            btnLogin.setText(i18n("account.login"));
            btnLogin.setOnAction(e -> this.step.set(new Step.StartLogin()));
            loginButtonSpinner.setLoading(false);
        } else if (currentStep instanceof Step.StartLogin) {
            loginButtonSpinner.setLoading(true);

            browserTaskExecutor = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.AUTHORIZATION_CODE))
                    .whenComplete(Schedulers.javafx(), (account, exception) -> {
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
                    })
                    .executor(true);
        } else if (currentStep instanceof Step.WaitForOpenBrowser wait) {
            btnLogin.setText("打开浏览器");
            btnLogin.setOnAction(e -> {
                FXUtils.openLink(wait.url());
                loginButtonSpinner.setLoading(true);
            });
            loginButtonSpinner.setLoading(false);

            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            {
                // TODO: i18n
                ArrayList<Node> content = new ArrayList<>();
                content.add(new Text("点击“打开浏览器”按钮以在浏览器中打开登录页面，或者"));
                content.add(new JFXHyperlink("复制链接", wait.url()));
                content.add(new Text("并在浏览器中打开。"));

                hintPane.setChildren(content.toArray(new Node[0]));
            }

            rootContainer.getChildren().add(hintPane);

            JFXHyperlink useQrCode = new JFXHyperlink("使用二维码登录"); // TODO: i18n
            useQrCode.setOnAction(e -> this.step.set(new Step.WaitForScanQrCode(wait.url())));
            linkBox.getChildren().add(useQrCode);
        } else if (currentStep instanceof Step.WaitForScanQrCode wait) {
            loginButtonSpinner.setLoading(true);

            VBox devicePanel = new VBox(10);
            devicePanel.setAlignment(Pos.CENTER);
            devicePanel.setPadding(new Insets(10));
            devicePanel.setPrefWidth(280);
            HBox.setHgrow(devicePanel, Priority.ALWAYS);

            QrCode code = QrCode.encodeText(wait.url(), QrCode.Ecc.MEDIUM);

            var qrCodeView = new SVGPath();
            qrCodeView.fillProperty().bind(Themes.colorSchemeProperty().getPrimary());
            qrCodeView.setContent(QrCodeUtils.toSVGPath(code));
            qrCodeView.setScaleX(2.5);
            qrCodeView.setScaleY(2.5);

            devicePanel.getChildren().setAll(new Group(qrCodeView));

            rootContainer.getChildren().add(devicePanel);

            JFXHyperlink userBrowser = new JFXHyperlink("使用浏览器登录"); // TODO: i18n
            userBrowser.setOnAction(e -> this.step.set(new Step.WaitForOpenBrowser(wait.url())));
            linkBox.getChildren().add(userBrowser);
        } else if (currentStep instanceof Step.LoginFailed failed) {
            loginButtonSpinner.setLoading(true);

            HintPane errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);
            errHintPane.setText(failed.message());
            loginButtonSpinner.showSpinner();
        }

        JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
        profileLink.setExternalLink("https://account.live.com/editprof.aspx");
        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);
        JFXHyperlink forgotLink = new JFXHyperlink(i18n("account.methods.forgot_password"));
        forgotLink.setExternalLink("https://account.live.com/ResetPassword.aspx");

        linkBox.getChildren().addAll(profileLink, purchaseLink, forgotLink);
        rootContainer.getChildren().add(linkBox);

        setBody(rootContainer);
    }

    private void cancelAllTasks() {
        if (browserTaskExecutor != null) browserTaskExecutor.cancel();
    }

    private void onCancel() {
        cancelAllTasks();
        if (cancelCallback != null) cancelCallback.run();
        fireEvent(new DialogCloseEvent());
    }

    private sealed interface Step {
        final class Init implements Step {
        }

        final class StartLogin implements Step {
        }

        record WaitForOpenBrowser(String url) implements Step {

        }

        record WaitForScanQrCode(String url) implements Step {

        }

        record LoginFailed(String message) implements Step {
        }
    }

}

