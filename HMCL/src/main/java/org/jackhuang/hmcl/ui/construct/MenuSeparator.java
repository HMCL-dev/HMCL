// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.geometry.Insets;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.theme.Themes;

public class MenuSeparator extends StackPane {

    public MenuSeparator() {
        Rectangle rect = new Rectangle();
        rect.widthProperty().bind(widthProperty().add(-14));
        rect.setHeight(1);
        rect.fillProperty().bind(Themes.colorSchemeProperty().getOutlineVariant());
        maxHeightProperty().set(10);
        setPadding(new Insets(3));
        getChildren().setAll(rect);
    }
}
