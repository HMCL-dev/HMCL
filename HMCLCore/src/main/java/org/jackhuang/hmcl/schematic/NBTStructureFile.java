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
            throw new IOException("Materials tag not found");
        else if (!(dataVersionTag instanceof IntTag))
            throw new IOException("Materials tag is not an integer");

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
            int width = tryGetInt(xTag);
            int height = tryGetInt(yTag);
            int length = tryGetInt(zTag);
            if (width >= 0 && height >= 0 && length >= 0) {
                enclosingSize = new Vec3i(width, height, length);
            }
        }

        return new NBTStructureFile(file, ((IntTag) dataVersionTag).getValue(), enclosingSize);
    }

    private NBTStructureFile(Path file, int dataVersion, Vec3i enclosingSize) {
        super(file, dataVersion, enclosingSize);
    }

    @Override
    public SchematicType getType() {
        return SchematicType.NBT_STRUCTURE;
    }

}
