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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.TexturesLoader;
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
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FriendListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final Account account;
    private final FriendControl control;
    private static final Pattern REGEX = Pattern.compile("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})");

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
        private final Canvas avatar = new Canvas(32, 32);
        private final TwoLineListItem twoLineListItem = new TwoLineListItem();

        public FriendListCell() {
            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8, 8, 8, 8));

            RipplerContainer container = new RipplerContainer(root);
            this.graphic = container;

            HBox center = new HBox();
            center.setMouseTransparent(true);
            center.setPrefWidth(Region.USE_PREF_SIZE);
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);

            center.getChildren().setAll(avatar, twoLineListItem);
            root.setCenter(center);

            HBox right = new HBox();

            var deleteButton = FXUtils.newToggleButton4(SVG.PERSON_CANCEL);

            right.getChildren().addAll(deleteButton);
            right.setAlignment(Pos.CENTER_RIGHT);
            root.setRight(right);
        }

        @Override
        protected void updateItem(FriendListItem item, boolean empty) {
            var currentItem = getItem();

            super.updateItem(item, empty);

            TexturesLoader.unbindAvatar(avatar);

            if (empty || item == null) {
                this.setGraphic(null);
            } else {
                this.setGraphic(this.graphic);

                if (currentItem == getItem()) return;

                String formatted = REGEX.matcher(item.profileId()).replaceAll("$1-$2-$3-$4-$5");
                var uuid = UUID.fromString(formatted);

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
                twoLineListItem.setSubtitle(toString());
            }
        }
    }

    private record FriendListItem(String profileId, String name, FriendStatus status) {
    }

    private enum FriendStatus {
        FRIEND, INCOMING, OUTGOING
    }
}
