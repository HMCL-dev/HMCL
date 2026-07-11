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
import com.jfoenix.controls.JFXSpinner;
import javafx.scene.control.Skin;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.friend.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Pair.pair;
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

        Task.supplyAsync(control::getFriendList)
                .thenApplyAsync(result -> pair(result, control.getPresence(EnumPresenceStatus.ONLINE)))
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    setLoading(false);

                    var friends = result.key();
                    var presence = result.value();

                    LOG.info("Received friend list" + friends);
                    LOG.info("Received presences" + presence);

                    if (exception != null) {
                        LOG.warning("Failed to get friend list" + exception);
                        setFailedReason(i18n("account.friend.failed"));
                    } else {
                        setData(friends, presence);
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

    public void confirmUpdateFriend(FriendListItem item, EnumUpdateType updateType, String text, String failedText) {
        JFXButton btnOk = new JFXButton(i18n("button.ok"));

        StackPane stackPane = new StackPane();

        var spinner = new JFXSpinner();
        spinner.getStyleClass().add("small-spinner");

        btnOk.setOnAction(action -> {
            stackPane.getChildren().setAll(spinner);

            Task.supplyAsync(() -> control.updateFriend(null, control.toUuidWithoutDashes(item.profileId()), updateType)).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception != null) {
                    LOG.warning("Failed to update friend", exception);
                    fireEvent(new DialogCloseEvent());
                    Controllers.dialog(failedText + "\n" + StringUtils.getStackTrace(exception), null, MessageDialogPane.MessageType.ERROR);
                    return;
                }

                setData(result, null);
                fireEvent(new DialogCloseEvent());
            }).start();
        });

        btnOk.getStyleClass().add("dialog-accept");
        stackPane.getChildren().setAll(btnOk);

        var dialog = new MessageDialogPane.Builder(i18n(text, item.name()), i18n("message.question"), MessageDialogPane.MessageType.QUESTION)
                .addAction(stackPane)
                .addCancel(null)
                .build();

        Controllers.dialog(dialog);
    }

    private PresenceResponse lastPresence;

    public void setData(FriendResponse friends, PresenceResponse nullablePresence) {
        if (nullablePresence != null) lastPresence = nullablePresence;

        var presence = lastPresence;

        getItems().clear();

        if (friends.empty()) {
            setFailedReason(i18n("account.friend.empty"));
            setOnFailedAction(event -> onAddFriend.run());
            return;
        }

        Map<String, PresenceItem> presenceMap = presence == null || presence.presence() == null
                ? Map.of()
                : presence.presence().stream()
                .collect(Collectors.toMap(PresenceItem::profileId, Function.identity()));

        List<Map.Entry<List<FriendItem>, FriendStatus>> sources = List.of(
                Map.entry(friends.friends(), FriendStatus.NORMAL),
                Map.entry(friends.outgoingRequests(), FriendStatus.OUTGOING),
                Map.entry(friends.incomingRequests(), FriendStatus.INCOMING)
        );

        sources.forEach(entry ->
                getItems().addAll(entry.getKey().stream()
                        .map(friend -> {
                            PresenceItem p = presenceMap.get(friend.profileId());
                            return new FriendListItem(
                                    friend.profileId(),
                                    friend.name(),
                                    entry.getValue(),
                                    p != null ? p.status() : null,
                                    p != null ? p.lastUpdated() : null
                            );
                        })
                        .toList())
        );
    }
}
