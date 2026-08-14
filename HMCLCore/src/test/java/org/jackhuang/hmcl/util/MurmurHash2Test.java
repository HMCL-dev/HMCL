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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Checksum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests the streaming MurmurHash2 checksum implementation.
@NotNullByDefault
public final class MurmurHash2Test {
    /// Compares whole-array, bulk, byte-wise, and fragmented updates across lengths and seeds.
    @Test
    public void testStreamingHash32AcrossLengthsAndSeeds() {
        byte[] data = new byte[513];
        new Random(0x6d75726d75724cL).nextBytes(data);

        for (int length = 0; length <= data.length; length++) {
            assertStreamingHash32(data, length, 0);
            assertStreamingHash32(data, length, -1);
            assertStreamingHash32(data, length, 0x9747b28c);
            assertStreamingHash32(data, length, 0x9747b28c ^ length * 31);
        }
    }

    /// Compares every two-part split while reading from a non-zero source-array offset.
    @Test
    public void testStreamingHash32AtEverySplitPoint() {
        byte[] data = new byte[73];
        new Random(0x73706c697473L).nextBytes(data);

        int sourceOffset = 5;
        byte[] paddedData = new byte[sourceOffset + data.length + 7];
        System.arraycopy(data, 0, paddedData, sourceOffset, data.length);

        for (int length = 0; length <= data.length; length++) {
            int seed = Integer.rotateLeft(0x4d757232, length & 31);
            long expected = hash32(data, length, seed);

            for (int split = 0; split <= length; split++) {
                Checksum checksum = MurmurHash2.hash32(length, seed);
                checksum.update(paddedData, sourceOffset, split);
                checksum.update(paddedData, sourceOffset + split, length - split);
                assertEquals(expected, checksum.getValue(),
                        "length=" + length + ", split=" + split);
            }
        }
    }

    /// Compares interleaved single-byte and bulk updates after every possible short prefix.
    @Test
    public void testStreamingHash32WithMixedUpdates() {
        byte[] data = new byte[129];
        new Random(0x6d69786564L).nextBytes(data);

        for (int length = 0; length <= data.length; length++) {
            int seed = 0x13579bdf ^ length;
            long expected = hash32(data, length, seed);

            for (int prefixLength = 0; prefixLength <= Math.min(length, 7); prefixLength++) {
                Checksum checksum = MurmurHash2.hash32(length, seed);
                int offset = 0;

                while (offset < prefixLength) {
                    checksum.update(data[offset++]);
                }

                // Exercise an empty bulk update both with and without a buffered tail.
                checksum.update(data, offset, 0);

                while (offset < length) {
                    int chunkLength = Math.min(offset % 5 + 1, length - offset);
                    checksum.update(data, offset, chunkLength);
                    offset += chunkLength;

                    if (offset < length) {
                        checksum.update(data[offset++]);
                    }
                }

                assertEquals(expected, checksum.getValue(),
                        "length=" + length + ", prefixLength=" + prefixLength);
            }
        }
    }

    /// Compares array-backed, read-only, direct, and empty-buffer updates.
    @Test
    public void testStreamingHash32WithByteBuffers() {
        byte[] data = new byte[8207];
        new Random(0x627566666572L).nextBytes(data);
        int seed = 0x2468ace0;
        long expected = hash32(data, data.length, seed);

        int sourceOffset = 3;
        byte[] paddedData = new byte[sourceOffset + data.length + 4];
        System.arraycopy(data, 0, paddedData, sourceOffset, data.length);
        ByteBuffer arrayBackedBuffer = ByteBuffer.wrap(paddedData);
        arrayBackedBuffer.position(sourceOffset);
        arrayBackedBuffer.limit(sourceOffset + data.length);
        assertTrue(arrayBackedBuffer.hasArray());

        Checksum arrayBackedChecksum = MurmurHash2.hash32(data.length, seed);
        arrayBackedChecksum.update(arrayBackedBuffer);
        assertEquals(arrayBackedBuffer.limit(), arrayBackedBuffer.position());
        assertEquals(expected, arrayBackedChecksum.getValue());

        ByteBuffer readOnlyBuffer = ByteBuffer.wrap(data).asReadOnlyBuffer();
        assertFalse(readOnlyBuffer.hasArray());

        Checksum readOnlyChecksum = MurmurHash2.hash32(data.length, seed);
        readOnlyChecksum.update(readOnlyBuffer);
        assertEquals(readOnlyBuffer.limit(), readOnlyBuffer.position());
        assertEquals(expected, readOnlyChecksum.getValue());

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(sourceOffset + data.length);
        directBuffer.position(sourceOffset);
        directBuffer.put(data);
        directBuffer.position(sourceOffset);
        assertFalse(directBuffer.hasArray());

        Checksum directChecksum = MurmurHash2.hash32(data.length, seed);
        directChecksum.update(directBuffer);
        assertEquals(directBuffer.limit(), directBuffer.position());
        assertEquals(expected, directChecksum.getValue());

        ByteBuffer emptyBuffer = ByteBuffer.allocate(4);
        emptyBuffer.position(2);
        emptyBuffer.limit(2);

        Checksum emptyFirstChecksum = MurmurHash2.hash32(data.length, seed);
        emptyFirstChecksum.update(emptyBuffer);
        emptyFirstChecksum.update(data);
        assertEquals(2, emptyBuffer.position());
        assertEquals(expected, emptyFirstChecksum.getValue());
    }

    /// Verifies that single-byte updates use only the low eight bits of each value.
    @Test
    public void testStreamingHash32SingleByteValues() {
        byte[] data = {0x12, (byte) 0xff, (byte) 0x80, 0x00};
        int seed = 0x12345678;

        Checksum checksum = MurmurHash2.hash32(data.length, seed);
        checksum.update(0x112);
        checksum.update(-1);
        checksum.update(0xabcdef80);
        checksum.update(0x100);

        assertEquals(hash32(data, data.length, seed), checksum.getValue());
    }

    /// Verifies that rejected array ranges leave the checksum state unchanged.
    @Test
    public void testStreamingHash32ArrayRangeValidation() {
        byte[] data = {0x12, 0x34, 0x56};
        int seed = 0x12345678;
        Checksum checksum = MurmurHash2.hash32(data.length, seed);

        assertThrows(NullPointerException.class, () -> checksum.update(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> checksum.update(data, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checksum.update(data, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checksum.update(data, data.length, 1));

        checksum.update(data);
        assertEquals(hash32(data, data.length, seed), checksum.getValue());
    }

    /// Verifies that `getValue()` rejects incomplete and excessive input without finalizing state.
    @Test
    public void testExactLengthValidation() {
        byte[] data = {0x12, 0x34, 0x56, 0x78, (byte) 0x9a};
        int seed = 0x12345678;
        long expected = hash32(data, data.length, seed);

        Checksum checksum = MurmurHash2.hash32(data.length, seed);
        checksum.update(data, 0, data.length - 1);
        assertThrows(IllegalStateException.class, checksum::getValue);

        checksum.update(data[data.length - 1]);
        assertEquals(expected, checksum.getValue());

        checksum.update(0xff);
        checksum.update(0xfe);
        assertThrows(IllegalStateException.class, checksum::getValue);

        checksum.reset();
        checksum.update(data, 0, data.length);
        assertEquals(expected, checksum.getValue());
    }

    /// Verifies the valid range of the declared input length.
    @Test
    public void testLengthRange() {
        assertThrows(IllegalArgumentException.class, () -> MurmurHash2.hash32(-1, 0));

        Checksum checksum = MurmurHash2.hash32(1L << 32, 0);
        assertThrows(IllegalStateException.class, checksum::getValue);
    }

    /// Compares the principal update styles with the byte-array implementation.
    ///
    /// @param data the input array
    /// @param length the number of bytes to hash
    /// @param seed the initial seed value
    private static void assertStreamingHash32(byte[] data, int length, int seed) {
        long expected = hash32(data, length, seed);

        byte[] exactData = Arrays.copyOf(data, length);
        Checksum wholeArray = MurmurHash2.hash32(length, seed);
        wholeArray.update(exactData);
        assertEquals(expected, wholeArray.getValue());

        Checksum bulk = MurmurHash2.hash32(length, seed);
        bulk.update(data, 0, length);
        assertEquals(expected, bulk.getValue());
        assertEquals(expected, bulk.getValue());

        Checksum byteWise = MurmurHash2.hash32(length, seed);
        for (int i = 0; i < length; i++) {
            byteWise.update(data[i]);
        }
        assertEquals(expected, byteWise.getValue());

        Checksum fragmented = MurmurHash2.hash32(length, seed);
        int offset = 0;
        while (offset < length) {
            int chunkLength = Math.min(offset % 7 + 1, length - offset);
            fragmented.update(data, offset, chunkLength);
            offset += chunkLength;
        }
        assertEquals(expected, fragmented.getValue());
    }

    /// Returns the unsigned value produced by the byte-array implementation.
    ///
    /// @param data the input array
    /// @param length the number of bytes to hash
    /// @param seed the initial seed value
    /// @return the unsigned 32-bit hash value represented by a `long`
    private static long hash32(byte[] data, int length, int seed) {
        return Integer.toUnsignedLong(MurmurHash2.hash32(data, length, seed));
    }

    /// Prevents instantiation.
    private MurmurHash2Test() {
    }
}
