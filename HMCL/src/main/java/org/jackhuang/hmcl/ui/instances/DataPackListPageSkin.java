// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.datapack.DataPack;
import org.jackhuang.hmcl.image.ImageLoaders;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
final class DataPackListPageSkin extends SkinBase<DataPackListPage> {

    private final TransitionPane toolbarPane;
    private final HBox searchBar;
    private final HBox normalToolbar;
    private final HBox selectingToolbar;

    private final JFXListView<DataPackInfoObject> listView;

    /// Whether the search mechanism is currently active.
    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);

    private final JFXTextField searchField;

    /// Timer for debouncing search input to avoid executing search on every keystroke.
    private final PauseTransition searchPause = new PauseTransition(Duration.millis(100));

    DataPackListPageSkin(DataPackListPage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");
        listView = new JFXListView<>();
        listView.getStyleClass().add("no-horizontal-scrollbar");

        // reason for not using selectAll() is that selectAll() first clears all selected then selects all, causing the toolbar to flicker
        var selectAllButton = createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () -> listView.getSelectionModel().selectRange(0, listView.getItems().size()));

        ListChangeListener<Object> listener = change -> selectAllButton.setDisable(!listView.getItems().isEmpty()
                    && listView.getSelectionModel().getSelectedItems().size() == listView.getItems().size());

        {
            toolbarPane = new TransitionPane();
            searchBar = new HBox();
            normalToolbar = new HBox();
            selectingToolbar = new HBox();

            normalToolbar.getChildren().addAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("datapack.add"), SVG.ADD, skinnable::add),
                    createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::openDataPackFolder),
                    createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
            );

            JFXButton removeButton = createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () ->
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () ->
                            skinnable.removeSelected(listView.getSelectionModel().getSelectedItems()), null));
            JFXButton enableButton = createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                    skinnable.setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), true));
            JFXButton disableButton = createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () ->
                    skinnable.setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), false));
            removeButton.disableProperty().bind(getSkinnable().readOnly);
            enableButton.disableProperty().bind(getSkinnable().readOnly);
            disableButton.disableProperty().bind(getSkinnable().readOnly);

            listView.getSelectionModel().getSelectedItems().addListener(listener);

            selectingToolbar.getChildren().addAll(
                    removeButton,
                    enableButton,
                    disableButton,
                    selectAllButton,
                    createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                            listView.getSelectionModel().clearSelection())
            );

            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            searchPause.setOnFinished(e -> search());
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                    searchPause.setRate(1);
                    searchPause.playFromStart();
                }
            });
            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                    () -> {
                        changeToolbar(normalToolbar);

                        searchField.clear();
                        searchPause.stop();

                        isSearching.set(false);
                        Bindings.bindContent(listView.getItems(), skinnable.getItems());
                    });
            FXUtils.onEscPressed(searchField, closeSearchBar::fire);
            searchBar.getChildren().addAll(searchField, closeSearchBar);

            root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
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
            root.getContent().add(toolbarPane);
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.loadingProperty().bind(skinnable.loadingProperty());

            listView.setCellFactory(x -> new DataPackInfoListCell(listView, getSkinnable().readOnly));
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

            Bindings.bindContent(listView.getItems(), skinnable.getItems());
            skinnable.getItems().addListener((ListChangeListener<? super DataPackInfoObject>) change -> {
                if (isSearching.get()) {
                    search();
                }
            });
            listView.getItems().addListener(listener);

            // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
            FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

            center.setContent(listView);
            root.getContent().add(center);
        }

        pane.getChildren().setAll(root);
        getChildren().setAll(pane);
    }

    /// Filters pack IDs and descriptions using the shared query syntax; invalid regular expressions leave the list empty.
    private void search() {
        isSearching.set(true);
        Bindings.unbindContent(listView.getItems(), getSkinnable().getItems());

        String queryString = searchField.getText();
        if (StringUtils.isBlank(queryString)) {
            listView.getItems().setAll(getSkinnable().getItems());
        } else {
            listView.getItems().clear();

            Predicate<@Nullable String> predicate;
            try {
                predicate = StringUtils.compileQuery(queryString);
            } catch (PatternSyntaxException e) {
                LOG.warning("Illegal regular expression", e);
                return;
            }

            for (DataPackInfoObject item : getSkinnable().getItems()) {
                if (predicate.test(item.getTitle()) || predicate.test(item.getSubtitle())) {
                    listView.getItems().add(item);
                }
            }
        }
    }

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
            return FXUtils.ensureValidIcon(packInfo.loadIcon((input, name) -> ImageLoaders.DEFAULT.load(input, name, 64, 64, true, true)), this::getDefaultIcon);
        }
    }

    private static final class DataPackInfoListCell extends MDListCell<DataPackInfoObject> {
        private final JFXSwitch activeSwitch = new JFXSwitch();
        ImageContainer imageContainer = new ImageContainer(32);
        final TwoLineListItem content = new TwoLineListItem();

        private final InvalidationListener activeListener = obs -> activeSwitch.setSelected(getItem().packInfo.isActive());
        private final WeakInvalidationListener weakActiveListener = new WeakInvalidationListener(activeListener);
        private @Nullable ReadOnlyBooleanProperty observedActiveProperty;

        /// Creates a selectable data pack row whose switch follows the read-only state.
        DataPackInfoListCell(JFXListView<DataPackInfoObject> listView, BooleanProperty isReadOnlyProperty) {
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
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(@Nullable DataPackInfoObject dataItem, boolean empty) {
            if (observedActiveProperty != null)
                observedActiveProperty.removeListener(weakActiveListener);
            if (empty || dataItem == null) return;
            content.setTitle(dataItem.getTitle());
            content.setSubtitle(dataItem.getSubtitle());

            DataPack.Pack packInfo = dataItem.packInfo;
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
            dataItem.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(this.itemProperty()));
        }
    }
}
