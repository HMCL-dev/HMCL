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
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// @author Glavo
public final class GameListPopupMenu extends StackPane {

    public static void show(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                            double initOffsetX, double initOffsetY,
                            HMCLGameRepository repository, List<GameInstanceManifest> versions) {
        GameListPopupMenu menu = new GameListPopupMenu();
        menu.getItems().setAll(versions.stream()
                .filter(it -> repository.hasInstance(it.getId()))
                .map(it -> new GameItem(repository, it.getId()))
                .toList());
        JFXPopup popup = new JFXPopup(menu);
        popup.show(owner, vAlign, hAlign, initOffsetX, initOffsetY);
    }

    private final JFXListView<GameItem> listView = new JFXListView<>();
    private final BooleanBinding isEmpty = Bindings.isEmpty(listView.getItems());

    public GameListPopupMenu() {
        this.setMaxHeight(365);
        this.getStyleClass().add("popup-menu-content");

        listView.setCellFactory(Cell::new);

        listView.setFixedCellSize(50);
        listView.setPrefWidth(300);

        listView.prefHeightProperty().bind(Bindings.size(getItems()).multiply(50).add(2));

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

        private final ImageContainer imageView;
        private final TwoLineListItem content;

        private final StringProperty tag = new SimpleStringProperty();

        public Cell(ListView<GameItem> listView) {
            this.setPadding(Insets.EMPTY);

            this.imageView = new ImageContainer(32);
            this.imageView.setMouseTransparent(true);
            BorderPane.setAlignment(imageView, Pos.CENTER);

            this.content = new TwoLineListItem();
            this.content.setMouseTransparent(true);
            FXUtils.onChangeAndOperate(tag, tag -> {
                content.getTags().clear();
                if (StringUtils.isNotBlank(tag)) {
                    content.addTag(tag);
                }
            });

            BorderPane container = new BorderPane();
            container.getStyleClass().add("container");
            container.setPickOnBounds(false);
            container.setLeft(imageView);
            container.setCenter(content);

            RipplerContainer ripplerContainer = new RipplerContainer(container);

            StackPane rootPane = new StackPane();
            rootPane.getStyleClass().add("advanced-list-item");
            rootPane.getChildren().setAll(ripplerContainer);
            rootPane.maxWidthProperty().bind(listView.widthProperty().subtract(5));

            FXUtils.onClicked(rootPane, () -> {
                GameItem item = getItem();
                if (item != null) {
                    item.getRepository().setSelectedInstance(item.getId());
                    if (getScene().getWindow() instanceof JFXPopup popup)
                        popup.hide();
                }
            });

            this.graphic = rootPane;
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
