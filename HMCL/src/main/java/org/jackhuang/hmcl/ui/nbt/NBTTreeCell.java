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

import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.glavo.nbt.NBTElement;
import org.glavo.nbt.chunk.Chunk;
import org.glavo.nbt.chunk.ChunkRegion;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class NBTTreeCell extends TreeCell<@Nullable NBTElement> {

    private static @Nullable Image getIcon(NBTElement element) {
        if (element instanceof Tag tag) {
            return switch (tag.getType()) {
                case BYTE -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Byte.png");
                case SHORT -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Short.png");
                case INT -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Int.png");
                case LONG -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Long.png");
                case FLOAT -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Float.png");
                case DOUBLE -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Double.png");
                case STRING -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_String.png");
                case BYTE_ARRAY -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Byte_Array.png");
                case INT_ARRAY -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Int_Array.png");
                case LONG_ARRAY -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Long_Array.png");
                case LIST -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_List.png");
                case COMPOUND -> FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Compound.png");
            };
        } else if (element instanceof ChunkRegion)
            return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_List.png");
        else if (element instanceof Chunk)
            return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Compound.png");
        else
            return null;
    }

    private final ImageView imageView;

    public NBTTreeCell() {
        imageView = new ImageView();
        imageView.setFitHeight(16);
        imageView.setFitWidth(16);
        setGraphic(imageView);
    }

    private NBTTreeItem getNBTTreeItem() {
        return (NBTTreeItem) getTreeItem();
    }

    private void setTagText(@Nullable String text) {
        String name = getNBTTreeItem().getOverrideName();
        if (name == null) {
            NBTElement value = getNBTTreeItem().getValue();

            if (value instanceof Tag tag) {
                if (tag.getParent() instanceof ListTag<?>) {
                    name = Integer.toString(tag.getIndex());
                } else {
                    name = tag.getName();
                }
            } else if (value instanceof Chunk chunk) {
                name = "Chunk (" + chunk.getLocalX() + ", " + chunk.getLocalZ() + ")";
            } else {
                name = "";
            }
        }


        if (text == null) {
            setText(name);
        } else {
            setText(name + ": " + text);
        }
    }

    private void setTagText(int nEntries) {
        setTagText(i18n("nbt.entries", nEntries));
    }

    @Override
    public void updateItem(@Nullable NBTElement item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            imageView.setImage(null);
            setText(null);
            return;
        }

        imageView.setImage(getIcon(item));

        if (item instanceof ArrayTag<?> arrayTag) {
            setTagText(arrayTag.size());
        } else if (item instanceof ParentTag<?> parentTag) {
            setTagText(parentTag.size());
        } else if (item instanceof ValueTag<?> valueTag) {
            setTagText(valueTag.getAsString());
        } else {
            setTagText(null);
        }
    }
}
