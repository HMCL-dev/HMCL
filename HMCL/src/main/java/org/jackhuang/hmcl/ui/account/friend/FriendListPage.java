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

import com.jfoenix.controls.JFXButton;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.friend.EnumUpdateType;
import org.jackhuang.hmcl.game.friend.FriendControl;
import org.jackhuang.hmcl.game.friend.FriendResponse;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FriendListPage extends ListPageBase<FriendListItem> {
    private final Account account;
    private final FriendControl control;
    private final Runnable onAddFriend;

    public FriendListPage(Account account, FriendControl control, Runnable onAddFriend) {
        super();

        this.account = account;
        this.control = control;
        this.onAddFriend = onAddFriend;

        refresh();
    }

    public void refresh() {
        setLoading(true);
        setFailedReason(null);
        setOnFailedAction(null);

        Task.supplyAsync(control::getFriendList).whenComplete(Schedulers.javafx(), (result, exception) -> {
            setLoading(false);

            if (exception != null) {
                LOG.warning("Failed to get friend list" + exception);
                setFailedReason(i18n("account.friend.failed"));
            } else {
                LOG.info("Received friend list" + result);
                setFriends(result);
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

    public void tryDeleteFriend(FriendListItem item) {
        SpinnerPane spinnerPane = new SpinnerPane();
        JFXButton btnOk = new JFXButton(i18n("button.ok"));

        btnOk.setOnAction(action -> {
            spinnerPane.showSpinner();
            Task.supplyAsync(() -> control.updateFriend(control.toUuidWithoutDashes(item.profileId()), null, EnumUpdateType.REMOVE)).whenComplete(Schedulers.javafx(), (result, exception) -> {
                spinnerPane.hideSpinner();

                if (exception != null) {
                    LOG.warning("Failed to delete friend", exception);
                    fireEvent(new DialogCloseEvent());
                    Controllers.dialog(i18n("account.friend.delete.failed"), null, MessageDialogPane.MessageType.ERROR);
                    return;
                }

                setFriends(result);
            }).start();
        });

        btnOk.getStyleClass().add("dialog-accept");
        spinnerPane.setContent(btnOk);


        var dialog = new MessageDialogPane.Builder(i18n("account.friend.delete.confirm", item.name()), i18n("message.question"), MessageDialogPane.MessageType.QUESTION)
                .addAction(spinnerPane)
                .addCancel(null)
                .build();

        Controllers.dialog(dialog);
    }

    public void setFriends(FriendResponse result) {
        getItems().clear();

        if (result.friends().isEmpty() && result.incomingRequests().isEmpty() && result.outgoingRequests().isEmpty()) {
            setFailedReason(i18n("account.friend.empty"));
            setOnFailedAction(event -> onAddFriend.run());
        } else {
            getItems().addAll(result.friends().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.NORMAL)).toList());
            getItems().addAll(result.outgoingRequests().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.OUTGOING)).toList());
            getItems().addAll(result.incomingRequests().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.INCOMING)).toList());
        }
    }
}
