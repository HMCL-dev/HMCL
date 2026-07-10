/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.account.friend;

import javafx.scene.control.Skin;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.friend.FriendControl;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.ListPageBase;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FriendListPage extends ListPageBase<FriendListItem> {
    private final Account account;
    private final FriendControl control;

    public FriendListPage(Account account, FriendControl control) {
        super();
        this.account = account;
        this.control = control;

        refresh();
    }

    private void refresh() {
        getItems().clear();
        setLoading(true);
        setFailedReason(null);

        Task.supplyAsync(control::getFriendList).whenComplete(Schedulers.javafx(), (result, exception) -> {
            setLoading(false);

            if (exception != null) {
                LOG.warning("Failed to get friend list" + exception);
                setFailedReason(i18n("account.friend.failed"));
            } else {
                LOG.info("Received friend list" + result);

                getItems().addAll(result.friends().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.NORMAL)).toList());
                getItems().addAll(result.outgoingRequests().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.OUTGOING)).toList());
                getItems().addAll(result.incomingRequests().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.INCOMING)).toList());
            }

        }).start();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new FriendListPageSkin(this);
    }

    public Account getAccount() {
        return account;
    }

    public FriendControl getControl() {
        return control;
    }
}
