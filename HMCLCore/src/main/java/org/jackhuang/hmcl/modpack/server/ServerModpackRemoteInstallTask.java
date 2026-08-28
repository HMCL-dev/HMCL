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
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceID;
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

    /// Whether this task updates the instance supplied at construction.
    private final boolean updating;

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
    /// @throws IllegalStateException if `instanceId` is already registered
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
    /// @throws IllegalStateException    if `instance` is no longer registered
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
    private ServerModpackRemoteInstallTask(
            DefaultDependencyManager dependencyManager,
            ServerModpackManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        this.instanceId = instanceId;
        this.updating = updateTarget != null;
        this.dependency = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.manifest = manifest;

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        GameBuilder builder = updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(updateTarget);
        if (updating && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a Server modpack. Cannot update this instance.");

        try {
            if (updating && Files.exists(json)) {
                @Nullable ModpackConfiguration<ServerModpackManifest> config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(ServerModpackManifest.class));

                if (config == null || !MODPACK_TYPE.equals(config.getType()))
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
            if (!updating && event.isFailed())
                repository.removeInstanceFromDisk(instanceId);
        });
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
