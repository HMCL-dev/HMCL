/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.util.Arrays;
import java.util.Objects;
import java.util.zip.Checksum;

/// Computes the 32-bit MurmurHash2 value of a sequence of bytes.
///
/// Bytes supplied by successive update operations are treated as one contiguous
/// input. Four-byte words are decoded in little-endian order, matching the
/// reference implementation on little-endian platforms. [#getValue()] returns
/// the resulting unsigned 32-bit value as a `long`.
///
/// Instances are not safe for concurrent use.
///
/// @apiNote MurmurHash2 is a non-cryptographic hash and must not be used where
/// collision resistance or protection against malicious input is required.
/// @implNote The reference algorithm incorporates the final input length before
/// processing any words, so its state cannot be finalized incrementally. This
/// implementation retains one pre-mixed value for every complete input word.
/// Storage therefore grows linearly with the largest input processed by an
/// instance and is reused after [#reset()].
/// @see <a href="https://github.com/rurban/smhasher/blob/master/MurmurHash2.cpp">MurmurHash2.cpp</a>
@NotNullByDefault
public final class MurmurHash2 implements Checksum {
    /// Multiplication constant used by the 32-bit MurmurHash2 mixer.
    private static final int MIX_MULTIPLIER = 0x5bd1e995;

    /// Right-shift distance used by the 32-bit MurmurHash2 mixer.
    private static final int MIX_SHIFT = 24;

    /// Initial capacity of the complete-word buffer.
    private static final int INITIAL_BLOCK_CAPACITY = 16;

    /// Seed mixed into the initial hash value.
    private final int seed;

    /// Pre-mixed values of all complete four-byte input words.
    private int[] mixedBlocks = new int[INITIAL_BLOCK_CAPACITY];

    /// Number of entries in [#mixedBlocks] that contain input words.
    private int blockCount;

    /// Incomplete trailing word assembled in little-endian order.
    private int tail;

    /// Number of input bytes currently stored in [#tail].
    private int tailLength;

    /// Total input length modulo 2<sup>32</sup>.
    private int length;

    /// Cached unsigned hash value returned by [#getValue()].
    private long value;

    /// Whether [#value] represents all input supplied so far.
    private boolean valueValid;

    /// Creates an empty MurmurHash2 checksum with the specified seed.
    ///
    /// @param seed the 32-bit seed mixed into the hash
    public MurmurHash2(int seed) {
        this.seed = seed;
    }

    /// Appends the low eight bits of `b` to the input sequence.
    ///
    /// @param b the value whose low eight bits are appended
    @Override
    public void update(int b) {
        valueValid = false;
        length++;

        tail |= (b & 0xff) << (tailLength * Byte.SIZE);
        tailLength++;

        if (tailLength == Integer.BYTES) {
            addBlock(tail);
            tail = 0;
            tailLength = 0;
        }
    }

    /// Appends `len` bytes starting at `off` in `b` to the input sequence.
    ///
    /// A zero-length update leaves the current value unchanged.
    ///
    /// @param b the array containing the bytes to append
    /// @param off the offset of the first byte to append
    /// @param len the number of bytes to append
    /// @throws NullPointerException if `b` is `null`
    /// @throws IndexOutOfBoundsException if `off` or `len` is negative, or if
    /// `off + len` is greater than the array length
    @Override
    public void update(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return;
        }

        valueValid = false;
        length += len;

        int end = off + len;

        // Complete a word left over from a preceding update before processing
        // directly from the caller's array.
        while (tailLength != 0 && off < end) {
            tail |= (b[off++] & 0xff) << (tailLength * Byte.SIZE);
            tailLength++;

            if (tailLength == Integer.BYTES) {
                addBlock(tail);
                tail = 0;
                tailLength = 0;
            }
        }

        while (off <= end - Integer.BYTES) {
            addBlock(ByteArray.getIntLE(b, off));
            off += Integer.BYTES;
        }

        while (off < end) {
            tail |= (b[off++] & 0xff) << (tailLength * Byte.SIZE);
            tailLength++;
        }
    }

    /// Returns the MurmurHash2 value of all bytes supplied since construction
    /// or the last call to [#reset()].
    ///
    /// The value is in the range `0` through `0xffff_ffffL`. This method does
    /// not reset the checksum; later updates append to the same input sequence.
    ///
    /// @return the current unsigned 32-bit hash value
    @Override
    public long getValue() {
        if (valueValid) {
            return value;
        }

        int hash = seed ^ length;

        for (int i = 0; i < blockCount; i++) {
            hash *= MIX_MULTIPLIER;
            hash ^= mixedBlocks[i];
        }

        if (tailLength != 0) {
            hash ^= tail;
            hash *= MIX_MULTIPLIER;
        }

        hash ^= hash >>> 13;
        hash *= MIX_MULTIPLIER;
        hash ^= hash >>> 15;

        value = Integer.toUnsignedLong(hash);
        valueValid = true;
        return value;
    }

    /// Discards all accumulated input while retaining the seed.
    ///
    /// After this method returns, [#getValue()] produces the hash of an empty
    /// input with the seed supplied to [#MurmurHash2(int)].
    @Override
    public void reset() {
        blockCount = 0;
        tail = 0;
        tailLength = 0;
        length = 0;
        value = 0;
        valueValid = false;
    }

    /// Pre-mixes and stores one complete little-endian input word.
    ///
    /// @param block the input word to store
    private void addBlock(int block) {
        if (blockCount == mixedBlocks.length) {
            int newCapacity = mixedBlocks.length << 1;
            if (newCapacity <= mixedBlocks.length) {
                newCapacity = Integer.MAX_VALUE;
            }
            mixedBlocks = Arrays.copyOf(mixedBlocks, newCapacity);
        }

        mixedBlocks[blockCount++] = mixBlock(block);
    }

    /// Applies the MurmurHash2 word mixer to one complete input word.
    ///
    /// @param block the input word
    /// @return the mixed word
    private static int mixBlock(int block) {
        block *= MIX_MULTIPLIER;
        block ^= block >>> MIX_SHIFT;
        block *= MIX_MULTIPLIER;
        return block;
    }
}
