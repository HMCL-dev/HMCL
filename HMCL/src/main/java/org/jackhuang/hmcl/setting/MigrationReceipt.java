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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Records successful legacy migrations so deleting a current file does not replay an unchanged legacy source.
///
/// @param source the absolute legacy source path, or `null` when a malformed receipt omits it
/// @param sourceSize the legacy source file size, or `null` when a malformed receipt omits it
/// @param sourceLastModified the legacy source last-modified time, or `null` when a malformed receipt omits it
/// @param sourceSha256 the legacy source SHA-256 hash, or `null` when a malformed receipt omits it
/// @param migratedAt when the migration receipt was written, or `null` before saving or when omitted
@NotNullByDefault
record MigrationReceipt(
        @Nullable String source,
        @Nullable Long sourceSize,
        @Nullable String sourceLastModified,
        @Nullable String sourceSha256,
        @Nullable String migratedAt) {
    /// Returns whether the receipt records the current state of the legacy source file.
    static boolean matches(Path receipt, Path source) {
        Objects.requireNonNull(receipt);
        Objects.requireNonNull(source);

        if (!Files.exists(receipt) || !Files.exists(source)) {
            return false;
        }

        try {
            @Nullable MigrationReceipt object = JsonUtils.fromJsonFile(receipt, MigrationReceipt.class);
            return object != null && object.matches(create(source));
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOG.warning("Failed to read migration receipt " + receipt, e);
            return false;
        }
    }

    /// Writes a receipt for a successful migration from the legacy source file.
    static void save(Path receipt, Path source) {
        Objects.requireNonNull(receipt);
        Objects.requireNonNull(source);

        try {
            FileUtils.saveSafely(receipt, JsonUtils.GSON.toJson(create(source, Instant.now().toString())));
        } catch (IOException e) {
            LOG.warning("Failed to write migration receipt " + receipt, e);
        }
    }

    /// Returns whether this receipt matches another source file state.
    private boolean matches(MigrationReceipt current) {
        return Objects.equals(source, current.source)
                && Objects.equals(sourceSize, current.sourceSize)
                && Objects.equals(sourceLastModified, current.sourceLastModified)
                && Objects.equals(sourceSha256, current.sourceSha256);
    }

    /// Creates receipt content for the current source file state.
    private static MigrationReceipt create(Path source) throws IOException {
        return create(source, null);
    }

    /// Creates receipt content for the current source file state and migration time.
    private static MigrationReceipt create(Path source, @Nullable String migratedAt) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        return new MigrationReceipt(
                source.toAbsolutePath().normalize().toString(),
                attributes.size(),
                attributes.lastModifiedTime().toString(),
                DigestUtils.digestToString("SHA-256", source),
                migratedAt);
    }
}
