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
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.mod.ModInfo;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.FloatListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.isNotBlank;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class ModListPageSkin extends SkinBase<ModListPage> {

    ModListPageSkin(ModListPage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.getStyleClass().addAll("notice-pane");

        BorderPane root = new BorderPane();
        JFXListView<ModInfoObject> listView = new JFXListView<>();

        {
            HBox toolbar = new HBox();
            toolbar.getStyleClass().add("jfx-tool-bar-second");
            JFXDepthManager.setDepth(toolbar, 1);
            toolbar.setPickOnBounds(false);

            toolbar.getChildren().add(createToolbarButton(i18n("button.refresh"), SVG::refresh, skinnable::refresh));
            toolbar.getChildren().add(createToolbarButton(i18n("mods.add"), SVG::plus, skinnable::add));
            toolbar.getChildren().add(createToolbarButton(i18n("button.remove"), SVG::delete, () -> {
                Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                    skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                }, null);
            }));
            toolbar.getChildren().add(createToolbarButton(i18n("mods.enable"), SVG::check, () ->
                    skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())));
            toolbar.getChildren().add(createToolbarButton(i18n("mods.disable"), SVG::close, () ->
                    skinnable.disableSelected(listView.getSelectionModel().getSelectedItems())));
            root.setTop(toolbar);
        }

        {
            SpinnerPane center = new SpinnerPane();
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            listView.setCellFactory(x -> new ModInfoListCell(listView));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            Bindings.bindContent(listView.getItems(), skinnable.getItems());

            center.setContent(listView);
            root.setCenter(center);
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
        private final ModInfo modInfo;
        private final String message;

        ModInfoObject(ModInfo modInfo) {
            this.modInfo = modInfo;
            this.active = modInfo.activeProperty();
            StringBuilder message = new StringBuilder(modInfo.getName());
            if (isNotBlank(modInfo.getVersion()))
                message.append(", ").append(i18n("archive.version")).append(": ").append(modInfo.getVersion());
            if (isNotBlank(modInfo.getGameVersion()))
                message.append(", ").append(i18n("archive.game_version")).append(": ").append(modInfo.getGameVersion());
            if (isNotBlank(modInfo.getAuthors()))
                message.append(", ").append(i18n("archive.author")).append(": ").append(modInfo.getAuthors());
            this.message = message.toString();
        }

        String getTitle() {
            return modInfo.getFileName();
        }

        String getSubtitle() {
            return message;
        }

        ModInfo getModInfo() {
            return modInfo;
        }

        @Override
        public int compareTo(@NotNull ModListPageSkin.ModInfoObject o) {
            return modInfo.getFileName().toLowerCase().compareTo(o.modInfo.getFileName().toLowerCase());
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

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

            JFXButton searchButton = new JFXButton();
            searchButton.getStyleClass().add("dialog-cancel");
            searchButton.setText(i18n("mods.mcmod.search"));
            searchButton.setOnAction(e -> {
                fireEvent(new DialogCloseEvent());
                FXUtils.openLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                        pair("key", modInfo.getModInfo().getName()),
                        pair("site", "all"),
                        pair("filter", "0")
                )));
            });

            if (StringUtils.isNotBlank(modInfo.getModInfo().getUrl())) {
                JFXButton officialPageButton = new JFXButton();
                officialPageButton.getStyleClass().add("dialog-cancel");
                officialPageButton.setText(i18n("mods.url"));
                officialPageButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(modInfo.getModInfo().getUrl());
                });

                setActions(okButton, officialPageButton, searchButton);
            } else {
                setActions(okButton, searchButton);
            }
        }
    }

    static class ModInfoListCell extends FloatListCell<ModInfoObject> {
        JFXCheckBox checkBox = new JFXCheckBox();
        TwoLineListItem content = new TwoLineListItem();
        JFXButton infoButton = new JFXButton();
        BooleanProperty booleanProperty;

        ModInfoListCell(JFXListView<ModInfoObject> listView) {
            super(listView);
            HBox container = new HBox(8);
            container.setAlignment(Pos.CENTER_LEFT);
            pane.getChildren().add(container);
            HBox.setHgrow(content, Priority.ALWAYS);

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(FXUtils.limitingSize(SVG.informationOutline(Theme.blackFillBinding(), 24, 24), 24, 24));

            container.getChildren().setAll(checkBox, content, infoButton);
        }

        @Override
        protected void updateControl(ModInfoObject dataItem, boolean empty) {
            if (empty) return;
            content.setTitle(dataItem.getTitle());
            content.setSubtitle(dataItem.getSubtitle());
            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.active);
            infoButton.setOnMouseClicked(e -> {
                Controllers.dialog(new ModInfoDialog(dataItem));
            });
        }
    }
}
