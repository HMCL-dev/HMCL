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

import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.util.ByteArray.*;
import static org.jackhuang.hmcl.util.ByteArray.getShortBE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Glavo
 */
public final class ByteArrayTest {
    private static final byte[] TEST_ARRAY = {
            (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78,
            (byte) 0x9a, (byte) 0xbc, (byte) 0xde, (byte) 0xf0,
            (byte) 0x00
    };

    @Test
    public void testGetByte() {
        assertEquals((byte) 0x12, getByte(TEST_ARRAY, 0));
        assertEquals((byte) 0x78, getByte(TEST_ARRAY, 3));
        assertEquals((byte) 0x00, getByte(TEST_ARRAY, 8));

        assertEquals(0x12, getUnsignedByte(TEST_ARRAY, 0));
        assertEquals(0x78, getUnsignedByte(TEST_ARRAY, 3));
        assertEquals(0x00, getUnsignedByte(TEST_ARRAY, 8));

        assertThrows(IndexOutOfBoundsException.class, () -> getByte(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getByte(TEST_ARRAY, 9));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedByte(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedByte(TEST_ARRAY, 9));
    }

    @Test
    public void testGetShort() {
        assertEquals((short) 0x3412, getShortLE(TEST_ARRAY, 0));
        assertEquals((short) 0x5634, getShortLE(TEST_ARRAY, 1));
        assertEquals((short) 0x00f0, getShortLE(TEST_ARRAY, 7));

        assertEquals(0x3412, getUnsignedShortLE(TEST_ARRAY, 0));
        assertEquals(0x5634, getUnsignedShortLE(TEST_ARRAY, 1));
        assertEquals(0x00f0, getUnsignedShortLE(TEST_ARRAY, 7));

        assertEquals((short) 0x1234, getShortBE(TEST_ARRAY, 0));
        assertEquals((short) 0x3456, getShortBE(TEST_ARRAY, 1));
        assertEquals((short) 0xf000, getShortBE(TEST_ARRAY, 7));

        assertEquals(0x1234, getUnsignedShortBE(TEST_ARRAY, 0));
        assertEquals(0x3456, getUnsignedShortBE(TEST_ARRAY, 1));
        assertEquals(0xf000, getUnsignedShortBE(TEST_ARRAY, 7));

        assertThrows(IndexOutOfBoundsException.class, () -> getShortLE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getShortLE(TEST_ARRAY, 8));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedShortLE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedShortLE(TEST_ARRAY, 8));
        assertThrows(IndexOutOfBoundsException.class, () -> getShortBE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getShortBE(TEST_ARRAY, 8));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedShortBE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedShortBE(TEST_ARRAY, 8));
    }

    @Test
    public void testGetInt() {
        assertEquals(0x78563412, getIntLE(TEST_ARRAY, 0));
        assertEquals(0x9a785634, getIntLE(TEST_ARRAY, 1));
        assertEquals(0x00f0debc, getIntLE(TEST_ARRAY, 5));

        assertEquals(0x78563412L, getUnsignedIntLE(TEST_ARRAY, 0));
        assertEquals(0x9a785634L, getUnsignedIntLE(TEST_ARRAY, 1));
        assertEquals(0x00f0debcL, getUnsignedIntLE(TEST_ARRAY, 5));

        assertEquals(0x12345678, getIntBE(TEST_ARRAY, 0));
        assertEquals(0x3456789a, getIntBE(TEST_ARRAY, 1));
        assertEquals(0xbcdef000, getIntBE(TEST_ARRAY, 5));

        assertEquals(0x12345678L, getUnsignedIntBE(TEST_ARRAY, 0));
        assertEquals(0x3456789aL, getUnsignedIntBE(TEST_ARRAY, 1));
        assertEquals(0xbcdef000L, getUnsignedIntBE(TEST_ARRAY, 5));

        assertThrows(IndexOutOfBoundsException.class, () -> getIntLE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getIntLE(TEST_ARRAY, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedIntLE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedIntLE(TEST_ARRAY, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> getIntBE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getIntBE(TEST_ARRAY, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedIntBE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getUnsignedIntBE(TEST_ARRAY, 6));
    }

    @Test
    public void testGetLong() {
        assertEquals(0xf0debc9a78563412L, getLongLE(TEST_ARRAY, 0));
        assertEquals(0x00f0debc9a785634L, getLongLE(TEST_ARRAY, 1));

        assertEquals(0x123456789abcdef0L, getLongBE(TEST_ARRAY, 0));
        assertEquals(0x3456789abcdef000L, getLongBE(TEST_ARRAY, 1));

        assertThrows(IndexOutOfBoundsException.class, () -> getLongLE(TEST_ARRAY, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> getLongLE(TEST_ARRAY, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> getLongBE(TEST_ARRAY, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> getLongBE(TEST_ARRAY, -1));
    }

}
