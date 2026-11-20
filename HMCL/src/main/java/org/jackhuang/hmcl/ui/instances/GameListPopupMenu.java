// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.glavo.m3fx.controls.M3ListCell;
import org.glavo.m3fx.controls.M3ListItem;
import org.glavo.m3fx.controls.M3ListView;
import org.glavo.m3fx.controls.M3SelectionMode;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays game instances in a popup selection list.
///
/// @author Glavo
public final class GameListPopupMenu extends StackPane {

    /// Shows an instance selection popup relative to its owner.
    ///
    /// @param owner     the node used to position the popup
    /// @param alignment the sidebar corner where the menu opens
    /// @param instances the instances to copy into the popup, in display order
    public static void show(Node owner, Pos alignment, List<HMCLGameInstance> instances) {
        GameListPopupMenu menu = new GameListPopupMenu();
        menu.setInstances(instances);
        new PopupMenu(menu).showBeside(owner, alignment);
    }

    private final M3ListView<GameItem> listView = new M3ListView<>();
    private final BooleanBinding isEmpty = Bindings.isEmpty(listView.getItems());

    /// Creates a virtualized list that fires an action when the user chooses an instance.
    public GameListPopupMenu() {
        this.setMaxHeight(365);

        listView.setSelectionMode(M3SelectionMode.SINGLE);
        listView.setCellFactory(Cell::new);
        listView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                choose(listView.getFocusedItem(), listView);
                event.consume();
            }
        });

        listView.setFixedCellSize(72);
        listView.setPrefWidth(300);

        listView.prefHeightProperty().bind(Bindings.size(listView.getItems()).multiply(listView.fixedCellSizeProperty()));

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
        listView.selectIndex(index);
        listView.focusIndex(index);
        listView.requestFocus();
    }

    /// Applies a selection and lets the enclosing popup dismiss itself.
    private static void choose(@Nullable GameItem item, Node source) {
        if (item != null) {
            item.getRepository().setSelectedInstance(item.getGameInstance());
            source.fireEvent(new ActionEvent());
        }
    }

    private static final class Cell extends M3ListCell<GameItem> {

        /// Shows the instance icon for the current row.
        private final ImageContainer icon = new ImageContainer(32);

        /// Creates a row owned by the popup list.
        private Cell(M3ListView<GameItem> listView) {
            super(listView);
        }

        /// Chooses the current instance and lets the popup handle the resulting action.
        @Override
        protected M3ListItem createListItem() {
            var row = new M3ListItem();
            row.setLeading(icon);
            row.setOnAction(event -> choose(getItem(), getListView()));
            return row;
        }

        /// Binds the visible row to the instance currently assigned by the virtual flow.
        @Override
        protected void updateListItem(M3ListItem row, GameItem item) {
            icon.imageProperty().bind(item.imageProperty());
            row.headlineTextProperty().bind(item.titleProperty().orElse(""));
            row.supportingTextProperty().bind(item.subtitleProperty().orElse(""));
            row.trailingSupportingTextProperty().bind(item.tagProperty().orElse(""));
        }

        /// Releases bindings to instances that have left the list.
        @Override
        protected void updateItem(@Nullable GameItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty && getListItem() != null) {
                icon.imageProperty().unbind();
                getListItem().headlineTextProperty().unbind();
                getListItem().supportingTextProperty().unbind();
                getListItem().trailingSupportingTextProperty().unbind();
            }
        }
    }
}
