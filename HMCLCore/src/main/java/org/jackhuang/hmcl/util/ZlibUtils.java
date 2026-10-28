// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Arrays;
import java.util.zip.Deflater;

/// Utilities for detecting the compatibility of the system's zlib implementation.
///
/// Some systems replace the JVM's default zlib with an alternative such as zlib-ng,
/// which may produce compressed output that differs byte-for-byte from the output of
/// the standard zlib. This can cause hash mismatches when verifying artifacts whose
/// expected checksums were computed with a specific zlib implementation.
///
/// @author Glavo
@NotNullByDefault
public final class ZlibUtils {

    /// Whether the JVM's [Deflater] produces output that is byte-for-byte identical
    /// to the expected output of the standard reference zlib for a known test vector.
    ///
    /// This flag is `true` when the system's zlib behaves like the standard reference
    /// implementation, and `false` when an alternative library such as zlib-ng is in
    /// use and may produce different compressed bytes.
    ///
    /// When this flag is `false`, hash verification for dynamically generated JAR
    /// files (e.g., those produced by the Forge/NeoForge installer processors) may
    /// be relaxed to a structural integrity check rather than an exact hash match,
    /// because the alternative zlib may legitimately produce files with different
    /// checksums.
    public static final boolean IS_ZLIB_COMPATIBLE;

    static {
        var expectedCompressed = new byte[]{120, -100, 99, 96, 0, 2, 0, 0, 5, 0, 1};
        byte[] compressed = new byte[64];
        int compressedLength = 0;

        var deflater = new Deflater();
        try {
            deflater.setInput(new byte[5]);
            deflater.finish();

            while (!deflater.finished()) {
                compressedLength += deflater.deflate(compressed, compressedLength, compressed.length - compressedLength);
            }
        } finally {
            deflater.end();
        }

        IS_ZLIB_COMPATIBLE = Arrays.equals(
                expectedCompressed,
                0, expectedCompressed.length,
                compressed,
                0, compressedLength
        );
    }

    private ZlibUtils() {
    }
}
