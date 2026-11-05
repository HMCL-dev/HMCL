// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Installs or updates a remote server modpack using the mode selected at construction.
public class ServerModpackRemoteInstallTask extends Task<Void> {

    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    private final DefaultDependencyManager dependency;
    private final DefaultGameRepository repository;
    private final List<Task<?>> dependencies = new ArrayList<>(1);
    private final List<Task<?>> dependents = new ArrayList<>(1);
    private final ServerModpackManifest manifest;

    /// Creates a task that installs a new remote server modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param manifest          the remote server modpack manifest
    /// @param instanceId        the id of the new instance
    /// @throws IllegalStateException if the target cannot be reserved or another repository draft
    ///                               is open
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
    /// @throws IllegalStateException    if `instance` is not the exact currently published object
    ///                                  or another repository draft is open
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
    /// @throws IllegalStateException    if the target cannot be reserved, an update target is not
    ///                                  the exact published object, or another draft is open
    private ServerModpackRemoteInstallTask(
            DefaultDependencyManager dependencyManager,
            ServerModpackManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
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

        onDone().register(event -> {
            if (this.updateTarget == null && event.isFailed())
                repository.removeInstanceFromDisk(instanceId);
        });

        try (GameBuilder builder = this.updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(this.updateTarget)) {
            builder.enableIsolation();
            for (ServerModpackManifest.Addon addon : manifest.getAddons()) {
                @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
                if (componentType != null)
                    builder.component(componentType, addon.getVersion());
            }
            dependents.add(builder.buildAsync());
        }
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
        dependencies.add(new ServerModpackCompletionTask(
                dependency,
                repository.getInstance(instanceId),
                new ModpackConfiguration<>(manifest, MODPACK_TYPE, manifest.getName(), manifest.getVersion(), Collections.emptyList())));
    }

    public static final String MODPACK_TYPE = "Server";
}
