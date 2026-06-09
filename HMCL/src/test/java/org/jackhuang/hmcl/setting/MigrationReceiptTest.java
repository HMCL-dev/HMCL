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
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests migration receipt matching against legacy source file state.
@NotNullByDefault
public final class MigrationReceiptTest {

    /// Tests that a receipt matches the source file state that was recorded.
    @Test
    public void matchesSavedSourceState() throws IOException {
        Path tempDir = createMigrationReceiptTestDirectory("matches");
        Path source = tempDir.resolve("hmcl.json");
        Path receipt = tempDir.resolve("settings.migration-receipt.json");
        Files.writeString(source, "{\"language\":\"en\"}");

        assertFalse(MigrationReceipt.matches(receipt, source));

        MigrationReceipt.save(receipt, source);

        assertTrue(MigrationReceipt.matches(receipt, source));
    }

    /// Tests that a receipt stops matching after the legacy source file changes.
    @Test
    public void rejectsChangedSourceState() throws IOException {
        Path tempDir = createMigrationReceiptTestDirectory("changed");
        Path source = tempDir.resolve("config.json");
        Path receipt = tempDir.resolve("user-settings.migration-receipt.json");
        Files.writeString(source, "{\"logRetention\":30}");

        MigrationReceipt.save(receipt, source);
        Files.writeString(source, "{\"logRetention\":31}");

        assertFalse(MigrationReceipt.matches(receipt, source));
    }

    /// Creates a temporary directory under Gradle's build directory for migration receipt tests.
    private static Path createMigrationReceiptTestDirectory(String prefix) throws IOException {
        Path root = Path.of("build", "tmp", "migration-receipt-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix + "-");
    }
}
