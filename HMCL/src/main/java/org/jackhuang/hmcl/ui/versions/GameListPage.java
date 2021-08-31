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
import javafx.scene.control.SkinBase;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.profile.ProfileListItem;
import org.jackhuang.hmcl.ui.profile.ProfilePage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public class GameListPage extends ListPageBase<GameListItem> implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("version.manage"), -1));
    private final ListProperty<Profile> profiles = new SimpleListProperty<>(FXCollections.observableArrayList());
    @SuppressWarnings("FieldCanBeLocal")
    private final ObservableList<ProfileListItem> profileListItems;
    private final ObjectProperty<Profile> selectedProfile;

    private ToggleGroup toggleGroup;

    public GameListPage() {
        EventBus.EVENT_BUS.channel(RefreshingVersionsEvent.class).register(event -> {
            if (event.getSource() == Profiles.getSelectedProfile().getRepository())
                runInFX(() -> setLoading(true));
        });
        profileListItems = MappedObservableList.create(profilesProperty(), profile -> {
            ProfileListItem item = new ProfileListItem(profile);
            FXUtils.setLimitWidth(item, 200);
            return item;
        });
        selectedProfile = createSelectedItemPropertyFor(profileListItems, Profile.class);
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

    @Override
    protected GameListPageSkin createDefaultSkin() {
        return new GameListPageSkin();
    }

    public void modifyGlobalGameSettings() {
        Versions.modifyGlobalSettings(Profiles.getSelectedProfile());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    private class GameListPageSkin extends SkinBase<GameListPage> {

        protected GameListPageSkin() {
            super(GameListPage.this);


            BorderPane root = new BorderPane();
            GameList gameList = new GameList();

            {
                BorderPane left = new BorderPane();
                FXUtils.setLimitWidth(left, 200);
                root.setLeft(left);

                {
                    AdvancedListItem addProfileItem = new AdvancedListItem();
                    addProfileItem.getStyleClass().add("navigation-drawer-item");
                    addProfileItem.setTitle(i18n("profile.new"));
                    addProfileItem.setActionButtonVisible(false);
                    addProfileItem.setLeftGraphic(VersionPage.wrap(SVG.plusCircleOutline(Theme.blackFillBinding(), 24, 24)));
                    addProfileItem.setOnAction(e -> Controllers.navigate(new ProfilePage(null)));

                    ScrollPane pane = new ScrollPane();
                    VBox wrapper = new VBox();
                    wrapper.getStyleClass().add("advanced-list-box-content");
                    VBox box = new VBox();
                    Bindings.bindContent(box.getChildren(), profileListItems);
                    wrapper.getChildren().setAll(box, addProfileItem);
                    pane.setContent(wrapper);
                    left.setCenter(pane);
                }

                {
                    AdvancedListItem installNewGameItem = new AdvancedListItem();
                    installNewGameItem.getStyleClass().add("navigation-drawer-item");
                    installNewGameItem.setTitle(i18n("install.new_game"));
                    installNewGameItem.setActionButtonVisible(false);
                    installNewGameItem.setLeftGraphic(VersionPage.wrap(SVG.plusCircleOutline(Theme.blackFillBinding(), 24, 24)));
                    installNewGameItem.setOnAction(e -> Versions.addNewGame());

                    AdvancedListItem installModpackItem = new AdvancedListItem();
                    installModpackItem.getStyleClass().add("navigation-drawer-item");
                    installModpackItem.setTitle(i18n("install.modpack"));
                    installModpackItem.setActionButtonVisible(false);
                    installModpackItem.setLeftGraphic(VersionPage.wrap(SVG.pack(Theme.blackFillBinding(), 24, 24)));
                    installModpackItem.setOnAction(e -> Versions.importModpack());

                    AdvancedListItem downloadModpackItem = new AdvancedListItem();
                    downloadModpackItem.getStyleClass().add("navigation-drawer-item");
                    downloadModpackItem.setTitle(i18n("modpack.download"));
                    downloadModpackItem.setActionButtonVisible(false);
                    downloadModpackItem.setLeftGraphic(VersionPage.wrap(SVG.fire(Theme.blackFillBinding(), 24, 24)));
                    downloadModpackItem.setOnAction(e -> Versions.downloadModpack());

                    AdvancedListItem refreshItem = new AdvancedListItem();
                    refreshItem.getStyleClass().add("navigation-drawer-item");
                    refreshItem.setTitle(i18n("button.refresh"));
                    refreshItem.setActionButtonVisible(false);
                    refreshItem.setLeftGraphic(VersionPage.wrap(SVG.refresh(Theme.blackFillBinding(), 24, 24)));
                    refreshItem.setOnAction(e -> gameList.refreshList());

                    AdvancedListItem globalManageItem = new AdvancedListItem();
                    globalManageItem.getStyleClass().add("navigation-drawer-item");
                    globalManageItem.setTitle(i18n("settings.type.global.manage"));
                    globalManageItem.setActionButtonVisible(false);
                    globalManageItem.setLeftGraphic(VersionPage.wrap(SVG.gearOutline(Theme.blackFillBinding(), 24, 24)));
                    globalManageItem.setOnAction(e -> modifyGlobalGameSettings());

                    AdvancedListBox bottomLeftCornerList = new AdvancedListBox()
                            .add(installNewGameItem)
                            .add(installModpackItem)
                            .add(downloadModpackItem)
                            .add(refreshItem)
                            .add(globalManageItem);
                    FXUtils.setLimitHeight(bottomLeftCornerList, 40 * 5 + 12 * 2);
                    left.setBottom(bottomLeftCornerList);
                }
            }

            root.setCenter(gameList);
            getChildren().setAll(root);
        }
    }

    private class GameList extends ListPageBase<GameListItem> {
        public GameList() {
            super();

            Profiles.registerVersionsListener(this::loadVersions);
        }

        private void loadVersions(Profile profile) {
            HMCLGameRepository repository = profile.getRepository();
            toggleGroup = new ToggleGroup();
            WeakListenerHolder listenerHolder = new WeakListenerHolder();
            toggleGroup.getProperties().put("ReferenceHolder", listenerHolder);
            List<GameListItem> children = repository.getVersions().parallelStream()
                    .filter(version -> !version.isHidden())
                    .sorted(Comparator.comparing((Version version) -> version.getReleaseTime() == null ? new Date(0L) : version.getReleaseTime())
                            .thenComparing(a -> VersionNumber.asVersion(a.getId())))
                    .map(version -> new GameListItem(toggleGroup, profile, version.getId()))
                    .collect(Collectors.toList());
            runInFX(() -> {
                if (profile == Profiles.getSelectedProfile()) {
                    setLoading(false);
                    itemsProperty().setAll(children);
                    children.forEach(GameListItem::checkSelection);

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
