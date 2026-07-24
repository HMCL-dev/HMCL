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
import com.jfoenix.controls.JFXListView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.game.friend.EnumPresenceStatus;
import org.jackhuang.hmcl.game.friend.EnumUpdateType;
import org.jackhuang.hmcl.game.friend.FriendControl;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FriendListCell extends MDListCell<FriendListItem> {
    private final Account account;
    private final FriendControl control;
    private final Canvas avatar = new Canvas(32, 32);
    private final TwoLineListItem twoLineListItem = new TwoLineListItem();

    private final JFXButton copyButton = FXUtils.newToggleButton4(SVG.CONTENT_COPY);
    private final JFXButton deleteButton = FXUtils.newToggleButton4(SVG.PERSON_OFF);
    private final JFXButton acceptButton = FXUtils.newToggleButton4(SVG.PERSON_CHECK);
    private final JFXButton rejectButton = FXUtils.newToggleButton4(SVG.PERSON_CANCEL);
    private final HBox actions = new HBox();

    public FriendListCell(JFXListView<FriendListItem> listView, Account account, FriendControl control, FriendListPage friendListPage) {
        super(listView);

        this.account = account;
        this.control = control;

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8, 8, 8, 8));

        HBox center = new HBox();
        center.setMouseTransparent(true);
        center.setPrefWidth(Region.USE_PREF_SIZE);
        center.setSpacing(8);
        center.setAlignment(Pos.CENTER_LEFT);

        center.getChildren().setAll(avatar, twoLineListItem);
        root.setCenter(center);

        copyButton.setOnAction(event -> FXUtils.copyText(getItem().profileId().toString()));
        FXUtils.installFastTooltip(copyButton, i18n("account.copy_uuid"));

        deleteButton.setOnAction(event -> friendListPage.confirmUpdateFriend(getItem(), EnumUpdateType.REMOVE, i18n("account.friend.delete.confirm"), i18n("account.friend.delete.failed")));
        FXUtils.installFastTooltip(deleteButton, i18n("account.friend.delete"));

        acceptButton.setOnAction(event -> friendListPage.confirmUpdateFriend(getItem(), EnumUpdateType.ADD, i18n("account.friend.accept.confirm"), i18n("account.friend.accept.failed")));
        FXUtils.installFastTooltip(acceptButton, i18n("account.friend.accept"));

        rejectButton.setOnAction(event -> friendListPage.confirmUpdateFriend(getItem(), EnumUpdateType.REMOVE, i18n("account.friend.reject.confirm"), i18n("account.friend.reject.failed")));
        FXUtils.installFastTooltip(rejectButton, i18n("account.friend.reject"));

        actions.setAlignment(Pos.CENTER_RIGHT);

        root.setRight(actions);

        getContainer().getChildren().setAll(root);
    }

    @Override
    protected void updateControl(FriendListItem item, boolean empty) {
        TexturesLoader.unbindAvatar(avatar);

        if (item == null || empty) return;

        var uuid = item.profileId();

        Task.supplyAsync(() -> {
            CompleteGameProfile profile = null;

            if (account instanceof YggdrasilAccount yggdrasilAccount) {
                profile = yggdrasilAccount.getYggdrasilService().getCompleteGameProfile(uuid).orElseThrow();
            } else if (account instanceof MicrosoftAccount microsoftAccount) {
                profile = microsoftAccount.getService().getCompleteGameProfile(uuid).orElseThrow();
            }

            var texture = YggdrasilService.getTextures(profile).map(it -> it.get(TextureType.SKIN)).orElseThrow();
            return TexturesLoader.loadTexture(texture);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                TexturesLoader.drawAvatar(avatar, result.image());
                return;
            } else LOG.warning("Failed to load skin", exception);

            var skin = TexturesLoader.getDefaultSkin(uuid);
            TexturesLoader.drawAvatar(avatar, skin.image());
        }).start();

        twoLineListItem.setTitle(item.name());

        var statusText = i18n("account.friend.presence_status." + (item.presenceStatus() == null ? EnumPresenceStatus.OFFLINE : item.presenceStatus()).name().toLowerCase(Locale.ROOT));

        twoLineListItem.setSubtitle(i18n("account.friend.presence_status.last_updated", I18n.formatDateTime(item.lastUpdated())) + " / " + statusText);

        twoLineListItem.getTags().clear();
        if (item.status() != FriendStatus.NORMAL) {
            var tag = new Label(i18n("account.friend." + item.status().name().toLowerCase(Locale.ROOT)));
            tag.getStyleClass().add("tag");
            twoLineListItem.getTags().add(tag);
        }

        actions.getChildren().setAll(copyButton);

        if (item.status() == FriendStatus.NORMAL) {
            actions.getChildren().addAll(deleteButton);
        } else if (item.status() == FriendStatus.INCOMING) {
            actions.getChildren().addAll(acceptButton, rejectButton);
        }
    }
}
