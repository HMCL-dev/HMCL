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
package org.jackhuang.hmcl.modpack;

import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/// Provides format-specific operations for reading, installing, updating, and completing modpacks.
@NotNullByDefault
public interface ModpackProvider {

    /// Returns the persistent provider name stored in modpack configurations.
    ///
    /// @return the provider name
    String getName();

    /// Creates a task that completes missing or outdated files for a registered instance.
    ///
    /// @param dependencyManager the dependency manager for `instance`'s repository
    /// @param instance          the registered instance to complete
    /// @return the completion task, or `null` when this format requires no completion
    @Nullable Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance);

    /// Creates a task that updates a registered instance from a local modpack archive.
    ///
    /// @param dependencyManager the dependency manager for `instance`'s repository
    /// @param instance          the registered instance to update
    /// @param zipFile           the modpack archive
    /// @param modpack           the parsed modpack
    /// @return the update task
    /// @throws MismatchedModpackTypeException if the parsed manifest belongs to another provider
    Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance, Path zipFile, Modpack modpack) throws MismatchedModpackTypeException;

    /// Reads this provider's manifest from an opened modpack archive.
    ///
    /// @param zipFile  the opened modpack archive
    /// @param file     the modpack archive path
    /// @param encoding the archive entry-name encoding
    /// @return the parsed modpack
    /// @throws IOException        if the archive cannot be read as this format
    /// @throws JsonParseException if the required manifest is missing or malformed
    Modpack readManifest(ZipArchiveReader zipFile, Path file, Charset encoding) throws IOException, JsonParseException;

    /// Injects provider-specific launch options from a serialized modpack configuration.
    ///
    /// @param modpackConfigurationJson the serialized configuration
    /// @param builder                  the launch options builder to update
    default void injectLaunchOptions(String modpackConfigurationJson, LaunchOptions.Builder builder) {
    }
}
