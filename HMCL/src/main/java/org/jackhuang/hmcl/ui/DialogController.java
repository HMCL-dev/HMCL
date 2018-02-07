/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.concurrency.JFXUtilities;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.MultiCharacterSelector;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.task.SilentException;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class DialogController {

    public static AuthInfo logIn(Account account) throws Exception {
        if (account instanceof YggdrasilAccount) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AuthInfo> res = new AtomicReference<>(null);
            JFXUtilities.runInFX(() -> {
                YggdrasilAccountLoginPane pane = new YggdrasilAccountLoginPane((YggdrasilAccount) account, it -> {
                        res.set(it);
                        latch.countDown();
                        Controllers.closeDialog();
                }, () -> {
                        latch.countDown();
                        Controllers.closeDialog();
                });
                pane.setDialog(Controllers.dialog(pane));
            });
            latch.await();
            return Optional.ofNullable(res.get()).orElseThrow(SilentException::new);
        }
        return account.logIn(MultiCharacterSelector.DEFAULT);
    }
}
