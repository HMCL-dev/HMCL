/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import org.jackhuang.hmcl.mod.ModInfo;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.JFXCheckBoxTreeTableCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import java.util.function.Function;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModListPageSkin extends SkinBase<ModListPage> {

    private static Node wrap(Node node) {
        StackPane stackPane = new StackPane();
        stackPane.setPadding(new Insets(0, 5, 0, 2));
        stackPane.getChildren().setAll(node);
        return stackPane;
    }

    public ModListPageSkin(ModListPage skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();
        JFXTreeTableView<ModInfoObject> tableView = new JFXTreeTableView<>();

        {
            HBox toolbar = new HBox();
            toolbar.getStyleClass().setAll("jfx-tool-bar-second");
            JFXDepthManager.setDepth(toolbar, 1);
            toolbar.setPickOnBounds(false);

            JFXButton btnRefresh = new JFXButton();
            btnRefresh.getStyleClass().add("jfx-tool-bar-button");
            btnRefresh.textFillProperty().bind(Theme.foregroundFillBinding());
            btnRefresh.setGraphic(wrap(SVG.refresh(Theme.foregroundFillBinding(), -1, -1)));
            btnRefresh.setText(i18n("button.refresh"));
            btnRefresh.setOnMouseClicked(e -> skinnable.refresh());
            toolbar.getChildren().add(btnRefresh);

            JFXButton btnAddMod = new JFXButton();
            btnAddMod.getStyleClass().add("jfx-tool-bar-button");
            btnAddMod.textFillProperty().bind(Theme.foregroundFillBinding());
            btnAddMod.setGraphic(wrap(SVG.plus(Theme.foregroundFillBinding(), -1, -1)));
            btnAddMod.setText(i18n("mods.add"));
            btnAddMod.setOnMouseClicked(e -> skinnable.add());
            toolbar.getChildren().add(btnAddMod);

            JFXButton btnRemove = new JFXButton();
            btnRemove.getStyleClass().add("jfx-tool-bar-button");
            btnRemove.textFillProperty().bind(Theme.foregroundFillBinding());
            btnRemove.setGraphic(wrap(SVG.delete(Theme.foregroundFillBinding(), -1, -1)));
            btnRemove.setText(i18n("mods.remove"));
            btnRemove.setOnMouseClicked(e -> skinnable.removeSelectedMods(tableView.getSelectionModel().getSelectedItems()));
            toolbar.getChildren().add(btnRemove);

            JFXButton btnEnable = new JFXButton();
            btnEnable.getStyleClass().add("jfx-tool-bar-button");
            btnEnable.textFillProperty().bind(Theme.foregroundFillBinding());
            btnEnable.setGraphic(wrap(SVG.check(Theme.foregroundFillBinding(), -1, -1)));
            btnEnable.setText(i18n("mods.enable"));
            btnEnable.setOnMouseClicked(e -> skinnable.enableSelectedMods(tableView.getSelectionModel().getSelectedItems()));
            toolbar.getChildren().add(btnEnable);

            JFXButton btnDisable = new JFXButton();
            btnDisable.getStyleClass().add("jfx-tool-bar-button");
            btnDisable.textFillProperty().bind(Theme.foregroundFillBinding());
            btnDisable.setGraphic(wrap(SVG.close(Theme.foregroundFillBinding(), -1, -1)));
            btnDisable.setText(i18n("mods.disable"));
            btnDisable.setOnMouseClicked(e -> skinnable.disableSelectedMods(tableView.getSelectionModel().getSelectedItems()));
            toolbar.getChildren().add(btnDisable);

            root.setTop(toolbar);
        }

        {
            SpinnerPane center = new SpinnerPane();
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

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

            JFXTreeTableColumn<ModInfoObject, String> fileNameColumn = new JFXTreeTableColumn<>();
            fileNameColumn.setText(i18n("archive.name"));
            setupCellValueFactory(fileNameColumn, ModInfoObject::fileNameProperty);
            fileNameColumn.prefWidthProperty().bind(tableView.widthProperty().subtract(40).multiply(0.8));

            JFXTreeTableColumn<ModInfoObject, String> versionColumn = new JFXTreeTableColumn<>();
            versionColumn.setText(i18n("archive.version"));
            versionColumn.setPrefWidth(100);
            setupCellValueFactory(versionColumn, ModInfoObject::versionProperty);
            versionColumn.prefWidthProperty().bind(tableView.widthProperty().subtract(40).multiply(0.2));

            tableView.getColumns().setAll(activeColumn, fileNameColumn, versionColumn);

            tableView.setColumnResizePolicy(JFXTreeTableView.CONSTRAINED_RESIZE_POLICY);
            center.setContent(tableView);
            root.setCenter(center);
        }

        getChildren().setAll(root);
    }

    private <T> void setupCellValueFactory(JFXTreeTableColumn<ModInfoObject, T> column, Function<ModInfoObject, ObservableValue<T>> mapper) {
        column.setCellValueFactory(new Callback<TreeTableColumn.CellDataFeatures<ModInfoObject, T>, ObservableValue<T>>() {
            @Override
            public ObservableValue<T> call(TreeTableColumn.CellDataFeatures<ModInfoObject, T> param) {
                if (column.validateValue(param))
                    return mapper.apply(param.getValue().getValue());
                else
                    return column.getComputedValue(param);
            }
        });
    }

    public static class ModInfoObject extends RecursiveTreeObject<ModInfoObject> {
        private final BooleanProperty active;
        private final StringProperty fileName;
        private final StringProperty version;
        private final ModInfo modInfo;

        public ModInfoObject(ModInfo modInfo) {
            this.modInfo = modInfo;
            this.active = modInfo.activeProperty();
            this.fileName = new SimpleStringProperty(modInfo.getFileName());
            this.version = new SimpleStringProperty(modInfo.getVersion());
        }

        public BooleanProperty activeProperty() {
            return active;
        }

        public StringProperty fileNameProperty() {
            return fileName;
        }

        public StringProperty versionProperty() {
            return version;
        }

        public ModInfo getModInfo() {
            return modInfo;
        }
    }
}
