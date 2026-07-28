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
package org.jackhuang.hmcl.ui.decorator;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Nullable;

/// A container that wraps the root node, used to replace the system window decoration and render shadows outside the window.
final class ShadowWrapper extends StackPane {
    private final StackPane shadowContainer;

    private final ObjectProperty<@Nullable Node> content = new ObjectPropertyBase<>() {
        @Override
        public Object getBean() {
            return ShadowWrapper.this;
        }

        @Override
        public String getName() {
            return "content";
        }

        @Override
        protected void invalidated() {
            @Nullable Node node = get();
            if (node != null)
                shadowContainer.getChildren().setAll(node);
            else
                shadowContainer.getChildren().clear();
        }
    };

    ShadowWrapper() {
        this.setPadding(new Insets(Decorator2.SHADOW_SIZE));

        this.shadowContainer = new StackPane();
        shadowContainer.setEffect(new DropShadow(
                BlurType.ONE_PASS_BOX,
                Color.rgb(0, 0, 0, 0.4),
                10, 0.3,
                0.0, 0.0));
        this.getChildren().setAll(shadowContainer);
    }

    public @Nullable Node getContent() {
        return content.get();
    }

    public void setContent(@Nullable Node value) {
        content.set(value);
    }

    public ObjectProperty<@Nullable Node> contentProperty() {
        return content;
    }
}
