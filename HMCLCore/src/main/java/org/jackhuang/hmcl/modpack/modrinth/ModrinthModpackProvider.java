// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.modrinth;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModrinthModpackProvider implements ModpackProvider.SupportOptional {
    public static final ModrinthModpackProvider INSTANCE = new ModrinthModpackProvider();

    @Override
    public String getName() {
        return "Modrinth";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        return new ModrinthCompletionTask(dependencyManager, instance);
    }

    /// Reads installed Modrinth metadata and binds the original pack's optional-only update.
    @Override
    public Modpack readInstalledModpack(Path instanceRoot, ModpackConfiguration<?> configuration) throws IOException {
        var installed = recoverExcludedFiles(instanceRoot, null);
        ModrinthManifest manifest = (ModrinthManifest) installed.getKey();
        @Unmodifiable Set<String> previousExcluded = Set.copyOf(installed.getValue());
        return new Modpack(configuration.getName(), "", configuration.getVersion(),
                manifest.getGameVersion(), manifest.getSummary(), manifest) {
            /// Changes optional files through the normal Modrinth update and backup tasks.
            @Override
            public Task<?> getInstallTask(DefaultDependencyManager dependency, Path file, GameInstanceID id,
                                          @Nullable String iconUrl, @Nullable Set<String> excludedFiles) {
                var instance = dependency.getGameRepository().getInstance(id);
                return new ModpackUpdateTask(instance, new ModrinthInstallTask(dependency, manifest, instance,
                        Objects.requireNonNull(excludedFiles), previousExcluded));
            }
        };
    }

    @Override
    public Task<?> createUpdateTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            Path zipFile,
            Modpack modpack,
            @Nullable Set<String> excludedFiles) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof ModrinthManifest modrinthManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(instance, new ModrinthInstallTask(dependencyManager, zipFile, modpack, modrinthManifest, instance, null, excludedFiles));
    }

    @Override
    public Modpack readManifest(ZipArchiveReader zip, Path file) throws IOException, JsonParseException {
        try {
            ModrinthManifest manifest = JsonUtils.fromNonNullJson(CompressingUtils.readTextZipEntry(zip, "modrinth.index.json"), ModrinthManifest.class);

            return new Modpack(manifest.getName(), "", manifest.getVersionId(), manifest.getGameVersion(), manifest.getSummary(), manifest) {
                @Override
                public Task<?> getInstallTask(
                        DefaultDependencyManager dependencyManager,
                        Path zipFile,
                        GameInstanceID instanceId,
                        String iconUrl,
                        @Nullable Set<String> excludedFiles) {
                    return new ModrinthInstallTask(dependencyManager, zipFile, this, manifest, instanceId, iconUrl, excludedFiles);
                }
            };
        } catch (IOException | JsonParseException ex) {
            try (var os = new PrintStream("/dev/stdout")) {
                ex.printStackTrace(os);
            }
            throw ex;
        }
    }

    @Override
    public ModrinthManifest loadFiles(DownloadProvider downloadProvider, ModpackManifest.SupportOptional manifest1) {
        if (!(manifest1 instanceof ModrinthManifest manifest))
            throw new IllegalArgumentException("Manifest is not a ModrinthManifest");
        return manifest.withFiles(manifest.getFiles().parallelStream().map(file -> {
            if (file.optional() && !file.addonQueried()) {
                try {
                    String sha1 = file.hashes().get("sha1");
                    if (sha1 == null) {
                        return file.withAddon(null);
                    }
                    RemoteAddon.Version version = ModrinthRemoteAddonRepository.getInstance().getRemoteVersionBySHA1(sha1).orElse(null);
                    if (version == null) {
                        return file.withAddon(null);
                    }
                    RemoteAddon addon = ModrinthRemoteAddonRepository.getInstance().getAddonById(downloadProvider, version.projectId());
                    return file.withAddon(addon);
                } catch (FileNotFoundException fof) {
                    LOG.warning("Could not query modrinth for deleted mods: " + file.fileName(), fof);
                    return file;
                } catch (IOException | JsonParseException e) {
                    LOG.warning("Unable to fetch the modid for " + file.fileName(), e);
                    return file;
                }
            } else {
                return file;
            }
        }).toList());
    }

    /// Reads the installed manifest and maps its persisted optional-file exclusions.
    /// Malformed or unreadable records fail instead of silently selecting every file.
    @Override
    public Pair<ModpackManifest.SupportOptional, Set<String>> recoverExcludedFiles(
            Path instanceRoot, @Nullable ModpackManifest.SupportOptional newManifest) throws IOException {
        if (!(newManifest instanceof ModrinthManifest) && newManifest != null)
            throw new IllegalArgumentException("newManifest is not a ModrinthManifest");
        Set<String> excludedFiles = new HashSet<>();
        Path oldManifestPath = instanceRoot.resolve("modrinth.index.json");
        ModrinthManifest oldManifest;
        try {
            oldManifest = JsonUtils.fromNonNullJson(Files.readString(oldManifestPath), ModrinthManifest.class);
            if (newManifest == null)
                newManifest = oldManifest;
        } catch (IOException | JsonParseException e) {
            throw new IOException("Unable to read installed Modrinth manifest", e);
        }
        Path oldExcludedFilesPath = instanceRoot.resolve("excluded.json");
        if (!Files.exists(oldExcludedFilesPath))
            return Pair.pair(newManifest, excludedFiles);
        Set<String> oldExcluded = new HashSet<>();
        try {
            oldExcluded = JsonUtils.fromNonNullJson(Files.readString(oldExcludedFilesPath), new TypeToken<Set<String>>(){});
        } catch (IOException | JsonParseException e) {
            throw new IOException("Unable to read installed optional-file exclusions", e);
        }
        return Pair.pair(newManifest, newManifest.getExcludedFilesFromAnother(oldManifest, oldExcluded));
    }
}
