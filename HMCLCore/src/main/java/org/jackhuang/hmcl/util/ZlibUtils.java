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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
    /// to the original uncompressed data for the internal test vector.
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
    public static boolean IS_ZLIB_COMPATIBLE;

    static {
        byte[] testData = "Hello World!".getBytes(StandardCharsets.UTF_8);

        var compressedTestData = new ByteArrayOutputStream(testData.length * 2);

        @SuppressWarnings("resource")
        var compressor = new Deflater();
        try {
            compressor.setInput(testData);
            compressor.finish();
            while (!compressor.finished()) {
                byte[] tmpBuffer = new byte[100];
                int numCompressed = compressor.deflate(tmpBuffer);
                compressedTestData.write(tmpBuffer, 0, numCompressed);
            }
        } finally {
            compressor.end();
        }

        byte[] compressedData = compressedTestData.toByteArray();

        IS_ZLIB_COMPATIBLE = Arrays.equals(
                new byte[]{120, -100, -13, 72, -51, -55, -55, 87, 8, -49, 47, -54, 73, 81, 4, 0, 28, 73, 4, 62},
                compressedData
        );
    }

    private ZlibUtils() {
    }
}
