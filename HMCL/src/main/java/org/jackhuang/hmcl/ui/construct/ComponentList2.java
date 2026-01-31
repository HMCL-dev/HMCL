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

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

// TODO: Rename

/// @author Glavo
public final class ComponentList2 extends Control {
    public ComponentList2() {
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ComponentList2Skin(this);
    }

    public sealed interface Element {
    }

    public static final class Title implements Element {
        private final @NotNull String title;

        private Label label;

        public Title(@NotNull String title, @Nullable Node leftNode) {
            this.title = title;
        }


        @Override
        public boolean equals(Object obj) {
            return this == obj
                    || obj instanceof Title that && Objects.equals(this.title, that.title);
        }

        @Override
        public int hashCode() {
            return title.hashCode();
        }

        @Override
        public String toString() {
            return "Title[%s]".formatted(title);
        }

    }

    public record ListElement(@NotNull Node node) implements Element {
    }
}
