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
package org.jackhuang.hmcl.addon.mod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ModIntegrityChecker}.
 */
class ModIntegrityCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void testCorruptFileNotZip() throws IOException {
        Path corruptFile = tempDir.resolve("corrupt-mod.jar");
        Files.write(corruptFile, "This is not a valid ZIP file".getBytes());

        assertEquals(ModIntegrityChecker.Result.CORRUPT, ModIntegrityChecker.check(corruptFile, true),
                "Non-ZIP file should be detected as corrupt");
    }

    @Test
    void testValidZipFile() throws IOException {
        Path validFile = tempDir.resolve("valid-mod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validFile))) {
            ZipEntry entry = new ZipEntry("test.txt");
            zos.putNextEntry(entry);
            zos.write("Hello World".getBytes());
            zos.closeEntry();
        }

        assertEquals(ModIntegrityChecker.Result.INTACT, ModIntegrityChecker.check(validFile, true),
                "Valid ZIP file should not be detected as corrupt");
    }

    @Test
    void testTruncatedZipFile() throws IOException {
        Path validFile = tempDir.resolve("valid-mod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validFile))) {
            ZipEntry entry = new ZipEntry("test.txt");
            zos.putNextEntry(entry);
            zos.write("Hello World".getBytes());
            zos.closeEntry();
        }

        byte[] validData = Files.readAllBytes(validFile);
        Path truncatedFile = tempDir.resolve("truncated-mod.jar");
        Files.write(truncatedFile, java.util.Arrays.copyOf(validData, validData.length / 2));

        assertEquals(ModIntegrityChecker.Result.CORRUPT, ModIntegrityChecker.check(truncatedFile, true),
                "Truncated ZIP file should be detected as corrupt");
    }

    @Test
    void testEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty-mod.jar");
        Files.write(emptyFile, new byte[0]);

        assertEquals(ModIntegrityChecker.Result.CORRUPT, ModIntegrityChecker.check(emptyFile, true),
                "Empty file should be detected as corrupt");
    }

    @Test
    void testNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("non-existent.jar");

        // A missing file is an environmental error: the check could not be performed,
        // so the result is UNKNOWN rather than INTACT or CORRUPT
        assertEquals(ModIntegrityChecker.Result.UNKNOWN, ModIntegrityChecker.check(nonExistentFile, true),
                "Non-existent file should yield an unknown result");
    }

    @Test
    void testCorruptedEntryData() throws IOException {
        // Create a valid ZIP file with STORED (no compression) to make corruption easier
        Path validFile = tempDir.resolve("valid-mod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validFile))) {
            ZipEntry entry = new ZipEntry("test.txt");
            entry.setMethod(ZipEntry.STORED);
            byte[] content = "Hello World".getBytes();
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }

        // Read ZIP file content
        byte[] data = Files.readAllBytes(validFile);

        // Find and modify entry data (without changing file size)
        // Look for "Hello" in the raw data
        boolean modified = false;
        for (int i = 0; i < data.length - 5; i++) {
            if (data[i] == 'H' && data[i + 1] == 'e' && data[i + 2] == 'l' && data[i + 3] == 'l' && data[i + 4] == 'o') {
                data[i] = 'X'; // Change "Hello" to "Xello"
                modified = true;
                break;
            }
        }
        assertTrue(modified, "Should have found and modified entry data");

        // Write modified file
        Path corruptedFile = tempDir.resolve("corrupted-entry.jar");
        Files.write(corruptedFile, data);

        // Verify CRC32 validation detects the corruption
        assertEquals(ModIntegrityChecker.Result.CORRUPT, ModIntegrityChecker.check(corruptedFile, true),
                "Corrupted entry data should be detected as corrupt");
    }

    @Test
    void testCorruptedDeflateEntry() throws IOException {
        // Create a valid ZIP file with DEFLATE compression
        Path validFile = tempDir.resolve("valid-deflate.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validFile))) {
            ZipEntry entry = new ZipEntry("test.txt");
            entry.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(entry);
            zos.write("Hello World".getBytes());
            zos.closeEntry();
        }

        // Read ZIP file content
        byte[] data = Files.readAllBytes(validFile);

        // Find and modify compressed data bytes (without changing file size)
        // Look for any non-zero byte in the middle section and flip it
        boolean modified = false;
        for (int i = data.length / 3; i < data.length * 2 / 3; i++) {
            if (data[i] != 0) {
                data[i] = (byte) (data[i] ^ 0xFF);
                modified = true;
                break;
            }
        }
        assertTrue(modified, "Should have found and modified compressed data");

        // Write modified file
        Path corruptedFile = tempDir.resolve("corrupted-deflate.jar");
        Files.write(corruptedFile, data);

        // Verify CRC32 validation detects the corruption
        assertEquals(ModIntegrityChecker.Result.CORRUPT, ModIntegrityChecker.check(corruptedFile, true),
                "Corrupted DEFLATE entry data should be detected as corrupt");
    }

    @Test
    void testNonStrictIgnoresEntryPayloadCorruption() throws IOException {
        // Build a ZIP whose entry payload is damaged while the central directory stays valid
        Path validFile = tempDir.resolve("valid-mod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validFile))) {
            ZipEntry entry = new ZipEntry("test.txt");
            entry.setMethod(ZipEntry.STORED);
            byte[] content = "Hello World".getBytes();
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }

        byte[] data = Files.readAllBytes(validFile);
        for (int i = 0; i < data.length - 5; i++) {
            if (data[i] == 'H' && data[i + 1] == 'e' && data[i + 2] == 'l') {
                data[i] = 'X';
                break;
            }
        }
        Path corruptedFile = tempDir.resolve("corrupted-entry.jar");
        Files.write(corruptedFile, data);

        // Non-strict mode only validates the central directory, so it does not see this damage;
        // strict mode does. Both behaviors are asserted so the difference stays deliberate.
        assertEquals(ModIntegrityChecker.Result.INTACT, ModIntegrityChecker.check(corruptedFile, false),
                "Non-strict mode only validates the central directory");
        assertEquals(ModIntegrityChecker.Result.CORRUPT, ModIntegrityChecker.check(corruptedFile, true),
                "Strict mode verifies entry checksums");
    }

    @Test
    void testNonStrictDetectsStructurallyInvalidZip() throws IOException {
        Path corruptFile = tempDir.resolve("corrupt-mod.jar");
        Files.write(corruptFile, "This is not a valid ZIP file".getBytes());

        assertEquals(ModIntegrityChecker.Result.CORRUPT, ModIntegrityChecker.check(corruptFile, false),
                "Non-strict mode still detects an unreadable archive");
    }

    @Test
    void testMultipleEntries() throws IOException {
        // Create a valid ZIP file with multiple entries
        Path validFile = tempDir.resolve("multi-entry.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validFile))) {
            ZipEntry entry1 = new ZipEntry("file1.txt");
            zos.putNextEntry(entry1);
            zos.write("Content 1".getBytes());
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("file2.txt");
            zos.putNextEntry(entry2);
            zos.write("Content 2".getBytes());
            zos.closeEntry();
        }

        assertEquals(ModIntegrityChecker.Result.INTACT, ModIntegrityChecker.check(validFile, true),
                "Valid multi-entry ZIP file should not be detected as corrupt");
    }

    @Test
    void testSizeMismatchDetected() throws IOException {
        // Create a valid ZIP with STORED entry and correct CRC
        Path validFile = tempDir.resolve("valid.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validFile))) {
            ZipEntry entry = new ZipEntry("test.txt");
            entry.setMethod(ZipEntry.STORED);
            byte[] content = "Hello World".getBytes();
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }

        // Patch the central directory's uncompressed-size field to 999 (offset+24 in CEN)
        byte[] data = Files.readAllBytes(validFile);
        int central = -1;
        for (int i = 0; i < data.length - 4; i++) {
            if (data[i] == 0x50 && data[i + 1] == 0x4b && data[i + 2] == 0x01 && data[i + 3] == 0x02) {
                central = i;
                break;
            }
        }
        assertTrue(central >= 0, "Should find central directory header");
        // CEN uncompressed size is at offset +24 (4 bytes, little-endian)
        data[central + 24] = (byte) 0xe7;
        data[central + 25] = 0x03;
        data[central + 26] = 0;
        data[central + 27] = 0;

        Path patched = tempDir.resolve("sizemismatch.jar");
        Files.write(patched, data);

        assertEquals(ModIntegrityChecker.Result.CORRUPT, ModIntegrityChecker.check(patched, true),
                "Strict mode should detect size mismatch");
        assertEquals(ModIntegrityChecker.Result.INTACT, ModIntegrityChecker.check(patched, false),
                "Non-strict mode does not verify size");
    }

    @Test
    void testEmptyZipIsNotIntact() throws IOException {
        // An empty ZIP (0 entries) cannot be verified in strict mode
        Path emptyZip = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // No entries
        }

        assertEquals(ModIntegrityChecker.Result.UNKNOWN, ModIntegrityChecker.check(emptyZip, true),
                "Empty ZIP has no entries to verify, should be UNKNOWN");
        assertEquals(ModIntegrityChecker.Result.INTACT, ModIntegrityChecker.check(emptyZip, false),
                "Non-strict mode only validates the central directory");
    }

    @Test
    void testDirectoryOnlyZipIsNotIntact() throws IOException {
        // A ZIP with only directory entries has no data entries to verify
        Path dirOnly = tempDir.resolve("dironly.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(dirOnly))) {
            zos.putNextEntry(new ZipEntry("folder/"));
            zos.closeEntry();
        }

        assertEquals(ModIntegrityChecker.Result.UNKNOWN, ModIntegrityChecker.check(dirOnly, true),
                "Directory-only ZIP has no verifiable entries, should be UNKNOWN");
        assertEquals(ModIntegrityChecker.Result.INTACT, ModIntegrityChecker.check(dirOnly, false),
                "Non-strict mode only validates the central directory");
    }
}
