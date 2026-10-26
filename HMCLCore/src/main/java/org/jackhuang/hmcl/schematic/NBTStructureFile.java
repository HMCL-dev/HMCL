// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.schematic;

import org.glavo.nbt.tag.CompoundTag;
import org.glavo.nbt.tag.ListTag;
import org.glavo.nbt.tag.Tag;
import org.jackhuang.hmcl.util.NBTUtils;
import org.jackhuang.hmcl.util.Point3I;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.NBTUtils.tryGetInt;

/// @author Calboot
/// @see <a href="https://minecraft.wiki/w/Structure_file">Structure File</a>
public final class NBTStructureFile extends Schematic {

    public static NBTStructureFile load(Path file) throws IOException {

        CompoundTag root = NBTUtils.readCompressed(file);

        if (!(root.get("blocks") instanceof ListTag<?>))
            throw new IOException("blocks tag not found");
        if (!(root.get("entities") instanceof ListTag<?>))
            throw new IOException("entities tag not found");

        Tag sizeTag = root.get("size");
        if (sizeTag == null)
            throw new IOException("size tag not found");
        else if (!(sizeTag instanceof ListTag<?>))
            throw new IOException("size tag is not a list");
        ListTag<?> size = (ListTag<?>) sizeTag;
        if (size.size() != 3)
            throw new IOException("size tag does not have 3 elements");
        Tag xTag = size.getTag(0);
        Tag yTag = size.getTag(1);
        Tag zTag = size.getTag(2);
        Point3I enclosingSize = null;
        int width = tryGetInt(xTag).orElse(0);
        int height = tryGetInt(yTag).orElse(0);
        int length = tryGetInt(zTag).orElse(0);
        if (width > 0 && height > 0 && length > 0) {
            enclosingSize = new Point3I(width, height, length);
        }

        return new NBTStructureFile(file, tryGetInt(root.get("DataVersion")).orElse(500), NBTUtils.tryGetString(root.get("author")), enclosingSize);
    }

    private final String author;

    private NBTStructureFile(Path file, int dataVersion, String author, Point3I enclosingSize) {
        super(file, dataVersion, enclosingSize);
        this.author = author;
    }

    @Override
    public SchematicType getType() {
        return SchematicType.NBT_STRUCTURE;
    }

    @Override
    public String getAuthor() {
        return author;
    }

}
