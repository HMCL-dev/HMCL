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
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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

        return new LitematicFile(file, root);
    }

    private final @NotNull Path file;
    private final @NotNull CompoundTag root;

    private LitematicFile(@NotNull Path file, @NotNull CompoundTag root) {
        this.file = file;
        this.root = root;
    }

    private @NotNull CompoundTag getMetadata() {
        return root.get("Metadata");
    }

    public @NotNull Path getFile() {
        return file;
    }

    public int getVersion() {
        return root.<IntTag>get("Version").getValue();
    }

    public LitematicFile withVersion(int version) {
        if (version < 0)
            throw new IllegalArgumentException("Illegal version: " + version);

        CompoundTag newRoot = root.clone();
        newRoot.<IntTag>get("Version").setValue(version);
        return new LitematicFile(file, newRoot);
    }

    public int getSubVersion() {
        return tryGetInt(root.get("SubVersion"));
    }

    public int getMinecraftDataVersion() {
        return tryGetInt(root.get("MinecraftDataVersion"));
    }

    public String getName() {
        return tryGetString(getMetadata().get("Name"));
    }

    public String getAuthor() {
        return tryGetString(getMetadata().get("Author"));
    }

    public String getDescription() {
        return tryGetString(getMetadata().get("Description"));
    }

    public Instant getTimeCreated() {
        return tryGetLongTimestamp(getMetadata().get("TimeCreated"));
    }

    public Instant getTimeModified() {
        return tryGetLongTimestamp(getMetadata().get("TimeModified"));
    }

    public int getTotalBlocks() {
        return tryGetInt(getMetadata().get("TotalBlocks"));
    }

    public int getTotalVolume() {
        return tryGetInt(getMetadata().get("TotalVolume"));
    }

    public Point3D getEnclosingSize() {
        Tag enclosingSizeTag = getMetadata().get("EnclosingSize");
        if (enclosingSizeTag instanceof CompoundTag) {
            CompoundTag list = (CompoundTag) enclosingSizeTag;
            int x = tryGetInt(list.get("x"));
            int y = tryGetInt(list.get("y"));
            int z = tryGetInt(list.get("z"));

            if (x >= 0 && y >= 0 && z >= 0)
                return new Point3D(x, y, z);
        }

        return null;
    }

    public int getRegionCount() {
        Tag regions = root.get("Regions");
        if (regions instanceof CompoundTag)
            return ((CompoundTag) regions).size();
        else return 0;
    }

    public void saveToFile() throws IOException {
        FileUtils.saveSafely(file, outputStream -> {
            try (GZIPOutputStream output = new GZIPOutputStream(outputStream)) {
                NBTIO.writeTag(output, root);
            }
        });
    }
}
