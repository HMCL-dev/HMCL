// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.nbt;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.glavo.m3fx.controls.M3TreeCell;
import org.glavo.nbt.NBTElement;
import org.glavo.nbt.chunk.Chunk;
import org.glavo.nbt.chunk.ChunkRegion;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.Nullable;

/// Displays a Material tree row with the icon for its NBT element type.
public final class NBTTreeCell extends M3TreeCell<NBTTreeItem.Entry> {

    private static @Nullable Image getIcon(NBTElement element) {
        if (element instanceof Tag tag) {
            TagType<?> type = tag.getType();
            if (type == TagType.BYTE) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Byte.png");
            } else if (type == TagType.SHORT) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Short.png");
            } else if (type == TagType.INT) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Int.png");
            } else if (type == TagType.LONG) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Long.png");
            } else if (type == TagType.FLOAT) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Float.png");
            } else if (type == TagType.DOUBLE) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Double.png");
            } else if (type == TagType.STRING) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_String.png");
            } else if (type == TagType.BYTE_ARRAY) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Byte_Array.png");
            } else if (type == TagType.INT_ARRAY) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Int_Array.png");
            } else if (type == TagType.LONG_ARRAY) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Long_Array.png");
            } else if (type == TagType.LIST) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_List.png");
            } else if (type == TagType.COMPOUND) {
                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Compound.png");
            } else {
                return null;
            }
        } else if (element instanceof ChunkRegion)
            return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_List.png");
        else if (element instanceof Chunk)
            return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Compound.png");
        else
            return null;
    }

    /// Reused type icon for the current row.
    private final ImageView imageView = new ImageView();

    /// Creates a row with a 16-pixel type icon.
    public NBTTreeCell() {
        imageView.setFitHeight(16);
        imageView.setFitWidth(16);
    }

    /// Updates the type icon when the Material row is assigned a different entry.
    @Override
    public void updateItem(NBTTreeItem.@Nullable Entry item, boolean empty) {
        super.updateItem(item, empty);
        imageView.setImage(empty || item == null ? null : getIcon(item.element()));
        setGraphic(imageView.getImage() == null ? null : imageView);
    }
}
