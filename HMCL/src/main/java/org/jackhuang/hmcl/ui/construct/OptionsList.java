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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

// TODO: Rename

/// @author Glavo
public final class OptionsList extends Control {
    public OptionsList() {
        this.getStyleClass().add("options-list");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new OptionsListSkin(this);
    }

    private final ObservableList<Element> elements = FXCollections.observableArrayList();

    public ObservableList<Element> getElements() {
        return elements;
    }

    public void addTitle(String title) {
        elements.add(new Title(title));
    }

    public void addElement(Node node) {
        elements.add(new NodeElement(node));
    }

    public void addListElement(@NotNull Node node) {
        elements.add(new ListElement(node));
    }

    public void addListElements(@NotNull Node... nodes) {
        for (Node node : nodes) {
            elements.add(new ListElement(node));
        }
    }

    public static abstract class Element {
        protected Node node;

        Node getNode() {
            if (node == null)
                node = createNode();
            return node;
        }

        protected abstract Node createNode();
    }

    public static final class Title extends Element {
        private final @NotNull String title;

        public Title(@NotNull String title) {
            this.title = title;
        }

        @Override
        protected Node createNode() {
            HBox node = new HBox();
            node.setAlignment(Pos.CENTER_LEFT);
            node.setPadding(new Insets(8, 0, 8, 0));

            Label advanced = new Label(title);
            node.getChildren().setAll(advanced);
            return node;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Title that && Objects.equals(this.title, that.title);
        }

        @Override
        public int hashCode() {
            return title.hashCode();
        }

        @Override
        public String toString() {
            return "Title[%s]".formatted(title);
        }

    }

    public static final class NodeElement extends Element {
        public NodeElement(@NotNull Node node) {
            this.node = node;
        }

        @Override
        protected Node createNode() {
            return node;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof NodeElement that && this.node.equals(that.node);
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }

        @Override
        public String toString() {
            return "NodeElement[node=%s]".formatted(node);
        }
    }

    public static final class ListElement extends Element {
        public ListElement(@NotNull Node node) {
            this.node = node;
        }

        @Override
        protected Node createNode() {
            return node;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ListElement that && this.node.equals(that.node);
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }

        @Override
        public String toString() {
            return "ListElement[node=%s]".formatted(node);
        }
    }
}
