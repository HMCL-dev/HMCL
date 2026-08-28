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
package org.jackhuang.hmcl.modpack.curse;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
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
    private final Path zipFile;
    private final Modpack modpack;
    private final CurseManifest manifest;
    private final GameInstanceID instanceId;

    /// Whether this task updates the instance supplied at construction.
    private final boolean updating;

    /// Optional remote icon URL supplied by the install source.
    private final @Nullable String iconUrl;
    private final Path run;

    /// Previous modpack configuration when updating, or `null` for a new installation.
    private final @Nullable ModpackConfiguration<CurseManifest> config;

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
    /// @throws IllegalStateException if `instanceId` is already registered
    public CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            CurseManifest manifest,
            GameInstanceID instanceId,
            @Nullable String iconUrl) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null, iconUrl);
    }

    /// Creates a task that updates an existing CurseForge modpack instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the CurseForge modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the CurseForge manifest
    /// @param instance          the existing instance to update
    /// @param iconUrl           the optional icon URL, or `null`
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    /// @throws IllegalStateException    if `instance` is no longer registered
    public CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            CurseManifest manifest,
            DefaultGameInstance instance,
            @Nullable String iconUrl) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance, iconUrl);
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
    private CurseInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            CurseManifest manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            @Nullable String iconUrl) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updating = updateTarget != null;
        this.iconUrl = iconUrl;
        this.repository = dependencyManager.getGameRepository();

        this.run = repository.getLayout().getInstanceRoot(instanceId);

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        GameBuilder builder = updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(updateTarget);
        if (updating && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a Curse modpack. Cannot update this instance.");

        @Nullable ModpackConfiguration<CurseManifest> config = null;
        try {
            if (updating && Files.exists(json)) {
                config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(CurseManifest.class));

                if (config == null || !CurseModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Instance " + instanceId + " is not a Curse modpack. Cannot update this instance.");
            }
        } catch (JsonParseException | IOException ignore) {
        }
        this.config = config;

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
        dependents.add(builder.buildAsync());

        onDone().register(event -> {
            @Nullable Exception ex = event.getTask().getException();
            if (!updating && event.isFailed()) {
                if (!(ex instanceof ModpackCompletionException)) {
                    repository.removeInstanceFromDisk(instanceId);
                }
            }
        });

        dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), Collections.singletonList(manifest.overrides()), any -> true, config).withStage("hmcl.modpack"));
        dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), Collections.singletonList(manifest.overrides()), manifest, CurseModpackProvider.INSTANCE, manifest.name(), manifest.version(), repository.getLayout().getModpackConfigurationFile(instanceId)).withStage("hmcl.modpack"));

        @Nullable URI iconUri = NetworkUtils.toURIOrNull(iconUrl);
        if (iconUri != null) {
            String ext = FileUtils.getExtension(StringUtils.substringAfter(iconUri.getPath(), '/')).toLowerCase(Locale.ROOT);
            if (Modpack.SUPPORTED_ICON_EXTS.contains(ext)) {
                iconExt = ext;
                dependents.add(downloadIconTask = new CacheFileTask(dependencyManager.getDownloadProvider().injectURLWithCandidates(iconUrl)));
            }
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

    private Set<String> getNewOverridesMods() throws IOException {
        Set<String> result = new HashSet<>();
        String overridesDir = manifest.overrides();
        if (StringUtils.isBlank(overridesDir)) {
            return result;
        }

        String pathPrefix = StringUtils.addSuffix(FileUtils.normalizePath(overridesDir), "/");
        try (var reader = CompressingUtils.openZipFileWithPossibleEncoding(zipFile, modpack.getEncoding())) {
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
                if (!newManifestFiles.contains(oldCurseManifestFile) && !newOverridesMods.contains(relativePath))
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
        dependencies.add(new CurseCompletionTask(dependencyManager, repository.getInstance(instanceId), manifest));
    }
}
