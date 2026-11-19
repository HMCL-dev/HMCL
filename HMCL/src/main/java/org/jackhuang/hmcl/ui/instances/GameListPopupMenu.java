// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.WindowEvent;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.Tag;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays game instances in a popup selection list.
///
/// @author Glavo
public final class GameListPopupMenu extends StackPane {

    /// Identifies the instance popup currently attached to an owner.
    private static final String KEY = GameListPopupMenu.class.getName() + ".popup";

    /// Shows an instance selection popup relative to its owner.
    ///
    /// @param owner       the node used to position the popup
    /// @param vAlign      the popup's vertical alignment relative to `owner`
    /// @param hAlign      the popup's horizontal alignment relative to `owner`
    /// @param initOffsetX the horizontal offset from the aligned position
    /// @param initOffsetY the vertical offset from the aligned position
    /// @param instances   the instances to copy into the popup, in display order
    public static void show(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                            double initOffsetX, double initOffsetY,
                            List<HMCLGameInstance> instances) {
        @Nullable JFXPopup previous = (JFXPopup) owner.getProperties().get(KEY);
        if (previous != null && previous.isShowing()) {
            previous.hide();
            return;
        }

        GameListPopupMenu menu = new GameListPopupMenu();
        menu.setInstances(instances);
        JFXPopup popup = new JFXPopup(menu);
        menu.addEventHandler(ActionEvent.ACTION, event -> popup.hide());
        owner.getProperties().put(KEY, popup);
        popup.addEventFilter(WindowEvent.WINDOW_HIDDEN, event -> owner.getProperties().remove(KEY, popup));
        popup.show(owner, vAlign, hAlign, initOffsetX, initOffsetY, true);
    }

    private final JFXListView<GameItem> listView = new JFXListView<>();
    private final BooleanBinding isEmpty = Bindings.isEmpty(listView.getItems());

    /// Creates a virtualized list that fires an action when the user chooses an instance.
    public GameListPopupMenu() {
        this.setMaxHeight(365);
        this.getStyleClass().add("popup-menu-content");

        listView.setCellFactory(Cell::new);
        listView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                choose(listView.getSelectionModel().getSelectedItem(), listView);
                event.consume();
            }
        });

        listView.setFixedCellSize(50);
        listView.setPrefWidth(300);

        listView.prefHeightProperty().bind(Bindings.size(listView.getItems()).multiply(50).add(2));

        Label placeholder = new Label(i18n("instance.empty"));
        placeholder.setStyle("-fx-padding: 10px; -fx-text-fill: -monet-on-surface-variant; -fx-font-style: italic;");

        FXUtils.onChangeAndOperate(isEmpty, empty -> getChildren().setAll(empty ? placeholder : listView));
    }

    /// Replaces the instances in display order.
    public void setInstances(List<HMCLGameInstance> instances) {
        listView.getItems().setAll(instances.stream().map(GameItem::new).toList());
    }

    /// Focuses the list at the current instance for keyboard selection.
    public void focusInstance(@Nullable HMCLGameInstance instance) {
        if (listView.getItems().isEmpty())
            return;

        int index = Math.max(0, Lang.indexWhere(listView.getItems(),
                item -> item.getGameInstance() == instance));
        listView.getSelectionModel().select(index);
        listView.scrollTo(index);
        listView.requestFocus();
    }

    /// Applies a selection and lets the enclosing popup dismiss itself.
    private static void choose(@Nullable GameItem item, Node source) {
        if (item != null) {
            item.getRepository().setSelectedInstance(item.getGameInstance());
            source.fireEvent(new ActionEvent());
        }
    }

    private static final class Cell extends ListCell<GameItem> {

        private final Region graphic;
        private final RipplerContainer ripplerContainer;

        private final ImageContainer imageView;
        private final TwoLineListItem content;

        private final StringProperty tag = new SimpleStringProperty();

        /// Creates a popup row with a leading icon and the shared advanced-item text styling.
        public Cell(ListView<GameItem> listView) {
            this.setPadding(Insets.EMPTY);

            this.imageView = new ImageContainer(32);
            this.imageView.setMouseTransparent(true);

            this.content = new TwoLineListItem();
            content.getStyleClass().add("container");
            content.setLeading(imageView);
            FXUtils.onChangeAndOperate(tag, tag -> {
                if (StringUtils.isNotBlank(tag))
                    content.getTags().setAll(new Tag(tag));
                else
                    content.getTags().clear();
            });

            this.ripplerContainer = new RipplerContainer(content);

            StackPane rootPane = new StackPane();
            rootPane.getStyleClass().add("advanced-list-item");
            rootPane.getChildren().setAll(ripplerContainer);
            rootPane.maxWidthProperty().bind(listView.widthProperty().subtract(5));

            FXUtils.onClicked(rootPane, () -> choose(getItem(), rootPane));

            this.graphic = rootPane;
        }

        @Override
        protected void updateItem(GameItem item, boolean empty) {
            GameItem oldItem = getItem();
            boolean oldEmpty = isEmpty();

            super.updateItem(item, empty);

            if (oldItem == item && oldEmpty == empty) return;

            this.ripplerContainer.resetFeedback();
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
