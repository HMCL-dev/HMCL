package org.jackhuang.hmcl.ui.account;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
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

import java.util.function.Consumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;
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
        FXUtils.onChangeAndOperate(deviceCode, deviceCode -> {
            if (deviceCode != null) {
                FXUtils.copyText(deviceCode.getUserCode());
                hintPane.setSegment(i18n("account.methods.microsoft.manual", deviceCode.getUserCode(), deviceCode.getVerificationUri()));
            } else {
                hintPane.setSegment(i18n("account.methods.microsoft.hint"));
            }
        });
        hintPane.setOnMouseClicked(e -> {
            if (deviceCode.get() != null) {
                FXUtils.copyText(deviceCode.get().getUserCode());
            }
        });

        HBox box = new HBox(8);
        JFXHyperlink birthLink = new JFXHyperlink(i18n("account.methods.microsoft.birth"));
        birthLink.setOnAction(e -> FXUtils.openLink("https://support.microsoft.com/zh-cn/account-billing/如何更改-microsoft-帐户上的出生日期-837badbc-999e-54d2-2617-d19206b9540a"));
        JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
        profileLink.setOnAction(e -> FXUtils.openLink("https://account.live.com/editprof.aspx"));
        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.yggdrasil.purchase"));
        purchaseLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.PURCHASE_URL));
        box.getChildren().setAll(profileLink, birthLink, purchaseLink);
        GridPane.setColumnSpan(box, 2);

        vbox.getChildren().setAll(usernameLabel, hintPane, box);
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
                        LOG.log(Level.INFO, "Failed to login when credentials expired: " + account, exception);
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
