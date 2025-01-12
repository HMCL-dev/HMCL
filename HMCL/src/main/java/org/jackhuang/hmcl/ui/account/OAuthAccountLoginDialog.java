package org.jackhuang.hmcl.ui.account;

import java.util.function.Consumer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.OAuthAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.ui.construct.HintPane;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;


import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class OAuthAccountLoginDialog extends DialogPane {
    private final OAuthAccount account;
    private final Consumer<AuthInfo> success;
    private final Runnable failed;
    private final ObjectProperty<OAuthServer.GrantDeviceCodeEvent> deviceCode = new SimpleObjectProperty<>();

    private final WeakListenerHolder holder = new WeakListenerHolder();

    public OAuthAccountLoginDialog(OAuthAccount account, Consumer<AuthInfo> success, Runnable failed) {
        this.account = account;
        this.success = success;
        this.failed = failed;

        setTitle(i18n("account.login.refresh"));

        VBox vbox = new VBox(8);
        Label usernameLabel = new Label(account.getUsername());

        HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
        FlowPane box = new FlowPane();
        box.setHgap(8);
        JFXHyperlink birthLink = new JFXHyperlink(i18n("account.methods.microsoft.birth"));
        birthLink.setExternalLink("https://support.microsoft.com/account-billing/837badbc-999e-54d2-2617-d19206b9540a");
        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);
        JFXHyperlink deauthorizeLink = new JFXHyperlink(i18n("account.methods.microsoft.deauthorize"));
        deauthorizeLink.setExternalLink("https://account.live.com/consent/Manage");
        JFXHyperlink forgotpasswordLink = new JFXHyperlink(i18n("account.methods.forgot_password"));
        forgotpasswordLink.setExternalLink("https://account.live.com/ResetPassword.aspx");
        JFXHyperlink loginwithpasswordLink = new JFXHyperlink(i18n("account.methods.login_with_password"));
        loginwithpasswordLink.setExternalLink("https://docs.hmcl.net/launcher/use-password-login-microsoft-account.html");
        JFXHyperlink createProfileLink = new JFXHyperlink(i18n("account.methods.microsoft.makegameidsettings"));
        createProfileLink.setExternalLink("https://www.minecraft.net/msaprofile/mygames/editprofile");
        box.getChildren().setAll(birthLink, purchaseLink, deauthorizeLink, forgotpasswordLink, loginwithpasswordLink, createProfileLink);
        GridPane.setColumnSpan(box, 2);

        FXUtils.onChangeAndOperate(deviceCode, deviceCode -> {
            if (deviceCode != null) {
                FXUtils.copyText(deviceCode.getUserCode());
                hintPane.setSegment(i18n("account.methods.microsoft.manual", deviceCode.getUserCode(), deviceCode.getVerificationUri()));
                JFXHyperlink qrCodeLoginLink = new JFXHyperlink(i18n("account.methods.qrcodelogin"));
                qrCodeLoginLink.setExternalLink("https://docs.hmcl.net/qr-login.html?verificationUri=" + deviceCode.getVerificationUri() + "&userCode=" + deviceCode.getUserCode());
                box.getChildren().setAll(purchaseLink, birthLink, deauthorizeLink, loginwithpasswordLink, createProfileLink, qrCodeLoginLink);
                if (!IntegrityChecker.isOfficial())
                    box.getChildren().remove(deauthorizeLink);
            } else {
                hintPane.setSegment(i18n("account.methods.microsoft.hint"));
            }
        });
        FXUtils.onClicked(hintPane, () -> {
            if (deviceCode.get() != null) {
                FXUtils.openLink(deviceCode.get().getVerificationUri());
                FXUtils.copyText(deviceCode.get().getUserCode());
            }
        });

        if (!IntegrityChecker.isOfficial()) {
            HintPane unofficialHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            unofficialHint.setText(i18n("unofficial.hint"));
            vbox.getChildren().add(unofficialHint);
        }

        vbox.getChildren().addAll(hintPane, box);

        vbox.getChildren().addAll(usernameLabel);
        setBody(vbox);

        holder.add(Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(this::onGrantDeviceCode));
    }

    private void onGrantDeviceCode(OAuthServer.GrantDeviceCodeEvent event) {
        FXUtils.runInFX(() -> {
            deviceCode.set(event);
        });
    }

    @Override
    protected void onAccept() {
        setLoading();
        Task.supplyAsync(account::logInWhenCredentialsExpired)
                .whenComplete(Schedulers.javafx(), (authInfo, exception) -> {
                    if (exception == null) {
                        success.accept(authInfo);
                        onSuccess();
                    } else {
                        LOG.info("Failed to login when credentials expired: " + account, exception);
                        onFailure(Accounts.localizeErrorMessage(exception));
                    }
                }).start();
    }

    @Override
    protected void onCancel() {
        failed.run();
        super.onCancel();
    }
}
