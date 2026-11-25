// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.curse;

import com.google.gson.JsonParseException;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Install a downloaded CurseForge modpack.
///
/// @author huangyuhui
public final class CurseInstallTask extends Task<@Nullable Void> {

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository repository;
    private final Path zipFile;
    private final Modpack modpack;
    private final CurseManifest manifest;
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    /// Optional remote icon URL supplied by the install source.
    private final @Nullable String iconUrl;
    private final Path run;

    /// Previous modpack configuration when updating, or `null` for a new installation.
    private final @Nullable ModpackConfiguration<CurseManifest> config;

    /// Keys of optional files the user chose not to install; `null` or empty means install all.
    private final @Nullable Set<String> excludedFiles;

    /// Creates a task that installs a new CurseForge modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the CurseForge modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the CurseForge manifest
    /// @param instanceId        the id of the new instance
    /// @param iconUrl           the optional icon URL, or `null`
    /// @param excludedFiles   keys of optional files the user chose not to install; `null` or empty means
    ///                          install all files. When non-null, must not contain `null` elements.
    public CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            CurseManifest manifest,
            GameInstanceID instanceId,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null, iconUrl, excludedFiles);
    }

    /// Creates a task that updates an existing CurseForge modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the CurseForge modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the CurseForge manifest
    /// @param instance          the existing instance to update
    /// @param iconUrl           the optional icon URL, or `null`
    /// @param excludedFiles   keys of optional files the user chose not to install; `null` or empty means
    ///                          install all files. When non-null, must not contain `null` elements.
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    public CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            CurseManifest manifest,
            DefaultGameInstance instance,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance, iconUrl, excludedFiles);
    }

    /// Creates a CurseForge installation task in the mode selected by `updateTarget`.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the CurseForge modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the CurseForge manifest
    /// @param instanceId        the target instance id
    /// @param updateTarget      the existing instance selecting update mode, or `null` for install
    /// @param iconUrl           the optional icon URL, or `null`
    /// @param excludedFiles   keys of optional files the user chose not to install; `null` or empty means
    ///                          install all files. When non-null, must not contain `null` elements.
    /// @throws IllegalArgumentException if an update target has no compatible configuration
    private CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            CurseManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        if (updateTarget != null)
            dependencyManager.validateGameInstance(updateTarget);
        this.iconUrl = iconUrl;
        this.excludedFiles = excludedFiles == null ? null : Set.copyOf(excludedFiles);
        this.repository = dependencyManager.getGameRepository();

        this.run = repository.getLayout().getInstanceRoot(instanceId);

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        if (this.updateTarget != null && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a Curse modpack. Cannot update this instance.");

        @Nullable ModpackConfiguration<CurseManifest> config = null;
        try {
            if (this.updateTarget != null && Files.exists(json)) {
                config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(CurseManifest.class));

                if (config == null || !CurseModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Instance " + instanceId + " is not a Curse modpack. Cannot update this instance.");
            }
        } catch (JsonParseException | IOException ignore) {
        }
        this.config = config;
    }

    private Set<String> getNewOverridesMods() throws IOException {
        Set<String> result = new HashSet<>();
        String overridesDir = manifest.overrides();
        if (StringUtils.isBlank(overridesDir)) {
            return result;
        }

        String pathPrefix = StringUtils.addSuffix(FileUtils.normalizePath(overridesDir), "/");
        try (var reader = CompressingUtils.openZipFile(zipFile)) {
            for (var entry : reader.getEntries()) {
                String normalizedPath = FileUtils.normalizePath(entry.getName());
                if (!normalizedPath.startsWith(pathPrefix)) {
                    continue;
                }
                String relativePath = normalizedPath.substring(pathPrefix.length());
                if (relativePath.startsWith("mods/") && !entry.isDirectory()) {
                    result.add(relativePath);
                }
            }
        }
        return result;
    }

    /// Installs the instance and downloads the missing CurseForge files.
    ///
    /// @throws IllegalStateException if the target cannot be reserved or an update target
    ///                               is no longer the exact published instance
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Modpack.installWithGameBuilder(context, dependencyManager, instanceId, updateTarget, true, builder -> install(context, builder));
        return null;
    }

    /// Installs the CurseForge pack into the target reserved by the builder.
    private void install(TaskExecutionContext context, DefaultGameBuilder builder) throws Exception {
        builder.enableIsolation()
                .component(GameComponentType.GAME, manifest.minecraft().gameVersion());
        for (CurseManifestModLoader modLoader : manifest.minecraft().modLoaders()) {
            if (modLoader.id().startsWith("forge-")) {
                builder.component(GameComponentType.FORGE, modLoader.id().substring("forge-".length()));
            } else if (modLoader.id().startsWith("fabric-")) {
                builder.component(GameComponentType.FABRIC, modLoader.id().substring("fabric-".length()));
            } else if (modLoader.id().startsWith("neoforge-")) {
                builder.component(GameComponentType.NEO_FORGE, modLoader.id().substring("neoforge-".length()));
            }
        }
        List<Task<?>> preparation = new ArrayList<>(4);
        preparation.add(new ModpackInstallTask<>(zipFile, run, Collections.singletonList(manifest.overrides()), any -> true, config).withStage(InstallStages.MODPACK));
        preparation.add(new MinecraftInstanceTask<>(zipFile, Collections.singletonList(manifest.overrides()), manifest, CurseModpackProvider.INSTANCE, manifest.name(), manifest.version(), repository.getLayout().getModpackConfigurationFile(instanceId)).withStage(InstallStages.MODPACK));

        @Nullable String iconExtension = null;
        @Nullable Task<Path> iconTask = null;
        @Nullable WebURL iconUri = NetworkUtils.toWebURLOrNull(iconUrl);
        if (iconUri != null) {
            String ext = FileUtils.getExtension(StringUtils.substringAfter(iconUri.getPath(), '/')).toLowerCase(Locale.ROOT);
            if (Modpack.SUPPORTED_ICON_EXTS.contains(ext)) {
                iconExtension = ext;
                DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
                iconTask = new CacheFileTask(downloadProvider.getDownloadCandidates(iconUrl));
                preparation.add(iconTask);
            }
        }

        context.runAll(preparation);
        DefaultGameInstance instance = builder.build(context);
        if (config != null) {
            Set<String> newOverridesMods = getNewOverridesMods();
            Set<CurseManifestFile> newManifestFiles = manifest.files() != null ? new HashSet<>(manifest.files()) : Collections.emptySet();

            // For update, remove mods not listed in new manifest.
            // ModpackConfiguration stored in modpack.json preserves the raw
            // CurseForge manifest where fileName is missing. CurseCompletionTask
            // resolves those file names and writes the enriched manifest to
            // manifest.json, so read from there when available.
            Path oldManifestFile = repository.getLayout().getInstanceRoot(instanceId).resolve("manifest.json");
            @Nullable List<CurseManifestFile> oldFiles = config.getManifest().files();
            if (Files.exists(oldManifestFile)) {
                try {
                    @Nullable CurseManifest oldManifest = JsonUtils.fromJsonFile(oldManifestFile, CurseManifest.class);
                    if (oldManifest != null) {
                        oldFiles = oldManifest.files();
                    }
                } catch (IOException | JsonParseException ignored) {
                }
            }
            if (oldFiles == null) {
                oldFiles = Collections.emptyList();
            }
            for (CurseManifestFile oldCurseManifestFile : oldFiles) {
                if (StringUtils.isBlank(oldCurseManifestFile.fileName())) continue;
                String relativePath = "mods/" + oldCurseManifestFile.fileName();
                Path oldFile = run.resolve(relativePath);
                if (Files.notExists(oldFile)) continue;
                if (!newManifestFiles.contains(oldCurseManifestFile) && !newOverridesMods.contains(relativePath))
                    Files.deleteIfExists(oldFile);
            }
        }

        Path root = repository.getLayout().getInstanceRoot(instanceId);
        Files.createDirectories(root);
        JsonUtils.writeToJsonFile(root.resolve("manifest.json"), manifest);

        if (iconExtension != null
                && iconTask != null
                && Modpack.SUPPORTED_ICON_NAMES.stream().map(root::resolve).allMatch(Files::notExists)) {
            try {
                Files.copy(iconTask.resultNow(), root.resolve("icon." + iconExtension));
            } catch (Exception e) {
                LOG.warning("Failed to copy modpack icon", e);
            }
        }

        // The game builder publishes the instance before file completion starts.
        context.run(new CurseCompletionTask(dependencyManager, instance, manifest, excludedFiles));
    }
}
