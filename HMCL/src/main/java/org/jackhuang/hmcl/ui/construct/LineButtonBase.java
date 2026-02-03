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

import javafx.scene.layout.StackPane;

/// @author Glavo
public abstract class LineButtonBase extends StackPane implements LineComponent {

    private static final String DEFAULT_STYLE_CLASS = "line-button-base";

    protected final LineComponentContainer root = new LineComponentContainer() {
        @Override
        protected LineComponent getBean() {
            return LineButtonBase.this;
        }
    };

    protected final RipplerContainer ripplerContainer;

    public LineButtonBase() {
        this.getStyleClass().addAll(LineComponent.DEFAULT_STYLE_CLASS, LineButtonBase.DEFAULT_STYLE_CLASS);

        this.ripplerContainer = new RipplerContainer(root);
        this.getChildren().setAll(ripplerContainer);
    }

    @Override
    public LineComponentContainer getRoot() {
        return root;
    }
}
