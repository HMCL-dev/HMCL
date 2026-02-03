package org.jackhuang.hmcl.schematic;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.*;
import org.jackhuang.hmcl.util.Lang;
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

    public static OptionalInt tryGetInt(Tag tag) {
        return tag instanceof IntTag ? OptionalInt.of(((IntTag) tag).getValue()) : OptionalInt.empty();
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

    public OptionalInt getVersion() {
        return OptionalInt.empty();
    }

    /// None-negative, otherwise empty
    public OptionalInt getSubVersion() {
        return OptionalInt.empty();
    }

    /// At least 100, otherwise empty
    public OptionalInt getMinecraftDataVersion() {
        return Lang.wrapWithMinValue(dataVersion, /* 15w32a */ 100);
    }

    @Nullable
    public String getMinecraftVersion() {
        return getMinecraftDataVersion().isPresent() ? Integer.toString(getMinecraftDataVersion().getAsInt()) : null;
    }

    @NotNull
    public String getName() {
        return FileUtils.getNameWithoutExtension(getFile());
    }

    @Nullable
    public String getAuthor() {
        return null;
    }

    @Nullable
    public Instant getTimeCreated() {
        return null;
    }

    @Nullable
    public Instant getTimeModified() {
        return null;
    }

    public OptionalInt getRegionCount() {
        return OptionalInt.empty();
    }

    public OptionalInt getTotalBlocks() {
        return OptionalInt.empty();
    }

    @Nullable
    public Vec3i getEnclosingSize() {
        return enclosingSize;
    }

    public OptionalInt getTotalVolume() {
        var enclosingSize = getEnclosingSize();
        if (enclosingSize != null) return OptionalInt.of(enclosingSize.x() * enclosingSize.y() * enclosingSize.z());
        return OptionalInt.empty();
    }

    public int @Nullable [] getPreviewImageData() {
        return null;
    }

}
