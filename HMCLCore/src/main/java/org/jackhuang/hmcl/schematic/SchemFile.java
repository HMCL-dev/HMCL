// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.schematic;

import org.glavo.nbt.tag.CompoundTag;
import org.glavo.nbt.tag.IntTag;
import org.glavo.nbt.tag.StringTag;
import org.glavo.nbt.tag.Tag;
import org.jackhuang.hmcl.util.NBTUtils;
import org.jackhuang.hmcl.util.Point3I;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.jackhuang.hmcl.util.NBTUtils.tryGetShort;
import static org.jackhuang.hmcl.util.NBTUtils.tryGetString;

/// @author Calboot
/// @see <a href="https://minecraft.wiki/w/Schematic_file_format">Schematic File Format Wiki</a>
/// @see <a href="https://github.com/Lunatrius/Schematica/blob/master/src/main/java/com/github/lunatrius/schematica/world/schematic/SchematicAlpha.java">Schematica</a>
/// @see <a href="https://github.com/SpongePowered/Schematic-Specification/tree/master/versions">Sponge Spec</a>
public final class SchemFile extends Schematic {

    private static final int DATA_VERSION_MC_1_13_2 = 1631;

    public static SchemFile load(Path file) throws IOException {

        CompoundTag root = NBTUtils.readCompressed(file);

        if (root.get("Materials") != null) return loadLegacy(file, root);
        else if (root.get("Version") != null) return loadSponge(file, root);
        else {
            var schem = root.get("Schematic");
            if (schem instanceof CompoundTag sponge3 && sponge3.get("Version") != null) {
                return loadSponge(file, sponge3);
            }
        }
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
            short width = tryGetShort(widthTag).orElse((short) 0);
            short height = tryGetShort(heightTag).orElse((short) 0);
            short length = tryGetShort(lengthTag).orElse((short) 0);
            if (width > 0 && height > 0 && length > 0) {
                enclosingSize = new Point3I(width, height, length);
            }
        }

        return new SchemFile(file, null, null, ((StringTag) materialsTag).getValue(), 0, 0, enclosingSize);
    }

    private static SchemFile loadSponge(Path file, CompoundTag root) throws IOException {
        Tag versionTag = root.get("Version");
        if (!(versionTag instanceof IntTag))
            throw new IOException("Version tag is not an integer");

        Tag dataVersionTag = root.get("DataVersion");
        int dataVersion;
        dataVersion = dataVersionTag == null ? DATA_VERSION_MC_1_13_2 : NBTUtils.tryGetInt(dataVersionTag).orElse(0);

        Tag widthTag = root.get("Width");
        Tag heightTag = root.get("Height");
        Tag lengthTag = root.get("Length");
        Point3I enclosingSize = null;
        if (widthTag != null && heightTag != null && lengthTag != null) {
            int width = tryGetShort(widthTag).orElse((short) 0) & 0xFFFF;
            int height = tryGetShort(heightTag).orElse((short) 0) & 0xFFFF;
            int length = tryGetShort(lengthTag).orElse((short) 0) & 0xFFFF;
            if (width > 0 && height > 0 && length > 0) {
                enclosingSize = new Point3I(width, height, length);
            }
        }

        Tag metaTag = root.get("Metadata");
        String name = null, author = null;
        if (metaTag instanceof CompoundTag meta) {
            name = tryGetString(meta.get("Name"));
            author = tryGetString(meta.get("Author"));
        }

        return new SchemFile(file, name, author, null, dataVersion, ((IntTag) versionTag).getValue(), enclosingSize);
    }

    private final @Nullable String name;
    private final @Nullable String author;
    private final @Nullable String materials;
    private final int version;

    private SchemFile(Path file, @Nullable String name, @Nullable String author, @Nullable String materials, int dataVersion, int version, Point3I enclosingSize) {
        super(file, dataVersion, enclosingSize);
        this.name = name;
        this.author = author;
        this.materials = materials;
        this.version = version;
    }

    @Override
    public @NotNull String getName() {
        if (name == null) return super.getName();
        return name;
    }

    @Override
    public String getAuthor() {
        if (author == null) return super.getAuthor();
        return author;
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
