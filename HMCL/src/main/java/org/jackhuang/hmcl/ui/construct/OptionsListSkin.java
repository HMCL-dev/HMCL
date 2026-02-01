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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXListView;
import javafx.beans.InvalidationListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;

/// @author Glavo
public final class OptionsListSkin extends SkinBase<OptionsList> {

    private final JFXListView<OptionsList.Element> listView;

    OptionsListSkin(OptionsList control) {
        super(control);

        this.listView = new JFXListView<>();
        listView.setItems(control.getElements());
        listView.setCellFactory(Cell::new);

        this.getChildren().setAll(listView);
    }

    private static final class Cell extends ListCell<OptionsList.Element> {
        private static final PseudoClass PSEUDO_CLASS_FIRST = PseudoClass.getPseudoClass("first");
        private static final PseudoClass PSEUDO_CLASS_LAST = PseudoClass.getPseudoClass("last");

        private StackPane wrapper;

        public Cell(ListView<OptionsList.Element> listView) {
            this.setPadding(Insets.EMPTY);
            FXUtils.limitCellWidth(listView, this);
        }

        @Override
        protected void updateItem(OptionsList.Element item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
            } else if (item instanceof OptionsList.ListElement element) {
                if (wrapper == null)
                    wrapper = createWrapper();
                else
                    wrapper.getStyleClass().remove("no-padding");

                Node node = element.getNode();
                if (node instanceof NoPadding || node.getProperties().containsKey("ComponentList.noPadding"))
                    wrapper.getStyleClass().add("no-padding");

                wrapper.getChildren().setAll(node);

                setGraphic(wrapper);
            } else {
                setGraphic(item.getNode());
            }
        }

        private StackPane createWrapper() {
            var wrapper = new StackPane();
            wrapper.setAlignment(Pos.CENTER_LEFT);
            wrapper.getStyleClass().add("options-list-item");

            InvalidationListener listener = ignored -> {
                OptionsList.Element item = getItem();
                int index = getIndex();
                if (!(item instanceof OptionsList.ListElement) || index < 0)
                    return;

                ObservableList<OptionsList.Element> items = getListView().getItems();

                wrapper.pseudoClassStateChanged(PSEUDO_CLASS_FIRST, index == 0 || !(items.get(index - 1) instanceof OptionsList.ListElement));
                wrapper.pseudoClassStateChanged(PSEUDO_CLASS_LAST, index == items.size() - 1 || !(items.get(index + 1) instanceof OptionsList.ListElement));
            };

            getListView().itemsProperty().addListener((o, oldValue, newValue) -> {
                if (oldValue != null)
                    oldValue.removeListener(listener);
                if (newValue != null)
                    newValue.addListener(listener);

                listener.invalidated(o);
            });
            itemProperty().addListener(listener);
            listener.invalidated(null);

            return wrapper;
        }
    }

}
