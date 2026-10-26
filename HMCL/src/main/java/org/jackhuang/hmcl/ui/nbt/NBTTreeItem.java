// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.nbt;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.glavo.nbt.NBTElement;
import org.glavo.nbt.NBTParent;
import org.glavo.nbt.chunk.Chunk;
import org.glavo.nbt.tag.*;
import org.jetbrains.annotations.Nullable;

/// @author Glavo
public final class NBTTreeItem extends TreeItem<NBTElement> {
    private final @Nullable String overrideName;

    public NBTTreeItem(NBTElement value) {
        this(value, null);
    }

    public NBTTreeItem(NBTElement value, @Nullable String overrideName) {
        super(value);
        this.overrideName = overrideName;
    }

    @Override
    public boolean isLeaf() {
        return !(getValue() instanceof NBTParent<?> parent) || parent.isEmpty();
    }

    private boolean needInitChildren = true;

    @Override
    public ObservableList<TreeItem<NBTElement>> getChildren() {
        ObservableList<TreeItem<NBTElement>> children = super.getChildren();
        if (needInitChildren) {
            needInitChildren = false;

            if (getValue() instanceof Chunk chunk) {
                if (chunk.getRootTag() != null) {
                    children.setAll(chunk.getRootTag().stream().map(NBTTreeItem::new).toList());
                }
            } else if (getValue() instanceof NBTParent<?> parent) {
                children.setAll(parent.stream().map(NBTTreeItem::new).toList());
            }
        }
        return children;
    }

    public @Nullable String getOverrideName() {
        return overrideName;
    }
}
