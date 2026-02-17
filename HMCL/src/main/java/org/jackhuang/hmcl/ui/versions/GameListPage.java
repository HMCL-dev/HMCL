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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.profile.ProfileListItem;
import org.jackhuang.hmcl.ui.profile.ProfilePage;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
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
                    .addNavigationDrawerItem(i18n("settings.type.global.manage"), SVG.SETTINGS, this::modifyGlobalGameSettings);
            FXUtils.setLimitHeight(bottomLeftCornerList, 40 * 3 + 12 * 2);
            setLeft(pane, bottomLeftCornerList);
        }

        setCenter(new GameList());
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

        private final ObservableList<GameListItem> sourceList = FXCollections.observableArrayList();
        private final FilteredList<GameListItem> filteredList = new FilteredList<>(sourceList);

        public GameList() {
            setItems(filteredList);

            Profiles.registerVersionsListener(this::loadVersions);

            setOnFailedAction(e -> Controllers.navigate(Controllers.getDownloadPage()));
        }

        @FXThread
        private void loadVersions(Profile profile) {
            listenerHolder.clear();
            setLoading(true);
            setFailedReason(null);

            List<GameListItem> versionItems = profile.getRepository().getDisplayVersions().map(instance -> new GameListItem(profile, instance.getId())).toList();

            sourceList.setAll(versionItems);

            if (versionItems.isEmpty()) {
                setFailedReason(i18n("version.empty.hint"));
            }

            setLoading(false);
        }

        private Predicate<GameListItem> createPredicate(String searchText) {
            if (searchText == null || searchText.isEmpty()) {
                return item -> true;
            }

            if (searchText.startsWith("regex:")) {
                String regex = searchText.substring("regex:".length());
                try {
                    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    return item -> pattern.matcher(item.id).find();
                } catch (PatternSyntaxException e) {
                    return item -> false;
                }
            } else {
                return item -> item.id.toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT));
            }
        }

        public void refreshList() {
            Profiles.getSelectedProfile().getRepository().refreshVersionsAsync().start();
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new GameListSkin(this);
        }

        private static class GameListSkin extends SkinBase<GameList> {
            private final TransitionPane toolbarPane;
            private final HBox searchBar;
            private final HBox toolbarNormal;

            private final JFXTextField searchField;

            public GameListSkin(GameList skinnable) {
                super(skinnable);

                StackPane pane = new StackPane();
                pane.setPadding(new Insets(10));
                pane.getStyleClass().addAll("notice-pane");

                ComponentList root = new ComponentList();
                root.getStyleClass().add("no-padding");
                JFXListView<GameListItem> listView = new JFXListView<>();

                {
                    toolbarPane = new TransitionPane();

                    searchBar = new HBox();
                    toolbarNormal = new HBox();

                    searchBar.setAlignment(Pos.CENTER);
                    searchBar.setPadding(new Insets(0, 5, 0, 5));
                    searchField = new JFXTextField();
                    searchField.setPromptText(i18n("search"));
                    HBox.setHgrow(searchField, Priority.ALWAYS);
                    PauseTransition pause = new PauseTransition(Duration.millis(100));
                    pause.setOnFinished(e -> skinnable.filteredList.setPredicate(skinnable.createPredicate(searchField.getText())));
                    searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                        pause.setRate(1);
                        pause.playFromStart();
                    });

                    JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE, () -> {
                        changeToolbar(toolbarNormal);
                        searchField.clear();
                    });

                    onEscPressed(searchField, closeSearchBar::fire);

                    searchBar.getChildren().setAll(searchField, closeSearchBar);

                    toolbarNormal.getChildren().setAll(createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refreshList), createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar)));

                    toolbarPane.setContent(toolbarNormal, ContainerAnimations.FADE);

                    root.getContent().add(toolbarPane);
                }

                {
                    SpinnerPane center = new SpinnerPane();
                    ComponentList.setVgrow(center, Priority.ALWAYS);
                    center.getStyleClass().add("large-spinner-pane");
                    center.loadingProperty().bind(skinnable.loadingProperty());

                    listView.setCellFactory(x -> new GameListCell());
                    Bindings.bindContent(listView.getItems(), skinnable.getItems());

                    ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                    center.failedReasonProperty().bind(skinnable.failedReasonProperty());
                    center.setContent(listView);
                    root.getContent().add(center);
                }

                pane.getChildren().setAll(root);
                getChildren().setAll(pane);
            }

            private void changeToolbar(HBox newToolbar) {
                Node oldToolbar = toolbarPane.getCurrentNode();
                if (newToolbar != oldToolbar) {
                    toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
                    if (newToolbar == searchBar) {
                        runInFX(searchField::requestFocus);
                    }
                }
            }
        }
    }
}
