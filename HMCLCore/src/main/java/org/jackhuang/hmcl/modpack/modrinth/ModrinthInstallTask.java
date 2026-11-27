// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.modrinth;

import com.google.gson.JsonParseException;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ModrinthInstallTask extends Task<@Nullable Void> {

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository repository;
    private final Path zipFile;
    private final Modpack modpack;
    private final ModrinthManifest manifest;
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    /// Optional remote icon URL supplied by the install source.
    private final @Nullable String iconUrl;
    private final Path run;

    /// Previous modpack configuration when updating, or `null` for a new installation.
    private final @Nullable ModpackConfiguration<ModrinthManifest> config;

    /// Keys of optional files the user chose not to install; `null` or empty means install all.
    private final @Nullable Set<String> excludedFiles;

    /// Creates a task that installs a new Modrinth modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the Modrinth modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the Modrinth index
    /// @param instanceId        the id of the new instance
    /// @param iconUrl           the optional icon URL, or `null`
    /// @param excludedFiles   keys of optional files the user chose not to install; `null` or empty means
    ///                          install all files. When non-null, must not contain `null` elements.
    public ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            GameInstanceID instanceId,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null, iconUrl, excludedFiles);
    }

    /// Creates a task that updates an existing Modrinth modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the Modrinth modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the Modrinth index
    /// @param instance          the existing instance to update
    /// @param iconUrl           the optional icon URL, or `null`
    /// @param excludedFiles   keys of optional files the user chose not to install; `null` or empty means
    ///                          install all files. When non-null, must not contain `null` elements.
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    public ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            DefaultGameInstance instance,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance, iconUrl, excludedFiles);
    }

    /// Creates a Modrinth installation task in the mode selected by `updateTarget`.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the Modrinth modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the Modrinth index
    /// @param instanceId        the target instance id
    /// @param updateTarget      the existing instance selecting update mode, or `null` for install
    /// @param iconUrl           the optional icon URL, or `null`
    /// @param excludedFiles   keys of optional files the user chose not to install; `null` or empty means
    ///                          install all files. When non-null, must not contain `null` elements.
    /// @throws IllegalArgumentException if an update target has no compatible configuration
    private ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        if (updateTarget != null)
            dependencyManager.validateGameInstance(updateTarget);
        this.iconUrl = iconUrl;
        this.excludedFiles = excludedFiles == null ? null : Set.copyOf(excludedFiles);
        this.repository = dependencyManager.getGameRepository();
        this.run = repository.getLayout().getInstanceRoot(instanceId);

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        if (this.updateTarget != null && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a Modrinth modpack. Cannot update this instance.");

        @Nullable ModpackConfiguration<ModrinthManifest> config = null;
        try {
            if (this.updateTarget != null && Files.exists(json)) {
                config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(ModrinthManifest.class));

                if (config == null || !ModrinthModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Instance " + instanceId + " is not a Modrinth modpack. Cannot update this instance.");
            }
        } catch (JsonParseException | IOException ignore) {
        }
        this.config = config;
    }

    /// Installs the instance and downloads the missing Modrinth files.
    ///
    /// @throws IllegalStateException if the target cannot be reserved or an update target
    ///                               is no longer the exact published instance, or the manifest
    ///                               declares an unsupported mod loader
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Modpack.installWithGameBuilder(context, dependencyManager, instanceId, updateTarget, true, builder -> install(context, builder));
        return null;
    }

    /// Installs the Modrinth pack into the target reserved by the builder.
    private void install(TaskExecutionContext context, DefaultGameBuilder builder) throws Exception {
        builder.enableIsolation();
        builder.component(GameComponentType.GAME, manifest.getGameVersion());
        for (Map.Entry<String, String> modLoader : manifest.getDependencies().entrySet()) {
            switch (modLoader.getKey()) {
                case "minecraft":
                    break;
                case "forge":
                    builder.component(GameComponentType.FORGE, modLoader.getValue());
                    break;
                case "neoforge":
                // https://github.com/HMCL-dev/HMCL/pull/5170
                case "neo-forge":
                    builder.component(GameComponentType.NEO_FORGE, modLoader.getValue());
                    break;
                case "fabric-loader":
                    builder.component(GameComponentType.FABRIC, modLoader.getValue());
                    break;
                case "quilt-loader":
                    builder.component(GameComponentType.QUILT, modLoader.getValue());
                    break;
                default:
                    throw new IllegalStateException("Unsupported mod loader " + modLoader.getKey());
            }
        }
        @Unmodifiable Set<String> previousExclusions = config == null ? Set.of() : ModrinthModpackProvider.readExcludedFiles(run);
        List<Task<?>> preparation = new ArrayList<>(3);
        @Unmodifiable List<String> subDirectories = List.of("/overrides", "/client-overrides");
        preparation.add(new ModpackInstallTask(zipFile, run, subDirectories, any -> true,
                updateTarget == null ? null : updateTarget.getModpackConfigurationFile()).withStage(InstallStages.MODPACK));
        var configuration = new MinecraftInstanceTask<>(zipFile, subDirectories, manifest, ModrinthModpackProvider.INSTANCE,
                manifest.getName(), manifest.getVersionId()).withStage(InstallStages.MODPACK);
        preparation.add(configuration);

        @Nullable String iconExtension = null;
        @Nullable Task<Path> iconTask = null;
        @Nullable WebURL iconUri = NetworkUtils.toWebURLOrNull(iconUrl);
        if (iconUri != null) {
            String ext = FileUtils.getExtension(StringUtils.substringAfter(iconUri.getPath(), '/')).toLowerCase(Locale.ROOT);
            if (Modpack.SUPPORTED_ICON_EXTS.contains(ext)) {
                iconExtension = ext;

                DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
                iconTask = new CacheFileTask(downloadProvider.getDownloadCandidates(iconUrl));
                preparation.add(iconTask);
            }
        }

        context.runAll(preparation);
        DefaultGameInstance instance = builder.build(context);
        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            if (draft.getBaseSnapshot().findInstance(instanceId) != instance)
                throw new IllegalStateException("Game instance has changed: " + instanceId);
            var current = configuration.resultNow();
            if (config != null)
                removeOutdatedFiles(instance.getRunDirectory(), config.getManifest(), previousExclusions, current, context);
            publishManifests(current);

            if (iconExtension != null
                    && iconTask != null
                    && Modpack.SUPPORTED_ICON_NAMES.stream().map(run::resolve).allMatch(Files::notExists)) {
                try {
                    Files.copy(iconTask.resultNow(), run.resolve("icon." + iconExtension));
                } catch (Exception e) {
                    LOG.warning("Failed to copy modpack icon", e);
                }
            }
            context.run(new ModrinthCompletionTask(dependencyManager, instance, manifest, excludedFiles));
        }
    }

    /// Removes obsolete index entries whose installed files still match the previous release.
    private void removeOutdatedFiles(Path runDirectory, ModrinthManifest previous, Set<String> previousExclusions,
                                     ModpackConfiguration<ModrinthManifest> current, TaskExecutionContext context) throws IOException {
        Set<String> overrides = new HashSet<>();
        for (var override : current.getOverrides())
            overrides.add(override.getPath());
        Map<String, ModrinthManifest.File> newFiles = new HashMap<>();
        for (var file : manifest.getFiles())
            newFiles.put(file.path(), file);

        for (var old : previous.getFiles()) {
            if (!old.isSelectedForClient(previousExclusions) || overrides.contains(old.path()))
                continue;
            @Nullable ModrinthManifest.File replacement = newFiles.get(old.path());
            @Nullable String oldHash = old.hashes().get("sha512");
            if (oldHash == null)
                continue;
            if (replacement != null && replacement.isSelectedForClient(excludedFiles)
                    && oldHash.equalsIgnoreCase(replacement.hashes().get("sha512")))
                continue;
            Modpack.removeUnmodifiedFile(runDirectory, old.path(), "SHA-512", oldHash, context);
        }
    }

    /// Records the new selection and index so a later launch can complete any missing downloads.
    private void publishManifests(ModpackConfiguration<ModrinthManifest> configuration) throws IOException {
        Path selection = run.resolve("excluded.json");
        if (excludedFiles == null)
            Files.deleteIfExists(selection);
        else
            JsonUtils.writeToJsonFileSafely(selection, excludedFiles);
        JsonUtils.writeToJsonFileSafely(run.resolve("modrinth.index.json"), manifest);
        configuration.writeTo(repository.getLayout().getModpackConfigurationFile(instanceId));
    }
}
