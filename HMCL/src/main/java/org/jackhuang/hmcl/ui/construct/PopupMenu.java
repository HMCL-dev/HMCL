/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;

public class PopupMenu extends Control {

    private final ObservableList<Node> content = FXCollections.observableArrayList();

    public PopupMenu() {
    }

    public ObservableList<Node> getContent() {
        return content;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new PopupMenuSkin();
    }

    private class PopupMenuSkin extends SkinBase<PopupMenu> {

        protected PopupMenuSkin() {
            super(PopupMenu.this);

            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToHeight(true);
            scrollPane.setFitToWidth(true);

            VBox content = new VBox();
            content.getStyleClass().add("menu");
            Bindings.bindContent(content.getChildren(), PopupMenu.this.getContent());
            scrollPane.setContent(content);

            FXUtils.smoothScrolling(scrollPane);

            getChildren().setAll(scrollPane);
        }
    }
}
