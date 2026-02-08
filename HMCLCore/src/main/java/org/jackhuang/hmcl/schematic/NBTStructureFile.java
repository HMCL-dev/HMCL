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
package org.jackhuang.hmcl.schematic;

import com.github.steveice10.opennbt.tag.builtin.*;
import org.jackhuang.hmcl.util.Vec3i;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/// @author Calboot
/// @see <a href="https://minecraft.wiki/w/Structure_file">Structure File</a>
public final class NBTStructureFile extends Schematic {

    public static NBTStructureFile load(Path file) throws IOException {

        CompoundTag root = Schematic.readRoot(file);

        Tag dataVersionTag = root.get("DataVersion");
        if (dataVersionTag == null)
            throw new IOException("DataVersion tag not found");
        else if (!(dataVersionTag instanceof IntTag))
            throw new IOException("DataVersion tag is not an integer");

        Tag sizeTag = root.get("size");
        if (sizeTag == null)
            throw new IOException("size tag not found");
        else if (!(sizeTag instanceof ListTag))
            throw new IOException("size tag is not a list");
        List<Tag> size = ((ListTag) sizeTag).getValue();
        if (size.size() != 3)
            throw new IOException("size tag does not have 3 elements");
        Tag xTag = size.get(0);
        Tag yTag = size.get(1);
        Tag zTag = size.get(2);
        Vec3i enclosingSize = null;
        if (xTag != null && yTag != null && zTag != null) {
            int width = tryGetInt(xTag).orElse(0);
            int height = tryGetInt(yTag).orElse(0);
            int length = tryGetInt(zTag).orElse(0);
            if (width > 0 && height > 0 && length > 0) {
                enclosingSize = new Vec3i(width, height, length);
            }
        }

        return new NBTStructureFile(file, ((IntTag) dataVersionTag).getValue(), tryGetString(root.get("author")), enclosingSize);
    }

    private final String author;

    private NBTStructureFile(Path file, int dataVersion, String author, Vec3i enclosingSize) {
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
