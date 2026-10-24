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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies byte-array ownership and equality when keys are used in hash collections.
@NotNullByDefault
class ByteArrayKeyTest {
    /// Keeps map lookup and the cached hash stable after input and output arrays are modified.
    @Test
    void ownsItsBytes() {
        byte[] input = {1, 2, 3};
        ByteArrayKey key = ByteArrayKey.copyOf(input);
        int hash = key.hashCode();
        var map = new HashMap<ByteArrayKey, String>();
        map.put(key, "value");

        input[0] = 9;
        byte[] output = key.toByteArray();
        output[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, key.toByteArray());
        assertEquals(hash, key.hashCode());
        assertEquals("value", map.get(ByteArrayKey.copyOf(new byte[]{1, 2, 3})));
        assertNotSame(output, key.toByteArray());
    }

    /// Creates equal keys through all factories and keeps the retained array hidden on output.
    @Test
    void supportsAllFactories() {
        byte[] bytes = {0, 10, -1};
        ByteArrayKey wrapped = ByteArrayKey.wrap(bytes);
        ByteArrayKey copied = ByteArrayKey.copyOf(bytes);
        ByteArrayKey parsed = ByteArrayKey.fromHexString("000aFF");
        assertEquals(copied, wrapped);
        assertEquals(copied, parsed);
        assertEquals(copied.hashCode(), wrapped.hashCode());
        assertEquals(copied.hashCode(), parsed.hashCode());
        wrapped.toByteArray()[0] = 1;
        assertEquals(parsed, wrapped);
        assertEquals(ByteArrayKey.copyOf(new byte[0]), ByteArrayKey.fromHexString(""));
        assertEquals(ByteArrayKey.copyOf(new byte[0]), ByteArrayKey.wrap(new byte[0]));
    }

    /// Rejects invalid hexadecimal strings and absent inputs.
    @Test
    void rejectsInvalidFactoryArguments() {
        for (String hex : new String[]{"0", "0g", "00 ff", "0x00"})
            assertThrows(IllegalArgumentException.class, () -> ByteArrayKey.fromHexString(hex));
        assertThrows(NullPointerException.class, () -> ByteArrayKey.fromHexString(null));
        assertThrows(NullPointerException.class, () -> ByteArrayKey.copyOf(null));
        assertThrows(NullPointerException.class, () -> ByteArrayKey.wrap(null));
    }

    /// Distinguishes different byte sequences even when their cached hash codes collide.
    @Test
    void resolvesHashCollisionsByContent() {
        ByteArrayKey first = ByteArrayKey.copyOf(new byte[]{0, 31});
        ByteArrayKey second = ByteArrayKey.copyOf(new byte[]{1, 0});
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, second);
        var map = new HashMap<ByteArrayKey, String>();
        map.put(first, "first");
        map.put(second, "second");
        assertEquals(2, map.size());
        assertEquals("first", map.get(ByteArrayKey.copyOf(new byte[]{0, 31})));
        assertEquals("second", map.get(ByteArrayKey.copyOf(new byte[]{1, 0})));
    }

    /// Supports empty sequences and enforces equality and null-input contracts.
    @Test
    void comparesByContent() {
        ByteArrayKey first = ByteArrayKey.copyOf(new byte[]{-128, 0, 127});
        ByteArrayKey second = ByteArrayKey.copyOf(new byte[]{-128, 0, 127});
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(Arrays.hashCode(new byte[]{-128, 0, 127}), first.hashCode());
        assertFalse(first.equals(null));
        assertFalse(first.equals(new byte[]{-128, 0, 127}));
        assertNotEquals(first, ByteArrayKey.copyOf(new byte[]{-128, 0}));
        assertEquals(ByteArrayKey.copyOf(new byte[0]), ByteArrayKey.copyOf(new byte[0]));
    }
}
