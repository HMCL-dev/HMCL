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
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

public class ComponentList extends Control implements NoPaddingComponent {

    public ComponentList() {
        getStyleClass().add("options-list");
    }

    private final ObservableList<Node> content = FXCollections.observableArrayList();

    public ObservableList<Node> getContent() {
        return content;
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new Skin(this);
    }

    private static final class Skin extends ControlSkinBase<ComponentList> {
        private static final PseudoClass PSEUDO_CLASS_FIRST = PseudoClass.getPseudoClass("first");
        private static final PseudoClass PSEUDO_CLASS_LAST = PseudoClass.getPseudoClass("last");

        private final ObservableList<Node> list;

        Skin(ComponentList control) {
            super(control);

            list = MappedObservableList.create(control.getContent(), node -> {
                Pane wrapper;
                if (node instanceof ComponentSublist sublist) {
                    sublist.getStyleClass().remove("options-list");
                    sublist.getStyleClass().add("options-sublist");
                    wrapper = new ComponentSublistWrapper(sublist);
                } else {
                    wrapper = new StackPane(node);
                }

                wrapper.getStyleClass().add("options-list-item");

                if (node.getProperties().get("ComponentList.vgrow") instanceof Priority priority) {
                    VBox.setVgrow(wrapper, priority);
                }

                if (node instanceof NoPaddingComponent || node.getProperties().containsKey("ComponentList.noPadding")) {
                    wrapper.getStyleClass().add("no-padding");
                }
                return wrapper;
            });

            updateStyle();
            list.addListener((InvalidationListener) o -> updateStyle());

            VBox vbox = new VBox();
            vbox.setFillWidth(true);
            Bindings.bindContent(vbox.getChildren(), list);
            node = vbox;
        }

        private Node prevFirstItem;
        private Node prevLastItem;

        private void updateStyle() {
            Node firstItem;
            Node lastItem;

            if (list.isEmpty()) {
                firstItem = null;
                lastItem = null;
            } else {
                firstItem = list.get(0);
                lastItem = list.get(list.size() - 1);
            }

            if (firstItem != prevFirstItem) {
                if (prevFirstItem != null)
                    prevFirstItem.pseudoClassStateChanged(PSEUDO_CLASS_FIRST, false);
                if (firstItem != null)
                    firstItem.pseudoClassStateChanged(PSEUDO_CLASS_FIRST, true);
                prevFirstItem = firstItem;
            }

            if (lastItem != prevLastItem) {
                if (prevLastItem != null)
                    prevLastItem.pseudoClassStateChanged(PSEUDO_CLASS_LAST, false);
                if (lastItem != null)
                    lastItem.pseudoClassStateChanged(PSEUDO_CLASS_LAST, true);
                prevLastItem = lastItem;
            }
        }
    }

    public static Node createComponentListTitle(String title) {
        HBox node = new HBox();
        node.setAlignment(Pos.CENTER_LEFT);
        node.setPadding(new Insets(8, 0, 0, 0));
        {
            Label advanced = new Label(title);
            node.getChildren().setAll(advanced);
        }
        return node;
    }

    public static void setVgrow(Node node, Priority priority) {
        node.getProperties().put("ComponentList.vgrow", priority);
    }

    public static void setNoPadding(Node node) {
        node.getProperties().put("ComponentList.noPadding", true);
    }
}
