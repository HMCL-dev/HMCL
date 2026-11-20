// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import org.glavo.m3fx.controls.M3ListCell;
import org.glavo.m3fx.controls.M3ListItem;
import org.glavo.m3fx.controls.M3ListView;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A virtualized Material row for launcher content that supplies its own layout and controls.
@NotNullByDefault
public abstract class GraphicListCell<T> extends M3ListCell<T> {
    /// Hosts the graphic for the data item currently assigned to this cell.
    private final StackPane content = new StackPane();

    /// Creates a cell for the supplied virtualized list.
    protected GraphicListCell(M3ListView<T> list) {
        super(list);
    }

    /// Keeps the Material row in the scene graph for logical focus and accessibility.
    @Override
    protected final M3ListItem createListItem() {
        var row = new M3ListItem();
        row.setSkin(new GraphicSkin(row, content));
        return row;
    }

    /// Each subclass updates its graphic when the cell is assigned a data item.
    @Override
    protected final void updateListItem(M3ListItem row, T item) {
    }

    /// Displays the supplied content, or releases it when the cell becomes empty.
    protected final void setRowGraphic(@Nullable Node graphic) {
        if (graphic == null)
            content.getChildren().clear();
        else if (content.getChildren().isEmpty() || content.getChildren().getFirst() != graphic)
            content.getChildren().setAll(graphic);
    }

    /// Sizes the supplied launcher content to the Material row.
    @NotNullByDefault
    private static final class GraphicSkin extends SkinBase<M3ListItem> {
        /// Installs the full-width graphic without intercepting its mouse input.
        GraphicSkin(M3ListItem row, StackPane content) {
            super(row);
            consumeMouseEvents(false);
            getChildren().add(content);
        }
    }
}
