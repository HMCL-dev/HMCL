// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.modpack.MinecraftInstanceTask;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;

/// Installs or updates an HMCL modpack by rebuilding its recognized game components.
@NotNullByDefault
public final class HMCLModpackInstallTask extends Task<@Nullable Void> {
    /// Archive containing the pack manifest and the files to extract.
    private final Path zipFile;

    /// Identifier reserved before installation begins.
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    /// Dependency manager for the target repository.
    private final DefaultDependencyManager dependency;

    /// Parsed metadata to write to the new configuration.
    private final Modpack modpack;

    /// Name captured for the instance configuration when the task was created.
    private final @Nullable String modpackName;

    /// Version captured for the instance configuration when the task was created.
    private final @Nullable String modpackVersion;

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
    /// @throws IllegalArgumentException if `instance` belongs to another repository
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
    /// @throws IllegalArgumentException if an update target belongs to another repository
    private HMCLModpackInstallTask(
            HMCLGameRepository repository,
            Path zipFile,
            Modpack modpack,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        this.dependency = repository.getDependency();
        this.zipFile = zipFile;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        this.modpack = modpack;
        this.modpackName = modpack.getName();
        this.modpackVersion = modpack.getVersion();
        if (updateTarget != null)
            dependency.validateGameInstance(updateTarget);
    }

    /// Reads the installed override hashes. A missing configuration or a different provider rejects the update.
    private @Nullable ModpackConfiguration<?> readUpdateConfiguration(Path file) {
        @Nullable ModpackConfiguration<?> config;
        try {
            config = JsonUtils.fromJsonFile(file, ModpackConfiguration.class);
        } catch (NoSuchFileException e) {
            config = null;
        } catch (JsonParseException | IOException ignored) {
            // An unreadable configuration supplies no previous override hashes.
            return null;
        }
        if (config == null || !HMCLModpackProvider.INSTANCE.getName().equals(config.getType()))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a HMCL modpack. Cannot update this instance.");
        return config;
    }

    /// Reserves the target before extracting the pack and installs its recognized components.
    ///
    /// The pack manifest is used only for component detection. The builder creates a new
    /// patch-structured manifest instead of attempting to mutate the legacy flattened manifest.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Modpack.installWithGameBuilder(context, dependency, instanceId, updateTarget, false, builder -> {
            DefaultGameRepositoryLayout layout = dependency.getGameRepository().getLayout();
            Path configurationFile = layout.getModpackConfigurationFile(instanceId);
            @Nullable ModpackConfiguration<?> oldConfiguration =
                    updateTarget == null ? null : readUpdateConfiguration(configurationFile);
            @Unmodifiable List<String> subDirectories = List.of("/minecraft");
            context.run(new ModpackInstallTask<>(zipFile, layout.getInstanceRoot(instanceId), subDirectories,
                    path -> !"pack.json".equals(path), oldConfiguration));
            context.run(new MinecraftInstanceTask<>(zipFile, subDirectories, modpack, HMCLModpackProvider.INSTANCE,
                    modpackName, modpackVersion, configurationFile).withStage(InstallStages.MODPACK));
            String json = CompressingUtils.readTextZipEntry(zipFile, "minecraft/pack.json");
            GameInstanceManifest originalManifest = JsonUtils.fromNonNullJson(json, GameInstanceManifest.class)
                    .withId(instanceId)
                    .withJar(null);
            GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(originalManifest, null);
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
            builder.build(context);
        });
        return null;
    }

}
