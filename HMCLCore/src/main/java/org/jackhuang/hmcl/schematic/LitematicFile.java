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

import com.github.steveice10.opennbt.tag.builtin.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.Point3I;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.OptionalInt;

/**
 * @author Glavo
 * @see <a href="https://litemapy.readthedocs.io/en/v0.9.0b0/litematics.html">The Litematic file format</a>
 */
public final class LitematicFile extends Schematic {

    public static LitematicFile load(Path file) throws IOException {

        CompoundTag root = readRoot(file);

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
        Tag regionsTag = root.get("Regions");
        if (regionsTag instanceof CompoundTag)
            regions = ((CompoundTag) regionsTag).size();

        Point3I enclosingSize    = null;
        Tag     enclosingSizeTag = ((CompoundTag) metadataTag).get("EnclosingSize");
        if (enclosingSizeTag instanceof CompoundTag) {
            CompoundTag list = (CompoundTag) enclosingSizeTag;
            int x = tryGetInt(list.get("x")).orElse(0);
            int y = tryGetInt(list.get("y")).orElse(0);
            int z = tryGetInt(list.get("z")).orElse(0);

            if (x > 0 && y > 0 && z > 0) enclosingSize = new Point3I(x, y, z);
        }

        Tag subVersionTag = root.get("SubVersion");
        return new LitematicFile(file, (CompoundTag) metadataTag,
                ((IntTag) versionTag).getValue(),
                tryGetInt(subVersionTag).orElse(-1),
                tryGetInt(root.get("MinecraftDataVersion")).orElse(0),
                regions,
                enclosingSize
        );
    }

    private final int version;
    private final int subVersion;
    private final int regionCount;
    private final int[] previewImageData;
    private final String name;
    private final String author;
    private final String description;
    private final Instant timeCreated;
    private final Instant timeModified;
    private final int totalBlocks;
    private final int totalVolume;

    private LitematicFile(@NotNull Path file, @NotNull CompoundTag metadata,
                          int version, int subVersion, int minecraftDataVersion, int regionCount, Point3I enclosingSize) {
        super(file, minecraftDataVersion, enclosingSize);
        this.version = version;
        this.subVersion = subVersion;
        this.regionCount = regionCount;

        Tag previewImageData = metadata.get("PreviewImageData");
        this.previewImageData = previewImageData instanceof IntArrayTag
                ? ((IntArrayTag) previewImageData).getValue()
                : null;

        this.name = tryGetString(metadata.get("Name"));
        this.author = tryGetString(metadata.get("Author"));
        this.description = tryGetString(metadata.get("Description"));
        this.timeCreated = tryGetLongTimestamp(metadata.get("TimeCreated"));
        this.timeModified = tryGetLongTimestamp(metadata.get("TimeModified"));
        this.totalBlocks = tryGetInt(metadata.get("TotalBlocks")).orElse(-1);
        this.totalVolume = tryGetInt(metadata.get("TotalVolume")).orElse(-1);

    }

    @Override
    public SchematicType getType() {
        return SchematicType.LITEMATIC;
    }

    @Override
    public OptionalInt getVersion() {
        return OptionalInt.of(version);
    }

    @Override
    public OptionalInt getSubVersion() {
        return Lang.wrapWithMinValue(subVersion, 0);
    }

    @Override
    public int[] getPreviewImageData() {
        return previewImageData != null ? previewImageData.clone() : null;
    }

    @Override
    public @NotNull String getName() {
        return StringUtils.isNotBlank(name) ? name : super.getName();
    }

    @Override
    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public Instant getTimeCreated() {
        return timeCreated;
    }

    @Override
    public Instant getTimeModified() {
        return timeModified;
    }

    @Override
    public OptionalInt getTotalBlocks() {
        return Lang.wrapWithMinValue(totalBlocks, 1);
    }

    @Override
    public OptionalInt getTotalVolume() {
        return Lang.wrapWithMinValue(totalVolume, 1);
    }

    @Override
    public OptionalInt getRegionCount() {
        return Lang.wrapWithMinValue(regionCount, 1);
    }

}
