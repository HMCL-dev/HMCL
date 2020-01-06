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

import com.jfoenix.controls.JFXTreeTableColumn;
import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.mod.ModInfo;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.JFXCheckBoxTreeTableCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

import static org.jackhuang.hmcl.ui.FXUtils.setupCellValueFactory;
import static org.jackhuang.hmcl.ui.FXUtils.wrapMargin;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton;
import static org.jackhuang.hmcl.util.StringUtils.isNotBlank;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class ModListPageSkin extends SkinBase<ModListPage> {

    ModListPageSkin(ModListPage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.getStyleClass().addAll("notice-pane", "white-background");

        BorderPane root = new BorderPane();
        JFXTreeTableView<ModInfoObject> tableView = new JFXTreeTableView<>();

        {
            HBox toolbar = new HBox();
            toolbar.getStyleClass().add("jfx-tool-bar-second");
            JFXDepthManager.setDepth(toolbar, 1);
            toolbar.setPickOnBounds(false);

            toolbar.getChildren().add(createToolbarButton(i18n("button.refresh"), SVG::refresh, skinnable::refresh));
            toolbar.getChildren().add(createToolbarButton(i18n("mods.add"), SVG::plus, skinnable::add));
            toolbar.getChildren().add(createToolbarButton(i18n("mods.remove"), SVG::delete, () ->
                    skinnable.removeSelected(tableView.getSelectionModel().getSelectedItems())));
            toolbar.getChildren().add(createToolbarButton(i18n("mods.enable"), SVG::check, () ->
                    skinnable.enableSelected(tableView.getSelectionModel().getSelectedItems())));
            toolbar.getChildren().add(createToolbarButton(i18n("mods.disable"), SVG::close, () ->
                    skinnable.disableSelected(tableView.getSelectionModel().getSelectedItems())));
            root.setTop(toolbar);
        }

        {
            SpinnerPane center = new SpinnerPane();
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            tableView.getStyleClass().add("no-header");
            tableView.setShowRoot(false);
            tableView.setEditable(true);
            tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            tableView.setRoot(new RecursiveTreeItem<>(skinnable.getItems(), RecursiveTreeObject::getChildren));

            JFXTreeTableColumn<ModInfoObject, Boolean> activeColumn = new JFXTreeTableColumn<>();
            setupCellValueFactory(activeColumn, ModInfoObject::activeProperty);
            activeColumn.setCellFactory(c -> new JFXCheckBoxTreeTableCell<>());
            activeColumn.setEditable(true);
            activeColumn.setMaxWidth(40);
            activeColumn.setMinWidth(40);

            JFXTreeTableColumn<ModInfoObject, Node> detailColumn = new JFXTreeTableColumn<>();
            setupCellValueFactory(detailColumn, ModInfoObject::nodeProperty);

            tableView.getColumns().setAll(activeColumn, detailColumn);

            tableView.setColumnResizePolicy(JFXTreeTableView.CONSTRAINED_RESIZE_POLICY);
            center.setContent(tableView);
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

    static class ModInfoObject extends RecursiveTreeObject<ModInfoObject> {
        private final BooleanProperty active;
        private final ModInfo modInfo;
        private final ObjectProperty<Node> node;

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
            this.node = new SimpleObjectProperty<>(wrapMargin(new TwoLineListItem(modInfo.getFileName(), message.toString()), new Insets(8, 0, 8, 0)));
        }

        BooleanProperty activeProperty() {
            return active;
        }

        ObjectProperty<Node> nodeProperty() {
            return node;
        }

        ModInfo getModInfo() {
            return modInfo;
        }
    }
}
