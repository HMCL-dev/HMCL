/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.modrinth;

import com.google.gson.JsonParseException;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.mod.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class ModrinthModpackProvider implements ModpackProvider {
    public static final ModrinthModpackProvider INSTANCE = new ModrinthModpackProvider();

    @Override
    public String getName() {
        return "Modrinth";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        return new ModrinthCompletionTask(dependencyManager, version);
    }

    @Override
    public Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, String name, File zipFile, Modpack modpack, Set<? extends ModpackFile> selectedFiles) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof ModrinthManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        // TODO: Fix optional files
        return new ModpackUpdateTask(dependencyManager.getGameRepository(), name, new ModrinthInstallTask(dependencyManager, zipFile, modpack, (ModrinthManifest) modpack.getManifest(), name, selectedFiles));
    }

    @Override
    public Modpack readManifest(ZipFile zip, Path file, Charset encoding) throws IOException, JsonParseException {
        ModrinthManifest manifest = JsonUtils.fromNonNullJson(CompressingUtils.readTextZipEntry(zip, "modrinth.index.json"), ModrinthManifest.class);
        return new Modpack(manifest.getName(), "", manifest.getVersionId(), manifest.getGameVersion(), manifest.getSummary(), encoding, manifest) {
            @Override
            public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, java.io.File zipFile, String name, Set<? extends ModpackFile> selectedFiles) {
                return new ModrinthInstallTask(dependencyManager, zipFile, this, manifest, name, selectedFiles);
            }
        };
    }

    @Override
    public ModpackManifest loadFiles(ModpackManifest manifest1) {
        if (!(manifest1 instanceof ModrinthManifest))
            throw new IllegalArgumentException("Manifest is not a ModrinthManifest");
        ModrinthManifest manifest = (ModrinthManifest) manifest1;
        return manifest.withFiles(manifest.getFiles().parallelStream().map(file -> {
            if (file.isOptional() && file.getMod() == null) {
                try {
                    RemoteMod.Version version = ModrinthRemoteModRepository.MODS.getRemoteVersionBySHA1(file.getHashes().get("sha1")).orElse(null);
                    if (version == null) {
                        return file.withMod(Optional.empty());
                    }
                    RemoteMod mod = ModrinthRemoteModRepository.MODS.getModById(version.getModid());
                    return file.withMod(Optional.ofNullable(mod));
                } catch (FileNotFoundException fof) {
                    Logging.LOG.log(Level.WARNING, "Could not query modrinth for deleted mods: " + file.getFileName(), fof);
                    return file;
                } catch (IOException | JsonParseException e) {
                    Logging.LOG.log(Level.WARNING, "Unable to fetch the modid for" + file.getFileName(), e);
                    return file;
                }
            } else {
                return file;
            }
        }).collect(Collectors.toList()));
    }
}
