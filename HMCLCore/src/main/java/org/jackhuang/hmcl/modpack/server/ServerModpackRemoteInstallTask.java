// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackGameInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Installs or updates a remote server modpack using the mode selected at construction.
@NotNullByDefault
public class ServerModpackRemoteInstallTask extends ModpackGameInstallTask {

    /// The remote synchronization task scheduled after the game has been installed.
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    /// The manifest supplying installation metadata and game components.
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
    /// @throws IllegalArgumentException if the update target belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    private ServerModpackRemoteInstallTask(
            DefaultDependencyManager dependencyManager,
            ServerModpackManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        super(dependencyManager, instanceId, updateTarget);
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

        for (ServerModpackManifest.Addon addon : manifest.getAddons()) {
            @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
            if (componentType != null)
                components.put(componentType, addon.getVersion());
        }
    }

    /// {@inheritDoc}
    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    /// Schedules synchronization with the remote server modpack manifest.
    @Override
    public void execute() throws Exception {
        dependencies.add(new ServerModpackCompletionTask(
                dependencyManager,
                repository.getInstance(instanceId),
                new ModpackConfiguration<>(manifest, MODPACK_TYPE, manifest.getName(), manifest.getVersion(), Collections.emptyList())));
    }

    /// The provider id written to the installed server modpack configuration.
    public static final String MODPACK_TYPE = "Server";
}
