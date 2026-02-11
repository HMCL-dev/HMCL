/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.*;
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.NoneMultipleSelectionModel;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;

public class DownloadListPage extends Control implements DecoratorPage, VersionPage.VersionLoadable {
    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final boolean versionSelection;
    private final ObjectProperty<Profile.ProfileVersion> version = new SimpleObjectProperty<>();
    private final IntegerProperty pageOffset = new SimpleIntegerProperty(0);
    private final IntegerProperty pageCount = new SimpleIntegerProperty(-1);
    private final ListProperty<RemoteMod> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final ObservableList<String> versions = FXCollections.observableArrayList();
    private final StringProperty selectedVersion = new SimpleStringProperty();
    private final DownloadPage.DownloadCallback callback;
    private boolean searchInitialized = false;
    protected final BooleanProperty supportChinese = new SimpleBooleanProperty();
    private final ObservableList<Node> actions = FXCollections.observableArrayList();
    protected final ListProperty<String> downloadSources = new SimpleListProperty<>(this, "downloadSources", FXCollections.observableArrayList());
    protected final StringProperty downloadSource = new SimpleStringProperty();
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private int searchID = 0;
    protected RemoteModRepository repository;
    private final DownloadProvider downloadProvider;

    private Runnable retrySearch;

    public DownloadListPage(RemoteModRepository repository) {
        this(repository, null, false);
    }

    public DownloadListPage(RemoteModRepository repository, DownloadPage.DownloadCallback callback, boolean versionSelection) {
        this.repository = repository;
        this.callback = callback;
        this.versionSelection = versionSelection;
        this.downloadProvider = DownloadProviders.getDownloadProvider();
    }

    public ObservableList<Node> getActions() {
        return actions;
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        this.version.set(new Profile.ProfileVersion(profile, version));

        setLoading(false);
        setFailed(false);

        if (!searchInitialized) {
            searchInitialized = true;
            search("", null, 0, "", RemoteModRepository.SortType.POPULARITY);
        }

        if (versionSelection) {
            versions.setAll(profile.getRepository().getDisplayVersions()
                    .map(Version::getId)
                    .collect(Collectors.toList()));
            selectedVersion.set(profile.getSelectedVersion());
        }
    }

    public boolean isFailed() {
        return failed.get();
    }

    public BooleanProperty failedProperty() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed.set(failed);
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public void selectVersion(String versionID) {
        FXUtils.runInFX(() -> selectedVersion.set(versionID));
    }

    private void search(String userGameVersion, RemoteModRepository.Category category, int pageOffset, String searchFilter, RemoteModRepository.SortType sort) {
        retrySearch = null;
        setLoading(true);
        setFailed(false);

        int currentSearchID = searchID = searchID + 1;
        Task.supplyAsync(() -> {
            Profile.ProfileVersion version = this.version.get();
            if (StringUtils.isBlank(version.getVersion())) {
                return userGameVersion;
            } else {
                return StringUtils.isNotBlank(version.getVersion())
                        ? version.getProfile().getRepository().getGameVersion(version.getVersion()).orElse("")
                        : "";
            }
        }).thenApplyAsync(
                gameVersion -> repository.search(downloadProvider, gameVersion, category, pageOffset, 50, searchFilter, sort, RemoteModRepository.SortOrder.DESC)
        ).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (searchID != currentSearchID) {
                return;
            }

            setLoading(false);
            if (exception == null) {
                items.setAll(result.getResults().collect(Collectors.toList()));
                pageCount.set(result.getTotalPages());
                failed.set(false);
            } else {
                failed.set(true);
                pageCount.set(-1);
                retrySearch = () -> search(userGameVersion, category, pageOffset, searchFilter, sort);
            }
        }).executor(true);
    }

    protected String getLocalizedCategory(String category) {
        return repository instanceof ModrinthRemoteModRepository
                ? i18n("modrinth.category." + category)
                : i18n("curse.category." + category);
    }

    protected boolean shouldDisplayCategory(String category) {
        return !"minecraft".equals(category);
    }

    private String getLocalizedCategoryIndent(ModDownloadListPageSkin.CategoryIndented category) {
        return StringUtils.repeats(' ', category.indent * 4) +
                (category.getCategory() == null
                        ? i18n("curse.category.0")
                        : getLocalizedCategory(category.getCategory().getId()));
    }

    protected String getLocalizedOfficialPage() {
        if (repository instanceof ModrinthRemoteModRepository) {
            return i18n("mods.modrinth");
        } else {
            return i18n("mods.curseforge");
        }
    }

    protected Profile.ProfileVersion getProfileVersion() {
        if (versionSelection) {
            return new Profile.ProfileVersion(version.get().getProfile(), selectedVersion.get());
        } else {
            return version.get();
        }
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadListPageSkin(this);
    }

    private static class ModDownloadListPageSkin extends SkinBase<DownloadListPage> {
        private final JFXListView<RemoteMod> listView = new JFXListView<>();
        private final RemoteImageLoader iconLoader = new RemoteImageLoader() {
            @Override
            protected @NotNull Task<Image> createLoadTask(@NotNull URI uri) {
                return FXUtils.getRemoteImageTask(uri, 80, 80, true, true);
            }
        };

        protected ModDownloadListPageSkin(DownloadListPage control) {
            super(control);

            listView.getStyleClass().add("no-horizontal-scrollbar");

            BorderPane pane = new BorderPane();

            GridPane searchPane = new GridPane();
            pane.setTop(searchPane);
            searchPane.getStyleClass().addAll("card");
            BorderPane.setMargin(searchPane, new Insets(10, 10, 0, 10));

            ColumnConstraints nameColumn = new ColumnConstraints();
            nameColumn.setMinWidth(USE_PREF_SIZE);
            ColumnConstraints column1 = new ColumnConstraints();
            column1.setHgrow(Priority.ALWAYS);
            ColumnConstraints column2 = new ColumnConstraints();
            column2.setHgrow(Priority.ALWAYS);
            searchPane.getColumnConstraints().setAll(nameColumn, column1, nameColumn, column2);

            searchPane.setHgap(16);
            searchPane.setVgap(10);

            {
                int rowIndex = 0;

                if (control.versionSelection || !control.downloadSources.isEmpty()) {
                    searchPane.addRow(rowIndex);
                    int columns = 0;
                    Node lastNode = null;
                    if (control.versionSelection) {
                        JFXComboBox<String> versionsComboBox = new JFXComboBox<>();
                        versionsComboBox.setMaxWidth(Double.MAX_VALUE);
                        Bindings.bindContent(versionsComboBox.getItems(), control.versions);
                        selectedItemPropertyFor(versionsComboBox).bindBidirectional(control.selectedVersion);

                        searchPane.add(new Label(i18n("version")), columns++, rowIndex);
                        searchPane.add(lastNode = versionsComboBox, columns++, rowIndex);
                    }

                    if (control.downloadSources.getSize() > 1) {
                        JFXComboBox<String> downloadSourceComboBox = new JFXComboBox<>();
                        downloadSourceComboBox.setMaxWidth(Double.MAX_VALUE);
                        downloadSourceComboBox.getItems().setAll(control.downloadSources.get());
                        downloadSourceComboBox.setConverter(stringConverter(I18n::i18n));
                        selectedItemPropertyFor(downloadSourceComboBox).bindBidirectional(control.downloadSource);

                        searchPane.add(new Label(i18n("settings.launcher.download_source")), columns++, rowIndex);
                        searchPane.add(lastNode = downloadSourceComboBox, columns++, rowIndex);
                    }

                    if (columns == 2) {
                        GridPane.setColumnSpan(lastNode, 3);
                    }

                    rowIndex++;
                }

                JFXTextField nameField = new JFXTextField();
                nameField.setPromptText(getSkinnable().supportChinese.get() ? i18n("search.hint.chinese") : i18n("search.hint.english"));
                if (getSkinnable().supportChinese.get()) {
                    FXUtils.installFastTooltip(nameField, i18n("search.hint.chinese"));
                } else {
                    FXUtils.installFastTooltip(nameField, i18n("search.hint.english"));
                }

                JFXComboBox<String> gameVersionField = new JFXComboBox<>();
                gameVersionField.setMaxWidth(Double.MAX_VALUE);
                gameVersionField.setEditable(true);
                gameVersionField.getItems().setAll(GameVersionNumber.getDefaultGameVersions());
                Label lblGameVersion = new Label(i18n("world.game_version"));
                searchPane.addRow(rowIndex++, new Label(i18n("mods.name")), nameField, lblGameVersion, gameVersionField);

                ObjectBinding<Boolean> hasVersion = BindingMapping.of(getSkinnable().version)
                        .map(version -> version.getVersion() == null);
                lblGameVersion.managedProperty().bind(hasVersion);
                lblGameVersion.visibleProperty().bind(hasVersion);
                gameVersionField.managedProperty().bind(hasVersion);
                gameVersionField.visibleProperty().bind(hasVersion);
                FXUtils.installFastTooltip(gameVersionField, i18n("search.enter"));

                FXUtils.onChangeAndOperate(getSkinnable().version, version -> {
                    if (StringUtils.isNotBlank(version.getVersion())) {
                        GridPane.setColumnSpan(nameField, 3);
                    } else {
                        GridPane.setColumnSpan(nameField, 1);
                    }
                });

                StackPane categoryStackPane = new StackPane();
                JFXComboBox<CategoryIndented> categoryComboBox = new JFXComboBox<>();
                categoryComboBox.getItems().setAll(CategoryIndented.ALL);
                categoryStackPane.getChildren().setAll(categoryComboBox);
                categoryComboBox.prefWidthProperty().bind(categoryStackPane.widthProperty());
                categoryComboBox.getStyleClass().add("fit-width");
                categoryComboBox.setPromptText(i18n("mods.category"));
                categoryComboBox.getSelectionModel().select(0);
                categoryComboBox.setConverter(stringConverter(getSkinnable()::getLocalizedCategoryIndent));
                FXUtils.onChangeAndOperate(getSkinnable().downloadSource, downloadSource -> {
                    categoryComboBox.getItems().setAll(CategoryIndented.ALL);
                    categoryComboBox.getSelectionModel().select(0);

                    Task.supplyAsync(() -> getSkinnable().repository.getCategories())
                            .thenAcceptAsync(Schedulers.javafx(), categories -> {
                                if (!Objects.equals(getSkinnable().downloadSource.get(), downloadSource)) {
                                    return;
                                }

                                List<CategoryIndented> result = new ArrayList<>();
                                result.add(CategoryIndented.ALL);
                                for (RemoteModRepository.Category category : Lang.toIterable(categories)) {
                                    resolveCategory(category, 0, result);
                                }
                                categoryComboBox.getItems().setAll(result);
                                categoryComboBox.getSelectionModel().select(0);
                            }).start();
                });

                StackPane sortStackPane = new StackPane();
                JFXComboBox<RemoteModRepository.SortType> sortComboBox = new JFXComboBox<>();
                sortStackPane.getChildren().setAll(sortComboBox);
                sortComboBox.prefWidthProperty().bind(sortStackPane.widthProperty());
                sortComboBox.getStyleClass().add("fit-width");
                sortComboBox.setConverter(stringConverter(sortType -> i18n("curse.sort." + sortType.name().toLowerCase(Locale.ROOT))));
                sortComboBox.getItems().setAll(RemoteModRepository.SortType.values());
                sortComboBox.getSelectionModel().select(0);
                searchPane.addRow(rowIndex++, new Label(i18n("mods.category")), categoryStackPane, new Label(i18n("search.sort")), sortStackPane);

                IntegerProperty filterID = new SimpleIntegerProperty(this, "Filter ID", 0);
                IntegerProperty currentFilterID = new SimpleIntegerProperty(this, "Current Filter ID", -1);
                EventHandler<ActionEvent> searchAction = e -> {
                    iconLoader.clearInvalidCache();
                    if (currentFilterID.get() != -1 && currentFilterID.get() != filterID.get()) {
                        control.pageOffset.set(0);
                    }
                    currentFilterID.set(filterID.get());

                    int pageOffset = control.pageOffset.get();
                    getSkinnable().search(gameVersionField.getSelectionModel().getSelectedItem(),
                            Optional.ofNullable(categoryComboBox.getSelectionModel().getSelectedItem())
                                    .map(CategoryIndented::getCategory)
                                    .orElse(null),
                            pageOffset == -1 ? 0 : pageOffset,
                            nameField.getText(),
                            sortComboBox.getSelectionModel().getSelectedItem());
                };

                control.listenerHolder.add(FXUtils.observeWeak(
                        () -> filterID.set(filterID.get() + 1),

                        control.downloadSource,
                        gameVersionField.getSelectionModel().selectedItemProperty(),
                        categoryComboBox.getSelectionModel().selectedItemProperty(),
                        nameField.textProperty(),
                        sortComboBox.getSelectionModel().selectedItemProperty()
                ));

                HBox actionsBox = new HBox(8);
                GridPane.setColumnSpan(actionsBox, 4);
                actionsBox.setAlignment(Pos.CENTER);
                {
                    AggregatedObservableList<Node> actions = new AggregatedObservableList<>();

                    Holder<Runnable> changeButton = new Holder<>();

                    JFXButton firstPageButton = FXUtils.newBorderButton(i18n("search.first_page"));
                    firstPageButton.setOnAction(event -> {
                        control.pageOffset.set(0);
                        searchAction.handle(event);
                        changeButton.value.run();
                    });

                    JFXButton previousPageButton = FXUtils.newBorderButton(i18n("search.previous_page"));
                    previousPageButton.setOnAction(event -> {
                        int pageOffset = control.pageOffset.get();
                        if (pageOffset > 0) {
                            control.pageOffset.set(pageOffset - 1);
                            searchAction.handle(event);
                            changeButton.value.run();
                        }
                    });

                    Label pageDescription = new Label();
                    pageDescription.textProperty().bind(Bindings.createStringBinding(() -> {
                        int pageCount = control.pageCount.get();
                        return i18n("search.page_n", control.pageOffset.get() + 1, pageCount == -1 ? "-" : String.valueOf(pageCount));
                    }, control.pageOffset, control.pageCount));

                    JFXButton nextPageButton = FXUtils.newBorderButton(i18n("search.next_page"));
                    nextPageButton.setOnAction(event -> {
                        int nv = control.pageOffset.get() + 1;
                        if (nv < control.pageCount.get()) {
                            control.pageOffset.set(nv);
                            searchAction.handle(event);
                            changeButton.value.run();
                        }
                    });

                    JFXButton lastPageButton = FXUtils.newBorderButton(i18n("search.last_page"));
                    lastPageButton.setOnAction(event -> {
                        control.pageOffset.set(control.pageCount.get() - 1);
                        searchAction.handle(event);
                        changeButton.value.run();
                    });

                    firstPageButton.setDisable(true);
                    previousPageButton.setDisable(true);
                    lastPageButton.setDisable(true);
                    nextPageButton.setDisable(true);

                    changeButton.value = () -> {
                        int pageOffset = control.pageOffset.get();
                        int pageCount = control.pageCount.get();

                        boolean disableAll = pageCount >= -1 && pageCount <= 1;

                        boolean disablePrevious = disableAll || pageOffset == 0;
                        firstPageButton.setDisable(disablePrevious);
                        previousPageButton.setDisable(disablePrevious);

                        boolean disableNext = disableAll || pageOffset == pageCount - 1;
                        nextPageButton.setDisable(disableNext);
                        lastPageButton.setDisable(disableNext);

                        listView.scrollTo(0);
                    };

                    FXUtils.onChange(control.pageCount, pageCountN -> {
                        int pageCount = pageCountN.intValue();

                        if (pageCount != -1) {
                            if (control.pageOffset.get() + 1 >= pageCount) {
                                control.pageOffset.set(pageCount - 1);
                            }
                        }

                        changeButton.value.run();
                    });

                    FXUtils.onChange(control.pageOffset, pageOffsetN -> {
                        changeButton.value.run();
                    });

                    Pane placeholder = new Pane();
                    HBox.setHgrow(placeholder, Priority.SOMETIMES);

                    JFXButton searchButton = FXUtils.newRaisedButton(i18n("search"));
                    searchButton.setOnAction(searchAction);

                    actions.appendList(FXCollections.observableArrayList(firstPageButton, previousPageButton, pageDescription, nextPageButton, lastPageButton, placeholder, searchButton));
                    actions.appendList(control.actions);
                    Bindings.bindContent(actionsBox.getChildren(), actions.getAggregatedList());
                }

                searchPane.addRow(rowIndex++, actionsBox);

                FXUtils.onChange(control.downloadSource, v -> searchAction.handle(null));
                nameField.setOnAction(searchAction);
                gameVersionField.setOnAction(searchAction);
                categoryComboBox.setOnAction(searchAction);
                sortComboBox.setOnAction(searchAction);
            }

            SpinnerPane spinnerPane = new SpinnerPane();
            pane.setCenter(spinnerPane);
            {
                spinnerPane.loadingProperty().bind(getSkinnable().loadingProperty());
                spinnerPane.failedReasonProperty().bind(Bindings.createStringBinding(() -> {
                    if (getSkinnable().isFailed()) {
                        return i18n("download.failed.refresh");
                    } else {
                        return null;
                    }
                }, getSkinnable().failedProperty()));
                spinnerPane.setOnFailedAction(e -> {
                    if (getSkinnable().retrySearch != null) {
                        getSkinnable().retrySearch.run();
                    }
                });

                spinnerPane.setContent(listView);
                Bindings.bindContent(listView.getItems(), getSkinnable().items);
                listView.setSelectionModel(new NoneMultipleSelectionModel<>());
                // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                listView.setCellFactory(x -> new ListCell<>() {
                    private static final Insets PADDING = new Insets(9, 9, 0, 9);

                    private final RipplerContainer graphic;

                    private final TwoLineListItem content = new TwoLineListItem();
                    private final ImageView imageView = new ImageView();

                    {
                        setPadding(PADDING);

                        HBox container = new HBox(8);
                        container.getStyleClass().add("card");
                        container.setCursor(Cursor.HAND);
                        container.setAlignment(Pos.CENTER_LEFT);
                        JFXDepthManager.setDepth(container, 1);

                        imageView.setFitWidth(40);
                        imageView.setFitHeight(40);

                        container.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content);
                        HBox.setHgrow(content, Priority.ALWAYS);

                        this.graphic = new RipplerContainer(container);
                        graphic.setPosition(JFXRippler.RipplerPos.FRONT);
                        FXUtils.onClicked(graphic, () -> {
                            RemoteMod item = getItem();
                            if (item != null)
                                Controllers.navigate(new DownloadPage(getSkinnable(), item, getSkinnable().getProfileVersion(), getSkinnable().callback));
                        });

                        setPrefWidth(0);

                        FXUtils.limitCellWidth(listView, this);

                    }

                    @Override
                    protected void updateItem(RemoteMod item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            ModTranslations.Mod mod = ModTranslations.getTranslationsByRepositoryType(getSkinnable().repository.getType()).getModByCurseForgeId(item.getSlug());
                            content.setTitle(mod != null && I18n.isUseChinese() ? mod.getDisplayName() : item.getTitle());
                            content.setSubtitle(item.getDescription());
                            content.getTags().clear();
                            for (String category : item.getCategories()) {
                                if (getSkinnable().shouldDisplayCategory(category))
                                    content.addTag(getSkinnable().getLocalizedCategory(category));
                            }
                            iconLoader.load(imageView.imageProperty(), item.getIconUrl());
                            setGraphic(graphic);
                        }
                    }
                });
            }

            getChildren().setAll(pane);
        }

        private static class CategoryIndented {
            private static final CategoryIndented ALL = new CategoryIndented(0, null);

            private final int indent;
            private final RemoteModRepository.Category category;

            public CategoryIndented(int indent, RemoteModRepository.Category category) {
                this.indent = indent;
                this.category = category;
            }

            public int getIndent() {
                return indent;
            }

            public RemoteModRepository.Category getCategory() {
                return category;
            }
        }

        private static void resolveCategory(RemoteModRepository.Category category, int indent, List<CategoryIndented> result) {
            result.add(new CategoryIndented(indent, category));
            for (RemoteModRepository.Category subcategory : category.getSubcategories()) {
                resolveCategory(subcategory, indent + 1, result);
            }
        }
    }
}
