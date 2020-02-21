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
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.FloatListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class DatapackListPageSkin extends SkinBase<DatapackListPage> {

    DatapackListPageSkin(DatapackListPage skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();
        JFXListView<DatapackInfoObject> listView = new JFXListView<>();

        {
            HBox toolbar = new HBox();
            toolbar.getStyleClass().add("jfx-tool-bar-second");
            JFXDepthManager.setDepth(toolbar, 1);
            toolbar.setPickOnBounds(false);

            toolbar.getChildren().add(createToolbarButton(i18n("button.refresh"), SVG::refresh, skinnable::refresh));
            toolbar.getChildren().add(createToolbarButton(i18n("datapack.add"), SVG::plus, skinnable::add));
            toolbar.getChildren().add(createToolbarButton(i18n("button.remove"), SVG::delete, () -> {
                Controllers.confirmDialog(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
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

            listView.setCellFactory(x -> new FloatListCell<DatapackInfoObject>() {
                JFXCheckBox checkBox = new JFXCheckBox();
                TwoLineListItem content = new TwoLineListItem();
                BooleanProperty booleanProperty;

                {
                    Region clippedContainer = (Region)listView.lookup(".clipped-container");
                    setPrefWidth(0);
                    HBox container = new HBox(8);
                    container.setPadding(new Insets(0, 0, 0, 6));
                    container.setAlignment(Pos.CENTER_LEFT);
                    pane.getChildren().add(container);
                    pane.setPadding(new Insets(8, 8, 8, 0));
                    if (clippedContainer != null) {
                        maxWidthProperty().bind(clippedContainer.widthProperty());
                        prefWidthProperty().bind(clippedContainer.widthProperty());
                        minWidthProperty().bind(clippedContainer.widthProperty());
                    }

                    container.getChildren().setAll(checkBox, content);
                }

                @Override
                protected void updateControl(DatapackInfoObject dataItem, boolean empty) {
                    if (empty) return;
                    content.setTitle(dataItem.getTitle());
                    content.setSubtitle(dataItem.getSubtitle());
                    if (booleanProperty != null) {
                        checkBox.selectedProperty().unbindBidirectional(booleanProperty);
                    }
                    checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.active);
                }
            });
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            Bindings.bindContent(listView.getItems(), skinnable.getItems());

            center.setContent(listView);
            root.setCenter(center);
        }

        getChildren().setAll(root);
    }

    static class DatapackInfoObject extends RecursiveTreeObject<DatapackInfoObject> {
        private final BooleanProperty active;
        private final Datapack.Pack packInfo;

        DatapackInfoObject(Datapack.Pack packInfo) {
            this.packInfo = packInfo;
            this.active = packInfo.activeProperty();
        }

        String getTitle() {
            return packInfo.getId();
        }

        String getSubtitle() {
            return StringUtils.parseColorEscapes(packInfo.getDescription());
        }

        Datapack.Pack getPackInfo() {
            return packInfo;
        }
    }
}
