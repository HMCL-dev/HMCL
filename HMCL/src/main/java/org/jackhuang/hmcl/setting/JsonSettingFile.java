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
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Loads, saves, and validates a detached JSON settings file with a [JsonSchema] marker.
///
/// @param <T> the settings object type
/// @author Glavo
@NotNullByDefault
final class JsonSettingFile<T extends ObservableSetting & JsonSchemaSetting> {
    /// The settings file location.
    private final Path location;

    /// The human-readable file type name used in logs.
    private final String displayName;

    /// The settings object type.
    private final Class<T> type;

    /// The JSON schema supported by the current code.
    private final JsonSchema expectedSchema;

    /// Creates a default settings object.
    private final Supplier<T> createDefault;

    /// Creates a detached JSON settings file helper.
    ///
    /// @param location the settings file location
    /// @param displayName the human-readable file type name used in logs
    /// @param type the settings object type
    /// @param expectedSchema the JSON schema supported by the current code
    /// @param createDefault creates a default settings object
    JsonSettingFile(
            Path location,
            String displayName,
            Class<T> type,
            JsonSchema expectedSchema,
            Supplier<T> createDefault) {
        this.location = Objects.requireNonNull(location);
        this.displayName = Objects.requireNonNull(displayName);
        this.type = Objects.requireNonNull(type);
        this.expectedSchema = Objects.requireNonNull(expectedSchema);
        this.createDefault = Objects.requireNonNull(createDefault);
    }

    /// Loads the settings file, falling back to migrated data or a default object when absent.
    ///
    /// @param migrated migrated settings data used when the file is absent
    /// @return the loaded settings object and whether it may be saved
    /// @throws IOException if reading the file fails
    LoadResult<T> load(@Nullable T migrated) throws IOException {
        if (Files.exists(location)) {
            try {
                JsonObject jsonObject = JsonUtils.fromJsonFile(location, JsonObject.class);
                if (jsonObject == null) {
                    LOG.warning(displayName + " are empty: " + location);
                } else {
                    JsonSchemaPolicy.Result checkResult =
                            JsonSchemaPolicy.check(location, displayName, jsonObject, expectedSchema);
                    if (!checkResult.readable()) {
                        return new LoadResult<>(createDefault.get(), false);
                    }

                    T deserialized = LauncherSettings.SETTINGS_GSON.<@Nullable T>fromJson(jsonObject, type);
                    if (deserialized != null) {
                        // Patch-compatible files keep their original schema because unknown members are preserved.
                        if (!checkResult.preserveSchema() && !expectedSchema.equals(deserialized.getSchema())) {
                            deserialized.setSchema(expectedSchema);
                        }

                        return new LoadResult<>(deserialized, checkResult.allowSave());
                    }

                    LOG.warning(displayName + " deserialized to null: " + location);
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed " + displayName + ".", e);
            }

            return new LoadResult<>(createDefault.get(), false);
        }

        return new LoadResult<>(Objects.requireNonNullElseGet(migrated, createDefault), true);
    }

    /// Installs an automatic save listener on a settings object.
    ///
    /// @param value the settings object to observe
    void installAutoSave(T value) {
        value.addListener(source -> save(value));
    }

    /// Saves a settings object.
    ///
    /// @param value the settings object to save
    void save(T value) {
        FileSaver.save(location, JsonUtils.GSON.toJson(value, type));
    }

    /// Result of loading a detached JSON settings file.
    ///
    /// @param value the loaded settings object
    /// @param allowSave whether the file may be overwritten
    record LoadResult<T extends ObservableSetting & JsonSchemaSetting>(T value, boolean allowSave) {
    }
}
