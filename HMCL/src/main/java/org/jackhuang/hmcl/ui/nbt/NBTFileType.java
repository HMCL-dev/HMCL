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
package org.jackhuang.hmcl.ui.nbt;

import org.glavo.nbt.NBTElement;
import org.glavo.nbt.chunk.ChunkRegion;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.Tag;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @author Glavo
 */
public enum NBTFileType {
    COMPRESSED("dat", "dat_old") {
        @Override
        public Tag read(Path file) throws IOException {
            return NBTCodec.of().readTag(file);
        }
    },
    ANVIL("mca") {
        @Override
        public NBTElement read(Path file) throws IOException {
            return REGION.read(file);
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
