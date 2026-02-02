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
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;

public sealed abstract class Schematic permits LitematicFile, SchemFile, NBTStructureFile {

    public static boolean isFileSchematic(Path file) {
        return SchematicType.getType(file) != null;
    }

    @Nullable
    public static Schematic load(Path file) throws IOException {
        var type = SchematicType.getType(file);
        if (type == null) return null;
        return switch (SchematicType.getType(file)) {
            case LITEMATIC -> LitematicFile.load(file);
            case SCHEM -> SchemFile.load(file);
            case NBT_STRUCTURE -> NBTStructureFile.load(file);
        };
    }

    public static CompoundTag readRoot(Path file) throws IOException {
        CompoundTag root;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            root = (CompoundTag) NBTIO.readTag(in);
        }
        return root;
    }

    public static int tryGetInt(Tag tag) {
        return tag instanceof IntTag ? ((IntTag) tag).getValue() : 0;
    }

    public static short tryGetShort(Tag tag) {
        return tag instanceof ShortTag ? ((ShortTag) tag).getValue() : 0;
    }

    public static @Nullable Instant tryGetLongTimestamp(Tag tag) {
        if (tag instanceof LongTag) {
            return Instant.ofEpochMilli(((LongTag) tag).getValue());
        }
        return null;
    }

    public static @Nullable String tryGetString(Tag tag) {
        return tag instanceof StringTag ? ((StringTag) tag).getValue() : null;
    }

    private final Path file;
    private final int dataVersion;
    private final Vec3i enclosingSize;

    protected Schematic(Path file, int dataVersion, @Nullable Vec3i enclosingSize) {
        this.file = file;
        this.dataVersion = dataVersion;
        this.enclosingSize = enclosingSize;
    }

    public abstract SchematicType getType();

    @NotNull
    public Path getFile() {
        return file;
    }

    public int getVersion() {
        return 0;
    }

    public OptionalInt getSubVersion() {
        return OptionalInt.empty();
    }

    public OptionalInt getMinecraftDataVersion() {
        return dataVersion >= 100 /* 15w32a */ ? OptionalInt.of(dataVersion) : OptionalInt.empty();
    }

    public String getMinecraftVersion() {
        return getMinecraftDataVersion().isPresent() ? Integer.toString(getMinecraftDataVersion().getAsInt()) : null;
    }

    public String getName() {
        return FileUtils.getNameWithoutExtension(getFile());
    }

    public String getAuthor() {
        return null;
    }

    public Instant getTimeCreated() {
        return null;
    }

    public Instant getTimeModified() {
        return null;
    }

    public int getRegionCount() {
        return 0;
    }

    public int getTotalBlocks() {
        return 0;
    }

    @Nullable
    public Vec3i getEnclosingSize() {
        return enclosingSize;
    }

    public int getTotalVolume() {
        var enclosingSize = getEnclosingSize();
        if (enclosingSize != null) return enclosingSize.x() * enclosingSize.y() * enclosingSize.z();
        return 0;
    }

}
