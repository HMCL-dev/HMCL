/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.account;

import javafx.application.Platform;
import javafx.stage.Modality;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.ui.WebStage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.Launcher.COOKIE_MANAGER;

public class MicrosoftAccountLoginStage extends WebStage implements MicrosoftService.WebViewCallback {
    public static final MicrosoftAccountLoginStage INSTANCE = new MicrosoftAccountLoginStage();

    CompletableFuture<String> future;
    Predicate<String> urlTester;

    public MicrosoftAccountLoginStage() {
        super(600, 600);
        initModality(Modality.APPLICATION_MODAL);

        titleProperty().bind(webEngine.titleProperty());

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
            COOKIE_MANAGER.getCookieStore().removeAll();

            webEngine.load(initialURL);
            show();
        });
        this.future = new CompletableFuture<>();
        this.urlTester = urlTester;
        return future;
    }
}
