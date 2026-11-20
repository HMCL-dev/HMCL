// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSwitch;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.Image;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.datapack.DataPack;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.image.ImageLoaders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPaneBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class DataPackListPage extends ListPaneBase<DataPackListPage.DataPackInfoObject> implements WorldManagePage.WorldRefreshable {
    /// Switches between toolbar modes.
    private final TransitionPane toolbarPane = new TransitionPane();
    /// Actions available while browsing.
    private final HBox normalToolbar = new HBox();
    /// Actions on the selected items.
    private final HBox selectingToolbar = new HBox();
    /// Toolbar containing the search input.
    private final HBox searchBar = new HBox();
    /// Text used to filter the list.
    private final JFXTextField searchField = new JFXTextField();
    /// Whether the search mechanism is currently active.
    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
    /// Timer for debouncing search input to avoid executing search on every keystroke.
    private final PauseTransition searchPause = new PauseTransition(Duration.millis(100));

    /// Source items matching the current search.
    private final FilteredList<DataPackInfoObject> filteredItems;

    private final World world;
    private final DataPack dataPack;
    final BooleanProperty readOnly;

    public DataPackListPage(WorldManagePage worldManagePage) {
        world = worldManagePage.getWorld();
        dataPack = new DataPack(world.getFile().resolve("datapacks"));
        filteredItems = new FilteredList<>(MappedObservableList.create(dataPack.getPacks(), DataPackInfoObject::new));
        readOnly = worldManagePage.readOnlyProperty();
        FXUtils.applyDragListener(this, it -> Objects.equals("zip", FileUtils.getExtension(it)),
                this::installMultiDataPack, this::refresh);

        refresh();

        initializeView();
    }

    @Override
    protected ItemCell<DataPackInfoObject> createItemCell(ListView<DataPackInfoObject> listView) {
        return new DataPackCell(listView, readOnly);
    }

    /// Configures the list and its page actions.
    private void initializeView() {
        getStyleClass().add("notice-pane");
        var listView = getListView();
        listView.setItems(filteredItems);

        // reason for not using selectAll() is that selectAll() first clears all selected then selects all, causing the toolbar to flicker
        var selectAllButton = createToolbarButton(i18n("button.select_all"), SVG.SELECT_ALL, () -> listView.getSelectionModel().selectRange(0, listView.getItems().size()));

        ListChangeListener<Object> listener = change -> selectAllButton.setDisable(!listView.getItems().isEmpty()
                    && listView.getSelectionModel().getSelectedItems().size() == listView.getItems().size());

        {
            normalToolbar.getChildren().addAll(
                    createToolbarButton(i18n("button.refresh"), SVG.REFRESH, this::refresh),
                    createToolbarButton(i18n("datapack.add"), SVG.ADD, this::add),
                    createToolbarButton(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, this::openDataPackFolder),
                    createToolbarButton(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
            );

            JFXButton removeButton = createToolbarButton(i18n("button.remove"), SVG.DELETE_FOREVER, () ->
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () ->
                            removeSelected(listView.getSelectionModel().getSelectedItems()), null));
            JFXButton enableButton = createToolbarButton(i18n("mods.enable"), SVG.CHECK, () ->
                    setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), true));
            JFXButton disableButton = createToolbarButton(i18n("mods.disable"), SVG.CLOSE, () ->
                    setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), false));
            removeButton.disableProperty().bind(readOnly);
            enableButton.disableProperty().bind(readOnly);
            disableButton.disableProperty().bind(readOnly);

            listView.getSelectionModel().getSelectedItems().addListener(listener);

            selectingToolbar.getChildren().addAll(
                    removeButton,
                    enableButton,
                    disableButton,
                    selectAllButton,
                    createToolbarButton(i18n("button.cancel"), SVG.CANCEL, () ->
                            listView.getSelectionModel().clearSelection())
            );

            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            searchPause.setOnFinished(e -> search());
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                    searchPause.setRate(1);
                    searchPause.playFromStart();
                }
            });
            JFXButton closeSearchBar = createToolbarButton(null, SVG.CLOSE,
                    () -> {
                        changeToolbar(normalToolbar);

                        searchField.clear();
                        searchPause.stop();

                        isSearching.set(false);
                        filteredItems.setPredicate(null);
                    });
            FXUtils.onEscPressed(searchField, closeSearchBar::fire);
            searchBar.getChildren().addAll(searchField, closeSearchBar);

            addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (listView.getSelectionModel().getSelectedItem() != null) {
                        listView.getSelectionModel().clearSelection();
                        e.consume();
                    }
                }
            });

            FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                    selectedItem -> {
                        if (selectedItem == null)
                            changeToolbar(isSearching.get() ? searchBar : normalToolbar);
                        else
                            changeToolbar(selectingToolbar);
                    });
            FXUtils.setOverflowHidden(toolbarPane, 8);
            setToolbar(toolbarPane);
        }

        {
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            StackPane placeholderContainer = new StackPane();
            placeholderContainer.getStyleClass().add("notice-pane");
            Label placeholderLabel = new Label(i18n("datapack.empty"));
            placeholderLabel.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    if (isSearching.get()) {
                        return i18n("search.no_results_found");
                    } else {
                        return i18n("datapack.empty");
                    }
                },
                isSearching)
            );
            placeholderContainer.getChildren().add(placeholderLabel);
            listView.setPlaceholder(placeholderContainer);

            listView.getItems().addListener(listener);
        }
    }

    private void installMultiDataPack(List<Path> dataPackPath) {
        dataPackPath.forEach(this::installSingleDataPack);
        if (readOnly.get()) {
            Controllers.showToast(i18n("datapack.reload.toast"));
        }
    }

    private void installSingleDataPack(Path dataPack) {
        try {
            this.dataPack.installPack(dataPack, world.getGameVersion());
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("Unable to parse datapack file " + dataPack, e);
        }
    }

    public void refresh() {
        setLoading(true);
        Task.runAsync(dataPack::loadFromDir)
                .withRunAsync(Schedulers.javafx(), () -> setLoading(false))
                .start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("datapack.add.title"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.datapack"), "*.zip"));
        List<Path> res = Controllers.showOpenMultipleDialog(chooser);
        if (res != null) {
            installMultiDataPack(res);
        }

        dataPack.loadFromDir();
    }

    void removeSelected(ObservableList<DataPackInfoObject> selectedItems) {
        List<String> failed = new ArrayList<>();
        selectedItems.stream()
                .map(DataPackInfoObject::getPackInfo)
                .forEach(pack -> {
                    try {
                        dataPack.deletePack(pack);
                    } catch (IOException e) {
                        failed.add(pack.getPath().toString());
                        LOG.warning("Failed to delete datapack \"" + pack.getId() + "\"", e);
                    }
                });
        if (!failed.isEmpty())
            Controllers.dialog(i18n("datapack.delete.failed") + "\n" + String.join(",\n", failed),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
    }

    void setSelectedEnabled(ObservableList<DataPackInfoObject> selectedItems, boolean enabled) {
        List<String> failed = new ArrayList<>();
        selectedItems.stream()
                .map(DataPackInfoObject::getPackInfo)
                .forEach(pack -> {
                    try {
                        pack.setActive(enabled);
                    } catch (IOException e) {
                        failed.add(pack.getPath().toString());
                        LOG.warning("Failed to enable or disable datapack \"" + pack.getId() + "\"", e);
                    }
                });
        if (!failed.isEmpty())
            Controllers.dialog(i18n("datapack.toggle.failed") + "\n" + String.join(",\n", failed),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
    }

    void openDataPackFolder() {
        FXUtils.openFolder(dataPack.getPath());
    }

    /// Filters pack IDs and descriptions using the shared query syntax; invalid regular expressions leave the list empty.
    /// Applies the search text to the displayed items.
    private void search() {
        isSearching.set(true);

        String queryString = searchField.getText();
        if (StringUtils.isBlank(queryString)) {
            filteredItems.setPredicate(null);
        } else {
            Predicate<@Nullable String> predicate;
            try {
                predicate = StringUtils.compileQuery(queryString);
            } catch (PatternSyntaxException e) {
                LOG.warning("Illegal regular expression", e);
                filteredItems.setPredicate(item -> false);
                return;
            }

            filteredItems.setPredicate(item -> {
                return predicate.test(item.getTitle()) || predicate.test(item.getSubtitle());
            });
        }
    }

    /// Shows the requested toolbar and focuses the search input when appropriate.
    private void changeToolbar(HBox newToolbar) {
        @Nullable Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
            if (newToolbar == searchBar) {
                // search button click will get focus while searchField request focus, this cause conflict.
                // Defer focus request to next pulse avoids this conflict.
                Platform.runLater(searchField::requestFocus);
            }
        }
    }

    static class DataPackInfoObject {
        private final DataPack.Pack packInfo;
        private final ItemPropertyAsyncCache<Image, DataPackInfoObject> iconCache;

        DataPackInfoObject(DataPack.Pack packInfo) {
            this.packInfo = packInfo;
            this.iconCache = new ItemPropertyAsyncCache.Soft<>(this, this::loadIcon, this::getDefaultIcon);
        }

        String getTitle() {
            return packInfo.getId();
        }

        String getSubtitle() {
            return packInfo.getDescription().toStringSingleLine();
        }

        DataPack.Pack getPackInfo() {
            return packInfo;
        }

        private Image getDefaultIcon() {
            return FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
        }

        /// Loads the icon, using the default icon if it is absent or invalid.
        private Image loadIcon() {
            Image icon = packInfo.loadIcon(ImageLoaders.DEFAULT);
            return FXUtils.isValidIcon(icon) ? icon : getDefaultIcon();
        }
    }

    private static final class DataPackCell extends ItemCell<DataPackInfoObject> {
        private final JFXSwitch activeSwitch = new JFXSwitch();
        ImageContainer imageContainer = new ImageContainer(32);
        final TwoLineListItem content = new TwoLineListItem();

        private final InvalidationListener activeListener = obs -> activeSwitch.setSelected(getItem().getPackInfo().isActive());
        private final WeakInvalidationListener weakActiveListener = new WeakInvalidationListener(activeListener);
        private @Nullable ReadOnlyBooleanProperty observedActiveProperty;

        /// Creates a selectable data pack row whose switch follows the read-only state.
        DataPackCell(ListView<DataPackInfoObject> listView, BooleanProperty isReadOnlyProperty) {
            super(listView);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(0, 8, 0, 8));
            HBox.setHgrow(content, Priority.ALWAYS);
            setSelectable();

            activeSwitch.disableProperty().bind(isReadOnlyProperty);
            activeSwitch.setSize(20);
            activeSwitch.setMinSize(30, 30);
            activeSwitch.setPrefSize(30, 30);
            activeSwitch.setMaxSize(30, 30);
            HBox.setMargin(activeSwitch, new Insets(0, 0, 0, 6));

            imageContainer.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_pack.png"));

            StackPane.setMargin(container, new Insets(8));
            container.getChildren().setAll(imageContainer, content, activeSwitch);
            setContent(container);
        }

        @Override
        protected void updateListItem(@Nullable DataPackInfoObject dataItem) {
            if (observedActiveProperty != null)
                observedActiveProperty.removeListener(weakActiveListener);
            if (dataItem == null) return;
            content.setTitle(dataItem.getTitle());
            content.setSubtitle(dataItem.getSubtitle());

            DataPack.Pack packInfo = dataItem.getPackInfo();
            (observedActiveProperty = packInfo.activeProperty()).addListener(weakActiveListener);
            activeSwitch.setDisableAnimation(true);
            activeSwitch.setSelected(packInfo.isActive());
            activeSwitch.setOnAction(event -> {
                try {
                    packInfo.setActive(activeSwitch.isSelected());
                } catch (IOException e) {
                    LOG.warning("Unable to change state of datapack " + packInfo.getPath(), e);
                    Controllers.showToast(i18n("datapack.toggle.failed"));
                } finally {
                    activeSwitch.setSelected(packInfo.isActive());
                }
            });
            activeSwitch.setDisableAnimation(!AnimationUtils.isAnimationEnabled());
            dataItem.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(itemProperty()));
        }
    }
}
