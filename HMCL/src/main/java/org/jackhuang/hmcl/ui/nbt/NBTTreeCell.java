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

import java.util.EnumMap;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class NBTTreeCell extends TreeCell<NBTElement> {

    private static final EnumMap<TagType, Image> icons = new EnumMap<>(TagType.class);

    private static Image getIcon(NBTElement element) {
        if (element instanceof Tag tag) {
            return icons.computeIfAbsent(tag.getType(), type -> {
                String tagName;

                int idx = type.name().indexOf('_');
                if (idx < 0) {
                    tagName = type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT);
                } else {
                    tagName = type.name().charAt(0) + type.name().substring(1, idx + 1).toLowerCase(Locale.ROOT)
                            + type.name().charAt(idx + 1) + type.name().substring(idx + 2).toLowerCase(Locale.ROOT);
                }

                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_" + tagName + ".png");
            });
        } else if (element instanceof ChunkRegion)
            return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_List.png");
        else if (element instanceof Chunk)
            return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Compound.png");
        else
            return null;
    }

    private void setTagText(String text) {
        String name = ((NBTTreeItem) getTreeItem()).getName();

        if (name == null) {
            setText(text);
        } else if (text == null) {
            setText(name);
        } else {
            setText(name + ": " + text);
        }
    }

    private void setTagText(int nEntries) {
        setTagText(i18n("nbt.entries", nEntries));
    }

    private NBTTreeItem getNBTTreeItem() {
        return (NBTTreeItem) getTreeItem();
    }

    @Override
    public void updateItem(NBTElement item, boolean empty) {
        super.updateItem(item, empty);

        ImageView imageView = (ImageView) this.getGraphic();
        if (imageView == null) {
            imageView = new ImageView();
            this.setGraphic(imageView);
        }

        if (empty || item == null) {
            imageView.setImage(null);
            setText(null);
            return;
        }

        imageView.setImage(getIcon(item));
        imageView.setFitHeight(16);
        imageView.setFitWidth(16);

        if (getNBTTreeItem().getText() != null) {
            setText(getNBTTreeItem().getText());
        } else {
            if (item instanceof ValueTag<?> valueTag) {
                setTagText(valueTag.getValue().toString());
            } else if (item instanceof ArrayTag<?> arrayTag) {
                setTagText(arrayTag.size());
            } else if (item instanceof ParentTag<?> parentTag) {
                setTagText(parentTag.size());
            } else {
                setTagText(null);
            }
        }
    }
}
