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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.scene.Node;

/// @author Glavo
public class LinePane extends LineComponent {
    private static final String DEFAULT_STYLE_CLASS = "line-pane";

    public LinePane() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    private ObjectProperty<Node> right;

    public ObjectProperty<Node> rightProperty() {
        if (right == null) {
            right = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return LinePane.this;
                }

                @Override
                public String getName() {
                    return "right";
                }

                @Override
                protected void invalidated() {
                    setNode(IDX_TRAILING, get());
                }
            };
        }
        return right;
    }

    public Node getRight() {
        return rightProperty().get();
    }

    public void setRight(Node right) {
        rightProperty().set(right);
    }
}
