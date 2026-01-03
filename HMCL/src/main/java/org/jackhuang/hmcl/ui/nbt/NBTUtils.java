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
package org.jackhuang.hmcl.ui.nbt;

import com.github.steveice10.opennbt.SNBTIO;
import com.viaversion.nbt.tag.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class NBTUtils {

    private NBTUtils() {
    }

    public static String getSNBT(com.github.steveice10.opennbt.tag.builtin.Tag tag) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SNBTIO.writeTag(baos, tag, false);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Tag getTag(Object value) {
        if (value instanceof Byte b) {
            return new ByteTag(b);
        } else if (value instanceof Double d) {
            return new DoubleTag(d);
        } else if (value instanceof Float f) {
            return new FloatTag(f);
        } else if (value instanceof Integer i) {
            return new IntTag(i);
        } else if (value instanceof Long l) {
            return new LongTag(l);
        } else if (value instanceof Short s) {
            return new ShortTag(s);
        } else if (value instanceof String s) {
            return new StringTag(s);
        }
        return null;
    }

    public static Tag getTag(Map<String, Tag> map) {
        return new CompoundTag(map);
    }

    public static Tag getTag(byte[] value) {
        return new ByteArrayTag(value);
    }

    public static Tag getTag(int[] value) {
        return new IntArrayTag(value);
    }

    public static Tag getTag(long[] value) {
        return new LongArrayTag(value);
    }
}
