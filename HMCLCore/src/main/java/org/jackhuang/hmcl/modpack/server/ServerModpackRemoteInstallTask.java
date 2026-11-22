// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/// Installs or updates a remote server modpack using the mode selected at construction.
public class ServerModpackRemoteInstallTask extends Task<@Nullable Void> {

    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    private final DefaultDependencyManager dependency;
    private final DefaultGameRepository repository;
    private final ServerModpackManifest manifest;

    /// Creates a task that installs a new remote server modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param manifest          the remote server modpack manifest
    /// @param instanceId        the id of the new instance
    public ServerModpackRemoteInstallTask(
            DefaultDependencyManager dependencyManager,
            ServerModpackManifest manifest,
            GameInstanceID instanceId) {
        this(dependencyManager, manifest, instanceId, null);
    }

    /// Creates a task that updates an existing remote server modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param manifest          the remote server modpack manifest
    /// @param instance          the existing instance to update
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    public ServerModpackRemoteInstallTask(
            DefaultDependencyManager dependencyManager,
            ServerModpackManifest manifest,
            DefaultGameInstance instance) {
        this(dependencyManager, manifest, instance.getId(), instance);
    }

    /// Creates a remote server modpack task in the mode selected by `updateTarget`.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param manifest          the remote server modpack manifest
    /// @param instanceId        the target instance id
    /// @param updateTarget      the existing instance selecting update mode, or `null` for install
    /// @throws IllegalArgumentException if an update target has no compatible configuration
    private ServerModpackRemoteInstallTask(
            DefaultDependencyManager dependencyManager,
            ServerModpackManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        if (updateTarget != null)
            dependencyManager.validateGameInstance(updateTarget);
        this.dependency = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.manifest = manifest;

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        if (this.updateTarget != null && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a Server modpack. Cannot update this instance.");

        try {
            if (this.updateTarget != null && Files.exists(json)) {
                @Nullable ModpackConfiguration<ServerModpackManifest> config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(ServerModpackManifest.class));

                if (config == null || !MODPACK_TYPE.equals(config.getType()))
                    throw new IllegalArgumentException("Instance " + instanceId + " is not a Server modpack. Cannot update this instance.");
            }
        } catch (JsonParseException | IOException ignore) {
        }
    }

    /// Installs the game components and synchronizes the remote server modpack.
    ///
    /// @throws IllegalStateException if the target cannot be reserved or an update target
    ///                               is no longer the exact published instance
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Modpack.installWithGameBuilder(dependency, instanceId, updateTarget, false, builder -> {
            builder.enableIsolation();
            for (ServerModpackManifest.Addon addon : manifest.getAddons()) {
                @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
                if (componentType != null)
                    builder.component(componentType, addon.getVersion());
            }
            context.run(builder.buildAsync());
            context.run(new ServerModpackCompletionTask(
                    dependency,
                    repository.getInstance(instanceId),
                    new ModpackConfiguration<>(manifest, MODPACK_TYPE, manifest.getName(), manifest.getVersion(), Collections.emptyList())));
        });
        return null;
    }

    public static final String MODPACK_TYPE = "Server";
}
