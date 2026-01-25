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
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MicrosoftAccountLoginDialog extends JFXDialogLayout implements DialogAware {

    private final Account accountToRelogin;
    private final Consumer<AuthInfo> loginCallback;
    private final Runnable cancelCallback;
    private final WeakListenerHolder holder = new WeakListenerHolder();

    private final ObjectProperty<OAuthServer.GrantDeviceCodeEvent> deviceCode = new SimpleObjectProperty<>();
    private final ObjectProperty<String> browserUrl = new SimpleObjectProperty<>();
    private final AtomicBoolean loginFirst = new AtomicBoolean(false);

    private TaskExecutor browserTask;
    private TaskExecutor deviceTask;

    private SpinnerPane mainSpinner;
    private HBox authContentBox;
    private HintPane errHintPane;
    private JFXButton btnStartLogin;
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

        errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);
        errHintPane.setVisible(false);

        if (Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            HintPane snapshotHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            snapshotHint.setSegment(i18n("account.methods.microsoft.snapshot"));
            rootContainer.getChildren().add(snapshotHint);
            mainSpinner = new SpinnerPane();
            return;
        }

        rootContainer.getChildren().add(hintPane);
        if (!IntegrityChecker.isOfficial()) {
            unofficialHintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            unofficialHintPane.setSegment(i18n("unofficial.hint"));
            rootContainer.getChildren().add(unofficialHintPane);
        }
        rootContainer.getChildren().add(errHintPane);

        authContentBox = new HBox(10);
        authContentBox.setAlignment(Pos.CENTER);
        authContentBox.setVisible(false);
        authContentBox.setPrefWidth(640);
        authContentBox.setMinHeight(250);

        VBox browserPanel = new VBox(15);
        browserPanel.setAlignment(Pos.TOP_CENTER);
        browserPanel.setPadding(new Insets(10));
        browserPanel.setPrefWidth(290);
        HBox.setHgrow(browserPanel, Priority.ALWAYS);

        Label browserTitle = new Label(i18n("account.methods.microsoft.methods.broswer"));
        browserTitle.getStyleClass().add("h4");
        browserTitle.setStyle("-fx-text-fill: -monet-on-surface;");

        Label browserDesc = new Label(i18n("account.methods.microsoft.methods.broswer.hint"));
        browserDesc.setStyle("-fx-text-fill: -monet-outline; -fx-line-spacing: 2px;");
        browserDesc.setWrapText(true);
        browserDesc.setTextAlignment(TextAlignment.CENTER);
        browserDesc.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(browserDesc, Priority.ALWAYS);

        JFXButton btnOpenBrowser = new JFXButton(i18n("account.methods.microsoft.methods.broswer.copy_open"));
        btnOpenBrowser.setDisable(true);
        btnOpenBrowser.disableProperty().bind(browserUrl.isNull().or(browserUrl.asString().isEmpty()));
        btnOpenBrowser.setMaxWidth(Double.MAX_VALUE);
        btnOpenBrowser.setOnAction(e -> {
            String url = browserUrl.get();
            if (url != null) FXUtils.openLink(url);
        });

        browserPanel.getChildren().addAll(browserTitle, browserDesc, btnOpenBrowser);

        VBox separatorBox = new VBox();
        separatorBox.setAlignment(Pos.CENTER);
        separatorBox.setMinWidth(40);
        HBox.setHgrow(separatorBox, Priority.NEVER);

        Separator sepTop = new Separator(Orientation.VERTICAL);
        VBox.setVgrow(sepTop, Priority.ALWAYS);

        Label orLabel = new Label(i18n("account.methods.microsoft.methods.or"));
        orLabel.setStyle("-fx-text-fill: -monet-outline; -fx-padding: 8 0 8 0; -fx-font-size: 12px; -fx-font-weight: bold;");

        Separator sepBottom = new Separator(Orientation.VERTICAL);
        VBox.setVgrow(sepBottom, Priority.ALWAYS);

        separatorBox.getChildren().addAll(sepTop, orLabel, sepBottom);

        VBox devicePanel = new VBox(15);
        devicePanel.setAlignment(Pos.TOP_CENTER);
        devicePanel.setPadding(new Insets(10));
        devicePanel.setPrefWidth(290);
        HBox.setHgrow(devicePanel, Priority.ALWAYS);

        Label deviceTitle = new Label(i18n("account.methods.microsoft.methods.device"));
        deviceTitle.getStyleClass().add("h4");
        deviceTitle.setStyle("-fx-text-fill: -monet-on-surface;");

        Label deviceDesc = new Label();
        deviceDesc.setStyle("-fx-text-fill: -monet-outline; -fx-line-spacing: 2px;");
        deviceDesc.setWrapText(true);
        deviceDesc.setTextAlignment(TextAlignment.CENTER);
        deviceDesc.setMaxWidth(Double.MAX_VALUE);
        deviceDesc.textProperty().bind(Bindings.createStringBinding(
                () -> i18n("account.methods.microsoft.methods.device.hint",
                        deviceCode.get() == null ? "..." : deviceCode.get().getVerificationUri()),
                deviceCode
        ));

        ImageView imageView = new ImageView(FXUtils.newBuiltinImage("/assets/img/microsoft_login.png"));
        imageView.setFitWidth(84);
        imageView.setFitHeight(84);

        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setMinHeight(84);
        VBox.setVgrow(imageContainer, Priority.ALWAYS);

        HBox codeBox = new HBox(10);
        codeBox.setAlignment(Pos.CENTER);
        codeBox.setStyle("-fx-background-color: -monet-surface-variant; -fx-background-radius: 6; -fx-padding: 8 15 8 15;");
        codeBox.setMaxWidth(Double.MAX_VALUE);

        Label lblCode = new Label("...");
        lblCode.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -monet-primary; -fx-font-family: \""
                + Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT) + "\"");

        SpinnerPane codeSpinner = new SpinnerPane();
        codeSpinner.setContent(lblCode);
        codeSpinner.showSpinner();

        JFXButton btnCopyCode = new JFXButton();
        btnCopyCode.setGraphic(SVG.CONTENT_COPY.createIcon(18));
        btnCopyCode.setTooltip(new Tooltip(i18n("account.methods.microsoft.methods.device.copy")));
        btnCopyCode.getStyleClass().add("jfx-button-flat");
        btnCopyCode.setStyle("-fx-cursor: hand; -fx-padding: 5;");
        btnCopyCode.setOnAction(e -> {
            if (deviceCode.get() != null) FXUtils.copyText(deviceCode.get().getUserCode());
        });

        FXUtils.onChangeAndOperate(deviceCode, event -> {
            if (event != null) {
                lblCode.setText(event.getUserCode());
                codeSpinner.hideSpinner();
            } else {
                codeSpinner.showSpinner();
            }
        });

        codeBox.getChildren().addAll(codeSpinner, btnCopyCode);
        devicePanel.getChildren().addAll(deviceTitle, deviceDesc, imageContainer, codeBox);

        authContentBox.getChildren().addAll(browserPanel, separatorBox, devicePanel);
        rootContainer.getChildren().add(authContentBox);

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

        btnStartLogin = new JFXButton(i18n("account.login"));
        btnStartLogin.getStyleClass().add("dialog-accept");
        btnStartLogin.setOnAction(e -> startLoginTasks());

        mainSpinner = new SpinnerPane();
        mainSpinner.getStyleClass().add("small-spinner-pane");
        mainSpinner.setContent(btnStartLogin);

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());
        onEscPressed(this, btnCancel::fire);

        HBox actions = new HBox(10, mainSpinner, btnCancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        setActions(actions);

        errHintPane.managedProperty().bind(errHintPane.visibleProperty());
        authContentBox.managedProperty().bind(authContentBox.visibleProperty());
        unofficialHintPane.managedProperty().bind(unofficialHintPane.visibleProperty());
    }

    private void startLoginTasks() {
        btnStartLogin.setVisible(false);
        mainSpinner.showSpinner();

        deviceCode.set(null);
        browserUrl.set(null);
        errHintPane.setVisible(false);
        authContentBox.setVisible(true);
        mainSpinner.hideSpinner();
        unofficialHintPane.setVisible(false);

        loginFirst.set(false);

        ExceptionalConsumer<MicrosoftAccount, Exception> onSuccess = (account) -> {
            if (loginFirst.compareAndSet(false, true)) {
                cancelAllTasks();
                runInFX(() -> handleLoginSuccess(account));
            }
        };

        ExceptionalConsumer<Exception, Exception> onFail = (e) -> runInFX(() -> {
            if (!loginFirst.get()) {
                if (!(e instanceof CancellationException)) {
                    errHintPane.setText(Accounts.localizeErrorMessage(e));
                    errHintPane.setVisible(true);
                    authContentBox.setVisible(false);
                }
            }
        });

        browserTask = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.AUTHORIZATION_CODE))
                .whenComplete(Schedulers.javafx(), onSuccess, onFail).executor(true);

        deviceTask = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.DEVICE))
                .whenComplete(Schedulers.javafx(), onSuccess, onFail).executor(true);
    }

    private void handleLoginSuccess(MicrosoftAccount account) {
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
                errHintPane.setManaged(true);
                return;
            }
        }
        fireEvent(new DialogCloseEvent());
    }

    private void cancelAllTasks() {
        if (browserTask != null) browserTask.cancel();
        if (deviceTask != null) deviceTask.cancel();
    }

    private void onCancel() {
        cancelAllTasks();
        if (cancelCallback != null) cancelCallback.run();
        fireEvent(new DialogCloseEvent());
    }
}