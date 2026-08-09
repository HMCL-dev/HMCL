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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests the 32-bit MurmurHash2 implementation against values produced by the
/// reference C++ implementation.
@NotNullByDefault
public final class MurmurHash2Test {

    /// Verifies fixed reference values, including every possible tail length.
    @Test
    public void testReferenceValues() {
        assertHash(0, new byte[0], 0L);
        assertHash(1, new byte[0], 1_540_447_798L);
        assertHash(1, new byte[]{0, 1}, 788_976_164L);
        assertHash(0, "hello".getBytes(StandardCharsets.UTF_8), 3_848_350_155L);
        assertHash(0x9747b28c, "hello".getBytes(StandardCharsets.UTF_8), 2_132_663_229L);
        assertHash(1, "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8),
                504_383_975L);

        assertHash(1, sequentialBytes(256), 253_525_554L);
        assertHash(-1, new byte[]{0, -1, -128, 127, 1, 2, 3, 4, 5}, 1_934_485_809L);
    }

    /// Verifies that update boundaries do not affect the resulting hash.
    @Test
    public void testFragmentedUpdates() {
        byte[] input = sequentialBytes(256);

        var byteAtATime = new MurmurHash2(1);
        for (byte value : input) {
            byteAtATime.update(value);
        }
        assertEquals(253_525_554L, byteAtATime.getValue());

        var chunks = new MurmurHash2(1);
        int offset = 0;
        int chunkSize = 1;
        while (offset < input.length) {
            int length = Math.min(chunkSize, input.length - offset);
            chunks.update(input, offset, length);
            offset += length;
            chunkSize = chunkSize % 7 + 1;
        }
        assertEquals(253_525_554L, chunks.getValue());
    }

    /// Verifies repeated finalization, continued updates, and reset behavior.
    @Test
    public void testLifecycle() {
        var hash = new MurmurHash2(1);
        hash.update(0x168);
        assertEquals(3_451_942_824L, hash.getValue());
        assertEquals(3_451_942_824L, hash.getValue());

        hash.update(new byte[]{'e', 'l', 'l'}, 0, 3);
        assertEquals(1_799_137_576L, hash.getValue());

        hash.update('o');
        assertEquals(2_788_266_382L, hash.getValue());

        hash.reset();
        assertEquals(1_540_447_798L, hash.getValue());
    }

    /// Verifies that invalid slice ranges are rejected without changing state.
    @Test
    public void testInvalidRangeDoesNotChangeState() {
        var hash = new MurmurHash2(1);
        byte[] input = {0, 1, 2};
        hash.update(input, 0, 2);
        long value = hash.getValue();

        assertThrows(IndexOutOfBoundsException.class, () -> hash.update(input, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> hash.update(input, 0, 4));
        assertEquals(value, hash.getValue());
    }

    /// Creates a byte array containing consecutive values starting at zero.
    ///
    /// @param length the array length
    /// @return the generated array
    private static byte[] sequentialBytes(int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) i;
        }
        return result;
    }

    /// Verifies a hash against a fixed reference value.
    ///
    /// @param seed the hash seed
    /// @param input the complete input
    /// @param expected the expected unsigned hash value
    private static void assertHash(int seed, byte[] input, long expected) {
        var hash = new MurmurHash2(seed);
        hash.update(input, 0, input.length);
        assertEquals(expected, hash.getValue());
    }
}
