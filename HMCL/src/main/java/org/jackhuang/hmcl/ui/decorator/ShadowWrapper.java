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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Reserves transparent window space and renders a shadow around one content node.
///
/// The wrapper is intended to be used as a scene root. Its insets remain available for custom resize hit testing,
/// while its content is laid out inside those insets.
@NotNullByDefault
final class ShadowWrapper extends StackPane {
    /// The inner pane to which the drop-shadow effect is applied.
    private final StackPane shadowContainer;

    /// The content rendered inside [#shadowContainer], or `null` when empty.
    private final ObjectProperty<@Nullable Node> content = new ObjectPropertyBase<>() {
        /// {@inheritDoc}
        @Override
        public Object getBean() {
            return ShadowWrapper.this;
        }

        /// {@inheritDoc}
        @Override
        public String getName() {
            return "content";
        }

        /// Replaces the node hosted by the shadow container.
        @Override
        protected void invalidated() {
            @Nullable Node node = get();
            if (node == null) {
                shadowContainer.getChildren().clear();
            } else {
                shadowContainer.getChildren().setAll(node);
            }
        }
    };

    /// Creates an empty wrapper with the standard main-window shadow.
    ShadowWrapper() {
        setPadding(new Insets(Decorator.SHADOW_SIZE));
        setPickOnBounds(true);

        shadowContainer = new StackPane();
        shadowContainer.setEffect(new DropShadow(
                BlurType.ONE_PASS_BOX,
                Color.rgb(0, 0, 0, 0.4),
                10,
                0.3,
                0.0,
                0.0));
        getChildren().setAll(shadowContainer);
    }

    /// Returns the node rendered inside the shadow.
    ///
    /// @return the content node, or `null` when empty
    @Nullable Node getContent() {
        return content.get();
    }

    /// Replaces the node rendered inside the shadow.
    ///
    /// @param content the new content, or `null` to empty the wrapper
    void setContent(@Nullable Node content) {
        this.content.set(content);
    }

    /// Returns the mutable content property.
    ///
    /// @return the content property
    ObjectProperty<@Nullable Node> contentProperty() {
        return content;
    }
}
