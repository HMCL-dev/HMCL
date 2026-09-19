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
package org.jackhuang.hmcl.modpack.mcbbs;

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
import java.util.Optional;

/// Installs or updates a local MCBBS modpack using the mode selected at construction.
public final class McbbsModpackLocalInstallTask extends Task<Void> {

    private final DefaultDependencyManager dependencyManager;
    private final Path zipFile;
    private final Modpack modpack;
    private final McbbsModpackManifest manifest;
    private final GameInstanceID instanceId;
    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;
    private final DefaultGameRepository repository;
    private final MinecraftInstanceTask<McbbsModpackManifest> instanceTask;
    private final List<Task<?>> dependencies = new ArrayList<>(2);
    private final List<Task<?>> dependents = new ArrayList<>(4);

    /// Creates a task that installs a new MCBBS modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the MCBBS modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the MCBBS manifest
    /// @param instanceId        the id of the new instance
    /// @throws IllegalStateException if the target cannot be reserved or another repository draft
    ///                               is open
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
    /// @throws IllegalStateException    if `instance` is not the exact currently published object
    ///                                  or another repository draft is open
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
    /// @throws IllegalStateException    if the target cannot be reserved, an update target is not
    ///                                  the exact published object, or another draft is open
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
        this.repository = dependencyManager.getGameRepository();
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

        onDone().register(event -> {
            if (this.updateTarget == null && event.isFailed())
                repository.removeInstanceFromDisk(instanceId);
        });

        dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), Collections.singletonList("/overrides"), any -> true, config).withStage("hmcl.modpack"));
        instanceTask = new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), Collections.singletonList("/overrides"), manifest, McbbsModpackProvider.INSTANCE, modpack.getName(), modpack.getVersion(), repository.getLayout().getModpackConfigurationFile(instanceId));
        dependents.add(instanceTask.withStage("hmcl.modpack"));

        try (GameBuilder builder = this.updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(this.updateTarget)) {
            builder.enableIsolation();
            for (McbbsModpackManifest.Addon addon : manifest.getAddons()) {
                @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
                if (componentType != null)
                    builder.component(componentType, addon.getVersion());
            }
            dependents.add(0, builder.buildAsync());
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

    private static final String PATCH_NAME = "mcbbs";
}
