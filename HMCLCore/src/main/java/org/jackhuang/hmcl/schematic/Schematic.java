// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.schematic;

import org.jackhuang.hmcl.util.Point3I;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.OptionalLong;

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

    private final Path file;
    private final int dataVersion;
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
        /* 15w32a */
        return dataVersion >= 100 ? OptionalInt.of(dataVersion) : OptionalInt.empty();
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

    public OptionalLong getTotalVolume() {
        var enclosingSize = getEnclosingSize();
        if (enclosingSize != null) return OptionalLong.of((long) enclosingSize.x() * enclosingSize.y() * enclosingSize.z());
        return OptionalLong.empty();
    }

    public int @Nullable [] getPreviewImageData() {
        return null;
    }

}
