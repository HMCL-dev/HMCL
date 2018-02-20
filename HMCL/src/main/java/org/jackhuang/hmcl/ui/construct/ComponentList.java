/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.DefaultProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

@DefaultProperty("content")
public class ComponentList extends StackPane {
    private final VBox vbox = new VBox();
    private final StringProperty title = new SimpleStringProperty(this, "title", "Group");
    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle", "");
    private final IntegerProperty depth = new SimpleIntegerProperty(this, "depth", 0);
    private boolean hasSubtitle = false;
    public final ObservableList<Node> content = FXCollections.observableArrayList();

    public ComponentList() {
        getChildren().setAll(vbox);
        content.addListener((ListChangeListener<? super Node>) change -> {
            while (change.next()) {
                for (int i = change.getFrom(); i < change.getTo(); ++i)
                    addChildren(change.getList().get(i));
            }
        });
        getStyleClass().add("options-list");
    }

    public void addChildren(Node node) {
        if (node instanceof ComponentList) {
            node.getProperties().put("title", ((ComponentList) node).getTitle());
            node.getProperties().put("subtitle", ((ComponentList) node).getSubtitle());
        }
        StackPane child = new StackPane();
        child.getChildren().add(new ComponentListCell(node));
        if (vbox.getChildren().isEmpty())
            child.getStyleClass().add("options-list-item-ahead");
        else
            child.getStyleClass().add("options-list-item");
        vbox.getChildren().add(child);
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle.set(subtitle);
    }

    public int getDepth() {
        return depth.get();
    }

    public IntegerProperty depthProperty() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth.set(depth);
    }

    public boolean isHasSubtitle() {
        return hasSubtitle;
    }

    public void setHasSubtitle(boolean hasSubtitle) {
        this.hasSubtitle = hasSubtitle;
    }

    public ObservableList<Node> getContent() {
        return content;
    }
}
