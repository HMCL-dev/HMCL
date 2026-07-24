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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.directory.GameDirectoryListItem;
import org.jackhuang.hmcl.ui.directory.GameDirectoryPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("version.manage")));
    /// Navigation drawer items for configured game directories.
    @SuppressWarnings("FieldCanBeLocal")
    private final ObservableList<GameDirectoryListItem> gameDirectoryListItems;

    public GameListPage() {
        gameDirectoryListItems = MappedObservableList.create(GameDirectoryManager.getGameDirectories(), gameDirectory -> {
            GameDirectoryListItem item = new GameDirectoryListItem(gameDirectory);
            FXUtils.setLimitWidth(item, 200);
            return item;
        });

        {
            ScrollPane pane = new ScrollPane();
            VBox.setVgrow(pane, Priority.ALWAYS);
            {
                AdvancedListItem addGameDirectoryItem = new AdvancedListItem();
                addGameDirectoryItem.getStyleClass().add("navigation-drawer-item");
                addGameDirectoryItem.setTitle(i18n("game_directory.new"));
                addGameDirectoryItem.setLeftIcon(SVG.ADD_CIRCLE);
                addGameDirectoryItem.setOnAction(e -> Controllers.navigate(new GameDirectoryPage(null)));

                pane.setFitToWidth(true);
                VBox wrapper = new VBox();
                wrapper.getStyleClass().add("advanced-list-box-content");
                VBox box = new VBox();
                box.setFillWidth(true);
                Bindings.bindContent(box.getChildren(), gameDirectoryListItems);
                wrapper.getChildren().setAll(box, addGameDirectoryItem);
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

        FXUtils.applyDragListener(this, file -> ModpackHelper.isFileModpackByExtension(file) || "json".equalsIgnoreCase(FileUtils.getNameWithoutExtension(file)), files -> {
            Path file = files.get(0);

            if (ModpackHelper.isFileModpackByExtension(file)) {
                Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(GameDirectoryManager.getSelectedRepository(), file), i18n("install.modpack"));
            } else if ("json".equalsIgnoreCase(FileUtils.getExtension(file))) {
                Versions.installFromJson(GameDirectoryManager.getSelectedRepository(), file);
            }
        });
    }

    public void modifyGlobalGameSettings() {
        Versions.modifyGlobalSettings(GameDirectoryManager.getSelectedRepository());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    private static class GameList extends ListPageBase<GameListItem> {
        private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

        public GameList() {
            GameDirectoryManager.registerVersionsListener(this::loadVersions);
            setOnFailedAction(e -> Controllers.navigate(Controllers.getDownloadPage()));
        }

        @FXThread
        private void loadVersions(HMCLGameRepository repository) {
            listenerHolder.clear();
            setLoading(true);
            setFailedReason(null);

            List<GameListItem> versionItems = repository.getDisplayVersions().map(instance -> new GameListItem(repository, instance.getId())).toList();

            getItems().setAll(versionItems); // 直接更新源数据

            if (versionItems.isEmpty()) {
                setFailedReason(i18n("version.empty.hint"));
            }

            setLoading(false);
        }

        public void refreshList() {
            GameDirectoryManager.getSelectedRepository().refreshVersionsAsync().start();
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new GameListSkin(this);
        }

        private static class GameListSkin extends ToolbarListPageSkin<GameListItem, GameList> {

            public GameListSkin(GameList skinnable) {
                super(skinnable, true);
                listView.setCellFactory(x -> new GameListCell());

                setupSkin(
                        new Node[]{
                                createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refreshList),
                                createToolbarButton2(i18n("search"), SVG.SEARCH, this::startSearch)
                        },
                        null
                );
            }

            @Override
            protected Predicate<GameListItem> updateSearchPredicate(String searchText) {
                if (searchText == null || searchText.isEmpty()) return item -> true;
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

            @Override
            protected String getEmptyPlaceholderText() {
                return i18n("version.empty.hint");
            }
        }
    }
}
