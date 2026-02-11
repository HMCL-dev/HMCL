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
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class DatapackListPageSkin extends SkinBase<DatapackListPage> {

    private final TransitionPane toolbarPane;
    private final HBox searchBar;
    private final HBox normalToolbar;
    private final HBox selectingToolbar;
    InvalidationListener updateBarByStateWeakListener;

    private final JFXListView<DatapackInfoObject> listView;
    private final FilteredList<DatapackInfoObject> filteredList;

    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
    private final BooleanProperty isSelecting = new SimpleBooleanProperty(false);
    private final JFXTextField searchField;

    private static final AtomicInteger lastShiftClickIndex = new AtomicInteger(-1);
    final Consumer<Integer> toggleSelect;

    DatapackListPageSkin(DatapackListPage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");
        listView = new JFXListView<>();
        filteredList = new FilteredList<>(skinnable.getItems());

        {
            toolbarPane = new TransitionPane();
            searchBar = new HBox();
            normalToolbar = new HBox();
            selectingToolbar = new HBox();

            normalToolbar.getChildren().addAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("datapack.add"), SVG.ADD, skinnable::add),
                    createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::openDataPackFolder),
                    createToolbarButton2(i18n("search"), SVG.SEARCH, () -> isSearching.set(true))
            );

            JFXButton removeButton = createToolbarButton2(i18n("button.remove"), SVG.DELETE, () -> {
                Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                    skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                }, null);
            });
            JFXButton enableButton = createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                    skinnable.enableSelected(listView.getSelectionModel().getSelectedItems()));
            JFXButton disableButton = createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () ->
                    skinnable.disableSelected(listView.getSelectionModel().getSelectedItems()));
            removeButton.disableProperty().bind(getSkinnable().readOnly);
            enableButton.disableProperty().bind(getSkinnable().readOnly);
            disableButton.disableProperty().bind(getSkinnable().readOnly);

            selectingToolbar.getChildren().addAll(
                    removeButton,
                    enableButton,
                    disableButton,
                    createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                            listView.getSelectionModel().selectRange(0, listView.getItems().size())),//reason for not using selectAll() is that selectAll() first clears all selected then selects all, causing the toolbar to flicker
                    createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                            listView.getSelectionModel().clearSelection())
            );

            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            PauseTransition pause = new PauseTransition(Duration.millis(100));
            pause.setOnFinished(e -> filteredList.setPredicate(skinnable.updateSearchPredicate(searchField.getText())));
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                pause.setRate(1);
                pause.playFromStart();
            });
            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                    () -> {
                        isSearching.set(false);
                        searchField.clear();
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
                    selectedItem -> isSelecting.set(selectedItem != null));
            root.getContent().add(toolbarPane);

            updateBarByStateWeakListener = FXUtils.observeWeak(() -> {
                if (isSelecting.get()) {
                    changeToolbar(selectingToolbar);
                } else if (!isSelecting.get() && !isSearching.get()) {
                    changeToolbar(normalToolbar);
                } else {
                    changeToolbar(searchBar);
                }
            }, isSearching, isSelecting);
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.loadingProperty().bind(skinnable.loadingProperty());

            listView.setCellFactory(x -> new DatapackInfoListCell(listView, getSkinnable().readOnly));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            this.listView.setItems(filteredList);

            // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
            FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

            center.setContent(listView);
            root.getContent().add(center);
        }

        toggleSelect = i -> {
            if (listView.getSelectionModel().isSelected(i)) {
                listView.getSelectionModel().clearSelection(i);
            } else {
                listView.getSelectionModel().select(i);
            }
        };

        pane.getChildren().setAll(root);
        getChildren().setAll(pane);
    }

    private void changeToolbar(HBox newToolbar) {
        Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
            if (newToolbar == searchBar) {
                // search button click will get focus while searchField request focus, this cause conflict.
                // Defer focus request to next pulse avoids this conflict.
                Platform.runLater(searchField::requestFocus);
            }
        }
    }

    static class DatapackInfoObject extends RecursiveTreeObject<DatapackInfoObject> {
        private final BooleanProperty activeProperty;
        private final Datapack.Pack packInfo;

        private SoftReference<CompletableFuture<Image>> iconCache;

        DatapackInfoObject(Datapack.Pack packInfo) {
            this.packInfo = packInfo;
            this.activeProperty = packInfo.activeProperty();
        }

        String getTitle() {
            return packInfo.getId();
        }

        String getSubtitle() {
            return packInfo.getDescription().toString();
        }

        Datapack.Pack getPackInfo() {
            return packInfo;
        }

        Image loadIcon() {
            Image image = null;
            Path imagePath;
            if (this.getPackInfo().isDirectory()) {
                imagePath = getPackInfo().getPath().resolve("pack.png");
                try {
                    image = FXUtils.loadImage(imagePath, 64, 64, true, true);
                } catch (Exception e) {
                    LOG.warning("fail to load image, datapack path: " + getPackInfo().getPath(), e);
                    return FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
                }
            } else {
                try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(getPackInfo().getPath())) {
                    imagePath = fs.getPath("/pack.png");
                    if (Files.exists(imagePath)) {
                        image = FXUtils.loadImage(imagePath, 64, 64, true, true);
                    }
                } catch (Exception e) {
                    LOG.warning("fail to load image, datapack path: " + getPackInfo().getPath(), e);
                    return FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
                }
            }

            if (image != null && !image.isError() && image.getWidth() > 0 && image.getHeight() > 0 &&
                    Math.abs(image.getWidth() - image.getHeight()) < 1) {
                return image;
            } else {
                return FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
            }
        }

        public void loadIcon(ImageView imageView, @Nullable WeakReference<ObjectProperty<DatapackInfoObject>> current) {
            SoftReference<CompletableFuture<Image>> iconCache = this.iconCache;
            CompletableFuture<Image> imageFuture;
            if (iconCache != null && (imageFuture = iconCache.get()) != null) {
                Image image = imageFuture.getNow(null);
                if (image != null) {
                    imageView.setImage(image);
                    return;
                }
            } else {
                imageFuture = CompletableFuture.supplyAsync(this::loadIcon, Schedulers.io());
                this.iconCache = new SoftReference<>(imageFuture);
            }
            imageView.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_pack.png"));
            imageFuture.thenAcceptAsync(image -> {
                if (current != null) {
                    ObjectProperty<DatapackInfoObject> infoObjectProperty = current.get();
                    if (infoObjectProperty == null || infoObjectProperty.get() != this) {
                        // The current ListCell has already switched to another object
                        return;
                    }
                }
                imageView.setImage(image);
            }, Schedulers.javafx());

        }
    }

    private final class DatapackInfoListCell extends MDListCell<DatapackInfoObject> {
        final JFXCheckBox checkBox = new JFXCheckBox();
        ImageView imageView = new ImageView();
        final TwoLineListItem content = new TwoLineListItem();
        BooleanProperty booleanProperty;

        DatapackInfoListCell(JFXListView<DatapackInfoObject> listView, BooleanProperty isReadOnlyProperty) {
            super(listView);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            checkBox.disableProperty().bind(isReadOnlyProperty);

            imageView.setFitWidth(32);
            imageView.setFitHeight(32);
            imageView.setPreserveRatio(true);
            imageView.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_pack.png"));

            StackPane.setMargin(container, new Insets(8));
            container.getChildren().setAll(checkBox, imageView, content);
            getContainer().getChildren().setAll(container);

            getContainer().getParent().addEventHandler(MouseEvent.MOUSE_PRESSED, mouseEvent -> handleSelect(this, mouseEvent));
        }

        @Override
        protected void updateControl(DatapackInfoObject dataItem, boolean empty) {
            if (empty) return;
            content.setTitle(dataItem.getTitle());
            content.setSubtitle(dataItem.getSubtitle());
            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.activeProperty);
            dataItem.loadIcon(imageView, new WeakReference<>(this.itemProperty()));
        }
    }

    public void handleSelect(DatapackInfoListCell cell, MouseEvent mouseEvent) {
        if (cell.isEmpty()) {
            mouseEvent.consume();
            return;
        }

        if (mouseEvent.isShiftDown()) {
            int currentIndex = cell.getIndex();
            if (lastShiftClickIndex.get() == -1) {
                lastShiftClickIndex.set(currentIndex);
                toggleSelect.accept(cell.getIndex());
            } else if (listView.getItems().size() >= lastShiftClickIndex.get() && !(lastShiftClickIndex.get() < -1)) {
                if (cell.isSelected()) {
                    IntStream.rangeClosed(
                                    Math.min(lastShiftClickIndex.get(), currentIndex),
                                    Math.max(lastShiftClickIndex.get(), currentIndex))
                            .forEach(listView.getSelectionModel()::clearSelection);
                } else {
                    listView.getSelectionModel().selectRange(lastShiftClickIndex.get(), currentIndex);
                    listView.getSelectionModel().select(currentIndex);
                }
                lastShiftClickIndex.set(-1);
            } else {
                lastShiftClickIndex.set(currentIndex);
                listView.getSelectionModel().select(currentIndex);
            }
        } else {
            toggleSelect.accept(cell.getIndex());
        }
        cell.requestFocus();
        mouseEvent.consume();
    }
}
