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

// TODO: Rename

import com.jfoenix.controls.JFXListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.SkinBase;

/// @author Glavo
public final class ComponentList2Skin extends SkinBase<ComponentList2> {

    private final JFXListView<ComponentList2.Element> listView;

    ComponentList2Skin(ComponentList2 control) {
        super(control);

        this.listView = new JFXListView<>();
        listView.setCellFactory(listView -> new Cell());

        this.getChildren().setAll(listView);
    }

    private static final class Cell extends ListCell<ComponentList2.Element> {
        @Override
        protected void updateItem(ComponentList2.Element item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else if (item instanceof ComponentList2.Title title) {
                // TODO
            } else if (item instanceof ComponentList2.ListElement element) {
                setGraphic(element.node()); // TODO
            }
        }
    }
}
