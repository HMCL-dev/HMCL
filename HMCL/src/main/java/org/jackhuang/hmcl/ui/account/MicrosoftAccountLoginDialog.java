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

public class MicrosoftAccountLoginDialog extends JFXDialogLayout implements DialogAware {
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
    private final HBox authMethodsContentBox = new HBox(10);
    private final HintPane errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);
    private HintPane unofficialHintPane;

    public MicrosoftAccountLoginDialog() {
        this(null, null, null);
    }

    public MicrosoftAccountLoginDialog(Account account, Consumer<AuthInfo> callback, Runnable onCancel) {
        this.accountToRelogin = account;
        this.loginCallback = callback;
        this.cancelCallback = onCancel;

        initUI();

        holder.add(Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(value -> runInFX(() -> deviceCode.set(value))));
        holder.add(Accounts.OAUTH_CALLBACK.onOpenBrowserAuthorizationCode.registerWeak(event -> runInFX(() -> browserUrl.set(event.getUrl()))));
    }

    private void initUI() {
        Label heading = new Label(accountToRelogin != null ? i18n("account.login.refresh") : i18n("account.create.microsoft"));
        heading.getStyleClass().add("header-label");
        setHeading(heading);

        VBox rootContainer = new VBox(15);
        rootContainer.setPadding(new Insets(10, 0, 0, 0));
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

        authMethodsContentBox.managedProperty().bind(authMethodsContentBox.visibleProperty());
        authMethodsContentBox.setAlignment(Pos.CENTER);
        authMethodsContentBox.setVisible(false);
        authMethodsContentBox.setPrefWidth(640);
        authMethodsContentBox.setMinHeight(250);

        VBox browserPanel = new VBox(15);
        browserPanel.setAlignment(Pos.TOP_CENTER);
        browserPanel.setPadding(new Insets(10));
        browserPanel.setPrefWidth(290);
        HBox.setHgrow(browserPanel, Priority.ALWAYS);

        Label browserTitle = new Label(i18n("account.methods.microsoft.methods.browser"));
        browserTitle.setStyle("-fx-text-fill: -monet-on-surface;");

        Label browserDesc = new Label(i18n("account.methods.microsoft.methods.browser.hint"));
        browserDesc.setStyle("-fx-text-fill: -monet-outline; -fx-line-spacing: 2px;");
        browserDesc.setWrapText(true);
        browserDesc.setTextAlignment(TextAlignment.CENTER);
        browserDesc.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(browserDesc, Priority.ALWAYS);

        JFXButton btnOpenBrowser = FXUtils.newBorderButton(i18n("account.methods.microsoft.methods.browser.copy_open"));
        btnOpenBrowser.setOnAction(e -> FXUtils.openLink(browserUrl.get()));

        browserPanel.getChildren().addAll(browserTitle, browserDesc, btnOpenBrowser);

        VBox separatorBox = new VBox();
        separatorBox.setAlignment(Pos.CENTER);
        separatorBox.setMinWidth(40);
        HBox.setHgrow(separatorBox, Priority.NEVER);

        Separator sepTop = new Separator(Orientation.VERTICAL);
        VBox.setVgrow(sepTop, Priority.ALWAYS);

        Label orLabel = new Label(i18n("account.methods.microsoft.methods.or"));
        orLabel.setPadding(new Insets(8, 0, 8, 0));
        orLabel.setStyle("-fx-text-fill: -monet-outline; -fx-font-size: 12px; -fx-font-weight: bold;");

        Separator sepBottom = new Separator(Orientation.VERTICAL);
        VBox.setVgrow(sepBottom, Priority.ALWAYS);

        separatorBox.getChildren().addAll(sepTop, orLabel, sepBottom);

        VBox devicePanel = new VBox(15);
        devicePanel.setAlignment(Pos.TOP_CENTER);
        devicePanel.setPadding(new Insets(10));
        devicePanel.setPrefWidth(290);
        HBox.setHgrow(devicePanel, Priority.ALWAYS);

        Label deviceTitle = new Label(i18n("account.methods.microsoft.methods.device"));
        deviceTitle.setStyle("-fx-text-fill: -monet-on-surface;");

        Label deviceDesc = new Label();
        deviceDesc.setLineSpacing(2);
        deviceDesc.setStyle("-fx-text-fill: -monet-outline;");
        deviceDesc.setWrapText(true);
        deviceDesc.setTextAlignment(TextAlignment.CENTER);
        deviceDesc.setMaxWidth(Double.MAX_VALUE);
        deviceDesc.textProperty().bind(Bindings.createStringBinding(() -> i18n("account.methods.microsoft.methods.device.hint", deviceCode.get() == null ? "..." : deviceCode.get().getVerificationUri()), deviceCode));

        ImageView imageView = new ImageView(FXUtils.newBuiltinImage("/assets/img/microsoft_login.png"));
        imageView.setFitWidth(84);
        imageView.setFitHeight(84);


        HBox codeBox = new HBox(10);
        codeBox.setAlignment(Pos.CENTER);
        codeBox.setStyle("-fx-background-color: -monet-surface-variant; -fx-background-radius: 6; -fx-padding: 8 15 8 15;");
        codeBox.setMaxWidth(Double.MAX_VALUE);

        Label lblCode = new Label("...");
        lblCode.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -monet-primary; -fx-font-family: \"" + Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT) + "\"");

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

        codeBox.getChildren().addAll(codeSpinner);
        devicePanel.getChildren().addAll(deviceTitle, deviceDesc, imageView, codeBox);

        authMethodsContentBox.getChildren().addAll(browserPanel, separatorBox, devicePanel);
        rootContainer.getChildren().add(authMethodsContentBox);

        HBox linkBox = new HBox(15);
        linkBox.setAlignment(Pos.CENTER);
        linkBox.setPadding(new Insets(10, 0, 0, 0));

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

    private void startLoginTasks() {
        deviceCode.set(null);
        browserUrl.set(null);
        errHintPane.setVisible(false);
        authMethodsContentBox.setVisible(true);
        unofficialHintPane.setVisible(false);

        loginButtonSpinner.showSpinner();

        ExceptionalConsumer<MicrosoftAccount, Exception> onSuccess = (account) -> {
            cancelAllTasks();
            runInFX(() -> handleLoginCompleted(account));
        };

        ExceptionalConsumer<Exception, Exception> onFail = (e) -> runInFX(() -> {
            if (!(e instanceof CancellationException)) {
                errHintPane.setText(Accounts.localizeErrorMessage(e));
                errHintPane.setVisible(true);
                authMethodsContentBox.setVisible(false);
            }
        });

        browserTaskExecuter = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.AUTHORIZATION_CODE)).whenComplete(Schedulers.javafx(), onSuccess, onFail).executor(true);
        deviceTaskExecuter = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.DEVICE)).whenComplete(Schedulers.javafx(), onSuccess, onFail).executor(true);
    }

    private void handleLoginCompleted(MicrosoftAccount account) {
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

