package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.OAuthServer;
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

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MicrosoftAccountLoginPane extends JFXDialogLayout implements DialogAware {
    private final Account accountToRelogin;
    private final Consumer<AuthInfo> loginCallback;
    private final Runnable cancelCallback;

    @SuppressWarnings("FieldCanBeLocal")
    private final WeakListenerHolder holder = new WeakListenerHolder();
    private final ObjectProperty<OAuthServer.GrantDeviceCodeEvent> deviceCode = new SimpleObjectProperty<>();
    private final ObjectProperty<String> browserUrl = new SimpleObjectProperty<>();
    private final Label lblCode;

    private TaskExecutor browserTaskExecutor;
    private TaskExecutor deviceTaskExecutor;

    private final SpinnerPane loginButtonSpinner;
    private final HBox authMethodsContentBox = new HBox(0);
    private final HintPane errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);
    private HintPane unofficialHintPane;

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

        JFXButton btnLogin = new JFXButton(i18n("account.login"));
        btnLogin.getStyleClass().add("dialog-accept");
        btnLogin.setOnAction(e -> startLoginTasks());

        loginButtonSpinner = new SpinnerPane();
        loginButtonSpinner.getStyleClass().add("small-spinner-pane");
        loginButtonSpinner.setContent(btnLogin);

        lblCode = new Label();
        lblCode.getStyleClass().add("code-label");
        lblCode.setStyle("-fx-font-family: \"" + Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT) + "\"");

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());

        HBox actions = new HBox(10, loginButtonSpinner, btnCancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        setActions(actions);

        VBox rootContainer = new VBox(10);
        setBody(rootContainer);
        rootContainer.setPadding(new Insets(5, 0, 0, 0));
        rootContainer.setAlignment(Pos.TOP_CENTER);

        HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
        hintPane.setText(i18n("account.methods.microsoft.hint"));
        FXUtils.onChangeAndOperate(deviceCode, event -> {
            if (event != null)
                hintPane.setSegment(i18n("account.methods.microsoft.manual", event.getVerificationUri()));
        });

        errHintPane.managedProperty().bind(errHintPane.visibleProperty());
        errHintPane.setVisible(false);
        rootContainer.getChildren().add(errHintPane);

        if (Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            HintPane snapshotHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            snapshotHint.setSegment(i18n("account.methods.microsoft.snapshot"));
            rootContainer.getChildren().add(snapshotHint);
            btnLogin.setDisable(true);
            return;
        }

        rootContainer.getChildren().add(hintPane);

        if (!IntegrityChecker.isOfficial()) {
            unofficialHintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            unofficialHintPane.managedProperty().bind(unofficialHintPane.visibleProperty());
            unofficialHintPane.setSegment(i18n("unofficial.hint"));
            rootContainer.getChildren().add(unofficialHintPane);
        }

        initAuthMethodsBox();
        rootContainer.getChildren().add(authMethodsContentBox);

        HBox linkBox = new HBox(15);
        linkBox.setAlignment(Pos.CENTER);
        linkBox.setPadding(new Insets(5, 0, 0, 0));

        JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
        profileLink.setExternalLink("https://account.live.com/editprof.aspx");
        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);
        JFXHyperlink forgotLink = new JFXHyperlink(i18n("account.methods.forgot_password"));
        forgotLink.setExternalLink("https://account.live.com/ResetPassword.aspx");

        linkBox.getChildren().addAll(profileLink, purchaseLink, forgotLink);
        rootContainer.getChildren().add(linkBox);

        FXUtils.onChangeAndOperate(deviceCode, event -> {
            if (event != null) {
                authMethodsContentBox.setVisible(true);
                lblCode.setText(event.getUserCode());
            }
        });

        holder.add(Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(value -> runInFX(() -> deviceCode.set(value))));
        holder.add(Accounts.OAUTH_CALLBACK.onOpenBrowserAuthorizationCode.registerWeak(event -> runInFX(() -> browserUrl.set(event.getUrl()))));
    }

    private void initAuthMethodsBox() {
        authMethodsContentBox.managedProperty().bind(authMethodsContentBox.visibleProperty());
        authMethodsContentBox.setAlignment(Pos.CENTER);
        authMethodsContentBox.setVisible(false);
        authMethodsContentBox.setPrefWidth(600);

        VBox browserPanel = new VBox(10);
        browserPanel.setAlignment(Pos.CENTER);
        browserPanel.setPadding(new Insets(10));
        browserPanel.setPrefWidth(280);
        HBox.setHgrow(browserPanel, Priority.ALWAYS);

        Label browserTitle = new Label(i18n("account.methods.microsoft.methods.browser"));
        browserTitle.getStyleClass().add("method-title");

        Label browserDesc = new Label(i18n("account.methods.microsoft.methods.browser.hint"));
        browserDesc.getStyleClass().add("method-desc");

        JFXButton btnOpenBrowser = FXUtils.newBorderButton(i18n("account.methods.microsoft.methods.browser.copy_open"));
        btnOpenBrowser.setOnAction(e -> {
            FXUtils.copyText(browserUrl.get());
            FXUtils.openLink(browserUrl.get());
        });
        btnOpenBrowser.disableProperty().bind(browserUrl.isNull());

        browserPanel.getChildren().addAll(browserTitle, browserDesc, btnOpenBrowser);

        VBox separatorBox = new VBox();
        separatorBox.setAlignment(Pos.CENTER);
        separatorBox.setMinWidth(30);
        HBox.setHgrow(separatorBox, Priority.NEVER);

        Separator sepTop = new Separator(Orientation.VERTICAL);
        VBox.setVgrow(sepTop, Priority.ALWAYS);

        Label orLabel = new Label(i18n("account.methods.microsoft.methods.or"));
        orLabel.setPadding(new Insets(5, 0, 5, 0));
        orLabel.setStyle("-fx-text-fill: -monet-outline; -fx-font-size: 11px; -fx-font-weight: bold;");

        Separator sepBottom = new Separator(Orientation.VERTICAL);
        VBox.setVgrow(sepBottom, Priority.ALWAYS);

        separatorBox.getChildren().addAll(sepTop, orLabel, sepBottom);

        VBox devicePanel = new VBox(10);
        devicePanel.setAlignment(Pos.CENTER);
        devicePanel.setPadding(new Insets(10));
        devicePanel.setPrefWidth(280);
        HBox.setHgrow(devicePanel, Priority.ALWAYS);

        Label deviceTitle = new Label(i18n("account.methods.microsoft.methods.device"));
        deviceTitle.getStyleClass().add("method-title");

        Label deviceDesc = new Label();
        deviceDesc.getStyleClass().add("method-desc");
        deviceDesc.textProperty().bind(Bindings.createStringBinding(
                () -> i18n("account.methods.microsoft.methods.device.hint", deviceCode.get() == null ? "..." : deviceCode.get().getVerificationUri()),
                deviceCode));

        var qrCode = new SVGPath();
        qrCode.fillProperty().bind(Themes.colorSchemeProperty().getPrimary());
        qrCode.setFillRule(FillRule.EVEN_ODD);
        qrCode.setContent("M740 740ZM0 0ZM240 80h20v20h-20zm80 0h20v20h-20zm120 0h20v20h-20zm20 0h20v20h-20zm-180 20h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm-200 20h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm-240 20h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm-200 20h20v20h-20zm100 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm-200 20h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm-240 20h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm-240 20h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zm60 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zM80 240h20v20H80zm80 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm-540 20h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm80 0h20v20h-20zm200 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm-480 20h20v20h-20zm80 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zM80 300h20v20H80zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zM80 320h20v20H80zm100 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zM80 340h20v20H80zm60 0h20v20h-20zm80 0h20v20h-20zm80 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm80 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zM80 360h20v20H80zm20 0h20v20h-20zm60 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm140 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zM80 380h20v20H80zm40 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm100 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm120 0h20v20h-20zM80 400h20v20H80zm60 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm120 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zM80 420h20v20H80zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm240 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm-440 20h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm60 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm-460 20h20v20h-20zm80 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm140 0h20v20h-20zM80 480h20v20H80zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm-320 20h20v20h-20zm40 0h20v20h-20zm60 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zm20 0h20v20h-20zm80 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm-360 20h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm100 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm-380 20h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm120 0h20v20h-20zm80 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm-380 20h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm100 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm-360 20h20v20h-20zm60 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm80 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm-360 20h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm60 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm-380 20h20v20h-20zm60 0h20v20h-20zm140 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm-360 20h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm20 0h20v20h-20zm20 0h20v20h-20zm40 0h20v20h-20zm40 0h20v20h-20zm60 0h20v20h-20z M80 80h140v140H80Zm20 20h100v100H100Zm420-20h140v140H520Zm20 20h100v100H540Z M120 120h60v60h-60zm440 0h60v60h-60z M80 520h140v140H80Zm20 20h100v100H100Z M120 560h60v60h-60z");
        qrCode.setScaleX(80.0 / 740.0);
        qrCode.setScaleY(80.0 / 740.0);

        HBox codeBox = new HBox(10);
        codeBox.getStyleClass().add("code-box");

        FXUtils.setLimitWidth(codeBox, 170);
        FXUtils.setLimitHeight(codeBox, 40);

        codeBox.getChildren().add(lblCode);
        devicePanel.getChildren().addAll(deviceTitle, deviceDesc, new Group(qrCode), codeBox);

        authMethodsContentBox.getChildren().addAll(browserPanel, separatorBox, devicePanel);
    }

    private void startLoginTasks() {
        deviceCode.set(null);
        browserUrl.set(null);
        errHintPane.setVisible(false);

        if (unofficialHintPane != null) {
            unofficialHintPane.setVisible(false);
        }

        loginButtonSpinner.showSpinner();

        Task.FinalizedCallbackWithResult<MicrosoftAccount> onComplete = (account, exception) -> {
            if (exception == null) {
                cancelAllTasks();
                runInFX(() -> onLoginCompleted(account));
            } else if (!(exception instanceof CancellationException)) {
                errHintPane.setText(Accounts.localizeErrorMessage(exception));
                errHintPane.setVisible(true);
                authMethodsContentBox.setVisible(false);
            }
        };

        browserTaskExecutor = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.AUTHORIZATION_CODE))
                .whenComplete(Schedulers.javafx(), onComplete)
                .executor(true);

        deviceTaskExecutor = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.DEVICE))
                .whenComplete(Schedulers.javafx(), onComplete)
                .executor(true);
    }

    private void onLoginCompleted(MicrosoftAccount account) {
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
                errHintPane.setText(Accounts.localizeErrorMessage(e));
                errHintPane.setVisible(true);
                loginButtonSpinner.showSpinner();
                return;
            }
        }
        fireEvent(new DialogCloseEvent());
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
}

