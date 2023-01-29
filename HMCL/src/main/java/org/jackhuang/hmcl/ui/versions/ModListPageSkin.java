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
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.isNotBlank;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class ModListPageSkin extends SkinBase<ModListPage> {

    ModListPageSkin(ModListPage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");
        JFXListView<ModInfoObject> listView = new JFXListView<>();
        JFXTextField searchField = new JFXTextField();

        {
            HBox searchBar = new HBox();
            searchBar.setAlignment(Pos.BASELINE_CENTER);
            searchBar.setPadding(new Insets(8, 8, 8, 8));

            HBox.setHgrow(searchField, Priority.ALWAYS);
            searchField.setPromptText(i18n("search"));

            JFXButton clearBtn = new JFXButton();
            clearBtn.setGraphic(SVG.close(Theme.blackFillBinding(), -1, -1));
            clearBtn.setOnMouseClicked((event) -> {
                searchField.textProperty().set("");
            });
            searchBar.getChildren().setAll(searchField, wrapMargin(clearBtn, new Insets(0, 0, 0, 9)));
            root.getContent().add(searchBar);
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            MutableObject<Object> lastCell = new MutableObject<>();
            listView.setCellFactory(x -> new ModInfoListCell(listView, lastCell));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            Bindings.bindContent(listView.getItems(), skinnable.getFilteredItems(searchField.textProperty()));

            center.setContent(listView);
            root.getContent().add(center);
        }

        {
            TransitionPane toolBarPane = new TransitionPane();
            HBox toolbarNormal = new HBox();
            toolbarNormal.getChildren().setAll(
                    createToolbarButton2(i18n("button.refresh"), SVG::refresh, skinnable::refresh),
                    createToolbarButton2(i18n("mods.add"), SVG::plus, skinnable::add),
                    createToolbarButton2(i18n("folder.mod"), SVG::folderOpen, skinnable::openModFolder),
                    createToolbarButton2(i18n("mods.check_updates"), SVG::update, skinnable::checkUpdates),
                    createToolbarButton2(i18n("download"), SVG::downloadOutline, skinnable::download));
            HBox toolbarSelecting = new HBox();
            toolbarSelecting.getChildren().setAll(
                    createToolbarButton2(i18n("button.remove"), SVG::delete, () -> {
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                            skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                        }, null);
                    }),
                    createToolbarButton2(i18n("mods.enable"), SVG::check, () ->
                            skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("mods.disable"), SVG::close, () ->
                            skinnable.disableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("button.select_all"), SVG::selectAll, () ->
                            listView.getSelectionModel().selectAll()),
                    createToolbarButton2(i18n("button.cancel"), SVG::cancel, () ->
                            listView.getSelectionModel().clearSelection()));
            FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(), selectedItem -> {
                if (selectedItem == null) {
                    toolBarPane.setContent(toolbarNormal, ContainerAnimations.FADE.getAnimationProducer());
                } else {
                    toolBarPane.setContent(toolbarSelecting, ContainerAnimations.FADE.getAnimationProducer());
                }
            });
            root.getContent().add(toolBarPane);
        }

        Label label = new Label(i18n("mods.not_modded"));
        label.prefWidthProperty().bind(pane.widthProperty().add(-100));

        FXUtils.onChangeAndOperate(skinnable.moddedProperty(), modded -> {

            if (modded) pane.getChildren().setAll(root);
            else pane.getChildren().setAll(label);
        });

        getChildren().setAll(pane);
    }

    static class ModInfoObject extends RecursiveTreeObject<ModInfoObject> implements Comparable<ModInfoObject> {
        private final BooleanProperty active;
        private final LocalModFile localModFile;
        private final String message;
        private final ModTranslations.Mod mod;

        ModInfoObject(LocalModFile localModFile) {
            this.localModFile = localModFile;
            this.active = localModFile.activeProperty();
            StringBuilder message = new StringBuilder(localModFile.getName());
            if (isNotBlank(localModFile.getVersion()))
                message.append(", ").append(i18n("archive.version")).append(": ").append(localModFile.getVersion());
            if (isNotBlank(localModFile.getGameVersion()))
                message.append(", ").append(i18n("archive.game_version")).append(": ").append(localModFile.getGameVersion());
            if (isNotBlank(localModFile.getAuthors()))
                message.append(", ").append(i18n("archive.author")).append(": ").append(localModFile.getAuthors());
            this.message = message.toString();
            this.mod = ModTranslations.MOD.getModById(localModFile.getId());
        }

        String getTitle() {
            return localModFile.getFileName();
        }

        String getSubtitle() {
            return message;
        }

        LocalModFile getModInfo() {
            return localModFile;
        }

        public ModTranslations.Mod getMod() {
            return mod;
        }

        @Override
        public int compareTo(@NotNull ModListPageSkin.ModInfoObject o) {
            return localModFile.getFileName().toLowerCase().compareTo(o.localModFile.getFileName().toLowerCase());
        }
    }

    static class ModInfoDialog extends JFXDialogLayout {

        ModInfoDialog(ModInfoObject modInfo) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            ImageView imageView = new ImageView();
            if (StringUtils.isNotBlank(modInfo.getModInfo().getLogoPath())) {
                Task.supplyAsync(() -> {
                    try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modInfo.getModInfo().getFile())) {
                        Path iconPath = fs.getPath(modInfo.getModInfo().getLogoPath());
                        if (Files.exists(iconPath)) {
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            Files.copy(iconPath, stream);
                            return new ByteArrayInputStream(stream.toByteArray());
                        }
                    }
                    return null;
                }).whenComplete(Schedulers.javafx(), (stream, exception) -> {
                    if (stream != null) {
                        imageView.setImage(new Image(stream, 40, 40, true, true));
                    } else {
                        imageView.setImage(new Image("/assets/img/command.png", 40, 40, true, true));
                    }
                }).start();
            }

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(modInfo.getModInfo().getName());
            if (StringUtils.isNotBlank(modInfo.getModInfo().getVersion())) {
                title.getTags().setAll(modInfo.getModInfo().getVersion());
            }
            title.setSubtitle(FileUtils.getName(modInfo.getModInfo().getFile()));

            titleContainer.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), title);
            setHeading(titleContainer);

            Label description = new Label(modInfo.getModInfo().getDescription().toString());
            setBody(description);

            if (StringUtils.isNotBlank(modInfo.getModInfo().getUrl())) {
                JFXHyperlink officialPageButton = new JFXHyperlink();
                officialPageButton.setText(i18n("mods.url"));
                officialPageButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(modInfo.getModInfo().getUrl());
                });

                getActions().add(officialPageButton);
            }

            if (modInfo.getMod() != null && StringUtils.isNotBlank(modInfo.getMod().getMcbbs())) {
                JFXHyperlink mcbbsButton = new JFXHyperlink();
                mcbbsButton.setText(i18n("mods.mcbbs"));
                mcbbsButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(ModManager.getMcbbsUrl(modInfo.getMod().getMcbbs()));
                });
                getActions().add(mcbbsButton);
            }

            if (modInfo.getMod() == null || StringUtils.isBlank(modInfo.getMod().getMcmod())) {
                JFXHyperlink searchButton = new JFXHyperlink();
                searchButton.setText(i18n("mods.mcmod.search"));
                searchButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                            pair("key", modInfo.getModInfo().getName()),
                            pair("site", "all"),
                            pair("filter", "0")
                    )));
                });
                getActions().add(searchButton);
            } else {
                JFXHyperlink mcmodButton = new JFXHyperlink();
                mcmodButton.setText(i18n("mods.mcmod.page"));
                mcmodButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(ModTranslations.MOD.getMcmodUrl(modInfo.getMod()));
                });
                getActions().add(mcmodButton);
            }

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }
    }

    private static final Lazy<PopupMenu> menu = new Lazy<>(PopupMenu::new);
    private static final Lazy<JFXPopup> popup = new Lazy<>(() -> new JFXPopup(menu.get()));

    final class ModInfoListCell extends MDListCell<ModInfoObject> {
        JFXCheckBox checkBox = new JFXCheckBox();
        TwoLineListItem content = new TwoLineListItem();
        JFXButton restoreButton = new JFXButton();
        JFXButton infoButton = new JFXButton();
        JFXButton revealButton = new JFXButton();
        BooleanProperty booleanProperty;

        ModInfoListCell(JFXListView<ModInfoObject> listView, MutableObject<Object> lastCell) {
            super(listView, lastCell);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            restoreButton.getStyleClass().add("toggle-icon4");
            restoreButton.setGraphic(FXUtils.limitingSize(SVG.restore(Theme.blackFillBinding(), 24, 24), 24, 24));

            FXUtils.installFastTooltip(restoreButton, i18n("mods.restore"));

            revealButton.getStyleClass().add("toggle-icon4");
            revealButton.setGraphic(FXUtils.limitingSize(SVG.folderOutline(Theme.blackFillBinding(), 24, 24), 24, 24));

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(FXUtils.limitingSize(SVG.informationOutline(Theme.blackFillBinding(), 24, 24), 24, 24));

            container.getChildren().setAll(checkBox, content, restoreButton, revealButton, infoButton);

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModInfoObject dataItem, boolean empty) {
            if (empty) return;
            content.setTitle(dataItem.getTitle());
            if (dataItem.getMod() != null && I18n.getCurrentLocale().getLocale() == Locale.CHINA) {
                content.getTags().setAll(dataItem.getMod().getDisplayName());
            } else {
                content.getTags().clear();
            }
            content.setSubtitle(dataItem.getSubtitle());
            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.active);
            restoreButton.setVisible(!dataItem.getModInfo().getMod().getOldFiles().isEmpty());
            restoreButton.setOnMouseClicked(e -> {
                menu.get().getContent().setAll(dataItem.getModInfo().getMod().getOldFiles().stream()
                        .map(localModFile -> new IconedMenuItem(null, localModFile.getVersion(), () -> {
                            popup.get().hide();
                            getSkinnable().rollback(dataItem.getModInfo(), localModFile);
                        }))
                        .collect(Collectors.toList())
                );

                popup.get().show(restoreButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, restoreButton.getHeight());
            });
            revealButton.setOnMouseClicked(e -> {
                FXUtils.showFileInExplorer(dataItem.getModInfo().getFile());
            });
            infoButton.setOnMouseClicked(e -> {
                Controllers.dialog(new ModInfoDialog(dataItem));
            });
        }
    }
}
