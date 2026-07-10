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

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.friend.EnumUpdateType;
import org.jackhuang.hmcl.game.friend.FriendControl;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FriendPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final Account account;
    private final FriendControl control;

    private final TabHeader tab;
    private final TabHeader.Tab<FriendListPage> friendPage = new TabHeader.Tab<>("friendPage");
    private final TransitionPane transitionPane = new TransitionPane();

    @SuppressWarnings("unused")
    private ChangeListener<String> instanceChangeListenerHolder;


    private ReadOnlyObjectWrapper<State> state = null;

    public FriendPage(Account account, FriendControl control) {
        this.account = account;
        this.control = control;

        friendPage.setNodeSupplier(() -> new FriendListPage(account, control));
        tab = new TabHeader(transitionPane, friendPage);
        tab.select(friendPage);

        BorderPane left = new BorderPane();
        FXUtils.setLimitWidth(left, 200);
        VBox.setVgrow(left, Priority.ALWAYS);
        setLeft(left);

        AdvancedListBox sideBar = new AdvancedListBox().addNavigationDrawerTab(tab, friendPage, i18n("account.friend"), SVG.GROUP);
        left.setTop(sideBar);

        AdvancedListBox toolbar = new AdvancedListBox().addNavigationDrawerItem(i18n("account.friend.add"), SVG.PERSON_ADD, this::onAddFriend);
        BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));
        left.setBottom(toolbar);

        setCenter(transitionPane);
    }

    private void onAddFriend() {
        Controllers.prompt(i18n("account.friend.add"), (name, resultHandler) -> {
            Task.runAsync(() -> control.updateFriend(name, EnumUpdateType.ADD)).whenComplete(Schedulers.javafx(), e -> {
                if (e == null) resultHandler.resolve();
                else {
                    LOG.warning("Failed to add friend", e);
                    resultHandler.reject(e.getMessage());
                }
            }).start();
        }, null);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        if (state == null)
            state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.friend") + " - " + account.getProfileName()));
        return state;
    }
}
