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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/// An immutable byte sequence with content-based equality and a cached hash code.
/// Use [#copyOf(byte[])] to copy mutable input, or [#wrap(byte[])] to retain an
/// array that the caller guarantees will not be modified.
@NotNullByDefault
public final class ByteArrayKey {
    /// The retained bytes, which must not change after construction.
    private final byte @Unmodifiable [] bytes;

    /// The content hash computed during construction.
    private final int hashCode;

    /// Retains the supplied bytes without copying and computes their content hash.
    /// The array must not be modified after construction.
    private ByteArrayKey(byte @Unmodifiable [] bytes) {
        this.bytes = Objects.requireNonNull(bytes);
        this.hashCode = Arrays.hashCode(bytes);
    }

    /// Creates a key by parsing pairs of hexadecimal digits without separators.
    /// Both uppercase and lowercase ASCII digits are accepted; an empty string creates an empty key.
    /// The decoded array is owned by the key and is not copied again.
    ///
    /// @param hex the hexadecimal representation of the bytes
    /// @throws IllegalArgumentException if the length is odd or a character is not a hexadecimal digit
    /// @throws NullPointerException if the string is null
    public static ByteArrayKey fromHexString(String hex) {
        return new ByteArrayKey(HexFormat.of().parseHex(hex));
    }

    /// Creates a key containing a copy of the supplied bytes, which may be empty.
    /// Subsequent changes to the input array do not affect the key.
    ///
    /// @param bytes the bytes to copy
    /// @throws NullPointerException if the array is null
    public static ByteArrayKey copyOf(byte[] bytes) {
        return new ByteArrayKey(bytes.clone());
    }

    /// Creates a key that retains the supplied array without copying it.
    /// The caller must ensure that the array is never modified after this call, including
    /// through other references. Modifying it violates the key's immutability and can make
    /// its cached hash inconsistent with its contents, invalidating hash-collection lookups.
    /// Use [#copyOf(byte[])] when this requirement cannot be met.
    ///
    /// @param bytes the bytes to retain, which may be empty
    /// @throws NullPointerException if the array is null
    public static ByteArrayKey wrap(byte @Unmodifiable [] bytes) {
        return new ByteArrayKey(bytes);
    }

    /// Returns a new array containing this key's bytes.
    /// Changes to the returned array do not affect this key.
    public byte[] toByteArray() {
        return bytes.clone();
    }

    /// Returns whether the other object is a [ByteArrayKey] with the same byte sequence.
    /// Hash collisions do not make distinct byte sequences equal.
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof ByteArrayKey key
                && hashCode == key.hashCode && Arrays.equals(bytes, key.bytes);
    }

    /// Returns the cached value of [Arrays#hashCode(byte[])] for this key's bytes.
    @Override
    public int hashCode() {
        return hashCode;
    }
}
