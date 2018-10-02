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

import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import javafx.beans.DefaultProperty;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

@DefaultProperty("content")
public class ComponentList extends Control {
    private final StringProperty title = new SimpleStringProperty(this, "title", "Group");
    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle", "");
    private final IntegerProperty depth = new SimpleIntegerProperty(this, "depth", 0);
    private boolean hasSubtitle = false;
    public final ObservableList<Node> content = FXCollections.observableArrayList();

    public ComponentList() {
        getStyleClass().add("options-list");
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

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new Skin(this);
    }

    protected static class Skin extends SkinBase<ComponentList> {
        private final ObservableList<Node> list;

        protected Skin(ComponentList control) {
            super(control);

            list = MappedObservableList.create(control.getContent(), ComponentListCell::new);
            ListFirstElementListener.observe(list,
                    first -> first.getStyleClass().setAll("options-list-item-ahead"),
                    last -> last.getStyleClass().setAll("options-list-item"));

            VBox vbox = new VBox();
            Bindings.bindContent(vbox.getChildren(), list);
            getChildren().setAll(vbox);
        }
    }
}
