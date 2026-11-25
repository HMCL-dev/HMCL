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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Installs or updates a Modrinth modpack from its archive.
@NotNullByDefault
public class ModrinthInstallTask extends ModpackGameInstallTask {

    /// The manifest supplying installation metadata and game components.
    private final ModrinthManifest manifest;

    /// The instance root receiving overrides and downloaded files.
    private final Path run;

    /// Previous modpack configuration when updating, or `null` for a new installation.
    private final @Nullable ModpackConfiguration<ModrinthManifest> config;

    /// Keys of optional files the user chose not to install; `null` or empty means install all.
    private final @Nullable @Unmodifiable Set<String> excludedFiles;

    /// Validated extension of the scheduled icon download, or `null` when no icon is scheduled.
    private @Nullable String iconExt;

    /// Scheduled icon download corresponding to [#iconExt], or `null` when absent.
    private @Nullable Task<Path> downloadIconTask;

    /// Tasks scheduled after the game and the archive contents have been installed.
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
    /// @throws IllegalArgumentException if the update target belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    private ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        super(dependencyManager, instanceId, updateTarget);
        this.manifest = manifest;
        this.excludedFiles = excludedFiles == null ? null : Set.copyOf(excludedFiles);
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

        List<String> subDirectories = Arrays.asList("/client-overrides", "/overrides");
        installTasks.add(new ModpackInstallTask<>(zipFile, run, subDirectories, any -> true, config).withStage("hmcl.modpack"));
        installTasks.add(new MinecraftInstanceTask<>(zipFile, subDirectories, manifest, ModrinthModpackProvider.INSTANCE, manifest.getName(), manifest.getVersionId(), repository.getLayout().getModpackConfigurationFile(instanceId)).withStage("hmcl.modpack"));

        @Nullable WebURL iconUri = NetworkUtils.toWebURLOrNull(iconUrl);
        if (iconUri != null) {
            String ext = FileUtils.getExtension(StringUtils.substringAfter(iconUri.getPath(), '/')).toLowerCase(Locale.ROOT);
            if (Modpack.SUPPORTED_ICON_EXTS.contains(ext)) {
                iconExt = ext;

                DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
                installTasks.add(downloadIconTask = new CacheFileTask(downloadProvider.getDownloadCandidates(iconUrl)));
            }
        }
    }

    /// Configures the game and loaders declared in the modpack manifest.
    ///
    /// @throws IllegalStateException if the manifest declares an unsupported loader
    @Override
    protected void configureGameBuilder(GameBuilder builder) {
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
    }

    /// Retains the installed instance if only modpack completion failed.
    @Override
    protected boolean shouldRemoveFailedInstance(@Nullable Exception failure) {
        return !(failure instanceof ModpackCompletionException);
    }

    /// {@inheritDoc}
    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    /// Applies update removals, writes the Modrinth index and schedules file completion.
    @Override
    public void execute() throws Exception {
        if (config != null) {
            // For update, remove mods not listed in new manifest
            for (ModrinthManifest.File oldManifestFile : config.getManifest().getFiles()) {
                Path oldFile = run.resolve(oldManifestFile.path());
                if (!Files.exists(oldFile)) continue;
                if (manifest.getFiles().stream().noneMatch(oldManifestFile::equals)) {
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
        dependencies.add(new ModrinthCompletionTask(dependencyManager, repository.getInstance(instanceId), manifest, excludedFiles));
    }
}
