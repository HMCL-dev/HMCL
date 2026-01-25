package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
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

    private final BooleanProperty logging = new SimpleBooleanProperty(false);
    private final ObjectProperty<OAuthServer.GrantDeviceCodeEvent> deviceCode = new SimpleObjectProperty<>();
    private final ObjectProperty<String> browserUrl = new SimpleObjectProperty<>();
    private final AtomicBoolean loginFirst = new AtomicBoolean(false);

    private TaskExecutor browserTask;
    private TaskExecutor deviceTask;

    private final SpinnerPane mainSpinner;
    private HBox authContentBox;
    private final HintPane errHintPane;
    private final HintPane hintPane;
    private final HintPane unofficialHintPane;
    private JFXButton btnStartLogin;

    public MicrosoftAccountLoginDialog() {
        this(null, null, null);
    }

    public MicrosoftAccountLoginDialog(Account account, Consumer<AuthInfo> callback, Runnable onCancel) {
        this.accountToRelogin = account;
        this.loginCallback = callback;
        this.cancelCallback = onCancel;

        Label heading = new Label(accountToRelogin != null ? i18n("account.login.refresh") : i18n("account.create.microsoft"));
        heading.getStyleClass().add("header-label");
        setHeading(heading);

        VBox rootContainer = new VBox(15);
        rootContainer.setPadding(new Insets(10, 0, 0, 0));
        rootContainer.setAlignment(Pos.TOP_CENTER);

        hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
        hintPane.setText(i18n("account.methods.microsoft.hint"));

        errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);
        errHintPane.setVisible(false);
        errHintPane.setManaged(false);

        unofficialHintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
        unofficialHintPane.setSegment(i18n("unofficial.hint"));

        if (Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            HintPane snapshotHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            snapshotHint.setSegment(i18n("account.methods.microsoft.snapshot"));
            rootContainer.getChildren().add(snapshotHint);
            mainSpinner = new SpinnerPane();
            return;
        }

        if (!IntegrityChecker.isOfficial()) {
            rootContainer.getChildren().add(unofficialHintPane);
        }
        rootContainer.getChildren().addAll(hintPane, errHintPane);

        authContentBox = createAuthContent();
        authContentBox.setVisible(false);
        authContentBox.setManaged(false);
        rootContainer.getChildren().add(authContentBox);

        rootContainer.getChildren().add(createFooterLinks());

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

        setupBindings();
    }

    private HBox createAuthContent() {
        HBox container = new HBox(0); // Spacing handled by separator margin
        container.setAlignment(Pos.CENTER);
        container.setPrefWidth(600);
        container.setMinHeight(220);

        Node browserPanel = createBrowserPanel();
        HBox.setHgrow(browserPanel, Priority.ALWAYS);

        VBox separatorBox = new VBox();
        separatorBox.setAlignment(Pos.CENTER);
        separatorBox.setMinWidth(40);

        Separator sepTop = new Separator(Orientation.VERTICAL);
        VBox.setVgrow(sepTop, Priority.ALWAYS);

        Label orLabel = new Label(i18n("account.methods.microsoft.methods.or"));
        orLabel.setStyle("-fx-text-fill: -monet-outline; -fx-padding: 8 0 8 0;");
        orLabel.setFont(Font.font(12));

        Separator sepBottom = new Separator(Orientation.VERTICAL);
        VBox.setVgrow(sepBottom, Priority.ALWAYS);

        separatorBox.getChildren().addAll(sepTop, orLabel, sepBottom);

        Node devicePanel = createDevicePanel();
        HBox.setHgrow(devicePanel, Priority.ALWAYS);

        container.getChildren().addAll(browserPanel, separatorBox, devicePanel);
        return container;
    }

    private Node createBrowserPanel() {
        VBox box = new VBox(15);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(10));
        box.setPrefWidth(280);

        Label title = new Label(i18n("account.methods.microsoft.methods.broswer"));
        title.getStyleClass().add("h4");
        title.setStyle("-fx-text-fill: -monet-on-surface;");

        Label desc = new Label(i18n("account.methods.microsoft.methods.broswer.hint"));
        desc.setWrapText(true);
        desc.setTextAlignment(TextAlignment.CENTER);
        desc.setStyle("-fx-text-fill: -monet-outline;");

        JFXButton btnOpen = new JFXButton(i18n("account.methods.microsoft.methods.broswer.copy_open"));
        btnOpen.getStyleClass().add("dialog-accept");
        btnOpen.setDisable(true);

        btnOpen.disableProperty().bind(browserUrl.isNull().or(browserUrl.asString().isEmpty()));
        btnOpen.setOnAction(e -> {
            String url = browserUrl.get();
            if (url != null) FXUtils.openLink(url);
        });

        VBox.setVgrow(desc, Priority.ALWAYS);

        box.getChildren().addAll(title, desc, btnOpen);
        return box;
    }

    private Node createDevicePanel() {
        SpinnerPane deviceSpinner = new SpinnerPane();

        VBox contentBox = new VBox(15);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(10));
        contentBox.setPrefWidth(280);

        Label title = new Label(i18n("account.methods.microsoft.methods.device"));
        title.getStyleClass().add("h4");
        title.setStyle("-fx-text-fill: -monet-on-surface;");

        Label desc = new Label();
        desc.setWrapText(true);
        desc.setTextAlignment(TextAlignment.CENTER);
        desc.setStyle("-fx-text-fill: -monet-outline;");
        desc.textProperty().bind(Bindings.createStringBinding(() -> i18n("account.methods.microsoft.methods.device.hint", deviceCode.get().getVerificationUri()), deviceCode));

        HBox codeBox = new HBox(10);
        codeBox.setAlignment(Pos.CENTER);
        codeBox.setStyle("-fx-background-color: -monet-surface-variant; -fx-background-radius: 8; -fx-padding: 10;");

        Label lblCode = new Label("...");
        lblCode.setStyle("-fx-font-size:22;-fx-text-fill: -monet-primary;" + "-fx-font-family: \"" + Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT));

        JFXButton btnCopy = new JFXButton();
        btnCopy.setGraphic(SVG.CONTENT_COPY.createIcon(20));
        btnCopy.setTooltip(new Tooltip(i18n("account.methods.microsoft.methods.device.copy")));
        btnCopy.getStyleClass().add("jfx-button-flat");
        btnCopy.setOnAction(e -> {
            if (deviceCode.get() != null) FXUtils.copyText(deviceCode.get().getUserCode());
        });

        codeBox.getChildren().addAll(lblCode, btnCopy);

        ImageView imageView = new ImageView(FXUtils.newBuiltinImage("/assets/img/microsoft_login.png"));
        FXUtils.limitingSize(imageView, 128, 128);
        imageView.setFitHeight(128);
        imageView.setFitWidth(128);

        contentBox.getChildren().addAll(title, desc, imageView, codeBox);

        deviceSpinner.setContent(lblCode);

        deviceSpinner.showSpinner();

        FXUtils.onChangeAndOperate(deviceCode, event -> {
            if (event != null) {
                lblCode.setText(event.getUserCode());
                deviceSpinner.hideSpinner();
            } else {
                deviceSpinner.showSpinner();
            }
        });

        return contentBox;
    }

    private Node createFooterLinks() {
        HBox linkBox = new HBox(15);
        linkBox.setAlignment(Pos.CENTER);
        linkBox.setPadding(new Insets(10, 0, 0, 0));

        JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
        profileLink.setExternalLink("https://account.live.com/editprof.aspx");

        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);

        JFXHyperlink forgotLink = new JFXHyperlink(i18n("account.methods.forgot_password"));
        forgotLink.setExternalLink("https://account.live.com/ResetPassword.aspx");

        linkBox.getChildren().setAll(profileLink, purchaseLink, forgotLink);
        return linkBox;
    }

    private void setupBindings() {
        holder.add(Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(value -> runInFX(() -> deviceCode.set(value))));

        holder.add(Accounts.OAUTH_CALLBACK.onOpenBrowser.registerWeak(event -> runInFX(() -> browserUrl.set(event.getUrl()))));

        FXUtils.onChangeAndOperate(deviceCode, event -> {
            if (event != null) {
                hintPane.setSegment(i18n("account.methods.microsoft.manual", event.getVerificationUri()));
            }
        });
    }

    private void startLoginTasks() {
        logging.set(true);
        btnStartLogin.setVisible(false);
        mainSpinner.showSpinner();

        deviceCode.set(null);
        browserUrl.set(null);
        errHintPane.setVisible(false);
        errHintPane.setManaged(false);
        unofficialHintPane.setVisible(false);
        unofficialHintPane.setManaged(false);
        authContentBox.setVisible(true);
        authContentBox.setManaged(true);

        mainSpinner.hideSpinner();

        loginFirst.set(false);

        ExceptionalConsumer<MicrosoftAccount, Exception> onSuccess = (account) -> {
            if (loginFirst.compareAndSet(false, true)) {
                cancelAllTasks();
                runInFX(() -> handleLoginSuccess(account));
            }
        };

        ExceptionalConsumer<Exception, Exception> onFail = (e) -> runInFX(() -> {
            if (!loginFirst.get()) {
                if (!(e instanceof java.util.concurrent.CancellationException)) {
                    errHintPane.setText(Accounts.localizeErrorMessage(e));
                    errHintPane.setVisible(true);
                    errHintPane.setManaged(true);
                }
            }
        });

        // Start Tasks
        browserTask = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.AUTHORIZATION_CODE)).whenComplete(Schedulers.javafx(), onSuccess, onFail).executor(true);

        deviceTask = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.DEVICE)).whenComplete(Schedulers.javafx(), onSuccess, onFail).executor(true);
    }

    private void handleLoginSuccess(MicrosoftAccount account) {
        if (accountToRelogin != null) {
            Accounts.getAccounts().remove(accountToRelogin);
        }

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
        if (cancelCallback != null) {
            cancelCallback.run();
        }
        fireEvent(new DialogCloseEvent());
    }
}

