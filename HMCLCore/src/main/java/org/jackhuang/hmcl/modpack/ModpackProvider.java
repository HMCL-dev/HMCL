// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Pair;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

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
    /// @param excludedFiles   keys of optional files the user chose not to install; `null` or empty means
    ///                          install all files. When non-null, must not contain `null` elements.
    /// @return the update task
    /// @throws MismatchedModpackTypeException if the parsed manifest belongs to another provider
    Task<?> createUpdateTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            Path zipFile,
            Modpack modpack,
            @Nullable Set<String> excludedFiles) throws MismatchedModpackTypeException;

    /// Reads this provider's manifest from an opened modpack archive.
    ///
    /// @param zipFile  the opened modpack archive
    /// @param file     the modpack archive path
    /// @return the parsed modpack
    /// @throws IOException        if the archive cannot be read as this format
    /// @throws JsonParseException if the required manifest is missing or malformed
    Modpack readManifest(ZipArchiveReader zipFile, Path file) throws IOException, JsonParseException;

    /// Injects provider-specific launch options from a serialized modpack configuration.
    ///
    /// @param modpackConfigurationJson the serialized configuration
    /// @param builder                  the launch options builder to update
    default void injectLaunchOptions(String modpackConfigurationJson, LaunchOptions.Builder builder) {
    }


    /// Provider operations for manifests with optional files.
    @NotNullByDefault
    interface SupportOptional extends ModpackProvider {
        /// Reads installed pack metadata for the basic-information and optional-file selection pages.
        /// The returned manifest implements [ModpackManifest.SupportOptional], and its install callback
        /// performs an optional-only update using the original manifest and recovered exclusions.
        ///
        /// @param instanceRoot the installed instance root
        /// @param configuration the installed pack's display name and version
        /// @throws IOException if the installed manifest or exclusions cannot be read
        Modpack readInstalledModpack(Path instanceRoot, ModpackConfiguration<?> configuration) throws IOException;

        /// Enriches a manifest with remote metadata for optional files (file names, URLs, addons).
        /// The type of the result manifest must be same as the original one.
        /// When the remote metadata can not be fetched, the original files are returned.
        ///
        /// @param downloadProvider the download provider used for remote queries
        /// @param manifest         the parsed manifest
        /// @return the enriched manifest, or `manifest` when no enrichment is needed
        ModpackManifest.SupportOptional loadFiles(DownloadProvider downloadProvider, ModpackManifest.SupportOptional manifest);

        /// Recovers the excluded files list from an installed modpack instance.
        /// @param instanceRoot the root path of the installed instance
        /// @param newManifest  the new manifest to compare with the installed instance, or null if the already installed manifest should be used
        /// @return a pair containing the used manifest and the excluded files
        /// @throws IOException if the installed manifest or exclusion record cannot be read
        Pair<ModpackManifest.SupportOptional, Set<String>>
            recoverExcludedFiles(Path instanceRoot, @Nullable ModpackManifest.SupportOptional newManifest) throws IOException;
    }
}
