// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
