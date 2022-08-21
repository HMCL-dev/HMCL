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

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.function.Consumer;

public class AdvancedListBox extends ScrollPane {
    private final VBox container = new VBox();

    {
        setContent(container);

        FXUtils.smoothScrolling(this);

        setFitToHeight(true);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        container.getStyleClass().add("advanced-list-box-content");
    }

    public AdvancedListBox add(Node child) {
        if (child instanceof Pane || child instanceof AdvancedListItem)
            container.getChildren().add(child);
        else {
            StackPane pane = new StackPane();
            pane.getStyleClass().add("advanced-list-box-item");
            pane.getChildren().setAll(child);
            container.getChildren().add(pane);
        }
        return this;
    }

    public AdvancedListBox addNavigationDrawerItem(Consumer<AdvancedListItem> fn) {
        AdvancedListItem item = new AdvancedListItem();
        item.getStyleClass().add("navigation-drawer-item");
        item.setActionButtonVisible(false);
        fn.accept(item);
        return add(item);
    }

    public AdvancedListBox add(int index, Node child) {
        if (child instanceof Pane || child instanceof AdvancedListItem)
            container.getChildren().add(index, child);
        else {
            StackPane pane = new StackPane();
            pane.getStyleClass().add("advanced-list-box-item");
            pane.getChildren().setAll(child);
            container.getChildren().add(index, pane);
        }
        return this;
    }

    public AdvancedListBox remove(Node child) {
        container.getChildren().remove(indexOf(child));
        return this;
    }

    public int indexOf(Node child) {
        if (child instanceof Pane) {
            return container.getChildren().indexOf(child);
        } else {
            for (int i = 0; i < container.getChildren().size(); ++i) {
                Node node = container.getChildren().get(i);
                if (node instanceof StackPane) {
                    ObservableList<Node> list = ((StackPane) node).getChildren();
                    if (list.size() == 1 && list.get(0) == child)
                        return i;
                }
            }
            return -1;
        }
    }

    public AdvancedListBox startCategory(String category) {
        return add(new ClassTitle(category));
    }

    public void setSpacing(double spacing) {
        container.setSpacing(spacing);
    }

    public void clear() {
        container.getChildren().clear();
    }
}
