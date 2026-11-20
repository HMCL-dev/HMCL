// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.directory.GameDirectoryListItem;
import org.jackhuang.hmcl.ui.directory.GameDirectoryPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("instance.manage")));
    /// Navigation drawer items for configured game directories.
    @SuppressWarnings("FieldCanBeLocal")
    private final ObservableList<GameDirectoryListItem> gameDirectoryListItems;

    /// Creates game-directory navigation, its bottom action, and the instance list.
    public GameListPage() {
        gameDirectoryListItems = MappedObservableList.create(GameDirectoryManager.getGameDirectories(), gameDirectory -> {
            GameDirectoryListItem item = new GameDirectoryListItem(gameDirectory);
            FXUtils.onSecondaryButtonClicked(item, () -> {
                PopupMenu menu = new PopupMenu();
                JFXPopup popup = new JFXPopup(menu);
                menu.getContent().addAll(
                        new IconedMenuItem(
                                SVG.EDIT,
                                i18n("button.edit"),
                                () -> Controllers.navigate(new GameDirectoryPage(gameDirectory)),
                                popup),
                        new IconedMenuItem(
                                SVG.FOLDER,
                                i18n("button.reveal_dir"),
                                () -> FXUtils.openFolder(gameDirectory.getPath().toPath()),
                                popup));
                popup.show(item, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, item.getWidth(), 0);
            });
            return item;
        });

        AdvancedListItem addGameDirectoryItem = new AdvancedListItem();
        addGameDirectoryItem.getStyleClass().add("navigation-drawer-item");
        addGameDirectoryItem.setTitle(i18n("game_directory.new"));
        addGameDirectoryItem.setLeftIcon(SVG.ADD_CIRCLE);
        addGameDirectoryItem.setOnAction(e -> Controllers.navigate(new GameDirectoryPage(null)));

        VBox directories = new VBox();
        Bindings.bindContent(directories.getChildren(), gameDirectoryListItems);
        getSidebar().add(directories).add(addGameDirectoryItem);
        setSidebarBottom(new AdvancedListBox()
                .addNavigationDrawerItem(i18n("settings.type.global.manage"), SVG.SETTINGS, this::modifyGlobalGameSettings));

        setContent(new GameList());

        FXUtils.applyDragListener(this, file -> ModpackHelper.isFileModpackByExtension(file) || "json".equalsIgnoreCase(FileUtils.getNameWithoutExtension(file)), files -> {
            Path file = files.get(0);

            if (ModpackHelper.isFileModpackByExtension(file)) {
                Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(GameDirectoryManager.getSelectedRepository(), file), i18n("install.modpack"));
            } else if ("json".equalsIgnoreCase(FileUtils.getExtension(file))) {
                Instances.installFromJson(GameDirectoryManager.getSelectedRepository(), file);
            }
        });
    }

    public void modifyGlobalGameSettings() {
        Instances.modifyGlobalSettings(GameDirectoryManager.getSelectedRepository());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    private static class GameList extends ListPaneBase<GameListItem> {
        /// Switches between toolbar modes.
        private final TransitionPane toolbarPane = new TransitionPane();
        /// Actions available while browsing.
        private final HBox toolbarNormal = new HBox();
        /// Toolbar containing the search input.
        private final HBox searchBar = new HBox();
        /// Text used to filter the list.
        private final JFXTextField searchField = new JFXTextField();
        /// Whether the search mechanism is currently active.
        private final BooleanProperty isSearching = new SimpleBooleanProperty(false);

        private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

        private final ObservableList<GameListItem> sourceList = FXCollections.observableArrayList();
        private final FilteredList<GameListItem> filteredList = new FilteredList<>(sourceList);

        public GameList() {
            getListView().setItems(filteredList);

            GameDirectoryManager.registerVersionsListener(this::loadVersions);

            setOnFailedAction(e -> Instances.addNewGame());

            initializeView();
        }

        @Override
        protected ItemCell<GameListItem> createItemCell(ListView<GameListItem> listView) {
            return new GameListCell(listView);
        }

        /// Configures the list and its page actions.
        private void initializeView() {
            getStyleClass().add("notice-pane");
            var listView = getListView();

            {

                searchBar.setAlignment(Pos.CENTER);
                searchBar.setPadding(new Insets(0, 5, 0, 5));
                searchField.setPromptText(i18n("search"));
                HBox.setHgrow(searchField, Priority.ALWAYS);
                PauseTransition pause = new PauseTransition(Duration.millis(100));
                pause.setOnFinished(e -> filteredList.setPredicate(createPredicate(searchField.getText())));
                searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                        pause.setRate(1);
                        pause.playFromStart();
                    }
                });

                JFXButton closeSearchBar = createToolbarButton(null, SVG.CLOSE, () -> {
                    changeToolbar(toolbarNormal);
                    searchField.clear();

                    pause.stop();
                    filteredList.setPredicate(null);
                    isSearching.set(false);
                });

                onEscPressed(searchField, closeSearchBar::fire);

                searchBar.getChildren().setAll(searchField, closeSearchBar);

                toolbarNormal.getChildren().setAll(createToolbarButton(i18n("button.refresh"), SVG.REFRESH, this::refreshList), createToolbarButton(i18n("install.new_game"), SVG.DOWNLOAD, Instances::addNewGame), createToolbarButton(i18n("install.modpack"), SVG.PACKAGE2, Instances::importModpack), createToolbarButton(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar)));

                toolbarPane.setContent(toolbarNormal, ContainerAnimations.FADE);

                FXUtils.setOverflowHidden(toolbarPane, 8);

                setToolbar(toolbarPane);
            }

            {

                StackPane placeholderContainer = new StackPane();
                placeholderContainer.getStyleClass().add("notice-pane");
                Label placeholderLabel = new Label();
                placeholderLabel.textProperty().bind(
                    Bindings.when(isSearching).then(i18n("search.no_results_found")).otherwise("")
                );
                placeholderContainer.getChildren().add(placeholderLabel);
                listView.setPlaceholder(placeholderContainer);
            }
        }

        @FXThread
        private void loadVersions(HMCLGameRepository repository) {
            listenerHolder.clear();
            setLoading(true);
            setFailedReason(null);

            List<GameListItem> instanceItems = repository.getDisplayInstances()
                    .map(GameListItem::new)
                    .toList();

            sourceList.setAll(instanceItems);

            if (instanceItems.isEmpty()) {
                setFailedReason(i18n("instance.empty.hint"));
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
                    return item -> pattern.matcher(item.getId()).find();
                } catch (PatternSyntaxException e) {
                    return item -> false;
                }
            } else {
                return item -> item.getId().toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT));
            }
        }

        public void refreshList() {
            GameDirectoryManager.getSelectedRepository().refreshAsync().start();
        }

        /// Shows the requested toolbar and focuses the search input when appropriate.
        private void changeToolbar(HBox newToolbar) {
            Node oldToolbar = toolbarPane.getCurrentNode();
            if (newToolbar != oldToolbar) {
                toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
                if (newToolbar == searchBar) {
                    runInFX(searchField::requestFocus);

                    isSearching.set(true);
                }
            }
        }
    }
}
