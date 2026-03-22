package org.jackhuang.hmcl.util;

import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.OptionalInt;

public final class NBTUtils {

    public static CompoundTag readCompressed(Path file) throws IOException {
        return NBTCodec.of().readTag(file, TagType.COMPOUND);
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

}
