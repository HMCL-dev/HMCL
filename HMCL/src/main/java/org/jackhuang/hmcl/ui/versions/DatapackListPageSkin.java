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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.JFXCheckBoxTreeTableCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.ui.FXUtils.setupCellValueFactory;
import static org.jackhuang.hmcl.ui.FXUtils.wrapMargin;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class DatapackListPageSkin extends SkinBase<DatapackListPage> {

    DatapackListPageSkin(DatapackListPage skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();
        JFXTreeTableView<DatapackInfoObject> tableView = new JFXTreeTableView<>();

        {
            HBox toolbar = new HBox();
            toolbar.getStyleClass().add("jfx-tool-bar-second");
            JFXDepthManager.setDepth(toolbar, 1);
            toolbar.setPickOnBounds(false);

            toolbar.getChildren().add(createToolbarButton(i18n("button.refresh"), SVG::refresh, skinnable::refresh));
            toolbar.getChildren().add(createToolbarButton(i18n("datapack.add"), SVG::plus, skinnable::add));
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

            tableView.getStyleClass().addAll("no-header");
            tableView.setShowRoot(false);
            tableView.setEditable(true);
            tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            tableView.setRoot(new RecursiveTreeItem<>(skinnable.getItems(), RecursiveTreeObject::getChildren));

            JFXTreeTableColumn<DatapackInfoObject, Boolean> activeColumn = new JFXTreeTableColumn<>();
            setupCellValueFactory(activeColumn, DatapackInfoObject::activeProperty);
            activeColumn.setCellFactory(c -> new JFXCheckBoxTreeTableCell<>());
            activeColumn.setEditable(true);
            activeColumn.setMaxWidth(40);
            activeColumn.setMinWidth(40);

            JFXTreeTableColumn<DatapackInfoObject, Node> detailColumn = new JFXTreeTableColumn<>();
            setupCellValueFactory(detailColumn, DatapackInfoObject::nodeProperty);

            tableView.getColumns().setAll(activeColumn, detailColumn);

            tableView.setColumnResizePolicy(JFXTreeTableView.CONSTRAINED_RESIZE_POLICY);
            center.setContent(tableView);
            root.setCenter(center);
        }

        getChildren().setAll(root);
    }

    static class DatapackInfoObject extends RecursiveTreeObject<DatapackInfoObject> {
        private final BooleanProperty active;
        private final Datapack.Pack packInfo;
        private final ObjectProperty<Node> node;

        DatapackInfoObject(Datapack.Pack packInfo) {
            this.packInfo = packInfo;
            this.active = packInfo.activeProperty();
            this.node = new SimpleObjectProperty<>(wrapMargin(new TwoLineListItem(packInfo.getId(), StringUtils.parseColorEscapes(packInfo.getDescription())),
                    new Insets(8, 0, 8, 0)));
        }

        BooleanProperty activeProperty() {
            return active;
        }

        ObjectProperty<Node> nodeProperty() {
            return node;
        }

        Datapack.Pack getPackInfo() {
            return packInfo;
        }
    }
}
