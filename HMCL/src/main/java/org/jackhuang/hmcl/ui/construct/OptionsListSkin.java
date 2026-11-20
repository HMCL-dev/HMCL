// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import org.glavo.m3fx.controls.M3ListView;

/// @author Glavo
public final class OptionsListSkin extends SkinBase<OptionsList> {

    private final M3ListView<OptionsList.Element> listView;
    private final ObjectBinding<ContentPaddings> contentPaddings;

    OptionsListSkin(OptionsList control) {
        super(control);

        this.listView = new M3ListView<>();
        Bindings.bindContent(listView.getItems(), control.getElements());
        listView.setCellFactory(Cell::new);

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

    private final class Cell extends GraphicListCell<OptionsList.Element> {
        private static final PseudoClass PSEUDO_CLASS_FIRST = PseudoClass.getPseudoClass("first");
        private static final PseudoClass PSEUDO_CLASS_LAST = PseudoClass.getPseudoClass("last");

        @SuppressWarnings("FieldCanBeLocal")
        private final InvalidationListener updateStyleListener = o -> updateStyle();

        private StackPane wrapper;

        public Cell(M3ListView<OptionsList.Element> listView) {
            super(listView);

            WeakInvalidationListener weakListener = new WeakInvalidationListener(updateStyleListener);
            listView.getItems().addListener(weakListener);
            itemProperty().addListener(weakListener);
            contentPaddings.addListener(weakListener);
        }

        @Override
        protected void updateItem(OptionsList.Element item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setRowGraphic(null);
            } else if (item instanceof OptionsList.ListElement element) {
                if (wrapper == null)
                    wrapper = createWrapper();
                else
                    wrapper.getStyleClass().remove("no-padding");

                Node node = element.getNode();
                if (node instanceof NoPaddingComponent || node.getProperties().containsKey("ComponentList.noPadding"))
                    wrapper.getStyleClass().add("no-padding");

                wrapper.getChildren().setAll(node);

                setRowGraphic(wrapper);
            } else {
                setRowGraphic(item.getNode());
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
