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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.InstanceSortType;
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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public class GameListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("instance.manage")));
    /// Navigation drawer items for configured game directories.
    @SuppressWarnings("FieldCanBeLocal")
    private final ObservableList<GameDirectoryListItem> gameDirectoryListItems;

    public GameListPage() {
        gameDirectoryListItems = MappedObservableList.create(GameDirectoryManager.getGameDirectories(), gameDirectory -> {
            GameDirectoryListItem item = new GameDirectoryListItem(gameDirectory);
            FXUtils.setLimitWidth(item, 200);
            FXUtils.onSecondaryButtonClicked(item, () -> {
                PopupMenu menu = new PopupMenu();
                JFXPopup popup = new JFXPopup(menu);
                menu.getContent().add(new IconedMenuItem(
                        SVG.EDIT,
                        i18n("button.edit"),
                        () -> Controllers.navigate(new GameDirectoryPage(gameDirectory)),
                        popup));
                popup.show(item, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, item.getWidth(), 0);
            });
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
            FXUtils.smoothScrolling(pane);

            AdvancedListBox bottomLeftCornerList = new AdvancedListBox()
                    .addNavigationDrawerItem(i18n("settings.type.global.manage"), SVG.SETTINGS, this::modifyGlobalGameSettings);
            FXUtils.setLimitHeight(bottomLeftCornerList, 40 + 12 * 2);
            setLeft(pane, bottomLeftCornerList);
        }

        setCenter(new GameList());

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

    /// Combines the current repository's instances with search, filtering and persisted sorting.
    private static class GameList extends ListPageBase<GameListItem> {
        private final ObservableList<GameListItem> sourceList = FXCollections.observableArrayList();
        private final FilteredList<GameListItem> filteredList = new FilteredList<>(sourceList);
        /// Filtered instances in the user's persisted display order.
        private final SortedList<GameListItem> sortedList = new SortedList<>(filteredList);
        /// Search query, including the existing optional `regex:` prefix.
        private final StringProperty search = new SimpleStringProperty("");
        /// Built-in instance type or loader filter.
        private final ObjectProperty<InstanceFilter> type = new SimpleObjectProperty<>(InstanceFilter.ALL);
        /// User-defined category selected for filtering.
        private final ObjectProperty<CategoryFilter> category = new SimpleObjectProperty<>(CategoryFilter.ALL);
        /// Whether to show all instances or only those with a given launch-menu visibility.
        private final ObjectProperty<LaunchMenuFilter> launchMenu = new SimpleObjectProperty<>(LaunchMenuFilter.ALL);
        /// Available custom categories, preceded by all and uncategorized choices.
        private final ObservableList<CategoryFilter> categories = FXCollections.observableArrayList();
        /// Unsubscribes metadata listeners before replacing the repository's instance list.
        private final List<Runnable> subscriptions = new ArrayList<>();

        /// Observes list controls and repository publications without creating local instance settings.
        public GameList() {
            setItems(sortedList);

            search.addListener(ignored -> updateFilter());
            type.addListener(ignored -> updateFilter());
            category.addListener(ignored -> updateFilter());
            launchMenu.addListener(ignored -> updateFilter());
            settings().instanceSortTypeProperty().addListener(ignored -> updateSort());
            updateSort();

            GameDirectoryManager.registerVersionsListener(this::loadVersions);

            setOnFailedAction(e -> Instances.addNewGame());
        }

        /// Replaces snapshot members and their metadata subscriptions after repository publications.
        @FXThread
        private void loadVersions(HMCLGameRepository repository) {
            subscriptions.forEach(Runnable::run);
            subscriptions.clear();
            setLoading(true);
            setFailedReason(null);

            @Unmodifiable List<GameListItem> instanceItems = repository.getDisplayInstances()
                    .map(GameListItem::new)
                    .toList();

            sourceList.setAll(instanceItems);

            /// Subscribes to list metadata so category choices and filters stay current; retains removers for the next reload.
            for (GameListItem item : instanceItems) {
                HMCLGameInstance instance = item.getGameInstance();
                ChangeListener<String> categoryListener = (observable, oldValue, newValue) -> {
                    updateCategories();
                    updateFilter();
                };
                ChangeListener<Boolean> visibilityListener = (observable, oldValue, newValue) -> updateFilter();
                instance.categoryProperty().addListener(categoryListener);
                instance.shownInLaunchMenuProperty().addListener(visibilityListener);
                subscriptions.add(() -> {
                    instance.categoryProperty().removeListener(categoryListener);
                    instance.shownInLaunchMenuProperty().removeListener(visibilityListener);
                });
            }
            updateCategories();
            updateFilter();

            if (instanceItems.isEmpty()) {
                setFailedReason(i18n("instance.empty.hint"));
            }

            setLoading(false);
        }

        /// Rebuilds category choices and resets a category that no longer exists in this directory.
        private void updateCategories() {
            List<CategoryFilter> choices = new ArrayList<>();
            choices.add(CategoryFilter.ALL);
            choices.add(new CategoryFilter(""));
            // Build category choices from every instance, independent of the active filters.
            sourceList.stream()
                    .map(item -> item.getGameInstance().getCategory())
                    .filter(name -> !name.isEmpty())
                    .distinct()
                    .sorted(InstanceSortType.categoryNameComparator())
                    .map(CategoryFilter::new)
                    .forEach(choices::add);
            // Avoid notifying the UI when the available category choices have not changed.
            if (!categories.equals(choices)) {
                categories.setAll(choices);
            }
            // The selected category may disappear after its last instance is reassigned.
            if (!choices.contains(category.get())) {
                category.set(CategoryFilter.ALL);
            }
        }

        /// Applies all filters together without replacing the sort order or selected instance.
        /// The sorted view reapplies its current comparator when the filtered source changes.
        private void updateFilter() {
            Predicate<GameListItem> matchesSearch = createPredicate(search.get());
            filteredList.setPredicate(item -> {
                HMCLGameInstance instance = item.getGameInstance();
                @Nullable String categoryName = category.get().name();
                return matchesSearch.test(item)
                        && type.get().matches(instance)
                        && (categoryName == null || categoryName.equals(instance.getCategory()))
                        && launchMenu.get().matches(instance);
            });
        }

        /// Applies the persisted display order when its sort mode changes.
        private void updateSort() {
            InstanceSortType sort = settings().instanceSortTypeProperty().get();
            sortedList.setComparator(sort == InstanceSortType.DEFAULT ? null
                    : Comparator.comparing(GameListItem::getGameInstance, sort.comparator()));
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
                String query = searchText.toLowerCase(Locale.ROOT);
                return item -> item.getId().toLowerCase(Locale.ROOT).contains(query);
            }
        }

        public void refreshList() {
            GameDirectoryManager.getSelectedRepository().refreshAsync().start();
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new GameListSkin(this);
        }

        private static class GameListSkin extends SkinBase<GameList> {
            private final TransitionPane toolbarPane;
            private final HBox searchBar;
            private final HBox toolbarNormal;

            /// Whether the search mechanism is currently active.
            private final BooleanProperty isSearching = new SimpleBooleanProperty(false);

            /// Whether the filter and sort controls are expanded; initially folded.
            private final BooleanProperty filtersExpanded = new SimpleBooleanProperty(false);

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
                    pause.setOnFinished(e -> skinnable.search.set(searchField.getText()));
                    searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                        if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                            pause.setRate(1);
                            pause.playFromStart();
                        }
                    });

                    JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE, () -> {
                        changeToolbar(toolbarNormal);
                        searchField.clear();

                        pause.stop();
                        skinnable.search.set("");
                        isSearching.set(false);
                    });

                    onEscPressed(searchField, closeSearchBar::fire);

                    searchBar.getChildren().setAll(searchField, closeSearchBar);

                    toolbarNormal.getChildren().setAll(createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refreshList), createToolbarButton2(i18n("install.new_game"), SVG.DOWNLOAD, Instances::addNewGame), createToolbarButton2(i18n("install.modpack"), SVG.PACKAGE2, Instances::importModpack), createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar)));

                    toolbarPane.setContent(toolbarNormal, ContainerAnimations.FADE);

                    FXUtils.setOverflowHidden(toolbarPane, 8);

                    JFXButton filterButton = createToolbarButton2(null, SVG.ARROW_DROP_DOWN,
                            () -> filtersExpanded.set(!filtersExpanded.get()));
                    filterButton.graphicProperty().bind(Bindings.createObjectBinding(
                            () -> (filtersExpanded.get() ? SVG.ARROW_DROP_UP : SVG.ARROW_DROP_DOWN).createIcon(20),
                            filtersExpanded));
                    filterButton.setMinWidth(Region.USE_PREF_SIZE);
                    Tooltip filterTooltip = new Tooltip();
                    filterTooltip.textProperty().bind(Bindings.when(filtersExpanded)
                            .then(i18n("instance.filter.hide")).otherwise(i18n("instance.filter.show")));
                    filterButton.accessibleTextProperty().bind(filterTooltip.textProperty());
                    FXUtils.installFastTooltip(filterButton, filterTooltip);

                    HBox toolbar = new HBox(toolbarPane, filterButton);
                    toolbar.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(toolbarPane, Priority.ALWAYS);
                    toolbarPane.setMinWidth(0);
                    root.getContent().add(toolbar);
                }

                {
                    GridPane filters = new GridPane();
                    filters.visibleProperty().bind(filtersExpanded);
                    filters.managedProperty().bind(filtersExpanded);
                    filters.setHgap(16);
                    filters.setVgap(10);
                    filters.setPadding(new Insets(8, 12, 16, 12));
                    ColumnConstraints labelColumn = new ColumnConstraints();
                    labelColumn.setMinWidth(Region.USE_PREF_SIZE);
                    filters.getColumnConstraints().setAll(labelColumn, FXUtils.getColumnHgrowing(),
                            labelColumn, FXUtils.getColumnHgrowing());

                    JFXComboBox<InstanceFilter> typeBox = new JFXComboBox<>();
                    typeBox.getItems().setAll(InstanceFilter.values());
                    typeBox.setConverter(stringConverter(value -> i18n("instance.filter.type." + value.name().toLowerCase(Locale.ROOT))));
                    typeBox.valueProperty().bindBidirectional(skinnable.type);

                    JFXComboBox<CategoryFilter> categoryBox = new JFXComboBox<>();
                    categoryBox.setConverter(stringConverter(value -> value.name() == null ? i18n("instance.category.all")
                            : value.name().isEmpty() ? i18n("instance.category.uncategorized") : value.name()));
                    categoryBox.setItems(skinnable.categories);
                    categoryBox.valueProperty().bindBidirectional(skinnable.category);
                    skinnable.categories.addListener((InvalidationListener) ignored -> categoryBox.setValue(skinnable.category.get()));

                    JFXComboBox<LaunchMenuFilter> launchMenuBox = new JFXComboBox<>();
                    launchMenuBox.getItems().setAll(LaunchMenuFilter.values());
                    launchMenuBox.setConverter(stringConverter(value -> i18n("instance.filter.launch_menu." + value.name().toLowerCase(Locale.ROOT))));
                    launchMenuBox.valueProperty().bindBidirectional(skinnable.launchMenu);

                    JFXComboBox<InstanceSortType> sortBox = new JFXComboBox<>();
                    sortBox.getItems().setAll(InstanceSortType.values());
                    sortBox.setConverter(stringConverter(value -> i18n("instance.sort." + value.name().toLowerCase(Locale.ROOT))));
                    sortBox.valueProperty().bindBidirectional(settings().instanceSortTypeProperty());

                    @Unmodifiable List<JFXComboBox<?>> filterBoxes = List.of(typeBox, categoryBox, launchMenuBox, sortBox);
                    for (JFXComboBox<?> box : filterBoxes) {
                        box.setMinWidth(0);
                        box.setMaxWidth(Double.MAX_VALUE);
                    }
                    filters.addRow(0, new Label(i18n("instance.filter.type")), typeBox,
                            new Label(i18n("instance.category")), categoryBox);
                    filters.addRow(1, new Label(i18n("instance.launch_menu")), launchMenuBox,
                            new Label(i18n("search.sort")), sortBox);
                    root.getContent().add(filters);
                }

                {
                    SpinnerPane center = new SpinnerPane();
                    ComponentList.setVgrow(center, Priority.ALWAYS);
                    center.loadingProperty().bind(skinnable.loadingProperty());
                    center.failedReasonProperty().bind(skinnable.failedReasonProperty());

                    listView.setCellFactory(x -> new GameListCell());
                    listView.setItems(skinnable.getItems());

                    ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                    StackPane placeholderContainer = new StackPane();
                    placeholderContainer.getStyleClass().add("notice-pane");
                    Label placeholderLabel = new Label();
                    placeholderLabel.setText(i18n("search.no_results_found"));
                    placeholderContainer.getChildren().add(placeholderLabel);
                    listView.setPlaceholder(placeholderContainer);

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

                        isSearching.set(true);
                    }
                }
            }
        }
    }

    /// A category choice; `null` denotes all categories and an empty name denotes uncategorized.
    private record CategoryFilter(@Nullable String name) {
        /// Shared all-categories choice, distinct from every user-entered category name.
        private static final CategoryFilter ALL = new CategoryFilter(null);
    }

    /// Launch-menu visibility choices for finding instances in the full management list.
    private enum LaunchMenuFilter {
        /// Includes every instance regardless of visibility.
        ALL,
        /// Includes instances shown in instance switchers.
        SHOWN,
        /// Includes instances excluded from instance switchers.
        HIDDEN;

        /// Tests the stored visibility, treating instances without settings as visible.
        private boolean matches(HMCLGameInstance instance) {
            return this == ALL || instance.isShownInLaunchMenu() == (this == SHOWN);
        }
    }

    /// Built-in instance and loader choices independent of custom categories.
    private enum InstanceFilter {
        /// Includes every instance.
        ALL(null),
        /// Includes instances without extra game components.
        VANILLA(null),
        /// Includes instances with extra game components.
        MODDED(null),
        /// Includes HMCL-managed modpacks.
        MODPACK(null),
        /// Includes instances with Forge installed.
        FORGE(GameComponentType.FORGE),
        /// Includes instances with NeoForge installed.
        NEOFORGE(GameComponentType.NEO_FORGE),
        /// Includes instances with Fabric installed.
        FABRIC(GameComponentType.FABRIC),
        /// Includes instances with Quilt installed.
        QUILT(GameComponentType.QUILT),
        /// Includes instances with OptiFine installed.
        OPTIFINE(GameComponentType.OPTIFINE),
        /// Includes instances with a mod loader that has no dedicated filter, such as LiteLoader.
        OTHER_MOD_LOADER(null);

        /// Loader types with dedicated filter options.
        private static final @Unmodifiable Set<GameComponentType> MOD_LOADERS_WITH_DEDICATED_FILTERS = Set.of(
                GameComponentType.FORGE,
                GameComponentType.NEO_FORGE,
                GameComponentType.FABRIC,
                GameComponentType.QUILT);

        /// Required game component, or `null` for a built-in aggregate choice.
        private final @Nullable GameComponentType component;

        /// Creates a built-in aggregate or component-specific filter.
        InstanceFilter(@Nullable GameComponentType component) {
            this.component = component;
        }

        /// Matches against the instance's cached component analysis and modpack metadata.
        private boolean matches(HMCLGameInstance instance) {
            if (this == ALL) return true;
            if (this == MODPACK) return instance.isModpack();
            if (component != null) return instance.getAnalyzer().has(component);
            if (this == OTHER_MOD_LOADER) {
                return GameComponentType.MOD_LOADERS.stream()
                        .filter(type -> !MOD_LOADERS_WITH_DEDICATED_FILTERS.contains(type))
                        .anyMatch(instance.getAnalyzer()::has);
            }
            for (GameComponentAnalyzer.Mark mark : instance.getAnalyzer()) {
                if (mark.componentType() != GameComponentType.GAME) return this == MODDED;
            }
            return this == VANILLA;
        }
    }
}
