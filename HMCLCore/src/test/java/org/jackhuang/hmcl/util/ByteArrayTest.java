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

import java.util.HexFormat;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.ByteArray.*;
import static org.junit.jupiter.api.Assertions.*;

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

    private static byte[] byteArray(String string) {
        try {
            return HexFormat.of().parseHex(string);
        } catch (IllegalArgumentException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] changedArray(Consumer<byte[]> consumer) {
        var array = TEST_ARRAY.clone();
        consumer.accept(array);
        return array;
    }

    @Test
    public void testSetByte() {
        assertArrayEquals(byteArray("ff3456789abcdef000"),
                changedArray(array -> setByte(array, 0, (byte) 0xff)));
        assertArrayEquals(byteArray("123456789affdef000"),
                changedArray(array -> setByte(array, 5, (byte) 0xff)));
        assertArrayEquals(byteArray("123456789abcdef0ff"),
                changedArray(array -> setByte(array, 8, (byte) 0xff)));

        assertArrayEquals(byteArray("ff3456789abcdef000"),
                changedArray(array -> setUnsignedByte(array, 0, 0xff)));
        assertArrayEquals(byteArray("123456789affdef000"),
                changedArray(array -> setUnsignedByte(array, 5, 0xff)));
        assertArrayEquals(byteArray("123456789abcdef0ff"),
                changedArray(array -> setUnsignedByte(array, 8, 0xff)));

        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setByte(array, -1, (byte) 0xff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setByte(array, 9, (byte) 0xff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedByte(array, -1, 0xff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedByte(array, 9, 0xff)));
    }

    @Test
    public void testSetShort() {
        assertArrayEquals(byteArray("ffee56789abcdef000"),
                changedArray(array -> setShortLE(array, 0, (short) 0xeeff)));
        assertArrayEquals(byteArray("123456789affeef000"),
                changedArray(array -> setShortLE(array, 5, (short) 0xeeff)));
        assertArrayEquals(byteArray("123456789abcdeffee"),
                changedArray(array -> setShortLE(array, 7, (short) 0xeeff)));

        assertArrayEquals(byteArray("ffee56789abcdef000"),
                changedArray(array -> setUnsignedShortLE(array, 0, 0xeeff)));
        assertArrayEquals(byteArray("123456789affeef000"),
                changedArray(array -> setUnsignedShortLE(array, 5, 0xeeff)));
        assertArrayEquals(byteArray("123456789abcdeffee"),
                changedArray(array -> setUnsignedShortLE(array, 7, 0xeeff)));

        assertArrayEquals(byteArray("eeff56789abcdef000"),
                changedArray(array -> setShortBE(array, 0, (short) 0xeeff)));
        assertArrayEquals(byteArray("123456789aeefff000"),
                changedArray(array -> setShortBE(array, 5, (short) 0xeeff)));
        assertArrayEquals(byteArray("123456789abcdeeeff"),
                changedArray(array -> setShortBE(array, 7, (short) 0xeeff)));

        assertArrayEquals(byteArray("eeff56789abcdef000"),
                changedArray(array -> setUnsignedShortBE(array, 0, 0xeeff)));
        assertArrayEquals(byteArray("123456789aeefff000"),
                changedArray(array -> setUnsignedShortBE(array, 5, 0xeeff)));
        assertArrayEquals(byteArray("123456789abcdeeeff"),
                changedArray(array -> setUnsignedShortBE(array, 7, 0xeeff)));

        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setShortLE(array, -1, (short) 0xeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setShortLE(array, 8, (short) 0xeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedShortLE(array, -1, 0xeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedShortLE(array, 8, 0xeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setShortBE(array, -1, (short) 0xeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setShortBE(array, 8, (short) 0xeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedShortBE(array, -1, 0xeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedShortBE(array, 8, 0xeeff)));
    }

    @Test
    public void testSetInt() {
        assertArrayEquals(byteArray("ffeeddcc9abcdef000"),
                changedArray(array -> setIntLE(array, 0, 0xccddeeff)));
        assertArrayEquals(byteArray("12345678ffeeddcc00"),
                changedArray(array -> setIntLE(array, 4, 0xccddeeff)));
        assertArrayEquals(byteArray("123456789affeeddcc"),
                changedArray(array -> setIntLE(array, 5, 0xccddeeff)));

        assertArrayEquals(byteArray("ffeeddcc9abcdef000"),
                changedArray(array -> setUnsignedIntLE(array, 0, 0xccddeeffL)));
        assertArrayEquals(byteArray("12345678ffeeddcc00"),
                changedArray(array -> setUnsignedIntLE(array, 4, 0xccddeeffL)));
        assertArrayEquals(byteArray("123456789affeeddcc"),
                changedArray(array -> setUnsignedIntLE(array, 5, 0xccddeeffL)));

        assertArrayEquals(byteArray("ccddeeff9abcdef000"),
                changedArray(array -> setIntBE(array, 0, 0xccddeeff)));
        assertArrayEquals(byteArray("12345678ccddeeff00"),
                changedArray(array -> setIntBE(array, 4, 0xccddeeff)));
        assertArrayEquals(byteArray("123456789accddeeff"),
                changedArray(array -> setIntBE(array, 5, 0xccddeeff)));

        assertArrayEquals(byteArray("ccddeeff9abcdef000"),
                changedArray(array -> setUnsignedIntBE(array, 0, 0xccddeeffL)));
        assertArrayEquals(byteArray("12345678ccddeeff00"),
                changedArray(array -> setUnsignedIntBE(array, 4, 0xccddeeffL)));
        assertArrayEquals(byteArray("123456789accddeeff"),
                changedArray(array -> setUnsignedIntBE(array, 5, 0xccddeeffL)));

        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setIntLE(array, -1, 0xccddeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setIntLE(array, 8, 0xccddeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedIntLE(array, -1, 0xccddeeffL)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedIntLE(array, 8, 0xccddeeffL)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setIntBE(array, -1, 0xccddeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setIntBE(array, 8, 0xccddeeff)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedIntBE(array, -1, 0xccddeeffL)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setUnsignedIntBE(array, 8, 0xccddeeffL)));
    }

    @Test
    public void testSetLong() {
        assertArrayEquals(byteArray("ffeeddccbbaa998800"),
                changedArray(array -> setLongLE(array, 0, 0x8899aabbccddeeffL)));
        assertArrayEquals(byteArray("12ffeeddccbbaa9988"),
                changedArray(array -> setLongLE(array, 1, 0x8899aabbccddeeffL)));

        assertArrayEquals(byteArray("8899aabbccddeeff00"),
                changedArray(array -> setLongBE(array, 0, 0x8899aabbccddeeffL)));
        assertArrayEquals(byteArray("128899aabbccddeeff"),
                changedArray(array -> setLongBE(array, 1, 0x8899aabbccddeeffL)));

        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setLongLE(array, -1, 0x8899aabbccddeeffL)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setLongLE(array, 2, 0x8899aabbccddeeffL)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setLongBE(array, -1, 0x8899aabbccddeeffL)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> changedArray(array -> setLongBE(array, 2, 0x8899aabbccddeeffL)));
    }
}
