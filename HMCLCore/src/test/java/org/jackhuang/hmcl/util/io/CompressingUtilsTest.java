// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.CRC32;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies filename detection against raw ZIP headers and decoded archive paths.
///
/// @author Glavo
@NotNullByDefault
public final class CompressingUtilsTest {

    /// Directory containing generated archives.
    @TempDir
    Path temporaryDirectory;

    /// Supplies the original UTF-8 and GBK regression archives.
    public static Stream<Arguments> arguments() {
        return Stream.of(
                pair("utf-8.zip", StandardCharsets.UTF_8),
                pair("gbk.zip", Charset.forName("GB18030"))
        ).map(pair -> {
            try {
                return Arguments.of(Paths.get(Objects.requireNonNull(CompressingUtilsTest.class.getResource("/zip/" + pair.getKey())).toURI()), pair.getValue());
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        });
    }

    /// Preserves detection of the original regression archives.
    @ParameterizedTest
    @MethodSource("arguments")
    public void testFindSuitableEncoding(Path path, Charset charset) throws IOException {
        assertEquals(charset, CompressingUtils.findSuitableEncoding(path));
    }

    /// Supplies names whose language distinguishes otherwise valid byte decodings.
    static Stream<Arguments> filenameEncodings() {
        return Stream.of(
                Arguments.of("UTF-8", "\u4e2d\u6587\u6587\u4ef6\u540d\u79f0"),
                Arguments.of("GB18030", "\u8fd9\u662f\u4e00\u4e2a\u7528\u4e8e\u6d4b\u8bd5\u7684\u4e2d\u6587\u6587\u4ef6\u540d\u79f0"),
                Arguments.of("Big5", "\u9019\u662f\u4e00\u500b\u7528\u65bc\u6e2c\u8a66\u7684\u7e41\u9ad4\u4e2d\u6587\u6a94\u6848\u540d\u7a31"),
                Arguments.of("windows-31j", "\u3053\u308c\u306f\u65e5\u672c\u8a9e\u306e\u30d5\u30a1\u30a4\u30eb\u540d\u3067\u3059"),
                Arguments.of("EUC-JP", "\u3053\u308c\u306f\u65e5\u672c\u8a9e\u306e\u30d5\u30a1\u30a4\u30eb\u540d\u3067\u3059"),
                Arguments.of("EUC-KR", "\uc774\uac83\uc740\ud55c\uad6d\uc5b4\ud30c\uc77c\uc774\ub984\uc785\ub2c8\ub2e4"),
                Arguments.of("windows-1251", "\u042d\u0442\u043e \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u0444\u0430\u0439\u043b\u0430 \u043d\u0430 \u0440\u0443\u0441\u0441\u043a\u043e\u043c \u044f\u0437\u044b\u043a\u0435"),
                Arguments.of("windows-1252", "Les fichiers fran\u00e7ais avec des caract\u00e8res accentu\u00e9s")
        );
    }

    /// Verifies decoded names through both archive access paths, including ambiguous bytes.
    @ParameterizedTest
    @MethodSource("filenameEncodings")
    void decodesLegacyNames(String encoding, String stem) throws IOException {
        Charset charset = Charset.forName(encoding);
        String name = stem + ".txt";
        byte[] rawName = name.getBytes(charset);
        assertEquals(name, charset.newDecoder().decode(ByteBuffer.wrap(rawName)).toString());
        Path archive = writeArchive(new RawEntry(rawName, 0, new byte[0]));
        assertNames(archive, List.of(name));
    }

    /// Preserves UTF-8 defaults for empty and ASCII-only archives.
    @Test
    void detectsEmptyAndAsciiArchives() throws IOException {
        for (Path archive : List.of(writeArchive(), writeArchive(entry("plain.txt", StandardCharsets.US_ASCII)))) {
            assertEquals(StandardCharsets.UTF_8, CompressingUtils.findSuitableEncoding(archive));
            try (var reader = CompressingUtils.openZipFile(archive)) {
                assertEquals(StandardCharsets.UTF_8, CompressingUtils.findSuitableEncoding(reader));
            }
        }
    }

    /// Excludes explicitly UTF-8 names when selecting the legacy encoding.
    @Test
    void preservesUtf8FlagsInLegacyArchives() throws IOException {
        String legacyName = "\u4e2d\u6587.txt";
        String utf8Name = "\u65e5\u672c\u8a9e.txt";
        Path archive = writeArchive(entry(legacyName, Charset.forName("GB18030")),
                new RawEntry(utf8Name.getBytes(StandardCharsets.UTF_8), 0x800, new byte[0]));
        // Some JDK zipfs implementations cannot address mixed EFS and legacy names.
        try (var reader = CompressingUtils.openZipFile(archive)) {
            assertEquals(List.of(legacyName, utf8Name), StreamSupport.stream(reader.getEntries().spliterator(), false)
                    .map(ZipArchiveEntry::getName).toList());
        }
    }

    /// Excludes Unicode-resolved names from both detection entry points.
    @Test
    void excludesUnicodeMetadataFromDetection() throws IOException {
        String rawName = "\u4e2d\u6587.txt";
        String unicodeName = "resolved-name.txt";
        byte[] bytes = rawName.getBytes(Charset.forName("GB18030"));
        Path archive = writeArchive(new RawEntry(bytes, 0, unicodeExtra(bytes, unicodeName, true)));
        try (var reader = CompressingUtils.openZipFile(archive)) {
            assertEquals(StandardCharsets.UTF_8, CompressingUtils.findSuitableEncoding(reader));
            assertEquals(unicodeName, reader.getEntries().iterator().next().getName());
            assertEquals(ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD, reader.getEntries().iterator().next().getNameSource());
        }
        assertEquals(StandardCharsets.UTF_8, CompressingUtils.findSuitableEncoding(archive));
    }

    /// Excludes Unicode-resolved raw bytes even when validating a legacy candidate.
    @Test
    void excludesUnicodeNamesFromLegacyValidation() throws IOException {
        String name = "\u4e2d\u6587.txt";
        Charset charset = Charset.forName("GB18030");
        byte[] invalidName = {(byte) 0xff};
        Path archive = writeArchive(entry(name, charset),
                new RawEntry(invalidName, 0, unicodeExtra(invalidName, "resolved.txt", true)));
        try (var reader = CompressingUtils.openZipFile(archive, StandardCharsets.UTF_8)) {
            assertEquals(charset, CompressingUtils.findSuitableEncoding(reader));
        }
        assertEquals(charset, CompressingUtils.findSuitableEncoding(archive));
    }

    /// Ignores a Unicode extra field whose CRC does not match the original name.
    @Test
    void rejectsInvalidUnicodeMetadata() throws IOException {
        String name = "\u4e2d\u6587.txt";
        byte[] bytes = name.getBytes(Charset.forName("GB18030"));
        Path archive = writeArchive(new RawEntry(bytes, 0, unicodeExtra(bytes, "incorrect.txt", false)));
        assertNames(archive, List.of(name));
    }

    /// Rejects an otherwise plausible encoding when an unsampled name is malformed.
    @Test
    void validatesNamesOutsideTheSample() throws IOException {
        Charset gb18030 = Charset.forName("GB18030");
        List<RawEntry> entries = new ArrayList<>();
        for (int i = 0; i < 1024; i++)
            entries.add(entry("\u4e2d\u6587" + i + ".txt", gb18030));
        entries.set(1, new RawEntry(new byte[]{(byte) 0xff, '.', 't', 'x', 't'}, 0, new byte[0]));
        Path archive = writeArchive(entries.toArray(RawEntry[]::new));
        Charset detected = CompressingUtils.findSuitableEncoding(archive);
        assertNotEquals(gb18030, detected);
        List<String> names = new ArrayList<>();
        for (RawEntry entry : entries)
            names.add(detected.newDecoder().decode(ByteBuffer.wrap(entry.name())).toString());
        assertNames(archive, names);
    }

    /// Exercises chunked UTF-8 validation with supplementary characters at buffer boundaries.
    @Test
    void decodesLongNames() throws IOException {
        String name = "a" + "\ud83d\ude00".repeat(2000) + ".txt";
        assertNames(writeArchive(entry(name, StandardCharsets.UTF_8)), List.of(name));
    }

    /// Allows callers to resolve short ambiguous names by explicitly supplying an encoding.
    @Test
    void acceptsExplicitEncoding() throws IOException {
        Charset cp437 = Charset.forName("IBM437");
        String name = "caf\u00e9.txt";
        Path archive = writeArchive(entry(name, cp437));
        try (var reader = CompressingUtils.openZipFile(archive, cp437)) {
            assertEquals(name, reader.getEntries().iterator().next().getName());
        }
    }

    /// Checks actual names and empty entry content through the reader and ZIP filesystem.
    private static void assertNames(Path archive, List<String> names) throws IOException {
        try (var reader = CompressingUtils.openZipFile(archive)) {
            assertEquals(names, StreamSupport.stream(reader.getEntries().spliterator(), false)
                    .map(ZipArchiveEntry::getName).toList());
            for (var entry : reader.getEntries()) {
                try (var input = Objects.requireNonNull(reader.getInputStream(entry))) {
                    assertEquals(-1, input.read());
                }
            }
        }
        try (var fileSystem = CompressingUtils.readonly(archive).build()) {
            for (String name : names)
                assertTrue(Files.exists(fileSystem.getPath(name)), name);
        }
    }

    /// Holds raw ZIP metadata without interpreting the filename bytes.
    ///
    /// @param name filename bytes shared with the test
    /// @param flags general-purpose ZIP flags
    /// @param extra extra-field bytes shared with the test
    @NotNullByDefault
    private record RawEntry(byte[] name, int flags, byte[] extra) {
    }

    /// Encodes an unmarked entry without Unicode extra fields.
    private static RawEntry entry(String name, Charset charset) {
        return new RawEntry(name.getBytes(charset), 0, new byte[0]);
    }

    /// Creates a Unicode path extra field with a matching or deliberately invalid CRC.
    private static byte[] unicodeExtra(byte[] rawName, String name, boolean valid) {
        CRC32 crc = new CRC32();
        crc.update(rawName);
        byte[] unicodeName = name.getBytes(StandardCharsets.UTF_8);
        return header(9 + unicodeName.length).putShort((short) 0x7075)
                .putShort((short) (5 + unicodeName.length)).put((byte) 1)
                .putInt((int) crc.getValue() ^ (valid ? 0 : 1)).put(unicodeName).array();
    }

    /// Allocates a little-endian ZIP header buffer.
    private static ByteBuffer header(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    /// Writes empty stored entries with exact raw metadata in both header locations.
    private Path writeArchive(RawEntry... entries) throws IOException {
        ByteArrayOutputStream local = new ByteArrayOutputStream();
        ByteArrayOutputStream central = new ByteArrayOutputStream();
        for (RawEntry entry : entries) {
            int offset = local.size();
            local.writeBytes(header(30).putInt(0x04034b50).putShort((short) 20)
                    .putShort((short) entry.flags()).putShort((short) 0).putInt(0)
                    .putInt(0).putInt(0).putInt(0).putShort((short) entry.name().length)
                    .putShort((short) entry.extra().length).array());
            local.writeBytes(entry.name());
            local.writeBytes(entry.extra());
            central.writeBytes(header(46).putInt(0x02014b50).putShort((short) 20).putShort((short) 20)
                    .putShort((short) entry.flags()).putShort((short) 0).putInt(0)
                    .putInt(0).putInt(0).putInt(0).putShort((short) entry.name().length)
                    .putShort((short) entry.extra().length).putShort((short) 0)
                    .putShort((short) 0).putShort((short) 0).putInt(0).putInt(offset).array());
            central.writeBytes(entry.name());
            central.writeBytes(entry.extra());
        }
        int centralOffset = local.size();
        local.writeBytes(central.toByteArray());
        local.writeBytes(header(22).putInt(0x06054b50).putInt(0).putShort((short) entries.length)
                .putShort((short) entries.length).putInt(central.size()).putInt(centralOffset)
                .putShort((short) 0).array());
        return Files.write(Files.createTempFile(temporaryDirectory, "encoding-", ".zip"), local.toByteArray());
    }
}
