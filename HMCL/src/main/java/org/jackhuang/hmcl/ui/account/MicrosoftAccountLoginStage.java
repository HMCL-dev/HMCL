package org.jackhuang.hmcl.ui.account;

import javafx.application.Platform;
import javafx.stage.Modality;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.ui.WebStage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class MicrosoftAccountLoginStage extends WebStage implements MicrosoftService.WebViewCallback {
    public static final MicrosoftAccountLoginStage INSTANCE = new MicrosoftAccountLoginStage();

    CompletableFuture<String> future;
    Predicate<String> urlTester;

    public MicrosoftAccountLoginStage() {
        super(600, 600);
        initModality(Modality.APPLICATION_MODAL);

        webEngine.locationProperty().addListener((observable, oldValue, newValue) -> {
            if (urlTester != null && urlTester.test(newValue)) {
                future.complete(newValue);
                hide();
            }
        });

        showingProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                if (future != null) {
                    future.completeExceptionally(new InterruptedException());
                }
                future = null;
                urlTester = null;
            }
        });
    }

    @Override
    public CompletableFuture<String> show(MicrosoftService service, Predicate<String> urlTester, String initialURL) {
        Platform.runLater(() -> {
            webEngine.load(initialURL);
            show();
        });
        this.future = new CompletableFuture<>();
        this.urlTester = urlTester;
        return future;
    }
}
