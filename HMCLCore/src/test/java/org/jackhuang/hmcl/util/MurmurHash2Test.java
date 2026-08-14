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

import java.util.Random;
import java.util.zip.Checksum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests the streaming MurmurHash2 checksum implementation.
@NotNullByDefault
public final class MurmurHash2Test {
    /// Verifies that arbitrary update boundaries produce the same value as the array implementation.
    @Test
    public void testStreamingHash32() {
        byte[] data = new byte[513];
        new Random(0x6d75726d75724cL).nextBytes(data);

        for (int length = 0; length <= data.length; length++) {
            assertStreamingHash32(data, length, 0x9747b28c ^ length * 31);
        }
    }

    /// Verifies that `getValue()` rejects incomplete and excessive input without finalizing state.
    @Test
    public void testExactLengthValidation() {
        byte[] data = {0x12, 0x34, 0x56, 0x78, (byte) 0x9a};
        int seed = 0x12345678;
        long expected = Integer.toUnsignedLong(MurmurHash2.hash32(data, data.length, seed));

        Checksum checksum = MurmurHash2.hash32(data.length, seed);
        checksum.update(data, 0, data.length - 1);
        assertThrows(IllegalStateException.class, checksum::getValue);

        checksum.update(data[data.length - 1]);
        assertEquals(expected, checksum.getValue());

        checksum.update(0xff);
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

    /// Compares bulk, byte-wise, and fragmented updates with the array implementation.
    ///
    /// @param data the input array
    /// @param length the number of bytes to hash
    /// @param seed the initial seed value
    private static void assertStreamingHash32(byte[] data, int length, int seed) {
        long expected = Integer.toUnsignedLong(MurmurHash2.hash32(data, length, seed));

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

    /// Prevents instantiation.
    private MurmurHash2Test() {
    }
}
