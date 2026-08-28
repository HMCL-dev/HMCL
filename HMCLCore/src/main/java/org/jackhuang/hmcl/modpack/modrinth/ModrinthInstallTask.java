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
package org.jackhuang.hmcl.modpack.modrinth;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ModrinthInstallTask extends Task<Void> {

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
    /// @throws IllegalStateException if `instanceId` is already registered
    public ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            GameInstanceID instanceId,
            @Nullable String iconUrl) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null, iconUrl);
    }

    /// Creates a task that updates an existing Modrinth modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the Modrinth modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the Modrinth index
    /// @param instance          the existing instance to update
    /// @param iconUrl           the optional icon URL, or `null`
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    /// @throws IllegalStateException    if `instance` is no longer registered
    public ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            DefaultGameInstance instance,
            @Nullable String iconUrl) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance, iconUrl);
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
    private ModrinthInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ModrinthManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            @Nullable String iconUrl) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        this.iconUrl = iconUrl;
        this.repository = dependencyManager.getGameRepository();
        this.run = repository.getLayout().getInstanceRoot(instanceId);

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        GameBuilder builder = this.updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(this.updateTarget);
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
        dependents.add(builder.buildAsync());

        onDone().register(event -> {
            @Nullable Exception ex = event.getTask().getException();
            if (this.updateTarget == null && event.isFailed()) {
                if (!(ex instanceof ModpackCompletionException)) {
                    repository.removeInstanceFromDisk(instanceId);
                }
            }
        });

        List<String> subDirectories = Arrays.asList("/client-overrides", "/overrides");
        dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), subDirectories, any -> true, config).withStage("hmcl.modpack"));
        dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), subDirectories, manifest, ModrinthModpackProvider.INSTANCE, manifest.getName(), manifest.getVersionId(), repository.getLayout().getModpackConfigurationFile(instanceId)).withStage("hmcl.modpack"));

        @Nullable URI iconUri = NetworkUtils.toURIOrNull(iconUrl);
        if (iconUri != null) {
            String ext = FileUtils.getExtension(StringUtils.substringAfter(iconUri.getPath(), '/')).toLowerCase(Locale.ROOT);
            if (Modpack.SUPPORTED_ICON_EXTS.contains(ext)) {
                iconExt = ext;

                dependents.add(downloadIconTask = new CacheFileTask(dependencyManager.getDownloadProvider().injectURLWithCandidates(iconUrl)));
            }
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
                Path oldFile = run.resolve(oldManifestFile.getPath());
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
        dependencies.add(new ModrinthCompletionTask(dependencyManager, repository.getInstance(instanceId), manifest));
    }
}
