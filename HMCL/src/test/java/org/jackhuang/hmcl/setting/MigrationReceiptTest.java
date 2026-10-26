// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests migration receipt matching against legacy source content.
@NotNullByDefault
public final class MigrationReceiptTest {

    /// Tests that a receipt matches the source file content that was recorded.
    @Test
    public void matchesSavedSourceState() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDir = createMigrationReceiptTestDirectory(fileSystem, "matches");
            Path source = tempDir.resolve("hmcl.json");
            Path receipt = tempDir.resolve("settings.migration-receipt.json");
            Files.writeString(source, "{\"language\":\"en\"}");

            assertFalse(MigrationReceipt.matches(receipt, source));

            MigrationReceipt.save(receipt, source);

            assertTrue(MigrationReceipt.matches(receipt, source));
        }
    }

    /// Tests that a receipt matches the same legacy source content at another path.
    @Test
    public void matchesSameSourceContentAtDifferentPath() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDir = createMigrationReceiptTestDirectory(fileSystem, "moved");
            Path source = tempDir.resolve("hmcl.json");
            Path copied = tempDir.resolve("copied-hmcl.json");
            Path receipt = tempDir.resolve("settings.migration-receipt.json");
            Files.writeString(source, "{\"language\":\"en\"}");

            MigrationReceipt.save(receipt, source);
            Files.writeString(copied, Files.readString(source));
            JsonObject receiptObject = JsonUtils.fromJsonFile(receipt, JsonObject.class);

            assertFalse(receiptObject.has("source"));
            assertTrue(receiptObject.has("sourceSHA256"));
            assertTrue(MigrationReceipt.matches(receipt, copied));
        }
    }

    /// Tests that a receipt stops matching after the legacy source file changes.
    @Test
    public void rejectsChangedSourceState() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDir = createMigrationReceiptTestDirectory(fileSystem, "changed");
            Path source = tempDir.resolve("config.json");
            Path receipt = tempDir.resolve("user-settings.migration-receipt.json");
            Files.writeString(source, "{\"logRetention\":30}");

            MigrationReceipt.save(receipt, source);
            Files.writeString(source, "{\"logRetention\":31}");

            assertFalse(MigrationReceipt.matches(receipt, source));
        }
    }

    /// Creates a temporary directory in an in-memory file system for migration receipt tests.
    private static Path createMigrationReceiptTestDirectory(FileSystem fileSystem, String prefix) throws IOException {
        Path root = fileSystem.getPath("/migration-receipt-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix + "-");
    }
}
