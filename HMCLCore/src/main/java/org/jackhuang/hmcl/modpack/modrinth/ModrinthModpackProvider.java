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
package org.jackhuang.hmcl.modpack.modrinth;

import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModrinthModpackProvider implements ModpackProvider {
    public static final ModrinthModpackProvider INSTANCE = new ModrinthModpackProvider();

    @Override
    public String getName() {
        return "Modrinth";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        return new ModrinthCompletionTask(dependencyManager, instance);
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
    public Modpack readManifest(ZipArchiveReader zip, Path file, Charset encoding) throws IOException, JsonParseException {
        try {
            ModrinthManifest manifest = JsonUtils.fromNonNullJson(CompressingUtils.readTextZipEntry(zip, "modrinth.index.json"), ModrinthManifest.class);

            return new Modpack(manifest.getName(), "", manifest.getVersionId(), manifest.getGameVersion(), manifest.getSummary(), encoding, manifest) {
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
    public ModpackManifest loadFiles(DownloadProvider downloadProvider, ModpackManifest manifest1) {
        if (!(manifest1 instanceof ModrinthManifest manifest))
            throw new IllegalArgumentException("Manifest is not a ModrinthManifest");
        return manifest.withFiles(manifest.getFiles().parallelStream().map(file -> {
            if (file.optional() && !file.addonQueried()) {
                try {
                    String sha1 = file.hashes().get("sha1");
                    if (sha1 == null) {
                        return file.withAddon(null);
                    }
                    RemoteAddon.Version version = ModrinthRemoteAddonRepository.MODS.getRemoteVersionBySHA1(sha1).orElse(null);
                    if (version == null) {
                        return file.withAddon(null);
                    }
                    RemoteAddon addon = ModrinthRemoteAddonRepository.MODS.getAddonById(downloadProvider, version.projectId());
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
}
