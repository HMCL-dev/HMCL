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

import com.jfoenix.controls.JFXListView;
import javafx.css.PseudoClass;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jackhuang.hmcl.ui.FXUtils;

public abstract class MDListCell<T> extends ListCell<T> {
    private final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final StackPane container = new StackPane();
    private final StackPane root = new StackPane();
    private final MutableObject<Object> lastCell;

    public MDListCell(JFXListView<T> listView, MutableObject<Object> lastCell) {
        this.lastCell = lastCell;

        setText(null);
        setGraphic(null);

        root.getStyleClass().add("md-list-cell");
        RipplerContainer ripplerContainer = new RipplerContainer(container);
        root.getChildren().setAll(ripplerContainer);

        Region clippedContainer = (Region) listView.lookup(".clipped-container");
        setPrefWidth(0);
        if (clippedContainer != null) {
            maxWidthProperty().bind(clippedContainer.widthProperty());
            prefWidthProperty().bind(clippedContainer.widthProperty());
            minWidthProperty().bind(clippedContainer.widthProperty());
        }
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        // https://mail.openjdk.org/pipermail/openjfx-dev/2022-July/034764.html
        if (lastCell != null) {
            if (this == lastCell.getValue() && !isVisible())
                return;
            lastCell.setValue(this);
        }

        updateControl(item, empty);
        if (empty) {
            setGraphic(null);
        } else {
            setGraphic(root);
        }
    }

    protected StackPane getContainer() {
        return container;
    }

    protected void setSelectable() {
        FXUtils.onChangeAndOperate(selectedProperty(), selected -> {
            root.pseudoClassStateChanged(SELECTED, selected);
        });
    }

    protected abstract void updateControl(T item, boolean empty);
}
