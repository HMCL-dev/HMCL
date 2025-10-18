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

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

final class DatapackListPageSkin extends SkinBase<DatapackListPage> {

    private final TransitionPane toolbarPane;
    private final HBox searchBar;
    private final HBox toolbarNormal;
    private final HBox toolbarSelecting;

    DatapackListPageSkin(DatapackListPage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");
        JFXListView<DatapackInfoObject> listView = new JFXListView<>();

        {
            toolbarPane = new TransitionPane();
            searchBar = new HBox();
            toolbarNormal = new HBox();
            toolbarSelecting = new HBox();

            toolbarNormal.getChildren().addAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("datapack.add"), SVG.ADD, skinnable::add),
                    createToolbarButton2(i18n("folder.datapack"), SVG.FOLDER_OPEN, skinnable::openDataPackFolder)
            );

            toolbarSelecting.getChildren().addAll(
                    createToolbarButton2(i18n("button.remove"), SVG.DELETE, () -> {
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                            skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                        }, null);
                    }),
                    createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                            skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () ->
                            skinnable.disableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                            listView.getSelectionModel().selectAll()),
                    createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                            listView.getSelectionModel().clearSelection())
            );

            FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                    selectedItem -> {
                        if (selectedItem == null)
                            changeToolbar(toolbarNormal);
                        else
                            changeToolbar(toolbarSelecting);
                    });
            root.getContent().add(toolbarPane);
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            Holder<Object> lastCell = new Holder<>();
            listView.setCellFactory(x -> new DatapackInfoListCell(listView, lastCell));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            Bindings.bindContent(listView.getItems(), skinnable.getItems());

            // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
            ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

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
                //searchField.requestFocus();
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
            return StringUtils.parseColorEscapes(packInfo.getDescription().toString());
        }

        Datapack.Pack getPackInfo() {
            return packInfo;
        }

        @FXThread
        Image loadIcon() {
            Image image = null;
            Path imagePath;
            if (this.getPackInfo().isDirectory()) {
                imagePath = getPackInfo().getPath().resolve("pack.png");
                System.out.println("Datapack Path: " + this.getPackInfo().getPath());
                try {
                    image = FXUtils.loadImage(imagePath,24,24,true,true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(getPackInfo().getPath())){
                    imagePath = fs.getPath("/pack.png");
                    System.out.println("Datapack(zip) path: " + packInfo.getPath() + "\nfs path: " + fs + "\nimage path: " + FileUtils.getAbsolutePath(imagePath) + "\nimage url: " + imagePath.toUri());
                    if (Files.exists(imagePath)) {
                        image = FXUtils.loadImage(imagePath,24,24,true,true);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
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

    private static final class DatapackInfoListCell extends MDListCell<DatapackInfoObject> {
        final JFXCheckBox checkBox = new JFXCheckBox();
        ImageView imageView = new ImageView();
        final TwoLineListItem content = new TwoLineListItem();
        BooleanProperty booleanProperty;

        DatapackInfoListCell(JFXListView<DatapackInfoObject> listView, Holder<Object> lastCell) {
            super(listView, lastCell);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            imageView.setFitWidth(24);
            imageView.setFitHeight(24);
            imageView.setPreserveRatio(true);
            imageView.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_pack.png"));

            StackPane.setMargin(container, new Insets(8));
            container.getChildren().setAll(checkBox, imageView, content);
            getContainer().getChildren().setAll(container);
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
}
