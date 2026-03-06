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
package org.jackhuang.hmcl.ui.nbt;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.glavo.nbt.NBTElement;
import org.glavo.nbt.NBTParent;
import org.glavo.nbt.chunk.Chunk;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// @author Glavo
public final class NBTTreeItem extends TreeItem<NBTElement> {
    private final String name;
    private String text;

    public NBTTreeItem(NBTElement value) {
        this(value, null);
    }

    public NBTTreeItem(NBTElement value, @Nullable String name) {
        super(value);
        this.name = name;

        FXUtils.onChangeAndOperate(expandedProperty(), expanded -> {
            if (expanded && getChildren().size() == 1)
                getChildren().get(0).setExpanded(true);
        });
    }

    @Override
    public boolean isLeaf() {
        return !(getValue() instanceof NBTParent<?> parent) || parent.isEmpty();
    }

    private boolean isFirstTimeChildren = true;

    @Override
    public ObservableList<TreeItem<NBTElement>> getChildren() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;

            List<NBTTreeItem> children;
            if (getValue() instanceof Chunk chunk) {
                children = chunk.getRootTag() != null
                        ? chunk.getRootTag().stream().map(NBTTreeItem::new).toList()
                        : List.of();
            } else if (getValue() instanceof NBTParent<?> parent) {
                children = parent.stream().map(NBTTreeItem::new).toList();
            } else {
                children = List.of();
            }

            super.getChildren().setAll(children);
        }
        return super.getChildren();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getName() {
        if (name != null) {
            return name;
        }
        NBTElement value = getValue();

        if (value instanceof Tag tag) {
            if (tag.getParent() instanceof ListTag<?>) {
                return Integer.toString(tag.getIndex());
            } else {
                return tag.getName();
            }
        } else if (value instanceof Chunk chunk) {
            return "Chunk (" + chunk.getLocalX() + ", " + chunk.getLocalZ() + ")";
        } else {
            return "";
        }

    }
}
