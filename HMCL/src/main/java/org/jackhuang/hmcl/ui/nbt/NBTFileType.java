// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.nbt;

import org.glavo.nbt.NBTElement;
import org.glavo.nbt.chunk.ChunkRegion;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.Tag;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/// @author Glavo
public enum NBTFileType {
    COMPRESSED("dat", "dat_old", "litematic", "nbt", "schematic", "schem") {
        @Override
        public Tag read(Path file) throws IOException {
            return NBTCodec.of().readTag(file);
        }
    },
    ANVIL("mca") {
        @Override
        public NBTElement read(Path file) throws IOException {
            return NBTCodec.of().readRegion(file);
        }
    },
    REGION("mcr") {
        @Override
        public ChunkRegion read(Path file) throws IOException {
            return NBTCodec.of().readRegion(file);
        }
    };

    static final NBTFileType[] types = values();

    public static boolean isNBTFileByExtension(Path file) {
        return NBTFileType.ofFile(file) != null;
    }

    public static @Nullable NBTFileType ofFile(Path file) {
        String ext = FileUtils.getExtension(file);
        for (NBTFileType type : types) {
            for (String extension : type.extensions) {
                if (extension.equals(ext))
                    return type;
            }
        }

        return null;
    }

    private final String[] extensions;

    NBTFileType(String... extensions) {
        this.extensions = extensions;
    }

    public abstract NBTElement read(Path file) throws IOException;

}
