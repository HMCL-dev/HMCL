// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.MinecraftInstanceTask;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackGameInstallTask;
import org.jackhuang.hmcl.modpack.ModpackInstallTask;
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

/// Installs or updates a local server modpack using the mode selected at construction.
@NotNullByDefault
public class ServerModpackLocalInstallTask extends ModpackGameInstallTask {

    /// The manifest supplying installation metadata and game components.
    private final ServerModpackManifest manifest;

    /// Tasks scheduled after the game and the archive contents have been installed.
    private final List<Task<?>> dependencies = new ArrayList<>();

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
    /// @throws IllegalArgumentException if the update target belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    private ServerModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            ServerModpackManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        super(dependencyManager, instanceId, updateTarget);
        this.manifest = manifest;
        Path run = repository.getLayout().getInstanceRoot(instanceId);

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

        installTasks.add(new ModpackInstallTask<>(zipFile, run, Collections.singletonList("/overrides"), any -> true, config).withStage("hmcl.modpack"));
        installTasks.add(new MinecraftInstanceTask<>(zipFile, Collections.singletonList("/overrides"), manifest, ServerModpackProvider.INSTANCE, modpack.getName(), modpack.getVersion(), repository.getLayout().getModpackConfigurationFile(instanceId)).withStage("hmcl.modpack"));
    }

    /// Configures components for manifest add-ons whose ids match recognized patch types.
    @Override
    protected void configureGameBuilder(GameBuilder builder) {
        for (ServerModpackManifest.Addon addon : manifest.getAddons()) {
            @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
            if (componentType != null)
                builder.component(componentType, addon.getVersion());
        }
    }

    /// {@inheritDoc}
    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    /// Completes the installation after the game and the archive contents have been installed.
    @Override
    public void execute() throws Exception {
    }
}
