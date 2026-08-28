/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.geometry.Point3D;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.GZIPInputStream;

/// @author Glavo
/// @see <a href="https://litemapy.readthedocs.io/en/v0.9.0b0/litematics.html">The Litematic file format</a>
public record LitematicFile(@NotNull Path file, int version, int subVersion, int minecraftDataVersion, int regionCount,
                            int[] previewImageData, String name, String author, String description, Instant timeCreated,
                            Instant timeModified, int totalBlocks, int totalVolume, Point3D enclosingSize) {
    private static LitematicFile parse(@NotNull Path file, @NotNull CompoundTag metadata,
                                       int version, int subVersion, int minecraftDataVersion, int regionCount) {
        Tag previewImageData = metadata.get("PreviewImageData");
        var previewImageDataArray = previewImageData instanceof IntArrayTag intArrayTag
                ? intArrayTag.getArray()
                : null;

        var name = tryGetString(metadata.get("Name"));
        var author = tryGetString(metadata.get("Author"));
        var description = tryGetString(metadata.get("Description"));
        var timeCreated = metadata.get("TimeCreated") instanceof LongTag time ? Instant.ofEpochMilli(time.getValue()) : null;
        var timeModified = metadata.get("TimeModified") instanceof LongTag time ? Instant.ofEpochMilli(time.getValue()) : null;
        var totalBlocks = metadata.getIntOrZero("TotalBlocks");
        var totalVolume = metadata.getIntOrZero("TotalVolume");

        Point3D enclosingSize = null;
        if (metadata.get("EnclosingSize") instanceof CompoundTag list) {
            int x = list.getIntOrZero("x");
            int y = list.getIntOrZero("y");
            int z = list.getIntOrZero("z");

            if (x >= 0 && y >= 0 && z >= 0)
                enclosingSize = new Point3D(x, y, z);
        }

        return new LitematicFile(
                file,
                version,
                subVersion,
                minecraftDataVersion,
                regionCount,
                previewImageDataArray,
                name,
                author,
                description,
                timeCreated,
                timeModified,
                totalBlocks,
                totalVolume,
                enclosingSize
        );
    }

    private static @Nullable String tryGetString(Tag tag) {
        return tag instanceof StringTag stringTag ? stringTag.get() : null;
    }

    public static LitematicFile load(Path file) throws IOException {

        CompoundTag root;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            root = NBTCodec.of().readTag(in, TagType.COMPOUND);
        }

        Tag versionTag = root.get("Version");
        if (versionTag == null)
            throw new IOException("Version tag not found");
        else if (!(versionTag instanceof IntTag))
            throw new IOException("Version tag is not an integer");

        Tag metadataTag = root.get("Metadata");
        if (metadataTag == null)
            throw new IOException("Metadata tag not found");
        else if (!(metadataTag instanceof CompoundTag))
            throw new IOException("Metadata tag is not a compound tag");

        int regions = 0;
        if (root.get("Regions") instanceof CompoundTag regionsTag)
            regions = regionsTag.size();

        return parse(file, (CompoundTag) metadataTag,
                ((IntTag) versionTag).getValue(),
                root.getIntOrZero("SubVersion"),
                root.getIntOrZero("MinecraftDataVersion"),
                regions
        );
    }

    @Override
    public int[] previewImageData() {
        return previewImageData != null ? previewImageData.clone() : null;
    }
}
