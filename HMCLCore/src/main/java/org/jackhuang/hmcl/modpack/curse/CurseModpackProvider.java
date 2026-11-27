// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.curse;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class CurseModpackProvider implements ModpackProvider.SupportOptional {
    public static final CurseModpackProvider INSTANCE = new CurseModpackProvider();

    @Override
    public String getName() {
        return "Curse";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        return new CurseCompletionTask(dependencyManager, instance);
    }

    /// Reads installed CurseForge metadata and binds the original pack's optional-only update.
    @Override
    public Modpack readInstalledModpack(Path instanceRoot, ModpackConfiguration<?> configuration) throws IOException {
        var installed = recoverExcludedFiles(instanceRoot, null);
        CurseManifest manifest = (CurseManifest) installed.getKey();
        @Unmodifiable Set<String> previousExcluded = Set.copyOf(installed.getValue());
        return new Modpack(configuration.getName(), manifest.author(), configuration.getVersion(),
                manifest.minecraft().gameVersion(), "", manifest) {
            /// Changes optional files through the normal CurseForge update and backup tasks.
            @Override
            public Task<?> getInstallTask(DefaultDependencyManager dependency, Path file, GameInstanceID id,
                                          @Nullable String iconUrl, @Nullable Set<String> excludedFiles) {
                var instance = dependency.getGameRepository().getInstance(id);
                return new ModpackUpdateTask(instance, new CurseInstallTask(dependency, manifest, instance,
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
        if (!(modpack.getManifest() instanceof CurseManifest curseManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(instance, new CurseInstallTask(dependencyManager, zipFile, modpack, curseManifest, instance, null, excludedFiles));
    }

    @Override
    public Modpack readManifest(ZipArchiveReader zip, Path file) throws IOException, JsonParseException {
        CurseManifest manifest = JsonUtils.fromNonNullJson(CompressingUtils.readTextZipEntry(zip, "manifest.json"), CurseManifest.class);
        String description = "No description";
        try {
            ZipArchiveEntry modlist = zip.getEntry("modlist.html");
            if (modlist != null)
                description = IOUtils.readFullyAsString(zip.getInputStream(modlist));
        } catch (Throwable ignored) {
        }

        return new Modpack(manifest.name(), manifest.author(), manifest.version(), manifest.minecraft().gameVersion(), description, manifest) {
            @Override
            public Task<?> getInstallTask(
                    DefaultDependencyManager dependencyManager,
                    Path zipFile,
                    GameInstanceID instanceId,
                    String iconUrl,
                    @Nullable Set<String> excludedFiles) {
                return new CurseInstallTask(dependencyManager, zipFile, this, manifest, instanceId, iconUrl, excludedFiles);
            }
        };
    }

    @Override
    public CurseManifest loadFiles(DownloadProvider downloadProvider, ModpackManifest.SupportOptional manifest1) {
        if (!(manifest1 instanceof CurseManifest manifest))
            throw new IllegalArgumentException("manifest1 is not a CurseManifest");
        return manifest.setFiles(
                manifest.files().parallelStream()
                        .map(file -> {
                            if (!file.optional()) {
                                return file;
                            }
                            try {
                                CurseManifestFile result = file;
                                if (StringUtils.isBlank(file.fileName()) || file.url() == null) {
                                    RemoteAddon.File remoteFile = CurseForgeRemoteAddonRepository.getInstance().getAddonFile(
                                            Integer.toString(file.projectID()), Integer.toString(file.fileID()));
                                    result = result.withFileName(remoteFile.filename()).withURL(remoteFile.url());
                                }
                                if (!file.addonQueried()) {
                                    RemoteAddon addon = CurseForgeRemoteAddonRepository.getInstance().getAddonById(
                                            downloadProvider, Integer.toString(file.projectID()));
                                    result = result.withAddon(addon);
                                }
                                return result;
                            } catch (FileNotFoundException fof) {
                                LOG.warning("Could not query api.curseforge.com for deleted mods: " + file.projectID() + ", " + file.fileID(), fof);
                                return file;
                            } catch (IOException | JsonParseException e) {
                                LOG.warning("Unable to fetch the file name projectID=" + file.projectID() + ", fileID=" + file.fileID(), e);
                                return file;
                            }
                        })
                        .toList());
    }

    /// Reads the installed manifest and maps its persisted optional-file exclusions.
    /// Malformed or unreadable records fail instead of silently selecting every file.
    @Override
    public Pair<ModpackManifest.SupportOptional, Set<String>> recoverExcludedFiles(
            Path instanceRoot, @Nullable ModpackManifest.SupportOptional newManifest) throws IOException {
        if (!(newManifest instanceof CurseManifest) && newManifest != null)
            throw new IllegalArgumentException("newManifest is not a CurseManifest");
        Set<String> excludedFiles = new HashSet<>();
        Path oldManifestPath = instanceRoot.resolve("manifest.json");
        CurseManifest oldManifest;
        try {
            oldManifest = JsonUtils.fromNonNullJson(Files.readString(oldManifestPath), CurseManifest.class);
            if (newManifest == null)
                newManifest = oldManifest;
        } catch (IOException | JsonParseException e) {
            throw new IOException("Unable to read installed CurseForge manifest", e);
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
