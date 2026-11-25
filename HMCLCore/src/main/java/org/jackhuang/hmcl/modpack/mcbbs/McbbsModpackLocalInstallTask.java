// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.mcbbs;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DefaultGameBuilder;
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
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// Installs or updates a local MCBBS modpack using the mode selected at construction.
public final class McbbsModpackLocalInstallTask extends Task<@Nullable Void> {

    private final DefaultDependencyManager dependencyManager;
    private final Path zipFile;
    private final Modpack modpack;
    private final McbbsModpackManifest manifest;
    private final GameInstanceID instanceId;
    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;
    private final DefaultGameRepository repository;
    /// Previously installed configuration, or `null` if unavailable.
    private final @Nullable ModpackConfiguration<McbbsModpackManifest> config;

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
    /// @throws IllegalArgumentException if an update target has no compatible configuration
    private McbbsModpackLocalInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            McbbsModpackManifest manifest,
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
        this.config = config;
    }

    /// Installs the instance, updates its patch and completes the modpack files.
    ///
    /// @throws IllegalStateException if the target cannot be reserved or an update target
    ///                               is no longer the exact published instance
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Modpack.installWithGameBuilder(context, dependencyManager, instanceId, updateTarget, false, builder -> install(context, builder));
        return null;
    }

    /// Installs the MCBBS pack into the target reserved by the builder.
    private void install(TaskExecutionContext context, DefaultGameBuilder builder) throws Exception {
        builder.enableIsolation();
        for (McbbsModpackManifest.Addon addon : manifest.getAddons()) {
            @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
            if (componentType != null)
                builder.component(componentType, addon.getVersion());
        }
        Path run = repository.getLayout().getInstanceRoot(instanceId);
        @Unmodifiable List<String> subDirectories = List.of("/overrides");
        context.run(new ModpackInstallTask<>(zipFile, run, subDirectories, any -> true, config).withStage(InstallStages.MODPACK));
        var configuration = context.run(new MinecraftInstanceTask<>(zipFile, subDirectories, manifest, McbbsModpackProvider.INSTANCE,
                modpack.getName(), modpack.getVersion(), repository.getLayout().getModpackConfigurationFile(instanceId))
                .withStage(InstallStages.MODPACK));
        DefaultGameInstance instance = builder.build(context);
        GameInstanceManifest instanceManifest = instance.getManifest();
        Task<?> completion = new McbbsModpackCompletionTask(dependencyManager, instance, configuration);
        if (this.updateTarget == null) {
            // Keep the builder's published manifest current until file completion can no longer fail.
            context.run(completion);
            GameInstancePatch patch = new GameInstancePatch(PATCH_NAME).withLibraries(manifest.getLibraries());
            try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
                if (draft.getBaseSnapshot().getInstance(instanceId).getManifest() != instanceManifest)
                    throw new IllegalStateException("Game instance has changed: " + instanceId);
                draft.put(instanceManifest.addPatch(patch));
                draft.commit();
            }
            return;
        }

        Optional<GameInstancePatch> mcbbsPatch = instanceManifest.getPatches().stream().filter(patch -> PATCH_NAME.equals(patch.id())).findFirst();
        if (mcbbsPatch.isPresent()) {
            GameInstancePatch patch = mcbbsPatch.get().withLibraries(manifest.getLibraries());
            context.runAll(List.of(repository.saveAsync(instanceManifest.addPatch(patch)), completion));
        } else {
            // Other launchers may manage the libraries outside the MCBBS patch.
            context.run(completion);
        }
    }

    private static final String PATCH_NAME = "mcbbs";
}
