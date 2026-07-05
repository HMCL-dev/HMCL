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
package org.jackhuang.hmcl.ui.account;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.friend.FriendControl;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FriendListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final Account account;
    private final FriendControl control;

    private final TabHeader tab;
    private final TabHeader.Tab<Right> friendPage = new TabHeader.Tab<>("friendPage");
    private final TransitionPane transitionPane = new TransitionPane();

    @SuppressWarnings("unused")
    private ChangeListener<String> instanceChangeListenerHolder;


    private ReadOnlyObjectWrapper<State> state = null;

    public FriendListPage(Account account, FriendControl control) {
        this.account = account;
        this.control = control;

        friendPage.setNodeSupplier(Right::new);
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

    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        if (state == null)
            state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.friend") + " - " + account.getProfileName()));
        return state;
    }

    private final class Right extends ListPageBase<FriendListItem> {
        private Right() {
            super();

            refresh();
        }

        private void refresh() {
            setItems(FXCollections.emptyObservableList());
            setLoading(true);
            setFailedReason(null);

            Task.supplyAsync(control::getFriendList).whenComplete(Schedulers.javafx(), (result, exception) -> {
                setLoading(false);
                if (exception != null) {
                    LOG.warning("Failed to get friend list: " + exception);
                    setFailedReason(i18n("account.friend.failed"));
                } else {
                    getItems().addAll(result.friends().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.FRIEND)).collect(Collectors.toList()));
                    getItems().addAll(result.outgoingRequests().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.OUTGOING)).collect(Collectors.toList()));
                    getItems().addAll(result.incomingRequests().stream().map(it -> new FriendListItem(it.profileId(), it.name(), FriendStatus.INCOMING)).collect(Collectors.toList()));
                }

            }).start();
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new RightSkin(this);
        }
    }

    private final class RightSkin extends ToolbarListPageSkin<FriendListItem, Right> {
        public RightSkin(Right skinnable) {
            super(skinnable);

            listView.setCellFactory(x -> new FriendListCell());
        }

        @Override
        protected List<Node> initializeToolbar(Right skinnable) {
            return List.of();
        }
    }

    private final class FriendListCell extends ListCell<FriendListItem> {
        private final Region graphic;

        public FriendListCell() {
            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8, 8, 8, 0));

            RipplerContainer container = new RipplerContainer(root);
            this.graphic = container;
        }
    }

    private record FriendListItem(String profileId, String name, FriendStatus status) {
    }

    private enum FriendStatus {
        FRIEND,
        INCOMING,
        OUTGOING
    }
}
