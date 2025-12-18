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

import com.jfoenix.controls.*;
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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
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

            selectingToolbar.getChildren().addAll(
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
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            Holder<Object> lastCell = new Holder<>();
            listView.setCellFactory(x -> new DatapackInfoListCell(listView, lastCell));
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

        listView.setOnContextMenuRequested(event -> {
            DatapackInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                listView.getSelectionModel().clearSelection();
                Controllers.dialog(new DatapackInfoDialog(selectedItem));
            }
        });

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
        JFXButton revealButton = new JFXButton();
        JFXButton infoButton = new JFXButton();
        BooleanProperty booleanProperty;

        DatapackInfoListCell(JFXListView<DatapackInfoObject> listView, Holder<Object> lastCell) {
            super(listView, lastCell);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            imageView.setFitWidth(32);
            imageView.setFitHeight(32);
            imageView.setPreserveRatio(true);
            imageView.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_pack.png"));

            revealButton.getStyleClass().add("toggle-icon4");
            revealButton.setGraphic(FXUtils.limitingSize(SVG.FOLDER.createIcon(24), 24, 24));
            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(FXUtils.limitingSize(SVG.INFO.createIcon(24), 24, 24));

            StackPane.setMargin(container, new Insets(8));
            container.getChildren().setAll(checkBox, imageView, content, revealButton, infoButton);
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
            revealButton.setOnAction(e -> FXUtils.showFileInExplorer(dataItem.getPackInfo().getPath()));
            infoButton.setOnAction(e -> Controllers.dialog(new DatapackInfoDialog(dataItem)));
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

    final class DatapackInfoDialog extends JFXDialogLayout {
        public DatapackInfoDialog(DatapackInfoObject datapackInfoObject) {

            Stage stage = Controllers.getStage();
            {
                maxWidthProperty().bind(stage.widthProperty().multiply(0.7));
            }


            //heading area
            HBox titleContainer = new HBox();
            {
                titleContainer.setSpacing(8);
                setHeading(titleContainer);
            }
            {
                TwoLineListItem title = new TwoLineListItem();
                {
                    title.setTitle(datapackInfoObject.getTitle());
                }

                ImageView imageView = new ImageView();
                {
                    FXUtils.limitSize(imageView, 40, 40);
                    datapackInfoObject.loadIcon(imageView, null);
                }

                titleContainer.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), title);
            }

            //body area
            Label description = new Label(datapackInfoObject.getSubtitle());
            {
                description.setWrapText(true);
                FXUtils.copyOnDoubleClick(description);
            }
            ScrollPane descriptionPane = new ScrollPane(description);
            {
                FXUtils.smoothScrolling(descriptionPane);
                descriptionPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                descriptionPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                descriptionPane.setFitToWidth(true);
                description.heightProperty().addListener((obs, oldVal, newVal) -> {
                    double maxHeight = stage.getHeight() * 0.5;
                    double targetHeight = Math.min(newVal.doubleValue(), maxHeight);
                    descriptionPane.setPrefViewportHeight(targetHeight);
                });

                setBody(descriptionPane);
            }

            //action area
            JFXHyperlink openInMcModButton = new JFXHyperlink(i18n("mods.mcmod.search"));
            {
                openInMcModButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                            pair("key", datapackInfoObject.getTitle()),
                            pair("site", "all"),
                            pair("filter", "0")
                    )));
                });
            }

            for (Pair<String, ? extends RemoteModRepository> item : Arrays.asList(
                    pair("mods.curseforge", CurseForgeRemoteModRepository.MODS),
                    pair("mods.modrinth", ModrinthRemoteModRepository.MODS)
            )) {
                RemoteModRepository repository = item.getValue();
                JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                Task.runAsync(() -> {
                    Optional<RemoteMod.Version> versionOptional = repository.getRemoteVersionByLocalFile(null, datapackInfoObject.getPackInfo().getPath());
                    versionOptional.ifPresent(version -> {
                        RemoteMod remoteMod;
                        try {
                            remoteMod = repository.getModById(version.getModid());
                        } catch (IOException e) {
                            LOG.warning("Cannot get remote mod of " + version.getModid(), e);
                            return;
                        }

                        FXUtils.runInFX(() -> {
                            button.setOnAction(e -> {
                                fireEvent(new DialogCloseEvent());
                                Controllers.navigate(new DownloadPage(
                                        repository instanceof CurseForgeRemoteModRepository ? HMCLLocalizedDownloadListPage.ofCurseForgeMod(null, false) : HMCLLocalizedDownloadListPage.ofModrinthMod(null, false),
                                        remoteMod,
                                        new Profile.ProfileVersion(getSkinnable().getProfile(), getSkinnable().getVersionID()),
                                        null
                                ));
                            });
                            button.setDisable(false);

                            ModTranslations.Mod modToOpenInMcMod = ModTranslations.getTranslationsByRepositoryType(repository.getType()).getModByCurseForgeId(remoteMod.getSlug());
                            if (modToOpenInMcMod != null) {
                                openInMcModButton.setOnAction(e -> {
                                    fireEvent(new DialogCloseEvent());
                                    FXUtils.openLink(ModTranslations.MOD.getMcmodUrl(modToOpenInMcMod));
                                });
                                openInMcModButton.setText(i18n("mods.mcmod.page"));
                            }
                        });
                    });
                }).start();
                button.setDisable(true);
                getActions().add(button);
            }

            JFXButton okButton = new JFXButton();
            {
                okButton.getStyleClass().add("dialog-accept");
                okButton.setText(i18n("button.ok"));
                okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            }

            getActions().addAll(openInMcModButton, okButton);

            onEscPressed(this, okButton::fire);
        }
    }
}
