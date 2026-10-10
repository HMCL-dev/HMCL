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
package org.jackhuang.hmcl.util;

import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public final class NBTUtils {

    public static CompoundTag readCompressed(Path file) throws IOException {
        return NBTCodec.of().readTag(file, TagType.COMPOUND);
    }

    public static OptionalLong tryGetLong(Tag tag) {
        if (tag instanceof ValueTag<?> valueTag) {
            Object val = valueTag.getValue();
            if (val instanceof Long || val instanceof Integer || val instanceof Short || val instanceof Byte) {
                return OptionalLong.of(((Number) val).longValue());
            }
        }
        return OptionalLong.empty();
    }

    public static OptionalInt tryGetInt(Tag tag) {
        var ol = tryGetLong(tag);
        if (ol.isEmpty()) return OptionalInt.empty();
        long l = ol.getAsLong();
        int i = (int) l;
        if (l == i) return OptionalInt.of(i);
        return OptionalInt.empty();
    }

    public static Optional<Short> tryGetShort(Tag tag) {
        var ol = tryGetLong(tag);
        if (ol.isEmpty()) return Optional.empty();
        long l = ol.getAsLong();
        short i = (short) l;
        if (l == i) return Optional.of(i);
        return Optional.empty();
    }

    public static @Nullable Instant tryGetLongTimestamp(Tag tag) {
        if (tag instanceof LongTag longTag) {
            return Instant.ofEpochMilli(longTag.get());
        }
        return null;
    }

    public static @Nullable String tryGetString(Tag tag) {
        return tag instanceof StringTag stringTag ? stringTag.get() : null;
    }

    private NBTUtils() {
    }

}
