package org.jackhuang.hmcl.ui.account;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.OAuthAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.MicrosoftAuthenticationServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.util.function.Consumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class OAuthAccountLoginDialog extends DialogPane {
    private final OAuthAccount account;
    private final Consumer<AuthInfo> success;
    private final Runnable failed;
    private final BooleanProperty logging = new SimpleBooleanProperty();

    public OAuthAccountLoginDialog(OAuthAccount account, Consumer<AuthInfo> success, Runnable failed) {
        this.account = account;
        this.success = success;
        this.failed = failed;

        setTitle(i18n("account.login.refresh"));

        VBox vbox = new VBox(8);
        Label usernameLabel = new Label(account.getUsername());

        HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
        hintPane.textProperty().bind(BindingMapping.of(logging).map(logging ->
                logging
                        ? i18n("account.methods.microsoft.manual")
                        : i18n("account.methods.microsoft.hint")));
        hintPane.setOnMouseClicked(e -> {
            if (logging.get() && MicrosoftAuthenticationServer.lastlyOpenedURL != null) {
                FXUtils.copyText(MicrosoftAuthenticationServer.lastlyOpenedURL);
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
    }

    @Override
    protected void onAccept() {
        setLoading();
        logging.set(true);
        Task.supplyAsync(account::logInWhenCredentialsExpired)
                .whenComplete(Schedulers.javafx(), (authInfo, exception) -> {
                    logging.set(false);
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
