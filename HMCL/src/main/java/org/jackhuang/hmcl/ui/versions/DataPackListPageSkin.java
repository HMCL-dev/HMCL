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
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.addon.datapack.DataPack;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
final class DataPackListPageSkin extends ToolbarListPageSkin<DataPackListPageSkin.DataPackInfoObject, DataPackListPage> {

    private static final AtomicInteger lastShiftClickIndex = new AtomicInteger(-1);
    final Consumer<Integer> toggleSelect;

    DataPackListPageSkin(DataPackListPage skinnable) {
        super(skinnable, true);

        listView.setCellFactory(x -> new DataPackInfoListCell(listView, getSkinnable().readOnly));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        JFXButton removeButton = createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
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

        setupSkin(
                new Node[]{
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                        createToolbarButton2(i18n("datapack.add"), SVG.ADD, skinnable::add),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::openDataPackFolder),
                        createToolbarButton2(i18n("search"), SVG.SEARCH, this::startSearch)
                },
                new Node[]{
                        removeButton, enableButton, disableButton,
                        createSelectAllButton(), createCancelSelectionButton()
                }
        );

        toggleSelect = i -> {
            if (listView.getSelectionModel().isSelected(i)) {
                listView.getSelectionModel().clearSelection(i);
            } else {
                listView.getSelectionModel().select(i);
            }
        };
    }

    @Override
    protected Predicate<DataPackListPageSkin.DataPackInfoObject> updateSearchPredicate(String queryString) {
        if (queryString.isBlank()) {
            return dataPack -> true;
        }

        final Predicate<String> stringPredicate;
        if (queryString.startsWith("regex:")) {
            try {
                Pattern pattern = Pattern.compile(StringUtils.substringAfter(queryString, "regex:"));
                stringPredicate = s -> pattern.matcher(s).find();
            } catch (Exception e) {
                return dataPack -> false;
            }
        } else {
            String lowerCaseFilter = queryString.toLowerCase(Locale.ROOT);
            stringPredicate = s -> s.toLowerCase(Locale.ROOT).contains(lowerCaseFilter);
        }

        return dataPack -> {
            String id = dataPack.getPackInfo().getId();
            String description = dataPack.getPackInfo().getDescription().toString();
            return stringPredicate.test(id) || stringPredicate.test(description);
        };
    }

    @Override
    protected String getEmptyPlaceholderText() {
        return i18n("datapack.empty");
    }

    static class DataPackInfoObject extends RecursiveTreeObject<DataPackInfoObject> {
        private final BooleanProperty activeProperty;
        private final DataPack.Pack packInfo;

        private SoftReference<CompletableFuture<Image>> iconCache;

        DataPackInfoObject(DataPack.Pack packInfo) {
            this.packInfo = packInfo;
            this.activeProperty = packInfo.activeProperty();
        }

        String getTitle() {
            return packInfo.getId();
        }

        String getSubtitle() {
            return packInfo.getDescription().toString();
        }

        DataPack.Pack getPackInfo() {
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

        public void loadIcon(ImageContainer imageContainer, @Nullable WeakReference<ObjectProperty<DataPackInfoObject>> current) {
            SoftReference<CompletableFuture<Image>> iconCache = this.iconCache;
            CompletableFuture<Image> imageFuture;
            if (iconCache != null && (imageFuture = iconCache.get()) != null) {
                Image image = imageFuture.getNow(null);
                if (image != null) {
                    imageContainer.setImage(image);
                    return;
                }
            } else {
                imageFuture = CompletableFuture.supplyAsync(this::loadIcon, Schedulers.io());
                this.iconCache = new SoftReference<>(imageFuture);
            }
            imageContainer.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_pack.png"));
            imageFuture.thenAcceptAsync(image -> {
                if (current != null) {
                    ObjectProperty<DataPackInfoObject> infoObjectProperty = current.get();
                    if (infoObjectProperty == null || infoObjectProperty.get() != this) {
                        // The current ListCell has already switched to another object
                        return;
                    }
                }
                imageContainer.setImage(image);
            }, Schedulers.javafx());

        }
    }

    private final class DataPackInfoListCell extends MDListCell<DataPackInfoObject> {
        final JFXCheckBox checkBox = new JFXCheckBox();
        ImageContainer imageContainer = new ImageContainer(32);
        final TwoLineListItem content = new TwoLineListItem();
        BooleanProperty booleanProperty;

        DataPackInfoListCell(JFXListView<DataPackInfoObject> listView, BooleanProperty isReadOnlyProperty) {
            super(listView);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            checkBox.disableProperty().bind(isReadOnlyProperty);

            imageContainer.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_pack.png"));

            StackPane.setMargin(container, new Insets(8));
            container.getChildren().setAll(checkBox, imageContainer, content);
            getContainer().getChildren().setAll(container);

            getContainer().getParent().addEventHandler(MouseEvent.MOUSE_PRESSED, mouseEvent -> handleSelect(this, mouseEvent));
        }

        @Override
        protected void updateControl(DataPackInfoObject dataItem, boolean empty) {
            if (empty) return;
            content.setTitle(dataItem.getTitle());
            content.setSubtitle(dataItem.getSubtitle());
            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.activeProperty);
            dataItem.loadIcon(imageContainer, new WeakReference<>(this.itemProperty()));
        }
    }

    public void handleSelect(DataPackInfoListCell cell, MouseEvent mouseEvent) {
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
