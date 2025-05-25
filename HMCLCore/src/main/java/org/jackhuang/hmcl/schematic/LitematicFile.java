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

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.*;
import javafx.geometry.Point3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.GZIPInputStream;

/**
 * @author Glavo
 * @see <a href="https://litemapy.readthedocs.io/en/v0.9.0b0/litematics.html">The Litematic file format</a>
 */
public final class LitematicFile {

    private static int tryGetInt(Tag tag) {
        return tag instanceof IntTag ? ((IntTag) tag).getValue() : 0;
    }

    private static @Nullable Instant tryGetLongTimestamp(Tag tag) {
        if (tag instanceof LongTag) {
            return Instant.ofEpochMilli(((LongTag) tag).getValue());
        }
        return null;
    }

    private static @Nullable String tryGetString(Tag tag) {
        return tag instanceof StringTag ? ((StringTag) tag).getValue() : null;
    }

    public static LitematicFile load(Path file) throws IOException {

        CompoundTag root;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            root = (CompoundTag) NBTIO.readTag(in);
        }

        int version;
        {
            Tag versionTag = root.get("Version");
            if (versionTag == null)
                throw new IOException("Version tag not found");
            else if (!(versionTag instanceof IntTag))
                throw new IOException("Version tag is not an integer");

            version = ((IntTag) versionTag).getValue();
        }
        int subVersion = tryGetInt(root.get("SubVersion"));
        int minecraftDataVersion = tryGetInt(root.get("MinecraftDataVersion"));

        CompoundTag metadata;
        {
            Tag metadataTag = root.get("Metadata");
            if (metadataTag == null)
                throw new IOException("Metadata tag not found");
            else if (!(metadataTag instanceof CompoundTag))
                throw new IOException("Metadata tag is not a compound tag");

            metadata = (CompoundTag) metadataTag;
        }

        String name = tryGetString(metadata.get("Name"));
        String author = tryGetString(metadata.get("Author"));
        String description = tryGetString(metadata.get("Description"));
        Instant timeCreated = tryGetLongTimestamp(metadata.get("TimeCreated"));
        Instant timeModified = tryGetLongTimestamp(metadata.get("TimeModified"));
        int totalBlocks = tryGetInt(metadata.get("TotalBlocks"));
        int totalVolume = tryGetInt(metadata.get("TotalVolume"));

        Point3D enclosingSize = null;
        {
            Tag enclosingSizeTag = metadata.get("EnclosingSize");
            if (enclosingSizeTag instanceof CompoundTag) {
                CompoundTag list = (CompoundTag) enclosingSizeTag;
                int x = tryGetInt(list.get("x"));
                int y = tryGetInt(list.get("y"));
                int z = tryGetInt(list.get("z"));

                if (x > 0 && y > 0 && z > 0)
                    enclosingSize = new Point3D(x, y, z);
            }
        }

        int totalRegions = 0;
        {
            Tag regions = root.get("Regions");
            if (regions instanceof CompoundTag)
                totalRegions = ((CompoundTag) regions).size();
        }

        return new LitematicFile(
                file,
                version, subVersion, minecraftDataVersion,
                name, author, description,
                timeCreated, timeModified,
                totalBlocks, totalVolume,
                enclosingSize,
                totalRegions
        );
    }

    private final @NotNull Path file;
    private final int version;
    private final int subVersion;
    private final int minecraftDataVersion;

    private final String name;
    private final String author;
    private final String description;
    private final Instant timeCreated;
    private final Instant timeModified;
    private final int totalBlocks;
    private final int totalVolume;
    private final Point3D enclosingSize;
    private final int totalRegions;

    public LitematicFile(@NotNull Path file, int version, int subVersion, int minecraftDataVersion,
                         String name, String author, String description,
                         Instant timeCreated, Instant timeModified,
                         int totalBlocks, int totalVolume, Point3D enclosingSize,
                         int totalRegions) {
        this.file = file;
        this.version = version;
        this.subVersion = subVersion;
        this.minecraftDataVersion = minecraftDataVersion;
        this.name = name;
        this.author = author;
        this.description = description;
        this.timeCreated = timeCreated;
        this.timeModified = timeModified;
        this.totalBlocks = totalBlocks;
        this.totalVolume = totalVolume;
        this.enclosingSize = enclosingSize;
        this.totalRegions = totalRegions;
    }

    public @NotNull Path getFile() {
        return file;
    }

    public int getVersion() {
        return version;
    }

    public int getSubVersion() {
        return subVersion;
    }

    public int getMinecraftDataVersion() {
        return minecraftDataVersion;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public Instant getTimeCreated() {
        return timeCreated;
    }

    public Instant getTimeModified() {
        return timeModified;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getTotalVolume() {
        return totalVolume;
    }

    public Point3D getEnclosingSize() {
        return enclosingSize;
    }

    public int getTotalRegions() {
        return totalRegions;
    }
}
