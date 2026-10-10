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
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies ZIP filename detection and decoding for short and marked names.
///
/// @author Glavo
@NotNullByDefault
public final class CompressingUtilsTest {

    /// Supplies archives with known filename encodings.
    public static Stream<Arguments> arguments() {
        return Stream.of(
                pair("utf-8.zip", StandardCharsets.UTF_8),
                pair("gbk.zip", Charset.forName("GB18030"))
        ).map(pair -> {
            try {
                return Arguments.of(Paths.get(Objects.requireNonNull(
                        CompressingUtilsTest.class.getResource("/zip/" + pair.getKey())).toURI()), pair.getValue());
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        });
    }

    /// Checks the selected charset against the fixture's encoding.
    @ParameterizedTest
    @MethodSource("arguments")
    public void testFindSuitableEncoding(Path path, Charset charset) throws IOException {
        assertEquals(charset, CompressingUtils.findSuitableEncoding(path));
    }

    /// Checks that automatic ZIP access exposes the original short Chinese name.
    @ParameterizedTest
    @MethodSource("arguments")
    public void testOpenZipFile(Path path) throws IOException {
        try (var reader = CompressingUtils.openZipFile(path)) {
            assertNotNull(reader.getEntry("\u4e2d\u6587.txt"));
            assertNotNull(reader.getEntry("test.txt"));
        }
    }

    /// Checks the UTF-8 convention for an archive without filename evidence.
    @Test
    public void testEmptyArchive(@TempDir Path directory) throws IOException {
        Path zip = writeZip(directory, StandardCharsets.UTF_8);
        assertEquals(StandardCharsets.UTF_8, CompressingUtils.findSuitableEncoding(zip));
    }

    /// Checks that ASCII names remain readable without an explicit UTF-8 flag.
    @Test
    public void testAsciiNames(@TempDir Path directory) throws IOException {
        Path zip = writeZip(directory, StandardCharsets.ISO_8859_1, "test.txt", "folder/another.txt");
        assertEquals(StandardCharsets.UTF_8, CompressingUtils.findSuitableEncoding(zip));
    }

    /// Checks UTF-8 names whose archive does not declare their encoding.
    @Test
    public void testUnmarkedUtf8(@TempDir Path directory) throws IOException {
        String name = "\u4e2d\u6587.txt";
        Path zip = writeZip(directory, StandardCharsets.ISO_8859_1,
                new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        assertEquals(StandardCharsets.UTF_8, CompressingUtils.findSuitableEncoding(zip));
        try (var reader = CompressingUtils.openZipFile(zip)) {
            assertNotNull(reader.getEntry(name));
        }
    }

    /// Checks that an authoritative Unicode path bypasses legacy-name detection.
    @Test
    public void testUnicodeExtraField(@TempDir Path directory) throws IOException {
        Path zip = directory.resolve("unicode.zip");
        String name = "\u4e2d\u6587.txt";
        byte @Unmodifiable [] rawName = name.getBytes(Charset.forName("GB18030"));
        byte @Unmodifiable [] unicodeName = name.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(rawName);
        ByteBuffer extra = ByteBuffer.allocate(9 + unicodeName.length).order(ByteOrder.LITTLE_ENDIAN);
        extra.putShort((short) 0x7075).putShort((short) (5 + unicodeName.length));
        extra.put((byte) 1).putInt((int) crc.getValue()).put(unicodeName);

        try (var out = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.ISO_8859_1)) {
            ZipEntry entry = new ZipEntry(new String(rawName, StandardCharsets.ISO_8859_1));
            entry.setExtra(extra.array());
            out.putNextEntry(entry);
            out.closeEntry();
        }

        assertEquals(StandardCharsets.UTF_8, CompressingUtils.findSuitableEncoding(zip));
        try (var reader = CompressingUtils.openZipFile(zip)) {
            assertNotNull(reader.getEntry(name));
        }
    }

    /// Checks that validation also covers entries beyond the statistical sample.
    @Test
    public void testInvalidUtf8BeyondSample(@TempDir Path directory) throws IOException {
        Path zip = directory.resolve("mixed.zip");
        String prefix = "\u4e2d\u6587".repeat(5000);
        try (var out = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.ISO_8859_1)) {
            for (int i = 0; i < 10; i++) {
                String name = prefix + i + ".txt";
                out.putNextEntry(new ZipEntry(new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)));
                out.closeEntry();
            }
            byte @Unmodifiable [] rawName = "\u4e2d\u6587.txt".getBytes(Charset.forName("GB18030"));
            out.putNextEntry(new ZipEntry(new String(rawName, StandardCharsets.ISO_8859_1)));
            out.closeEntry();
        }

        // The sampled UTF-8 candidate cannot decode the last entry.
        assertThrows(IOException.class, () -> CompressingUtils.findSuitableEncoding(zip));
        assertThrows(IOException.class, () -> CompressingUtils.openZipFile(zip));
    }

    /// Writes an empty entry for each name using the supplied ZIP charset.
    private static Path writeZip(Path directory, Charset charset, String @Unmodifiable ... names) throws IOException {
        Path zip = directory.resolve("test.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip), charset)) {
            for (String name : names) {
                out.putNextEntry(new ZipEntry(name));
                out.closeEntry();
            }
        }
        return zip;
    }
}
