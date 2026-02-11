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
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;

/// @author Glavo
public final class OptionsListSkin extends SkinBase<OptionsList> {

    private final JFXListView<OptionsList.Element> listView;
    private final ObjectBinding<ContentPaddings> contentPaddings;

    OptionsListSkin(OptionsList control) {
        super(control);

        this.listView = new JFXListView<>();
        listView.setItems(control.getElements());
        listView.setCellFactory(listView1 -> new Cell());

        this.contentPaddings = Bindings.createObjectBinding(() -> {
            Insets padding = control.getContentPadding();
            return padding == null ? ContentPaddings.EMPTY : new ContentPaddings(
                    new Insets(padding.getTop(), padding.getRight(), 0, padding.getLeft()),
                    new Insets(0, padding.getRight(), padding.getBottom(), padding.getLeft()),
                    new Insets(0, padding.getRight(), 0, padding.getLeft())
            );
        }, control.contentPaddingProperty());

        this.getChildren().setAll(listView);
    }

    private record ContentPaddings(Insets first, Insets last, Insets middle) {
        static final ContentPaddings EMPTY = new ContentPaddings(Insets.EMPTY, Insets.EMPTY, Insets.EMPTY);
    }

    private final class Cell extends ListCell<OptionsList.Element> {
        private static final PseudoClass PSEUDO_CLASS_FIRST = PseudoClass.getPseudoClass("first");
        private static final PseudoClass PSEUDO_CLASS_LAST = PseudoClass.getPseudoClass("last");

        @SuppressWarnings("FieldCanBeLocal")
        private final InvalidationListener updateStyleListener = o -> updateStyle();

        private StackPane wrapper;

        public Cell() {
            FXUtils.limitCellWidth(listView, this);

            WeakInvalidationListener weakListener = new WeakInvalidationListener(updateStyleListener);
            listView.itemsProperty().addListener((o, oldValue, newValue) -> {
                if (oldValue != null)
                    oldValue.removeListener(weakListener);
                if (newValue != null)
                    newValue.addListener(weakListener);

                weakListener.invalidated(o);
            });
            itemProperty().addListener(weakListener);
            contentPaddings.addListener(weakListener);
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
                if (node instanceof NoPaddingComponent || node.getProperties().containsKey("ComponentList.noPadding"))
                    wrapper.getStyleClass().add("no-padding");

                wrapper.getChildren().setAll(node);

                setGraphic(wrapper);
            } else {
                setGraphic(item.getNode());
            }

            updateStyle();
        }

        private StackPane createWrapper() {
            var wrapper = new StackPane();
            wrapper.setAlignment(Pos.CENTER_LEFT);
            wrapper.getStyleClass().add("options-list-item");
            updateStyle();
            return wrapper;
        }

        private void updateStyle() {
            OptionsList.Element item = getItem();
            int index = getIndex();
            ObservableList<OptionsList.Element> items = getListView().getItems();

            if (item == null || index < 0 || index >= items.size()) {
                this.setPadding(Insets.EMPTY);
                return;
            }

            boolean isFirst = index == 0;
            boolean isLast = index == items.size() - 1;

            ContentPaddings paddings = contentPaddings.get();
            if (isFirst) {
                this.setPadding(paddings.first);
            } else if (isLast) {
                this.setPadding(paddings.last);
            } else {
                this.setPadding(paddings.middle);
            }

            if (item instanceof OptionsList.ListElement && wrapper != null) {
                wrapper.pseudoClassStateChanged(PSEUDO_CLASS_FIRST, isFirst || !(items.get(index - 1) instanceof OptionsList.ListElement));
                wrapper.pseudoClassStateChanged(PSEUDO_CLASS_LAST, isLast || !(items.get(index + 1) instanceof OptionsList.ListElement));
            }
        }
    }

}
