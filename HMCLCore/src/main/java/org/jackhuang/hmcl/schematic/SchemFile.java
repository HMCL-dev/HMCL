package org.jackhuang.hmcl.schematic;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.jackhuang.hmcl.util.Vec3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.schematic.Schematic.*;
import static org.jackhuang.hmcl.schematic.Schematic.tryGetShort;

/// @author Calboot
/// @see <a href="https://minecraft.wiki/w/Schematic_file_format">Schematic File Format Wiki</a>
/// @see <a href="https://github.com/Lunatrius/Schematica/blob/master/src/main/java/com/github/lunatrius/schematica/world/schematic/SchematicAlpha.java">Schematica</a>
/// @see <a href="https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/SpongeSchematicReader.java">Schem</a>
public final class SchemFile implements Schematic {

    private static final int DATA_VERSION_MC_1_13_2 = 1631;

    public static SchemFile load(Path file) throws IOException {

        CompoundTag root = readRoot(file);

        if (root.contains("Materials")) return loadLegacy(file, root);
        else if (root.contains("Version")) return loadSponge(file, root);
        throw new IOException("No Materials tag or Version tag found");
    }

    private static SchemFile loadLegacy(Path file, CompoundTag root) throws IOException {
        Tag materialsTag = root.get("Materials");
        if (!(materialsTag instanceof StringTag))
            throw new IOException("Materials tag is not a string");

        Tag widthTag = root.get("Width");
        Tag heightTag = root.get("Height");
        Tag lengthTag = root.get("Length");
        Vec3i enclosingSize = null;
        if (widthTag != null && heightTag != null && lengthTag != null) {
            short width = tryGetShort(widthTag);
            short height = tryGetShort(heightTag);
            short length = tryGetShort(lengthTag);
            if (width >= 0 && height >= 0 && length >= 0) {
                enclosingSize = new Vec3i(width, height, length);
            }
        }

        return new SchemFile(file, ((StringTag) materialsTag).getValue(), 0, 0, enclosingSize);
    }

    private static SchemFile loadSponge(Path file, CompoundTag root) throws IOException {
        Tag versionTag = root.get("Version");
        if (!(versionTag instanceof IntTag))
            throw new IOException("Version tag is not an integer");

        Tag dataVersionTag = root.get("DataVersion");
        int dataVersion = dataVersionTag == null ? DATA_VERSION_MC_1_13_2 : tryGetInt(dataVersionTag);

        Tag widthTag = root.get("Width");
        Tag heightTag = root.get("Height");
        Tag lengthTag = root.get("Length");
        Vec3i enclosingSize = null;
        if (widthTag != null && heightTag != null && lengthTag != null) {
            int width = tryGetShort(widthTag) & 0xFFFF;
            int height = tryGetShort(heightTag) & 0xFFFF;
            int length = tryGetShort(lengthTag) & 0xFFFF;
            enclosingSize = new Vec3i(width, height, length);
        }

        return new SchemFile(file, null, dataVersion, ((IntTag) versionTag).getValue(), enclosingSize);
    }

    private final Path file;
    private final String materials;
    private final int dataVersion;
    private final int version;
    private final Vec3i enclosingSize;

    private SchemFile(Path file, @Nullable String materials, int dataVersion, int version, Vec3i enclosingSize) {
        this.file = file;
        this.materials = materials;
        this.dataVersion = dataVersion;
        this.version = version;
        this.enclosingSize = enclosingSize;
    }

    @Override
    public SchematicType getType() {
        return SchematicType.SCHEM;
    }

    @Override
    public @NotNull Path getFile() {
        return file;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public int getMinecraftDataVersion() {
        return dataVersion;
    }

    @Override
    public String getMinecraftVersion() {
        return materials == null ? Integer.toString(dataVersion) : materials;
    }

    @Override
    public @Nullable Vec3i getEnclosingSize() {
        return enclosingSize;
    }

}
