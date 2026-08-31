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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.Checksum;

/**
 * Implementation of the MurmurHash2 32-bit and 64-bit hash functions.
 *
 * <p>MurmurHash is a non-cryptographic hash function suitable for general
 * hash-based lookup. The name comes from two basic operations, multiply (MU)
 * and rotate (R), used in its inner loop. Unlike cryptographic hash functions,
 * it is not specifically designed to be difficult to reverse by an adversary,
 * making it unsuitable for cryptographic purposes.</p>
 *
 * <p>This contains a Java port of the 32-bit hash function {@code MurmurHash2}
 * and the 64-bit hash function {@code MurmurHash64A} from Austin Applyby's
 * original {@code c++} code in SMHasher.</p>
 *
 * <p>This is a re-implementation of the original C code plus some additional
 * features.</p>
 *
 * <p>This is public domain code with no copyrights. From home page of
 * <a href="https://github.com/aappleby/smhasher">SMHasher</a>:</p>
 *
 * <blockquote>
 * "All MurmurHash versions are public domain software, and the author
 * disclaims all copyright to their code."
 * </blockquote>
 *
 * @see <a href="https://en.wikipedia.org/wiki/MurmurHash">MurmurHash</a>
 * @see <a href="https://github.com/aappleby/smhasher/blob/master/src/MurmurHash2.cpp">
 *   Original MurmurHash2 c++ code</a>
 * @since 1.13
 */
@NotNullByDefault
public final class MurmurHash2 {

    // Constants for 32-bit variant
    private static final int M32 = 0x5bd1e995;
    private static final int R32 = 24;

    // Constants for 64-bit variant
    private static final long M64 = 0xc6a4a7935bd1e995L;
    private static final int R64 = 47;

    /**
     * No instance methods.
     */
    private MurmurHash2() {
    }

    /// Creates a streaming MurmurHash2 32-bit checksum for exactly `length` bytes.
    ///
    /// The length is incorporated into the initial hash state using its low 32 bits. Calls to
    /// [Checksum#update(int)] and [Checksum#update(byte[], int, int)] may divide the input at
    /// arbitrary byte boundaries. [Checksum#getValue()] returns the hash as an unsigned 32-bit
    /// value represented by a `long`, and does not change the checksum state.
    ///
    /// The returned checksum verifies the number of supplied bytes when `getValue()` is called.
    /// If too few bytes have been supplied, the caller may continue updating the checksum and call
    /// `getValue()` again. Once too many bytes have been supplied, [Checksum#reset()] must be called
    /// before a value can be obtained. Resetting retains the configured length and seed. The
    /// returned checksum is mutable and is not safe for concurrent use.
    ///
    /// @param length the exact number of input bytes
    /// @param seed the initial seed value
    /// @return a new checksum initialized with the specified length and seed
    /// @throws IllegalArgumentException if `length` is negative
    public static Checksum hash32(final long length, final int seed) {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative: " + length);
        }
        return new Hash32Checksum(length, seed);
    }

    /// Computes a MurmurHash2 32-bit value from updates whose total length is known in advance.
    private static final class Hash32Checksum implements Checksum {
        /// The exact number of bytes required before the hash can be obtained.
        private final long expectedLength;

        /// The hash state restored by [#reset()].
        private final int initialHash;

        /// The hash state after all complete four-byte blocks received so far.
        private int hash;

        /// The number of bytes counted before [#inputLengthExceeded] becomes `true`.
        private long inputLength;

        /// Whether more than [#expectedLength] bytes have been received.
        private boolean inputLengthExceeded;

        /// Up to three unprocessed bytes packed in little-endian order.
        private int tail;

        /// The number of bytes currently stored in [#tail].
        private int tailLength;

        /// Creates a checksum with a precomputed initial hash state.
        ///
        /// @param expectedLength the exact number of input bytes
        /// @param seed the initial seed value
        private Hash32Checksum(long expectedLength, int seed) {
            this.expectedLength = expectedLength;
            this.initialHash = seed ^ (int) expectedLength;
            this.hash = initialHash;
        }

        /// Incorporates the low eight bits of `value` into this checksum.
        ///
        /// @param value the value whose low eight bits are incorporated
        @Override
        public void update(int value) {
            addInputLength(1);
            appendByte(value);
        }

        /// Incorporates `length` bytes beginning at `offset` into this checksum.
        ///
        /// @param data the array containing the input bytes
        /// @param offset the offset of the first input byte
        /// @param length the number of bytes to incorporate
        @Override
        public void update(byte[] data, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, data.length);
            addInputLength(length);

            int index = offset;
            final int end = offset + length;

            while (tailLength != 0 && index < end) {
                appendByte(data[index++]);
            }

            while (index <= end - Integer.BYTES) {
                mixBlock(ByteArray.getIntLE(data, index));
                index += Integer.BYTES;
            }

            while (index < end) {
                appendByte(data[index++]);
            }
        }

        /// Returns the MurmurHash2 value after verifying the exact input length.
        ///
        /// @return the unsigned 32-bit hash value represented by a `long`
        /// @throws IllegalStateException if the number of supplied bytes differs from the expected
        /// length
        @Override
        public long getValue() {
            if (inputLengthExceeded) {
                throw new IllegalStateException(
                        "Expected " + expectedLength + " bytes, but received more than expected");
            }
            if (inputLength != expectedLength) {
                throw new IllegalStateException(
                        "Expected " + expectedLength + " bytes, but received " + inputLength);
            }

            int result = hash;
            if (tailLength != 0) {
                result ^= tail;
                result *= M32;
            }

            result ^= result >>> 13;
            result *= M32;
            result ^= result >>> 15;
            return Integer.toUnsignedLong(result);
        }

        /// Restores this checksum to its initial state while retaining its expected length and seed.
        @Override
        public void reset() {
            hash = initialHash;
            inputLength = 0;
            inputLengthExceeded = false;
            tail = 0;
            tailLength = 0;
        }

        /// Records that `length` more input bytes have been supplied.
        ///
        /// @param length the non-negative number of additional bytes
        private void addInputLength(int length) {
            if (inputLengthExceeded) {
                return;
            }
            if (length > expectedLength - inputLength) {
                inputLengthExceeded = true;
            } else {
                inputLength += length;
            }
        }

        /// Buffers one byte and mixes the resulting block when four bytes are available.
        ///
        /// @param value the value whose low eight bits are appended
        private void appendByte(int value) {
            tail |= (value & 0xff) << (tailLength * Byte.SIZE);
            tailLength++;
            if (tailLength == Integer.BYTES) {
                mixBlock(tail);
                tail = 0;
                tailLength = 0;
            }
        }

        /// Mixes one little-endian four-byte block into the current hash state.
        ///
        /// @param block the block to mix
        private void mixBlock(int block) {
            int mixedBlock = block;
            mixedBlock *= M32;
            mixedBlock ^= mixedBlock >>> R32;
            mixedBlock *= M32;
            hash *= M32;
            hash ^= mixedBlock;
        }
    }

    /**
     * Generates a 32-bit hash from byte array with the given length and seed.
     *
     * @param data   The input byte array
     * @param length The length of the array
     * @param seed   The initial seed value
     * @return The 32-bit hash
     */
    public static int hash32(final byte[] data, final int length, final int seed) {
        // Initialize the hash to a random value
        int h = seed ^ length;

        // Mix 4 bytes at a time into the hash
        final int nblocks = length >> 2;

        // body
        for (int i = 0; i < nblocks; i++) {
            final int index = (i << 2);
            int k = ByteArray.getIntLE(data, index);
            k *= M32;
            k ^= k >>> R32;
            k *= M32;
            h *= M32;
            h ^= k;
        }

        // Handle the last few bytes of the input array
        final int index = (nblocks << 2);
        switch (length - index) {
            case 3:
                h ^= (data[index + 2] & 0xff) << 16;
                // fallthrough
            case 2:
                h ^= (data[index + 1] & 0xff) << 8;
                // fallthrough
            case 1:
                h ^= (data[index] & 0xff);
                h *= M32;
        }

        // Do a few final mixes of the hash to ensure the last few
        // bytes are well-incorporated.
        h ^= h >>> 13;
        h *= M32;
        h ^= h >>> 15;

        return h;
    }

    /**
     * Generates a 32-bit hash from byte array with the given length and a default seed value.
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0x9747b28c;
     * int hash = MurmurHash2.hash32(data, length, seed);
     * </pre>
     *
     * @param data   The input byte array
     * @param length The length of the array
     * @return The 32-bit hash
     * @see #hash32(byte[], int, int)
     */
    public static int hash32(final byte[] data, final int length) {
        return hash32(data, length, 0x9747b28c);
    }

    /**
     * Generates a 32-bit hash from a string with a default seed.
     * <p>
     * Before 1.14 the string was converted using default encoding.
     * Since 1.14 the string is converted to bytes using UTF-8 encoding.
     * </p>
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0x9747b28c;
     * byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
     * int hash = MurmurHash2.hash32(bytes, bytes.length, seed);
     * </pre>
     *
     * @param text The input string
     * @return The 32-bit hash
     * @see #hash32(byte[], int, int)
     */
    public static int hash32(final String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return hash32(bytes, bytes.length);
    }

    /**
     * Generates a 32-bit hash from a substring with a default seed value.
     * The string is converted to bytes using the default encoding.
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0x9747b28c;
     * byte[] bytes = text.substring(from, from + length).getBytes(StandardCharsets.UTF_8);
     * int hash = MurmurHash2.hash32(bytes, bytes.length, seed);
     * </pre>
     *
     * @param text   The input string
     * @param from   The starting index
     * @param length The length of the substring
     * @return The 32-bit hash
     * @see #hash32(byte[], int, int)
     */
    public static int hash32(final String text, final int from, final int length) {
        return hash32(text.substring(from, from + length));
    }

    /**
     * Generates a 64-bit hash from byte array of the given length and seed.
     *
     * @param data   The input byte array
     * @param length The length of the array
     * @param seed   The initial seed value
     * @return The 64-bit hash of the given array
     */
    public static long hash64(final byte[] data, final int length, final int seed) {
        long h = (seed & 0xffffffffL) ^ (length * M64);

        final int nblocks = length >> 3;

        // body
        for (int i = 0; i < nblocks; i++) {
            final int index = (i << 3);
            long k = ByteArray.getLongLE(data, index);

            k *= M64;
            k ^= k >>> R64;
            k *= M64;

            h ^= k;
            h *= M64;
        }

        final int index = (nblocks << 3);
        switch (length - index) {
            case 7:
                h ^= ((long) data[index + 6] & 0xff) << 48;
                // fallthrough
            case 6:
                h ^= ((long) data[index + 5] & 0xff) << 40;
                // fallthrough
            case 5:
                h ^= ((long) data[index + 4] & 0xff) << 32;
                // fallthrough
            case 4:
                h ^= ((long) data[index + 3] & 0xff) << 24;
                // fallthrough
            case 3:
                h ^= ((long) data[index + 2] & 0xff) << 16;
                // fallthrough
            case 2:
                h ^= ((long) data[index + 1] & 0xff) << 8;
                // fallthrough
            case 1:
                h ^= ((long) data[index] & 0xff);
                h *= M64;
        }

        h ^= h >>> R64;
        h *= M64;
        h ^= h >>> R64;

        return h;
    }

    /**
     * Generates a 64-bit hash from byte array with given length and a default seed value.
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0xe17a1465;
     * int hash = MurmurHash2.hash64(data, length, seed);
     * </pre>
     *
     * @param data   The input byte array
     * @param length The length of the array
     * @return The 64-bit hash
     * @see #hash64(byte[], int, int)
     */
    public static long hash64(final byte[] data, final int length) {
        return hash64(data, length, 0xe17a1465);
    }

    /**
     * Generates a 64-bit hash from a string with a default seed.
     * <p>
     * Before 1.14 the string was converted using default encoding.
     * Since 1.14 the string is converted to bytes using UTF-8 encoding.
     * </p>
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0xe17a1465;
     * byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
     * int hash = MurmurHash2.hash64(bytes, bytes.length, seed);
     * </pre>
     *
     * @param text The input string
     * @return The 64-bit hash
     * @see #hash64(byte[], int, int)
     */
    public static long hash64(final String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return hash64(bytes, bytes.length);
    }

    /**
     * Generates a 64-bit hash from a substring with a default seed value.
     * The string is converted to bytes using the default encoding.
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0xe17a1465;
     * byte[] bytes = text.substring(from, from + length).getBytes(StandardCharsets.UTF_8);
     * int hash = MurmurHash2.hash64(bytes, bytes.length, seed);
     * </pre>
     *
     * @param text   The The input string
     * @param from   The starting index
     * @param length The length of the substring
     * @return The 64-bit hash
     * @see #hash64(byte[], int, int)
     */
    public static long hash64(final String text, final int from, final int length) {
        return hash64(text.substring(from, from + length));
    }
}
