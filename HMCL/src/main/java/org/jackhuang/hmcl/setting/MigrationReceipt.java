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

import com.google.gson.JsonObject;
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
@NotNullByDefault
final class MigrationReceipt {
    /// The JSON member storing the absolute legacy source path.
    private static final String PROPERTY_SOURCE = "source";

    /// The JSON member storing the legacy source file size.
    private static final String PROPERTY_SOURCE_SIZE = "sourceSize";

    /// The JSON member storing the legacy source last-modified time.
    private static final String PROPERTY_SOURCE_LAST_MODIFIED = "sourceLastModified";

    /// The JSON member storing the legacy source SHA-256 hash.
    private static final String PROPERTY_SOURCE_SHA256 = "sourceSha256";

    /// The JSON member storing when the migration receipt was written.
    private static final String PROPERTY_MIGRATED_AT = "migratedAt";

    /// Prevents instantiation.
    private MigrationReceipt() {
    }

    /// Returns whether the receipt records the current state of the legacy source file.
    static boolean matches(Path receipt, Path source) {
        Objects.requireNonNull(receipt);
        Objects.requireNonNull(source);

        if (!Files.exists(receipt) || !Files.exists(source)) {
            return false;
        }

        try {
            JsonObject object = JsonUtils.fromJsonFile(receipt, JsonObject.class);
            return object != null && matches(object, source);
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
            JsonObject object = create(source);
            object.addProperty(PROPERTY_MIGRATED_AT, Instant.now().toString());
            FileUtils.saveSafely(receipt, JsonUtils.GSON.toJson(object));
        } catch (IOException e) {
            LOG.warning("Failed to write migration receipt " + receipt, e);
        }
    }

    /// Returns whether a parsed receipt matches the current source file state.
    private static boolean matches(JsonObject object, Path source) throws IOException {
        JsonObject current = create(source);
        return Objects.equals(JsonUtils.getString(object, PROPERTY_SOURCE),
                        JsonUtils.getString(current, PROPERTY_SOURCE))
                && Objects.equals(getSourceSize(object), getSourceSize(current))
                && Objects.equals(JsonUtils.getString(object, PROPERTY_SOURCE_LAST_MODIFIED),
                        JsonUtils.getString(current, PROPERTY_SOURCE_LAST_MODIFIED))
                && Objects.equals(JsonUtils.getString(object, PROPERTY_SOURCE_SHA256),
                        JsonUtils.getString(current, PROPERTY_SOURCE_SHA256));
    }

    /// Reads the source file size from a receipt object.
    private static @Nullable Long getSourceSize(JsonObject object) {
        try {
            return object.get(PROPERTY_SOURCE_SIZE).getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /// Creates receipt content for the current source file state.
    private static JsonObject create(Path source) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);

        JsonObject object = new JsonObject();
        object.addProperty(PROPERTY_SOURCE, source.toAbsolutePath().normalize().toString());
        object.addProperty(PROPERTY_SOURCE_SIZE, attributes.size());
        object.addProperty(PROPERTY_SOURCE_LAST_MODIFIED, attributes.lastModifiedTime().toString());
        object.addProperty(PROPERTY_SOURCE_SHA256, DigestUtils.digestToString("SHA-256", source));
        return object;
    }
}
