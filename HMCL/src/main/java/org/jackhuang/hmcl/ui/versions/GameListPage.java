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
import com.jfoenix.controls.JFXTextField;
import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.directory.GameDirectoryListItem;
import org.jackhuang.hmcl.ui.directory.GameDirectoryPage;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.setting.SettingsManager.settings;

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

    private static class GameList extends ListPageBase<GameListEntry> {
        private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

        private final ObservableList<GameListEntry> sourceList = FXCollections.observableArrayList();
        private final InvalidationListener groupsListener = observable -> {
            finishGroupAnimation();
            rebuildRows();
        };
        private List<GameListItem> versionItems = List.of();
        private HMCLGameRepository repository;
        private String searchText = "";
        private @Nullable Timeline groupAnimation;
        private @Nullable Runnable groupAnimationFinalizer;

        public GameList() {
            setItems(sourceList);

            GameDirectoryManager.registerVersionsListener(this::loadVersions);
            settings().getInstanceGroupNames().addListener(groupsListener);
            settings().getInstanceGroupMembership().addListener(groupsListener);

            setOnFailedAction(e -> Controllers.navigate(Controllers.getDownloadPage()));
        }

        @FXThread
        private void loadVersions(HMCLGameRepository repository) {
            listenerHolder.clear();
            setLoading(true);
            setFailedReason(null);

            this.repository = repository;
            this.versionItems = repository.getDisplayVersions()
                    .map(instance -> new GameListItem(repository, instance.getId()))
                    .toList();
            rebuildRows();

            if (this.versionItems.isEmpty()) {
                setFailedReason(i18n("version.empty.hint"));
            }

            setLoading(false);
        }

        private void rebuildRows() {
            rebuildRows(false);
        }

        private void rebuildRows(boolean preserveGroupVisibility) {
            if (repository == null) {
                return;
            }

            Predicate<GameListItem> predicate = createPredicate(searchText);
            List<GameListItem> visibleItems = versionItems.stream().filter(predicate).toList();
            List<LauncherSettings.InstanceGroup> groups = settings().getInstanceGroups(repository.getGameDirectory().getId());
            if (groups.isEmpty()) {
                visibleItems.forEach(item -> item.setGroupVisibility(1));
                sourceList.setAll(visibleItems);
                return;
            }

            Map<String, List<GameListItem>> groupedItems = new HashMap<>();
            List<GameListItem> ungroupedItems = new ArrayList<>();
            for (GameListItem item : visibleItems) {
                String groupId = settings().getInstanceGroup(repository.getGameDirectory().getId(), item.getId());
                if (groupId == null) {
                    ungroupedItems.add(item);
                } else {
                    groupedItems.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(item);
                }
            }

            boolean searching = searchText != null && !searchText.isEmpty();
            List<GameListEntry> rows = new ArrayList<>();
            for (LauncherSettings.InstanceGroup group : groups) {
                List<GameListItem> items = groupedItems.getOrDefault(group.id(), List.of());
                if (searching && items.isEmpty()) {
                    continue;
                }
                boolean expanded = searching || !settings().isInstanceGroupCollapsed(
                        repository.getGameDirectory().getId(), group.id());
                rows.add(new GameListGroupItem(group.id(), group.name(), items.size(), expanded,
                        () -> toggleGroup(group.id()), () -> renameGroup(group), () -> deleteGroup(group)));
                if (expanded) {
                    if (!preserveGroupVisibility) {
                        items.forEach(item -> item.setGroupVisibility(1));
                    }
                    rows.addAll(items);
                }
            }

            if (!ungroupedItems.isEmpty()) {
                String ungroupedId = "";
                boolean expanded = searching || !settings().isInstanceGroupCollapsed(
                        repository.getGameDirectory().getId(), ungroupedId);
                rows.add(new GameListGroupItem(null, i18n("version.group.ungrouped"), ungroupedItems.size(), expanded,
                        () -> toggleGroup(ungroupedId), null, null));
                if (expanded) {
                    if (!preserveGroupVisibility) {
                        ungroupedItems.forEach(item -> item.setGroupVisibility(1));
                    }
                    rows.addAll(ungroupedItems);
                }
            }
            sourceList.setAll(rows);
        }

        private void toggleGroup(String groupId) {
            if (searchText != null && !searchText.isEmpty()) {
                return;
            }

            finishGroupAnimation();
            GameListGroupItem currentHeader = findGroupHeader(groupId);
            if (currentHeader == null) {
                return;
            }

            boolean expanding = !currentHeader.isExpanded();
            GameListGroupItem animatedHeader;
            List<GameListItem> animatedItems;
            if (expanding) {
                settings().setInstanceGroupCollapsed(repository.getGameDirectory().getId(), groupId, false);
                animatedItems = versionItems.stream()
                        .filter(item -> belongsToGroup(item, groupId))
                        .toList();
                animatedItems.forEach(item -> item.setGroupVisibility(0));
                animatedHeader = currentHeader;
                animatedHeader.setExpanded(true);
                animatedHeader.setExpansionProgress(0);
                int headerIndex = sourceList.indexOf(animatedHeader);
                sourceList.addAll(headerIndex + 1, animatedItems);
            } else {
                settings().setInstanceGroupCollapsed(repository.getGameDirectory().getId(), groupId, true);
                currentHeader.setExpanded(false);
                animatedHeader = currentHeader;
                animatedItems = getDisplayedGroupItems(animatedHeader);
            }

            double target = expanding ? 1 : 0;
            Runnable finalizer = () -> {
                animatedHeader.setExpansionProgress(target);
                animatedItems.forEach(item -> item.setGroupVisibility(target));
                if (!expanding) {
                    sourceList.removeAll(animatedItems);
                }
            };

            if (!AnimationUtils.isAnimationEnabled()) {
                finalizer.run();
                return;
            }

            Interpolator interpolator = Motion.EASE_IN_OUT_CUBIC_EMPHASIZED;
            List<KeyValue> values = new ArrayList<>();
            values.add(new KeyValue(animatedHeader.expansionProgressProperty(), target, interpolator));
            for (GameListItem item : animatedItems) {
                values.add(new KeyValue(item.groupVisibilityProperty(), target, interpolator));
            }

            groupAnimationFinalizer = finalizer;
            groupAnimation = new Timeline(new KeyFrame(Motion.LONG2, values.toArray(KeyValue[]::new)));
            groupAnimation.setOnFinished(event -> completeGroupAnimation());
            groupAnimation.play();
        }

        private boolean belongsToGroup(GameListItem item, String groupId) {
            String itemGroupId = settings().getInstanceGroup(repository.getGameDirectory().getId(), item.getId());
            return Objects.equals(itemGroupId, groupId.isEmpty() ? null : groupId);
        }

        private @Nullable GameListGroupItem findGroupHeader(String groupId) {
            for (GameListEntry entry : sourceList) {
                if (entry instanceof GameListGroupItem group
                        && Objects.equals(group.getId(), groupId.isEmpty() ? null : groupId)) {
                    return group;
                }
            }
            return null;
        }

        private List<GameListItem> getDisplayedGroupItems(GameListGroupItem header) {
            int headerIndex = sourceList.indexOf(header);
            List<GameListItem> items = new ArrayList<>();
            for (int i = headerIndex + 1; i < sourceList.size(); i++) {
                GameListEntry entry = sourceList.get(i);
                if (entry instanceof GameListGroupItem) {
                    break;
                }
                items.add((GameListItem) entry);
            }
            return items;
        }

        private void finishGroupAnimation() {
            if (groupAnimation != null) {
                groupAnimation.stop();
                completeGroupAnimation();
            }
        }

        private void completeGroupAnimation() {
            @Nullable Runnable finalizer = groupAnimationFinalizer;
            groupAnimation = null;
            groupAnimationFinalizer = null;
            if (finalizer != null) {
                finalizer.run();
            }
        }

        private void createGroup() {
            if (repository == null) {
                return;
            }
            Controllers.prompt(i18n("version.group.create.prompt"), (name, handler) -> {
                try {
                    settings().createInstanceGroup(repository.getGameDirectory().getId(), name);
                    handler.resolve();
                } catch (IllegalArgumentException exception) {
                    handler.reject(i18n("version.group.name.invalid"));
                }
            }, "", new RequiredValidator());
        }

        private void renameGroup(LauncherSettings.InstanceGroup group) {
            if (repository == null) {
                return;
            }
            Controllers.prompt(i18n("version.group.rename.prompt"), (name, handler) -> {
                try {
                    settings().renameInstanceGroup(repository.getGameDirectory().getId(), group.id(), name);
                    handler.resolve();
                } catch (IllegalArgumentException exception) {
                    handler.reject(i18n("version.group.name.invalid"));
                }
            }, group.name(), new RequiredValidator());
        }

        private void deleteGroup(LauncherSettings.InstanceGroup group) {
            if (repository == null) {
                return;
            }
            Controllers.confirm(i18n("version.group.delete.confirm", group.name()), i18n("version.group.delete"),
                    MessageDialogPane.MessageType.WARNING,
                    () -> settings().deleteInstanceGroup(repository.getGameDirectory().getId(), group.id()), null);
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
            GameDirectoryManager.getSelectedRepository().refreshVersionsAsync().start();
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
                VBox listContent = new VBox();
                listContent.setFillWidth(true);
                ListChangeListener<GameListEntry> listListener = change -> updateListContent(listContent, change);
                skinnable.getItems().addListener(listListener);
                skinnable.listenerHolder.add(listListener);
                updateListContent(listContent, skinnable.getItems());

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
                    pause.setOnFinished(e -> {
                        skinnable.searchText = searchField.getText();
                        skinnable.rebuildRows();
                    });
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

                    toolbarNormal.getChildren().setAll(
                            createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refreshList),
                            createToolbarButton2(i18n("version.group.create"), SVG.CREATE_NEW_FOLDER, skinnable::createGroup),
                            createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar)));

                    toolbarPane.setContent(toolbarNormal, ContainerAnimations.FADE);

                    FXUtils.setOverflowHidden(toolbarPane, 8);

                    root.getContent().add(toolbarPane);
                }

                {
                    SpinnerPane center = new SpinnerPane();
                    ComponentList.setVgrow(center, Priority.ALWAYS);
                    center.loadingProperty().bind(skinnable.loadingProperty());
                    center.failedReasonProperty().bind(skinnable.failedReasonProperty());

                    ScrollPane listPane = new ScrollPane(listContent);
                    listPane.setFitToWidth(true);
                    listPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    FXUtils.smoothScrolling(listPane);

                    ignoreEvent(listPane, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                    center.setContent(listPane);
                    root.getContent().add(center);
                }

                pane.getChildren().setAll(root);
                getChildren().setAll(pane);
            }

            private static void updateListContent(VBox listContent, ListChangeListener.Change<? extends GameListEntry> change) {
                while (change.next()) {
                    if (change.wasPermutated() || change.wasUpdated()) {
                        updateListContent(listContent, change.getList());
                        return;
                    }
                    if (change.wasRemoved()) {
                        listContent.getChildren().remove(change.getFrom(), change.getFrom() + change.getRemovedSize());
                    }
                    if (change.wasAdded()) {
                        List<Node> addedCells = change.getAddedSubList().stream()
                                .map(GameListSkin::createListCell)
                                .toList();
                        listContent.getChildren().addAll(change.getFrom(), addedCells);
                    }
                }
            }

            private static void updateListContent(VBox listContent, List<? extends GameListEntry> entries) {
                listContent.getChildren().setAll(entries.stream().map(GameListSkin::createListCell).toList());
            }

            private static Node createListCell(GameListEntry entry) {
                GameListCell cell = new GameListCell();
                cell.updateItem(entry, false);
                cell.setMaxWidth(Double.MAX_VALUE);
                return cell;
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
