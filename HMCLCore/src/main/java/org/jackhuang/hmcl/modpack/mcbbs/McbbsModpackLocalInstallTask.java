// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.mcbbs;

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
import java.util.Optional;

/// Installs or updates a local MCBBS modpack using the mode selected at construction.
@NotNullByDefault
public final class McbbsModpackLocalInstallTask extends ModpackGameInstallTask {

    /// The manifest supplying installation metadata and game components.
    private final McbbsModpackManifest manifest;

    /// The task that records installed files for subsequent updates.
    private final MinecraftInstanceTask<McbbsModpackManifest> instanceTask;

    /// Tasks scheduled after the game and the archive contents have been installed.
    private final List<Task<?>> dependencies = new ArrayList<>(2);

    /// Creates a task that installs a new MCBBS modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the MCBBS modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the MCBBS manifest
    /// @param instanceId        the id of the new instance
    public McbbsModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            McbbsModpackManifest manifest,
            GameInstanceID instanceId) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null);
    }

    /// Creates a task that updates an existing MCBBS modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the MCBBS modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the MCBBS manifest
    /// @param instance          the existing instance to update
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    public McbbsModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            McbbsModpackManifest manifest,
            DefaultGameInstance instance) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance);
    }

    /// Creates an MCBBS installation task in the mode selected by `updateTarget`.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the MCBBS modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the MCBBS manifest
    /// @param instanceId        the target instance id
    /// @param updateTarget      the existing instance selecting update mode, or `null` for install
    /// @throws IllegalArgumentException if the update target belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    private McbbsModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            McbbsModpackManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        super(dependencyManager, instanceId, updateTarget);
        this.manifest = manifest;
        Path run = repository.getLayout().getInstanceRoot(instanceId);

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        if (this.updateTarget != null && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a Mcbbs modpack. Cannot update this instance.");

        @Nullable ModpackConfiguration<McbbsModpackManifest> config = null;
        try {
            if (this.updateTarget != null && Files.exists(json)) {
                config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(McbbsModpackManifest.class));

                if (config == null || !McbbsModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Instance " + instanceId + " is not a Mcbbs modpack. Cannot update this instance.");
            }
        } catch (JsonParseException | IOException ignore) {
        }

        installTasks.add(new ModpackInstallTask<>(zipFile, run, Collections.singletonList("/overrides"), any -> true, config).withStage("hmcl.modpack"));
        instanceTask = new MinecraftInstanceTask<>(zipFile, Collections.singletonList("/overrides"), manifest, McbbsModpackProvider.INSTANCE, modpack.getName(), modpack.getVersion(), repository.getLayout().getModpackConfigurationFile(instanceId));
        installTasks.add(instanceTask.withStage("hmcl.modpack"));
    }

    /// Configures components for manifest add-ons whose ids match recognized patch types.
    @Override
    protected void configureGameBuilder(GameBuilder builder) {
        for (McbbsModpackManifest.Addon addon : manifest.getAddons()) {
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

    /// Schedules the library patch and completion of the installed instance.
    @Override
    public void execute() throws Exception {
        GameInstanceManifest instanceManifest = repository.getInstanceManifest(instanceId);
        Optional<GameInstancePatch> mcbbsPatch = instanceManifest.getPatches().stream().filter(patch -> PATCH_NAME.equals(patch.id())).findFirst();
        if (this.updateTarget == null) {
            GameInstancePatch patch = new GameInstancePatch(PATCH_NAME).withLibraries(manifest.getLibraries());
            dependencies.add(repository.saveAsync(instanceManifest.addPatch(patch)));
        } else if (mcbbsPatch.isPresent()) {
            // This mcbbs modpack was installed by HMCL.
            GameInstancePatch patch = mcbbsPatch.get().withLibraries(manifest.getLibraries());
            dependencies.add(repository.saveAsync(instanceManifest.addPatch(patch)));
        } else {
            // This mcbbs modpack was installed by other launchers.
            // TODO: maintain libraries.
        }

        dependencies.add(new McbbsModpackCompletionTask(
                dependencyManager,
                repository.getInstance(instanceId),
                instanceTask.getResult()));
    }

    /// The patch id used to persist libraries declared by MCBBS modpacks.
    private static final String PATCH_NAME = "mcbbs";
}
