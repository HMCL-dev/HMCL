/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.effects.JFXDepthManager;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;

public abstract class FloatListCell<T> extends ListCell<T> {
    private final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    protected final StackPane pane = new StackPane();

    {
        setText(null);
        setGraphic(null);

        pane.getStyleClass().add("card");
        pane.setCursor(Cursor.HAND);
        setPadding(new Insets(9, 9, 0, 9));
        JFXDepthManager.setDepth(pane, 1);

        FXUtils.onChangeAndOperate(selectedProperty(), selected -> {
            pane.pseudoClassStateChanged(SELECTED, selected);
        });
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        updateControl(item, empty);
        if (empty) {
            setGraphic(null);
        } else {
            setGraphic(pane);
        }
    }

    protected abstract void updateControl(T dataItem, boolean empty);
}
