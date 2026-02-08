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

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Point3I;
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
        return switch (type) {
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
    private final int     dataVersion;
    private final Point3I enclosingSize;

    protected Schematic(Path file, int dataVersion, @Nullable Point3I enclosingSize) {
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

    /// Non-negative, otherwise empty
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
    public Point3I getEnclosingSize() {
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
