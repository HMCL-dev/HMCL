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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.profile.ProfileListItem;
import org.jackhuang.hmcl.ui.profile.ProfilePage;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public class GameListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("version.manage")));
    private final ListProperty<Profile> profiles = new SimpleListProperty<>(FXCollections.observableArrayList());
    @SuppressWarnings("FieldCanBeLocal")
    private final ObservableList<ProfileListItem> profileListItems;
    private final ObjectProperty<Profile> selectedProfile;

    public GameListPage() {
        profileListItems = MappedObservableList.create(profilesProperty(), profile -> {
            ProfileListItem item = new ProfileListItem(profile);
            FXUtils.setLimitWidth(item, 200);
            return item;
        });
        selectedProfile = createSelectedItemPropertyFor(profileListItems, Profile.class);

        GameList gameList = new GameList();

        {
            ScrollPane pane = new ScrollPane();
            VBox.setVgrow(pane, Priority.ALWAYS);
            {
                AdvancedListItem addProfileItem = new AdvancedListItem();
                addProfileItem.getStyleClass().add("navigation-drawer-item");
                addProfileItem.setTitle(i18n("profile.new"));
                addProfileItem.setLeftIcon(SVG.ADD_CIRCLE);
                addProfileItem.setOnAction(e -> Controllers.navigate(new ProfilePage(null)));

                pane.setFitToWidth(true);
                VBox wrapper = new VBox();
                wrapper.getStyleClass().add("advanced-list-box-content");
                VBox box = new VBox();
                box.setFillWidth(true);
                Bindings.bindContent(box.getChildren(), profileListItems);
                wrapper.getChildren().setAll(box, addProfileItem);
                pane.setContent(wrapper);
            }

            AdvancedListBox bottomLeftCornerList = new AdvancedListBox()
                    .addNavigationDrawerItem(i18n("install.new_game"), SVG.ADD_CIRCLE, Versions::addNewGame)
                    .addNavigationDrawerItem(i18n("install.modpack"), SVG.PACKAGE2, Versions::importModpack)
                    .addNavigationDrawerItem(i18n("button.refresh"), SVG.REFRESH, gameList::refreshList)
                    .addNavigationDrawerItem(i18n("settings.type.global.manage"), SVG.SETTINGS, this::modifyGlobalGameSettings);
            FXUtils.setLimitHeight(bottomLeftCornerList, 40 * 4 + 12 * 2);
            setLeft(pane, bottomLeftCornerList);
        }

        setCenter(gameList);
    }

    public ObjectProperty<Profile> selectedProfileProperty() {
        return selectedProfile;
    }

    public ObservableList<Profile> getProfiles() {
        return profiles.get();
    }

    public ListProperty<Profile> profilesProperty() {
        return profiles;
    }

    public void setProfiles(ObservableList<Profile> profiles) {
        this.profiles.set(profiles);
    }

    public void modifyGlobalGameSettings() {
        Versions.modifyGlobalSettings(Profiles.getSelectedProfile());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    private static class GameList extends ListPageBase<GameListItem> {
        private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

        public GameList() {
            Profiles.registerVersionsListener(this::loadVersions);

            setOnFailedAction(e -> Controllers.navigate(Controllers.getDownloadPage()));
        }

        @FXThread
        private void loadVersions(Profile profile) {
            listenerHolder.clear();
            setLoading(true);
            setFailedReason(null);
            if (profile != Profiles.getSelectedProfile())
                return;

            ObservableList<GameListItem> children = FXCollections.observableList(profile.getRepository().getDisplayVersions()
                    .map(instance -> new GameListItem(profile, instance.getId()))
                    .toList());
            setItems(children);
            if (children.isEmpty()) {
                setFailedReason(i18n("version.empty.hint"));
            }
            setLoading(false);
        }

        public void refreshList() {
            Profiles.getSelectedProfile().getRepository().refreshVersionsAsync().start();
        }

        @Override
        protected GameListSkin createDefaultSkin() {
            return new GameListSkin();
        }

        private class GameListSkin extends ToolbarListPageSkin<GameListItem, GameList> {

            public GameListSkin() {
                super(GameList.this);
            }

            @Override
            protected List<Node> initializeToolbar(GameList skinnable) {
                return Collections.emptyList();
            }

            @Override
            protected ListCell<GameListItem> createListCell(JFXListView<GameListItem> listView) {
                return new GameListCell();
            }
        }
    }

}
