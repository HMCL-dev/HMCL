package org.jackhuang.hmcl.schematic;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.*;
import org.jackhuang.hmcl.util.Vec3i;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.GZIPInputStream;

public sealed interface Schematic permits LitematicFile, SchematicFile, NBTStructureFile {

    static boolean isFileSchematicAlike(Path file) {
        if (file == null) return false;
        return LitematicFile.isFileLitematic(file) || SchematicFile.isFileSchematic(file) || NBTStructureFile.isFileNBTStructure(file);
    }

    @Nullable
    static Schematic load(Path file) throws IOException {
        if (file == null) return null;
        if (LitematicFile.isFileLitematic(file)) {
            return LitematicFile.load(file);
        } else if (SchematicFile.isFileSchematic(file)) {
            return SchematicFile.load(file);
        } else if (NBTStructureFile.isFileNBTStructure(file)) {
            return NBTStructureFile.load(file);
        }
        return null;
    }

    static CompoundTag readRoot(Path file) throws IOException {
        CompoundTag root;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            root = (CompoundTag) NBTIO.readTag(in);
        }
        return root;
    }

    static int tryGetInt(Tag tag) {
        return tag instanceof IntTag ? ((IntTag) tag).getValue() : 0;
    }

    static short tryGetShort(Tag tag) {
        return tag instanceof ShortTag ? ((ShortTag) tag).getValue() : 0;
    }

    static @Nullable Instant tryGetLongTimestamp(Tag tag) {
        if (tag instanceof LongTag) {
            return Instant.ofEpochMilli(((LongTag) tag).getValue());
        }
        return null;
    }

    static @Nullable String tryGetString(Tag tag) {
        return tag instanceof StringTag ? ((StringTag) tag).getValue() : null;
    }

    @NotNull Path getFile();

    String getMinecraftVersion();

    default String getName() {
        return FileUtils.getNameWithoutExtension(getFile());
    }

    default String getAuthor() {
        return null;
    }

    default Instant getTimeCreated() {
        return null;
    }

    default Instant getTimeModified() {
        return null;
    }

    default int getRegionCount() {
        return 0;
    }

    default int getTotalBlocks() {
        return 0;
    }

    @Nullable Vec3i getEnclosingSize();

    default int getTotalVolume() {
        var enclosingSize = getEnclosingSize();
        if (enclosingSize != null) return enclosingSize.x() * enclosingSize.y() * enclosingSize.z();
        return 0;
    }

}
