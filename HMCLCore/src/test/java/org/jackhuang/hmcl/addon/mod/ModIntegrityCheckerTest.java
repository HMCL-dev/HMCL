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

        assertTrue(ModIntegrityChecker.isCorrupt(corruptFile),
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

        assertFalse(ModIntegrityChecker.isCorrupt(validFile),
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

        assertTrue(ModIntegrityChecker.isCorrupt(truncatedFile),
                "Truncated ZIP file should be detected as corrupt");
    }

    @Test
    void testEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty-mod.jar");
        Files.write(emptyFile, new byte[0]);

        assertTrue(ModIntegrityChecker.isCorrupt(emptyFile),
                "Empty file should be detected as corrupt");
    }

    @Test
    void testNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("non-existent.jar");

        assertTrue(ModIntegrityChecker.isCorrupt(nonExistentFile),
                "Non-existent file should be detected as corrupt");
    }
}
