// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.modrinth;

import com.google.gson.JsonParseException;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Task;
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

public class ModrinthInstallTask extends Task<Void> {

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository repository;
    /// Source archive, absent when only changing installed optional files.
    private final @Nullable Path zipFile;
    /// Source metadata, absent when only changing installed optional files.
    private final @Nullable Modpack modpack;
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

    /// Previous exclusions limit downloads and skip archive/component installation; `null` means normal install/update.
    private final @Nullable @Unmodifiable Set<String> previousExcludedFiles;

    /// Validated extension of the scheduled icon download, or `null` when no icon is scheduled.
    private @Nullable String iconExt;

    /// Scheduled icon download corresponding to [#iconExt], or `null` when absent.
    private @Nullable Task<Path> downloadIconTask;
    private final List<Task<?>> dependents = new ArrayList<>(4);
    private final List<Task<?>> dependencies = new ArrayList<>(1);

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
    /// @throws IllegalStateException if the manifest declares an unsupported mod loader, the target
    ///                               cannot be reserved, or another repository draft is open
    public ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            GameInstanceID instanceId,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null, iconUrl, excludedFiles, null);
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
    /// @throws IllegalStateException    if the manifest declares an unsupported mod loader,
    ///                                  `instance` is not the exact currently published object, or
    ///                                  another repository draft is open
    public ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            DefaultGameInstance instance,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance, iconUrl, excludedFiles, null);
    }

    /// Changes optional files using the existing update/removal rules, without installing components or overrides.
    public ModrinthInstallTask(DefaultDependencyManager dependencyManager, ModrinthManifest manifest,
                               DefaultGameInstance instance, Set<String> excludedFiles, Set<String> previousExcludedFiles) {
        this(dependencyManager, null, null, manifest, instance.getId(), instance, null, excludedFiles, previousExcludedFiles);
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
    /// @param previousExcludedFiles previous exclusions for optional-only updates, or `null` for normal installation
    /// @throws IllegalArgumentException if an update target has no compatible configuration
    /// @throws IllegalStateException    if the manifest declares an unsupported mod loader, the
    ///                                  target cannot be reserved, an update target is not the exact
    ///                                  published object, or another draft is open
    private ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            @Nullable Path zipFile,
            @Nullable Modpack modpack,
            ModrinthManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles,
            @Nullable Set<String> previousExcludedFiles) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        this.iconUrl = iconUrl;
        this.excludedFiles = excludedFiles == null ? null : Set.copyOf(excludedFiles);
        this.previousExcludedFiles = previousExcludedFiles == null ? null : Set.copyOf(previousExcludedFiles);
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

        if (previousExcludedFiles != null) {
            dependencyManager.validateGameInstance(Objects.requireNonNull(updateTarget));
            if (config == null)
                throw new IllegalArgumentException("Cannot read installed Modrinth modpack configuration");
            return;
        }
        Objects.requireNonNull(zipFile);

        onDone().register(event -> {
            @Nullable Exception ex = event.getTask().getException();
            if (this.updateTarget == null && event.isFailed()) {
                if (!(ex instanceof ModpackCompletionException)) {
                    repository.removeInstanceFromDisk(instanceId);
                }
            }
        });

        List<String> subDirectories = Arrays.asList("/client-overrides", "/overrides");
        dependents.add(new ModpackInstallTask<>(zipFile, run, subDirectories, any -> true, config).withStage("hmcl.modpack"));
        dependents.add(new MinecraftInstanceTask<>(zipFile, subDirectories, manifest, ModrinthModpackProvider.INSTANCE, manifest.getName(), manifest.getVersionId(), repository.getLayout().getModpackConfigurationFile(instanceId)).withStage("hmcl.modpack"));

        @Nullable WebURL iconUri = NetworkUtils.toWebURLOrNull(iconUrl);
        if (iconUri != null) {
            String ext = FileUtils.getExtension(StringUtils.substringAfter(iconUri.getPath(), '/')).toLowerCase(Locale.ROOT);
            if (Modpack.SUPPORTED_ICON_EXTS.contains(ext)) {
                iconExt = ext;

                DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
                dependents.add(downloadIconTask = new CacheFileTask(downloadProvider.getDownloadCandidates(iconUrl)));
            }
        }

        try (GameBuilder builder = this.updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(this.updateTarget)) {
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
            dependents.add(0, builder.buildAsync());
        }
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() throws Exception {
        if (config != null) {
            // For update, remove mods not listed in new manifest
            for (ModrinthManifest.File oldManifestFile : config.getManifest().getFiles()) {
                Path oldFile = run.resolve(oldManifestFile.path());
                if (!Files.exists(oldFile)) continue;
                if (manifest.getFiles().stream().filter(f -> (excludedFiles == null || !excludedFiles.contains(f.key()))).noneMatch(oldManifestFile::equals)) {
                    Files.deleteIfExists(oldFile);
                }
            }
        }

        Path root = repository.getLayout().getInstanceRoot(instanceId);
        Files.createDirectories(root);
        JsonUtils.writeToJsonFile(root.resolve("modrinth.index.json"), manifest);

        @Nullable String iconExtension = iconExt;
        @Nullable Task<Path> iconTask = downloadIconTask;
        if (iconExtension != null
                && iconTask != null
                && Modpack.SUPPORTED_ICON_NAMES.stream().map(root::resolve).allMatch(Files::notExists)) {
            try {
                Files.copy(iconTask.getResult(), root.resolve("icon." + iconExtension));
            } catch (Exception e) {
                LOG.warning("Failed to copy modpack icon", e);
            }
        }

        // The game builder runs as a dependent and registers the instance before this phase.
        dependencies.add(new ModrinthCompletionTask(dependencyManager, repository.getInstance(instanceId), manifest, excludedFiles, previousExcludedFiles));
    }
}
