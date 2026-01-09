/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// @author Glavo
public final class GameListPopupMenu extends StackPane {
    private final JFXListView<GameItem> listView = new JFXListView<>();
    private final BooleanBinding isEmpty = Bindings.isEmpty(listView.getItems());

    public GameListPopupMenu() {
        this.setMaxHeight(365);
        this.getStyleClass().add("popup-menu-content");

        listView.setCellFactory(listView -> new Cell());
        listView.setFixedCellSize(60);
        listView.setPrefWidth(300);

        listView.prefHeightProperty().bind(Bindings.size(getItems()).multiply(60).add(2));

        Label placeholder = new Label(i18n("version.empty"));
        placeholder.setStyle("-fx-padding: 10px; -fx-text-fill: -monet-on-surface-variant; -fx-font-style: italic;");

        FXUtils.onChangeAndOperate(isEmpty, empty -> {
            getChildren().setAll(empty ? placeholder : listView);
        });
    }

    public ObservableList<GameItem> getItems() {
        return listView.getItems();
    }

    private static final class Cell extends ListCell<GameItem> {

        private final Region graphic;

        private final ImageView imageView;
        private final TwoLineListItem content;

        private final StringProperty tag = new SimpleStringProperty();

        public Cell() {
            this.setPadding(Insets.EMPTY);
            HBox root = new HBox();

            root.setSpacing(8);
            root.setAlignment(Pos.CENTER_LEFT);

            StackPane imageViewContainer = new StackPane();
            FXUtils.setLimitWidth(imageViewContainer, 32);
            FXUtils.setLimitHeight(imageViewContainer, 32);

            this.imageView = new ImageView();
            FXUtils.limitSize(imageView, 32, 32);
            imageViewContainer.getChildren().setAll(imageView);

            this.content = new TwoLineListItem();
            FXUtils.onChangeAndOperate(tag, tag -> {
                content.getTags().clear();
                if (StringUtils.isNotBlank(tag)) {
                    content.addTag(tag);
                }
            });
            BorderPane.setAlignment(content, Pos.CENTER);
            root.getChildren().setAll(imageView, content);

            StackPane pane = new StackPane();
            pane.getChildren().setAll(root);
            pane.getStyleClass().add("menu-container");
            root.setMouseTransparent(true);

            RipplerContainer ripplerContainer = new RipplerContainer(pane);
            FXUtils.onClicked(ripplerContainer, () -> {
                GameItem item = getItem();
                if (item != null) {
                    item.getProfile().setSelectedVersion(item.getId());
                    if (getScene().getWindow() instanceof JFXPopup popup)
                        popup.hide();
                }
            });
            this.graphic = ripplerContainer;
        }

        @Override
        protected void updateItem(GameItem item, boolean empty) {
            super.updateItem(item, empty);

            this.imageView.imageProperty().unbind();
            this.content.titleProperty().unbind();
            this.content.subtitleProperty().unbind();
            this.tag.unbind();

            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(this.graphic);

                this.imageView.imageProperty().bind(item.imageProperty());
                this.content.titleProperty().bind(item.titleProperty());
                this.content.subtitleProperty().bind(item.subtitleProperty());
                this.tag.bind(item.tagProperty());
            }
        }
    }
}
