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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.FloatListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.AggregatedObservableList;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;

public class DownloadListPage extends Control implements DecoratorPage, VersionPage.VersionLoadable {
    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final boolean versionSelection;
    private final ObjectProperty<Profile.ProfileVersion> version = new SimpleObjectProperty<>();
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
    private TaskExecutor executor;
    protected RemoteModRepository repository;

    private Runnable retrySearch;

    public DownloadListPage(RemoteModRepository repository) {
        this(repository, null);
    }

    public DownloadListPage(RemoteModRepository repository, DownloadPage.DownloadCallback callback) {
        this(repository, callback, false);
    }

    public DownloadListPage(RemoteModRepository repository, DownloadPage.DownloadCallback callback, boolean versionSelection) {
        this.repository = repository;
        this.callback = callback;
        this.versionSelection = versionSelection;
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
            search("", null, 0, "", RemoteModRepository.SortType.DATE_CREATED);
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

    public void search(String userGameVersion, RemoteModRepository.Category category, int pageOffset, String searchFilter, RemoteModRepository.SortType sort) {
        retrySearch = null;
        setLoading(true);
        setFailed(false);
        File versionJar = StringUtils.isNotBlank(version.get().getVersion())
                ? version.get().getProfile().getRepository().getVersionJar(version.get().getVersion())
                : null;
        if (executor != null && !executor.isCancelled()) {
            executor.cancel();
        }

        executor = Task.supplyAsync(() -> {
            String gameVersion;
            if (StringUtils.isBlank(version.get().getVersion())) {
                gameVersion = userGameVersion;
            } else {
                gameVersion = GameVersion.minecraftVersion(versionJar).orElse("");
            }
            return gameVersion;
        }).thenApplyAsync(gameVersion -> {
            return repository.search(gameVersion, category, pageOffset, 50, searchFilter, sort, RemoteModRepository.SortOrder.DESC);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            setLoading(false);
            if (exception == null) {
                items.setAll(result.collect(Collectors.toList()));
                failed.set(false);
            } else {
                failed.set(true);
                retrySearch = () -> search(userGameVersion, category, pageOffset, searchFilter, sort);
            }
        }).executor(true);
    }

    protected String getLocalizedCategory(String category) {
        return i18n("curse.category." + category);
    }

    protected String getLocalizedCategoryIndent(ModDownloadListPageSkin.CategoryIndented category) {
        return StringUtils.repeats(' ', category.indent * 4) + getLocalizedCategory(category.getCategory() == null ? "0" : category.getCategory().getId());
    }

    protected String getLocalizedOfficialPage() {
        return i18n("mods.curseforge");
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
        private final AggregatedObservableList<Node> actions = new AggregatedObservableList<>();

        protected ModDownloadListPageSkin(DownloadListPage control) {
            super(control);

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

                if (control.versionSelection) {
                    JFXComboBox<String> versionsComboBox = new JFXComboBox<>();
                    versionsComboBox.setMaxWidth(Double.MAX_VALUE);
                    Bindings.bindContent(versionsComboBox.getItems(), control.versions);
                    selectedItemPropertyFor(versionsComboBox).bindBidirectional(control.selectedVersion);

                    searchPane.addRow(rowIndex, new Label(i18n("version")), versionsComboBox);

                    if (control.downloadSources.getSize() > 1) {
                        JFXComboBox<String> downloadSourceComboBox = new JFXComboBox<>();
                        downloadSourceComboBox.setMaxWidth(Double.MAX_VALUE);
                        downloadSourceComboBox.getItems().setAll(control.downloadSources.get());
                        downloadSourceComboBox.setConverter(stringConverter(I18n::i18n));
                        selectedItemPropertyFor(downloadSourceComboBox).bindBidirectional(control.downloadSource);

                        searchPane.add(new Label(i18n("settings.launcher.download_source")), 2, rowIndex);
                        searchPane.add(downloadSourceComboBox, 3, rowIndex);
                    } else {
                        GridPane.setColumnSpan(versionsComboBox, 3);
                    }

                    rowIndex++;
                }

                JFXTextField nameField = new JFXTextField();
                nameField.setPromptText(getSkinnable().supportChinese.get() ? i18n("search.hint.chinese") : i18n("search.hint.english"));

                JFXComboBox<String> gameVersionField = new JFXComboBox<>();
                gameVersionField.setMaxWidth(Double.MAX_VALUE);
                gameVersionField.setEditable(true);
                gameVersionField.getItems().setAll(RemoteModRepository.DEFAULT_GAME_VERSIONS);
                Label lblGameVersion = new Label(i18n("world.game_version"));
                searchPane.addRow(rowIndex++, new Label(i18n("mods.name")), nameField, lblGameVersion, gameVersionField);

                ObjectBinding<Boolean> hasVersion = BindingMapping.of(getSkinnable().version)
                        .map(version -> version.getVersion() == null);
                lblGameVersion.managedProperty().bind(hasVersion);
                lblGameVersion.visibleProperty().bind(hasVersion);
                gameVersionField.managedProperty().bind(hasVersion);
                gameVersionField.visibleProperty().bind(hasVersion);

                FXUtils.onChangeAndOperate(getSkinnable().version, version -> {
                    if (StringUtils.isNotBlank(version.getVersion())) {
                        GridPane.setColumnSpan(nameField, 3);
                    } else {
                        GridPane.setColumnSpan(nameField, 1);
                    }
                });

                StackPane categoryStackPane = new StackPane();
                JFXComboBox<CategoryIndented> categoryComboBox = new JFXComboBox<>();
                categoryComboBox.getItems().setAll(new CategoryIndented(0, null));
                categoryStackPane.getChildren().setAll(categoryComboBox);
                categoryComboBox.prefWidthProperty().bind(categoryStackPane.widthProperty());
                categoryComboBox.getStyleClass().add("fit-width");
                categoryComboBox.setPromptText(i18n("mods.category"));
                categoryComboBox.getSelectionModel().select(0);
                categoryComboBox.setConverter(stringConverter(getSkinnable()::getLocalizedCategoryIndent));
                Task.supplyAsync(() -> getSkinnable().repository.getCategories())
                        .thenAcceptAsync(Schedulers.javafx(), categories -> {
                            List<CategoryIndented> result = new ArrayList<>();
                            result.add(new CategoryIndented(0, null));
                            for (RemoteModRepository.Category category : Lang.toIterable(categories)) {
                                resolveCategory(category, 0, result);
                            }
                            categoryComboBox.getItems().setAll(result);
                        }).start();

                StackPane sortStackPane = new StackPane();
                JFXComboBox<RemoteModRepository.SortType> sortComboBox = new JFXComboBox<>();
                sortStackPane.getChildren().setAll(sortComboBox);
                sortComboBox.prefWidthProperty().bind(sortStackPane.widthProperty());
                sortComboBox.getStyleClass().add("fit-width");
                sortComboBox.setConverter(stringConverter(sortType -> i18n("curse.sort." + sortType.name().toLowerCase(Locale.ROOT))));
                sortComboBox.getItems().setAll(RemoteModRepository.SortType.values());
                sortComboBox.getSelectionModel().select(0);
                searchPane.addRow(rowIndex++, new Label(i18n("mods.category")), categoryStackPane, new Label(i18n("search.sort")), sortStackPane);

                JFXButton searchButton = new JFXButton();
                searchButton.setText(i18n("search"));
                searchButton.getStyleClass().add("jfx-button-raised");
                searchButton.setButtonType(JFXButton.ButtonType.RAISED);
                ObservableList<Node> last = FXCollections.observableArrayList(searchButton);
                HBox searchBox = new HBox(8);
                actions.appendList(control.actions);
                actions.appendList(last);
                Bindings.bindContent(searchBox.getChildren(), actions.getAggregatedList());
                GridPane.setColumnSpan(searchBox, 4);
                searchBox.setAlignment(Pos.CENTER_RIGHT);
                searchPane.addRow(rowIndex++, searchBox);

                EventHandler<ActionEvent> searchAction = e -> getSkinnable()
                        .search(gameVersionField.getSelectionModel().getSelectedItem(),
                                Optional.ofNullable(categoryComboBox.getSelectionModel().getSelectedItem())
                                        .map(CategoryIndented::getCategory)
                                        .orElse(null),
                                0,
                                nameField.getText(),
                                sortComboBox.getSelectionModel().getSelectedItem());
                searchButton.setOnAction(searchAction);
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

                JFXListView<RemoteMod> listView = new JFXListView<>();
                spinnerPane.setContent(listView);
                Bindings.bindContent(listView.getItems(), getSkinnable().items);
                listView.setOnMouseClicked(e -> {
                    if (listView.getSelectionModel().getSelectedIndex() < 0)
                        return;
                    RemoteMod selectedItem = listView.getSelectionModel().getSelectedItem();
                    Controllers.navigate(new DownloadPage(getSkinnable(), selectedItem, getSkinnable().getProfileVersion(), getSkinnable().callback));
                });
                listView.setCellFactory(x -> new FloatListCell<RemoteMod>(listView) {
                    TwoLineListItem content = new TwoLineListItem();
                    ImageView imageView = new ImageView();

                    {
                        HBox container = new HBox(8);
                        container.setAlignment(Pos.CENTER_LEFT);
                        pane.getChildren().add(container);

                        container.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content);
                    }

                    @Override
                    protected void updateControl(RemoteMod dataItem, boolean empty) {
                        if (empty) return;
                        ModTranslations.Mod mod = ModTranslations.getTranslationsByRepositoryType(getSkinnable().repository.getType()).getModByCurseForgeId(dataItem.getSlug());
                        content.setTitle(mod != null ? mod.getDisplayName() : dataItem.getTitle());
                        content.setSubtitle(dataItem.getDescription());
                        content.getTags().setAll(dataItem.getCategories().stream()
                                .map(category -> getSkinnable().getLocalizedCategory(category))
                                .collect(Collectors.toList()));

                        if (StringUtils.isNotBlank(dataItem.getIconUrl())) {
                            imageView.setImage(new Image(dataItem.getIconUrl(), 40, 40, true, true, true));
                        }
                    }
                });
            }

            getChildren().setAll(pane);
        }

        private static class CategoryIndented {
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
