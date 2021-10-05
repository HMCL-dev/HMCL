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

import java.nio.charset.StandardCharsets;

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
public class MurmurHash2 {

    // Constants for 32-bit variant
    private static final int M32 = 0x5bd1e995;
    private static final int R32 = 24;

    // Constants for 64-bit variant
    private static final long M64 = 0xc6a4a7935bd1e995L;
    private static final int R64 = 47;

    /** No instance methods. */
    private MurmurHash2() {
    }

    /**
     * Generates a 32-bit hash from byte array with the given length and seed.
     *
     * @param data The input byte array
     * @param length The length of the array
     * @param seed The initial seed value
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
            int k = getLittleEndianInt(data, index);
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
            case 2:
                h ^= (data[index + 1] & 0xff) << 8;
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
     * @param data The input byte array
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
     * @param text The input string
     * @param from The starting index
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
     * @param data The input byte array
     * @param length The length of the array
     * @param seed The initial seed value
     * @return The 64-bit hash of the given array
     */
    public static long hash64(final byte[] data, final int length, final int seed) {
        long h = (seed & 0xffffffffL) ^ (length * M64);

        final int nblocks = length >> 3;

        // body
        for (int i = 0; i < nblocks; i++) {
            final int index = (i << 3);
            long k = getLittleEndianLong(data, index);

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
            case 6:
                h ^= ((long) data[index + 5] & 0xff) << 40;
            case 5:
                h ^= ((long) data[index + 4] & 0xff) << 32;
            case 4:
                h ^= ((long) data[index + 3] & 0xff) << 24;
            case 3:
                h ^= ((long) data[index + 2] & 0xff) << 16;
            case 2:
                h ^= ((long) data[index + 1] & 0xff) << 8;
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
     * @param data The input byte array
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
     * @param text The The input string
     * @param from The starting index
     * @param length The length of the substring
     * @return The 64-bit hash
     * @see #hash64(byte[], int, int)
     */
    public static long hash64(final String text, final int from, final int length) {
        return hash64(text.substring(from, from + length));
    }

    /**
     * Gets the little-endian int from 4 bytes starting at the specified index.
     *
     * @param data The data
     * @param index The index
     * @return The little-endian int
     */
    private static int getLittleEndianInt(final byte[] data, final int index) {
        return ((data[index    ] & 0xff)      ) |
                ((data[index + 1] & 0xff) <<  8) |
                ((data[index + 2] & 0xff) << 16) |
                ((data[index + 3] & 0xff) << 24);
    }

    /**
     * Gets the little-endian long from 8 bytes starting at the specified index.
     *
     * @param data The data
     * @param index The index
     * @return The little-endian long
     */
    private static long getLittleEndianLong(final byte[] data, final int index) {
        return (((long) data[index    ] & 0xff)      ) |
                (((long) data[index + 1] & 0xff) <<  8) |
                (((long) data[index + 2] & 0xff) << 16) |
                (((long) data[index + 3] & 0xff) << 24) |
                (((long) data[index + 4] & 0xff) << 32) |
                (((long) data[index + 5] & 0xff) << 40) |
                (((long) data[index + 6] & 0xff) << 48) |
                (((long) data[index + 7] & 0xff) << 56);
    }
}
