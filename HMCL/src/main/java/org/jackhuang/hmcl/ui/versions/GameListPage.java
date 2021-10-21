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

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.profile.ProfileListItem;
import org.jackhuang.hmcl.ui.profile.ProfilePage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public class GameListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("version.manage")));
    private final ListProperty<Profile> profiles = new SimpleListProperty<>(FXCollections.observableArrayList());
    @SuppressWarnings("FieldCanBeLocal")
    private final ObservableList<ProfileListItem> profileListItems;
    private final ObjectProperty<Profile> selectedProfile;

    private ToggleGroup toggleGroup;

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
                addProfileItem.setActionButtonVisible(false);
                addProfileItem.setLeftGraphic(VersionPage.wrap(SVG::plusCircleOutline));
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
                    .addNavigationDrawerItem(installNewGameItem -> {
                        installNewGameItem.setTitle(i18n("install.new_game"));
                        installNewGameItem.setLeftGraphic(VersionPage.wrap(SVG::plusCircleOutline));
                        installNewGameItem.setOnAction(e -> Versions.addNewGame());
                    })
                    .addNavigationDrawerItem(installModpackItem -> {
                        installModpackItem.setTitle(i18n("install.modpack"));
                        installModpackItem.setLeftGraphic(VersionPage.wrap(SVG::pack));
                        installModpackItem.setOnAction(e -> Versions.importModpack());
                    })
                    .addNavigationDrawerItem(refreshItem -> {
                        refreshItem.setTitle(i18n("button.refresh"));
                        refreshItem.setLeftGraphic(VersionPage.wrap(SVG::refresh));
                        refreshItem.setOnAction(e -> gameList.refreshList());
                    })
                    .addNavigationDrawerItem(globalManageItem -> {
                        globalManageItem.setTitle(i18n("settings.type.global.manage"));
                        globalManageItem.setLeftGraphic(VersionPage.wrap(SVG::gearOutline));
                        globalManageItem.setOnAction(e -> modifyGlobalGameSettings());
                    });
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

    private class GameList extends ListPageBase<GameListItem> {
        public GameList() {
            super();

            Profiles.registerVersionsListener(this::loadVersions);

            setOnFailedAction(e -> Controllers.navigate(Controllers.getDownloadPage()));
        }

        private void loadVersions(Profile profile) {
            setLoading(true);
            setFailedReason(null);
            HMCLGameRepository repository = profile.getRepository();
            toggleGroup = new ToggleGroup();
            WeakListenerHolder listenerHolder = new WeakListenerHolder();
            toggleGroup.getProperties().put("ReferenceHolder", listenerHolder);
            runInFX(() -> {
                if (profile == Profiles.getSelectedProfile()) {
                    setLoading(false);
                    List<GameListItem> children = repository.getDisplayVersions()
                            .map(version -> new GameListItem(toggleGroup, profile, version.getId()))
                            .collect(Collectors.toList());
                    itemsProperty().setAll(children);
                    children.forEach(GameListItem::checkSelection);

                    if (children.isEmpty()) {
                        setFailedReason(i18n("version.empty.hint"));
                    }

                    profile.selectedVersionProperty().addListener(listenerHolder.weak((a, b, newValue) -> {
                        FXUtils.checkFxUserThread();
                        children.forEach(it -> it.selectedProperty().set(false));
                        children.stream()
                                .filter(it -> it.getVersion().equals(newValue))
                                .findFirst()
                                .ifPresent(it -> it.selectedProperty().set(true));
                    }));
                }
                toggleGroup.selectedToggleProperty().addListener((o, a, toggle) -> {
                    if (toggle == null) return;
                    GameListItem model = (GameListItem) toggle.getUserData();
                    model.getProfile().setSelectedVersion(model.getVersion());
                });
            });
        }

        public void refreshList() {
            Profiles.getSelectedProfile().getRepository().refreshVersionsAsync().start();
        }

        @Override
        protected GameListSkin createDefaultSkin() {
            return new GameListSkin();
        }

        private class GameListSkin extends ToolbarListPageSkin<GameList> {

            public GameListSkin() {
                super(GameList.this);
            }

            @Override
            protected List<Node> initializeToolbar(GameList skinnable) {
                return Collections.emptyList();
            }
        }
    }

}
