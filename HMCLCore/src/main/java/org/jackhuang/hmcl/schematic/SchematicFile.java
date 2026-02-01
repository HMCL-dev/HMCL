package org.jackhuang.hmcl.schematic;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.jackhuang.hmcl.util.Vec3i;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.schematic.Schematic.tryGetShort;

/// @author Calboot
/// @see <a href="https://minecraft.wiki/w/Schematic_file_format">Schematic File Format Wiki</a>
/// @see <a href="https://github.com/Lunatrius/Schematica/blob/master/src/main/java/com/github/lunatrius/schematica/world/schematic/SchematicAlpha.java">Schematica</a>
public final class SchematicFile implements Schematic {

    public static boolean isFileSchematic(Path path) {
        return "schematic".equals(FileUtils.getExtension(path)) && Files.isRegularFile(path);
    }

    public static SchematicFile load(Path file) throws IOException {
        if (!isFileSchematic(file)) return null;

        CompoundTag root = Schematic.readRoot(file);

        Tag materialsTag = root.get("Materials");
        if (materialsTag == null)
            throw new IOException("Materials tag not found");
        else if (!(materialsTag instanceof StringTag))
            throw new IOException("Materials tag is not an integer");

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

        return new SchematicFile(file, ((StringTag) materialsTag).getValue(), enclosingSize);
    }

    private final Path file;
    private final String materials;
    private final Vec3i enclosingSize;

    private SchematicFile(Path file, String materials, Vec3i enclosingSize) {
        this.file = file;
        this.materials = materials;
        this.enclosingSize = enclosingSize;
    }

    @Override
    public @NotNull Path getFile() {
        return file;
    }

    @Override
    public String getMinecraftVersion() {
        return materials;
    }

    @Override
    public @Nullable Vec3i getEnclosingSize() {
        return enclosingSize;
    }

}
