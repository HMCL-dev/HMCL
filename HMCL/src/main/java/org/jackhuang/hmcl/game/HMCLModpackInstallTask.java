/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.modpack.MinecraftInstanceTask;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/// Installs or updates an HMCL modpack by rebuilding its recognized game components.
@NotNullByDefault
public final class HMCLModpackInstallTask extends Task<Void> {
    private final Path zipFile;
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    private final HMCLGameRepository repository;
    private final DefaultDependencyManager dependency;
    private final Modpack modpack;
    private final List<Task<?>> dependencies = new ArrayList<>(1);
    private final List<Task<?>> dependents = new ArrayList<>(4);

    /// Creates a task that installs a new HMCL modpack instance.
    ///
    /// @param repository the target HMCL game repository
    /// @param zipFile    the HMCL modpack archive
    /// @param modpack    the parsed modpack metadata
    /// @param instanceId the id of the new instance
    public HMCLModpackInstallTask(
            HMCLGameRepository repository,
            Path zipFile,
            Modpack modpack,
            GameInstanceID instanceId) {
        this(repository, zipFile, modpack, instanceId, null);
    }

    /// Creates a task that updates an existing HMCL modpack instance.
    ///
    /// @param repository the target HMCL game repository
    /// @param zipFile    the HMCL modpack archive
    /// @param modpack    the parsed modpack metadata
    /// @param instance   the existing instance to update
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    public HMCLModpackInstallTask(
            HMCLGameRepository repository,
            Path zipFile,
            Modpack modpack,
            DefaultGameInstance instance) {
        this(repository, zipFile, modpack, instance.getId(), instance);
    }

    /// Creates an HMCL modpack task in the mode selected by `updateTarget`.
    ///
    /// @param repository   the target HMCL game repository
    /// @param zipFile      the HMCL modpack archive
    /// @param modpack      the parsed modpack metadata
    /// @param instanceId   the target instance id
    /// @param updateTarget the existing instance selecting update mode, or `null` for install
    /// @throws IllegalArgumentException if an update target belongs to another repository or has
    ///                                  no compatible configuration
    private HMCLModpackInstallTask(
            HMCLGameRepository repository,
            Path zipFile,
            Modpack modpack,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        this.repository = repository;
        this.dependency = repository.getDependency();
        this.zipFile = zipFile;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        this.modpack = modpack;
        if (this.updateTarget != null) {
            dependency.validateGameInstance(this.updateTarget);
        }

        Path run = repository.getLayout().getInstanceRoot(this.instanceId);
        Path json = repository.getLayout().getModpackConfigurationFile(this.instanceId);
        if (this.updateTarget != null && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a HMCL modpack. Cannot update this instance.");

        @Nullable ModpackConfiguration<Modpack> config = null;
        try {
            if (this.updateTarget != null && Files.exists(json)) {
                config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(Modpack.class));

                if (config == null || !HMCLModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Instance " + instanceId + " is not a HMCL modpack. Cannot update this instance.");
            }
        } catch (JsonParseException | IOException ignore) {
        }

        onDone().register(event -> {
            if (this.updateTarget == null && event.isFailed()) {
                repository.removeInstanceFromDisk(this.instanceId);
            }
        });

        dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), List.of("/minecraft"), it -> !"pack.json".equals(it), config));
        dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), List.of("/minecraft"), modpack, HMCLModpackProvider.INSTANCE, modpack.getName(), modpack.getVersion(), repository.getLayout().getModpackConfigurationFile(this.instanceId)).withStage("hmcl.modpack"));
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public List<Task<?>> getDependents() {
        return dependents;
    }

    /// Reads the legacy pack manifest and schedules one builder for all recognized components.
    ///
    /// The pack manifest is used only for component detection. The builder creates a new
    /// patch-structured manifest instead of attempting to mutate the legacy flattened manifest.
    @Override
    public void execute() throws Exception {
        String json = CompressingUtils.readTextZipEntry(zipFile, "minecraft/pack.json");
        GameInstanceManifest originalManifest = JsonUtils.fromNonNullJson(json, GameInstanceManifest.class)
                .withId(instanceId)
                .withJar(null);
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(originalManifest, null);

        try (GameBuilder builder = updateTarget == null
                ? dependency.newGameBuilder(instanceId)
                : dependency.newGameBuilder(updateTarget)) {
            builder.enableIsolation();

            String gameVersion = modpack.getGameVersion();
            builder.component(GameComponentType.GAME, gameVersion);
            for (GameComponentAnalyzer.Mark mark : analyzer) {
                if (mark.componentType() == GameComponentType.GAME) {
                    continue;
                }

                @Nullable String componentVersion = mark.version();
                if (componentVersion != null) {
                    if (mark.componentType() == GameComponentType.OPTIFINE) {
                        Matcher matcher = GameComponentAnalyzer.OPTIFINE_VERSION_PATTERN.matcher(componentVersion);
                        if (matcher.matches()) {
                            componentVersion = matcher.group("optifine");
                        }
                    }
                    builder.component(mark.componentType(), componentVersion);
                }
            }
            dependencies.add(builder.buildAsync());
        }
    }

}
