// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.MinecraftInstanceTask;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/// Installs or updates a local server modpack using the mode selected at construction.
public class ServerModpackLocalInstallTask extends Task<@Nullable Void> {

    private final Path zipFile;
    private final Modpack modpack;
    private final ServerModpackManifest manifest;
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    /// The dependency manager that reserves the instance at execution time.
    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository repository;
    /// Previously installed configuration, or `null` if unavailable.
    private final @Nullable ModpackConfiguration<ServerModpackManifest> config;

    /// Creates a task that installs a new local server modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the server modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the server modpack manifest
    /// @param instanceId        the id of the new instance
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
    /// @throws IllegalArgumentException if an update target has no compatible configuration
    private ServerModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ServerModpackManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        if (updateTarget != null)
            dependencyManager.validateGameInstance(updateTarget);
        this.repository = dependencyManager.getGameRepository();

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
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
        this.config = config;
    }

    /// Installs the game components and extracts the server modpack overrides.
    ///
    /// @throws IllegalStateException if the target cannot be reserved or an update target
    ///                               is no longer the exact published instance
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Modpack.installWithGameBuilder(dependencyManager, instanceId, updateTarget, false, builder -> {
            builder.enableIsolation();
            for (ServerModpackManifest.Addon addon : manifest.getAddons()) {
                @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
                if (componentType != null)
                    builder.component(componentType, addon.getVersion());
            }
            Path run = repository.getLayout().getInstanceRoot(instanceId);
            Task<?> overrides = new ModpackInstallTask<>(zipFile, run, Collections.singletonList("/overrides"), any -> true, config);
            Task<?> instance = new MinecraftInstanceTask<>(zipFile, Collections.singletonList("/overrides"), manifest, ServerModpackProvider.INSTANCE, modpack.getName(), modpack.getVersion(), repository.getLayout().getModpackConfigurationFile(instanceId));
            context.runAll(List.of(overrides.withStage(InstallStages.MODPACK), instance.withStage(InstallStages.MODPACK)));
            builder.build(context);
        });
        return null;
    }
}
