// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.curse;

import com.google.gson.JsonParseException;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Install a downloaded CurseForge modpack.
///
/// @author huangyuhui
public final class CurseInstallTask extends Task<Void> {

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository repository;
    /// Source archive, absent when only changing installed optional files.
    private final @Nullable Path zipFile;
    /// Source metadata, absent when only changing installed optional files.
    private final @Nullable Modpack modpack;
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

    /// Previous exclusions limit downloads and skip archive/component installation; `null` means normal install/update.
    private final @Nullable @Unmodifiable Set<String> previousExcludedFiles;

    /// Validated extension of the scheduled icon download, or `null` when no icon is scheduled.
    private @Nullable String iconExt;

    /// Scheduled icon download corresponding to [#iconExt], or `null` when absent.
    private @Nullable Task<Path> downloadIconTask;
    private final List<Task<?>> dependents = new ArrayList<>(4);
    private final List<Task<?>> dependencies = new ArrayList<>(1);

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
    /// @throws IllegalStateException if the target cannot be reserved or another repository draft
    ///                               is open
    public CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            CurseManifest manifest,
            GameInstanceID instanceId,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null, iconUrl, excludedFiles, null);
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
    /// @throws IllegalStateException    if `instance` is not the exact currently published object
    ///                                  or another repository draft is open
    public CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            CurseManifest manifest,
            DefaultGameInstance instance,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance, iconUrl, excludedFiles, null);
    }

    /// Changes optional files using the existing update/removal rules, without installing components or overrides.
    public CurseInstallTask(DefaultDependencyManager dependencyManager, CurseManifest manifest,
                            DefaultGameInstance instance, Set<String> excludedFiles, Set<String> previousExcludedFiles) {
        this(dependencyManager, null, null, manifest, instance.getId(), instance, null, excludedFiles, previousExcludedFiles);
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
    /// @param previousExcludedFiles previous exclusions for optional-only updates, or `null` for normal installation
    /// @throws IllegalArgumentException if an update target has no compatible configuration
    /// @throws IllegalStateException    if the target cannot be reserved, an update target is not
    ///                                  the exact published object, or another draft is open
    private CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            @Nullable Path zipFile,
            @Nullable Modpack modpack,
            CurseManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles,
            @Nullable Set<String> previousExcludedFiles) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        this.iconUrl = iconUrl;
        this.excludedFiles = excludedFiles == null ? null : Set.copyOf(excludedFiles);
        this.previousExcludedFiles = previousExcludedFiles == null ? null : Set.copyOf(previousExcludedFiles);
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

        if (previousExcludedFiles != null) {
            dependencyManager.validateGameInstance(Objects.requireNonNull(updateTarget));
            if (config == null)
                throw new IllegalArgumentException("Cannot read installed CurseForge modpack configuration");
            return;
        }
        Objects.requireNonNull(zipFile);

        onDone().register(event -> {
            @Nullable Exception ex = event.getTask().getException();
            if (this.updateTarget == null && event.isFailed()) {
                if (!(ex instanceof ModpackCompletionException)) {
                    repository.removeInstanceFromDisk(instanceId);
                }
            }
        });

        dependents.add(new ModpackInstallTask<>(zipFile, run, Collections.singletonList(manifest.overrides()), any -> true, config).withStage("hmcl.modpack"));
        dependents.add(new MinecraftInstanceTask<>(zipFile, Collections.singletonList(manifest.overrides()), manifest, CurseModpackProvider.INSTANCE, manifest.name(), manifest.version(), repository.getLayout().getModpackConfigurationFile(instanceId)).withStage("hmcl.modpack"));

        @Nullable WebURL iconUri = NetworkUtils.toWebURLOrNull(iconUrl);
        if (iconUri != null) {
            String ext = FileUtils.getExtension(StringUtils.substringAfter(iconUri.getPath(), '/')).toLowerCase(Locale.ROOT);
            if (Modpack.SUPPORTED_ICON_EXTS.contains(ext)) {
                iconExt = ext;
                DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
                dependents.add(downloadIconTask = new CacheFileTask(downloadProvider.getDownloadCandidates(iconUrl)));
            }
        }

        try (GameBuilder builder = this.updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(this.updateTarget)) {
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
            dependents.add(0, builder.buildAsync());
        }
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    /// Finds archive override mods, or the original override paths when changing only optional files.
    private Set<String> getNewOverridesMods() throws IOException {
        Set<String> result = new HashSet<>();
        if (previousExcludedFiles != null) {
            for (var file : config.getOverrides()) {
                if (file.getPath().startsWith("mods/"))
                    result.add(file.getPath());
            }
            return result;
        }
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

    @Override
    public void execute() throws Exception {
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
                if (newManifestFiles.stream().filter(f -> (excludedFiles == null || !excludedFiles.contains(f.key()))).noneMatch(oldCurseManifestFile::equals) && !newOverridesMods.contains(relativePath))
                    Files.deleteIfExists(oldFile);
            }
        }

        Path root = repository.getLayout().getInstanceRoot(instanceId);
        Files.createDirectories(root);
        JsonUtils.writeToJsonFile(root.resolve("manifest.json"), manifest);

        @Nullable String iconExtension = iconExt;
        @Nullable Task<Path> iconTask = downloadIconTask;
        if (iconExtension != null
                && iconTask != null
                && Modpack.SUPPORTED_ICON_NAMES.stream().map(root::resolve).allMatch(Files::notExists)) {
            try {
                Files.copy(iconTask.getResult(), root.resolve("icon." + iconExtension));
            } catch (Exception e) {
                LOG.warning("Failed to copy modpack icon", e);
            }
        }

        // The game builder runs as a dependent and registers the instance before this phase.
        dependencies.add(new CurseCompletionTask(dependencyManager, repository.getInstance(instanceId), manifest, excludedFiles, previousExcludedFiles));
    }
}
