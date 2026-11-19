// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.nbt;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.glavo.nbt.NBTElement;
import org.glavo.nbt.NBTParent;
import org.glavo.nbt.chunk.Chunk;
import org.glavo.nbt.tag.*;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Populates the children of an NBT node on first access.
public final class NBTTreeItem extends TreeItem<NBTTreeItem.Entry> {

    /// Creates a node with its NBT name.
    public NBTTreeItem(NBTElement value) {
        this(value, null);
    }

    /// Creates a node with an optional display name, such as the root file name.
    public NBTTreeItem(NBTElement value, @Nullable String overrideName) {
        super(new Entry(value, overrideName));
    }

    @Override
    public boolean isLeaf() {
        return !(getValue().element() instanceof NBTParent<?> parent) || parent.isEmpty();
    }

    private boolean needInitChildren = true;

    @Override
    public ObservableList<TreeItem<Entry>> getChildren() {
        ObservableList<TreeItem<Entry>> children = super.getChildren();
        if (needInitChildren) {
            needInitChildren = false;

            NBTElement element = getValue().element();
            if (element instanceof Chunk chunk) {
                if (chunk.getRootTag() != null) {
                    children.setAll(chunk.getRootTag().stream().map(NBTTreeItem::new).toList());
                }
            } else if (element instanceof NBTParent<?> parent) {
                children.setAll(parent.stream().map(NBTTreeItem::new).toList());
            }
        }
        return children;
    }

    /// Supplies the same NBT label for the tree cell and its full-text tooltip.
    public record Entry(NBTElement element, @Nullable String overrideName) {
        /// Formats a tag name or list index with its value or child count.
        @Override
        public String toString() {
            String name;
            if (overrideName != null) {
                name = overrideName;
            } else if (element instanceof Tag tag) {
                name = tag.getParent() instanceof ListTag<?> ? Integer.toString(tag.getIndex()) : tag.getName();
            } else if (element instanceof Chunk chunk) {
                name = "Chunk (" + chunk.getLocalX() + ", " + chunk.getLocalZ() + ")";
            } else {
                name = "";
            }
            if (element instanceof ParentTag<?> parent)
                return name + ": " + i18n("nbt.entries", parent.size());
            if (element instanceof ValueTag<?> tag)
                return name + ": " + tag.getAsString();
            return name;
        }
    }
}
