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
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.MinecraftInstanceTask;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Installs or updates a local server modpack using the mode selected at construction.
public class ServerModpackLocalInstallTask extends Task<Void> {

    private final Path zipFile;
    private final Modpack modpack;
    private final ServerModpackManifest manifest;
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    private final DefaultGameRepository repository;
    private final List<Task<?>> dependencies = new ArrayList<>();
    private final List<Task<?>> dependents = new ArrayList<>(4);

    /// Creates a task that installs a new local server modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the server modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the server modpack manifest
    /// @param instanceId        the id of the new instance
    /// @throws IllegalStateException if `instanceId` is already registered
    public ServerModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ServerModpackManifest manifest,
            GameInstanceID instanceId) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null);
    }

    /// Creates a task that updates an existing local server modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the server modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the server modpack manifest
    /// @param instance          the existing instance to update
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    /// @throws IllegalStateException    if `instance` is no longer registered
    public ServerModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ServerModpackManifest manifest,
            DefaultGameInstance instance) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance);
    }

    /// Creates a local server modpack task in the mode selected by `updateTarget`.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the server modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the server modpack manifest
    /// @param instanceId        the target instance id
    /// @param updateTarget      the existing instance selecting update mode, or `null` for install
    private ServerModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ServerModpackManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        this.repository = dependencyManager.getGameRepository();
        Path run = repository.getLayout().getInstanceRoot(instanceId);

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        GameBuilder builder = this.updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(this.updateTarget);
        if (this.updateTarget != null && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a Server modpack. Cannot update this instance.");

        @Nullable ModpackConfiguration<ServerModpackManifest> config = null;
        try {
            if (this.updateTarget != null && Files.exists(json)) {
                config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(ServerModpackManifest.class));

                if (config == null || !ServerModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Instance " + instanceId + " is not a Server modpack. Cannot update this instance.");
            }
        } catch (JsonParseException | IOException ignore) {
        }

        builder.enableIsolation();
        for (ServerModpackManifest.Addon addon : manifest.getAddons()) {
            @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
            if (componentType != null)
                builder.component(componentType, addon.getVersion());
        }

        dependents.add(builder.buildAsync());
        onDone().register(event -> {
            if (this.updateTarget == null && event.isFailed())
                repository.removeInstanceFromDisk(instanceId);
        });

        dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), Collections.singletonList("/overrides"), any -> true, config).withStage("hmcl.modpack"));
        dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), Collections.singletonList("/overrides"), manifest, ServerModpackProvider.INSTANCE, modpack.getName(), modpack.getVersion(), repository.getLayout().getModpackConfigurationFile(instanceId)).withStage("hmcl.modpack"));
    }

    @Override
    public List<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() throws Exception {
    }
}
