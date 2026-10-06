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
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class CurseModpackProvider implements ModpackProvider {
    public static final CurseModpackProvider INSTANCE = new CurseModpackProvider();

    @Override
    public String getName() {
        return "Curse";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        return new CurseCompletionTask(dependencyManager, instance);
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
    public Modpack readManifest(ZipArchiveReader zip, Path file, Charset encoding) throws IOException, JsonParseException {
        CurseManifest manifest = JsonUtils.fromNonNullJson(CompressingUtils.readTextZipEntry(zip, "manifest.json"), CurseManifest.class);
        String description = "No description";
        try {
            ZipArchiveEntry modlist = zip.getEntry("modlist.html");
            if (modlist != null)
                description = IOUtils.readFullyAsString(zip.getInputStream(modlist));
        } catch (Throwable ignored) {
        }

        return new Modpack(manifest.name(), manifest.author(), manifest.version(), manifest.minecraft().gameVersion(), description, encoding, manifest) {
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
    public CurseManifest loadFiles(DownloadProvider downloadProvider, ModpackManifest manifest1) {
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
                                    RemoteAddon.File remoteFile = CurseForgeRemoteAddonRepository.MODS.getAddonFile(
                                            Integer.toString(file.projectID()), Integer.toString(file.fileID()));
                                    result = result.withFileName(remoteFile.filename()).withURL(remoteFile.url());
                                }
                                if (!file.addonQueried()) {
                                    RemoteAddon addon = CurseForgeRemoteAddonRepository.MODS.getAddonById(
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
}
