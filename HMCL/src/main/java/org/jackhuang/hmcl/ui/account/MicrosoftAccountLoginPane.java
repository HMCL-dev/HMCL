package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
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
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;

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

    private TaskExecutor browserTaskExecuter;
    private TaskExecutor deviceTaskExecuter;

    private SpinnerPane loginButtonSpinner;
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

        initUI(bodyonly);

        holder.add(Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(value -> runInFX(() -> deviceCode.set(value))));
        holder.add(Accounts.OAUTH_CALLBACK.onOpenBrowserAuthorizationCode.registerWeak(event -> runInFX(() -> browserUrl.set(event.getUrl()))));
    }

    private void initUI(boolean bodyonly) {
        if (!bodyonly) {
            Label heading = new Label(accountToRelogin != null ? i18n("account.login.refresh") : i18n("account.create.microsoft"));
            heading.getStyleClass().add("header-label");
            setHeading(heading);
        } else {
            setStyle("-fx-padding: 0px 0px 0px 0px;");
        }

        VBox rootContainer = new VBox(10);
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
            loginButtonSpinner = new SpinnerPane();
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

        setBody(rootContainer);

        JFXButton btnStartLogin = new JFXButton(i18n("account.login"));
        btnStartLogin.getStyleClass().add("dialog-accept");
        btnStartLogin.setOnAction(e -> startLoginTasks());

        loginButtonSpinner = new SpinnerPane();
        loginButtonSpinner.getStyleClass().add("small-spinner-pane");
        loginButtonSpinner.setContent(btnStartLogin);

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());
        onEscPressed(this, btnCancel::fire);

        HBox actions = new HBox(10, loginButtonSpinner, btnCancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        setActions(actions);
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
        browserTitle.setStyle("-fx-text-fill: -monet-on-surface; -fx-font-weight: bold;");

        Label browserDesc = new Label(i18n("account.methods.microsoft.methods.browser.hint"));
        browserDesc.setLineSpacing(2);
        browserDesc.setStyle("-fx-text-fill: -monet-outline; -fx-font-size: 0.9em;");
        browserDesc.setWrapText(true);
        browserDesc.setTextAlignment(TextAlignment.CENTER);

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
        deviceTitle.setStyle("-fx-text-fill: -monet-on-surface; -fx-font-weight: bold;");

        Label deviceDesc = new Label();
        deviceDesc.setLineSpacing(2);
        deviceDesc.setStyle("-fx-text-fill: -monet-outline; -fx-font-size: 0.9em;");
        deviceDesc.setWrapText(true);
        deviceDesc.setTextAlignment(TextAlignment.CENTER);
        deviceDesc.textProperty().bind(Bindings.createStringBinding(
                () -> i18n("account.methods.microsoft.methods.device.hint", deviceCode.get() == null ? "..." : deviceCode.get().getVerificationUri()),
                deviceCode));

        ImageView imageView = new ImageView(FXUtils.newBuiltinImage("/assets/img/microsoft_login.png"));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(80);
        imageView.setFitHeight(80);

        HBox codeBox = new HBox(10);
        codeBox.setAlignment(Pos.CENTER);
        codeBox.setPadding(new Insets(6, 12, 6, 12));
        codeBox.setStyle("-fx-background-color: -monet-surface-variant; -fx-background-radius: 6;");
        codeBox.setMaxWidth(Double.MAX_VALUE);

        Label lblCode = new Label("...");
        lblCode.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: -monet-primary; -fx-font-family: \"" + Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT) + "\"");

        SpinnerPane codeSpinner = new SpinnerPane();
        codeSpinner.setContent(lblCode);
        codeSpinner.showSpinner();

        FXUtils.onChangeAndOperate(deviceCode, event -> {
            if (event != null) {
                lblCode.setText(event.getUserCode());
                codeSpinner.hideSpinner();
            } else {
                codeSpinner.showSpinner();
            }
        });

        codeBox.getChildren().add(codeSpinner);
        devicePanel.getChildren().addAll(deviceTitle, deviceDesc, imageView, codeBox);

        authMethodsContentBox.getChildren().addAll(browserPanel, separatorBox, devicePanel);
    }

    private void startLoginTasks() {
        deviceCode.set(null);
        browserUrl.set(null);
        errHintPane.setVisible(false);
        authMethodsContentBox.setVisible(true);

        if (unofficialHintPane != null) {
            unofficialHintPane.setVisible(false);
        }

        loginButtonSpinner.showSpinner();

        ExceptionalConsumer<MicrosoftAccount, Exception> onSuccess = (account) -> {
            cancelAllTasks();
            runInFX(() -> onLoginCompleted(account));
        };

        ExceptionalConsumer<Exception, Exception> onFail = (e) -> runInFX(() -> {
            if (!(e instanceof CancellationException)) {
                errHintPane.setText(Accounts.localizeErrorMessage(e));
                errHintPane.setVisible(true);
                authMethodsContentBox.setVisible(false);
                loginButtonSpinner.hideSpinner();
            }
        });

        browserTaskExecuter = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.AUTHORIZATION_CODE))
                .whenComplete(Schedulers.javafx(), onSuccess, onFail)
                .executor(true);

        deviceTaskExecuter = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.DEVICE))
                .whenComplete(Schedulers.javafx(), onSuccess, onFail)
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
        if (browserTaskExecuter != null) browserTaskExecuter.cancel();
        if (deviceTaskExecuter != null) deviceTaskExecuter.cancel();
    }

    private void onCancel() {
        cancelAllTasks();
        if (cancelCallback != null) cancelCallback.run();
        fireEvent(new DialogCloseEvent());
    }
}

