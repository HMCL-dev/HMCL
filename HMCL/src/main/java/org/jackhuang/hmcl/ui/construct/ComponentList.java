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

import javafx.beans.DefaultProperty;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.function.Consumer;

@DefaultProperty("content")
public class ComponentList extends Control {
    private final StringProperty title = new SimpleStringProperty(this, "title", "Group");
    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle", "");
    private final IntegerProperty depth = new SimpleIntegerProperty(this, "depth", 0);
    private boolean hasSubtitle = false;
    public final ObservableList<Node> content = FXCollections.observableArrayList();
    private boolean expanded = false;
    private Consumer<ComponentList> lazyInitializer;

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

    public void setLazyInitializer(Consumer<ComponentList> lazyInitializer) {
        this.lazyInitializer = lazyInitializer;
    }

    public void onExpand() {
        if (!expanded && lazyInitializer != null) {
            lazyInitializer.accept(this);
        }

        expanded = true;
    }

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new Skin(this);
    }

    protected static class Skin extends SkinBase<ComponentList> {
        private static final PseudoClass PSEUDO_CLASS_FIRST = PseudoClass.getPseudoClass("first");
        private static final PseudoClass PSEUDO_CLASS_LAST = PseudoClass.getPseudoClass("last");

        private final ObservableList<Node> list;
        private final ObjectBinding<Node> firstItem;
        private final ObjectBinding<Node> lastItem;

        protected Skin(ComponentList control) {
            super(control);

            list = MappedObservableList.create(control.getContent(), node -> {
                ComponentListCell cell = new ComponentListCell(node);
                cell.getStyleClass().add("options-list-item");
                if (node.getProperties().containsKey("vgrow")) {
                    VBox.setVgrow(cell, Priority.ALWAYS);
                }
                return cell;
            });

            firstItem = Bindings.valueAt(list, 0);
            firstItem.addListener((observable, oldValue, newValue) -> {
                if (newValue != null)
                    newValue.pseudoClassStateChanged(PSEUDO_CLASS_FIRST, true);
                if (oldValue != null)
                    oldValue.pseudoClassStateChanged(PSEUDO_CLASS_FIRST, false);
            });
            if (!list.isEmpty())
                list.get(0).pseudoClassStateChanged(PSEUDO_CLASS_FIRST, true);

            lastItem = Bindings.valueAt(list, Bindings.subtract(Bindings.size(list), 1));
            lastItem.addListener((observable, oldValue, newValue) -> {
                if (newValue != null)
                    newValue.pseudoClassStateChanged(PSEUDO_CLASS_LAST, true);
                if (oldValue != null)
                    oldValue.pseudoClassStateChanged(PSEUDO_CLASS_LAST, false);
            });
            if (!list.isEmpty())
                list.get(list.size() - 1).pseudoClassStateChanged(PSEUDO_CLASS_LAST, true);

            VBox vbox = new VBox();
            vbox.setFillWidth(true);
            Bindings.bindContent(vbox.getChildren(), list);
            getChildren().setAll(vbox);
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
}
