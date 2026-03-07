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

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.jackhuang.hmcl.util.Point3I;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;

/// @author Calboot
/// @see <a href="https://minecraft.wiki/w/Schematic_file_format">Schematic File Format Wiki</a>
/// @see <a href="https://github.com/Lunatrius/Schematica/blob/master/src/main/java/com/github/lunatrius/schematica/world/schematic/SchematicAlpha.java">Schematica</a>
/// @see <a href="https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/SpongeSchematicReader.java">Schem</a>
public final class SchemFile extends Schematic {

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
        Point3I enclosingSize = null;
        if (widthTag != null && heightTag != null && lengthTag != null) {
            short width = tryGetShort(widthTag);
            short height = tryGetShort(heightTag);
            short length = tryGetShort(lengthTag);
            if (width >= 0 && height >= 0 && length >= 0) {
                enclosingSize = new Point3I(width, height, length);
            }
        }

        return new SchemFile(file, ((StringTag) materialsTag).getValue(), 0, 0, enclosingSize);
    }

    private static SchemFile loadSponge(Path file, CompoundTag root) throws IOException {
        Tag versionTag = root.get("Version");
        if (!(versionTag instanceof IntTag))
            throw new IOException("Version tag is not an integer");

        Tag dataVersionTag = root.get("DataVersion");
        int dataVersion = dataVersionTag == null ? DATA_VERSION_MC_1_13_2 : tryGetInt(dataVersionTag).orElse(0);

        Tag widthTag = root.get("Width");
        Tag heightTag = root.get("Height");
        Tag lengthTag = root.get("Length");
        Point3I enclosingSize = null;
        if (widthTag != null && heightTag != null && lengthTag != null) {
            int width = tryGetShort(widthTag) & 0xFFFF;
            int height = tryGetShort(heightTag) & 0xFFFF;
            int length = tryGetShort(lengthTag) & 0xFFFF;
            enclosingSize = new Point3I(width, height, length);
        }

        return new SchemFile(file, null, dataVersion, ((IntTag) versionTag).getValue(), enclosingSize);
    }

    private final String materials;
    private final int version;

    private SchemFile(Path file, @Nullable String materials, int dataVersion, int version, Point3I enclosingSize) {
        super(file, dataVersion, enclosingSize);
        this.materials = materials;
        this.version = version;
    }

    @Override
    public SchematicType getType() {
        return SchematicType.SCHEM;
    }

    @Override
    public OptionalInt getVersion() {
        return version > 0 ? OptionalInt.of(version) : OptionalInt.empty();
    }

    @Override
    public String getMinecraftVersion() {
        return materials == null ? super.getMinecraftVersion() : materials;
    }

}
