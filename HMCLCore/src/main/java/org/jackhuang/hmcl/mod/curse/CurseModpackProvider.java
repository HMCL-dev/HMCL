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
package org.jackhuang.hmcl.mod.curse;

import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.mod.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class CurseModpackProvider implements ModpackProvider {
    public static final CurseModpackProvider INSTANCE = new CurseModpackProvider();

    @Override
    public String getName() {
        return "Curse";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        return new CurseCompletionTask(dependencyManager, version);
    }

    @Override
    public Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, String name, File zipFile, Modpack modpack, Set<? extends ModpackFile> selectedFiles) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof CurseManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(dependencyManager.getGameRepository(), name, new CurseInstallTask(dependencyManager, zipFile, modpack, (CurseManifest) modpack.getManifest(), name, selectedFiles));
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

        return new Modpack(manifest.getName(), manifest.getAuthor(), manifest.getVersion(), manifest.getMinecraft().getGameVersion(), description, encoding, manifest) {
            @Override
            public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, File zipFile, String name, Set<? extends ModpackFile> selectedFiles) {
                return new CurseInstallTask(dependencyManager, zipFile, this, manifest, name, selectedFiles);
            }
        };
    }

    @Override
    public CurseManifest loadFiles(ModpackManifest manifest1) {
        if (!(manifest1 instanceof CurseManifest))
            throw new IllegalArgumentException("manifest1 is not a CurseManifest");
        CurseManifest manifest = (CurseManifest) manifest1;
        return manifest.setFiles(
                manifest.getFiles().parallelStream()
                        .map(file -> {
                            if ((StringUtils.isBlank(file.getFileName()) || file.getUrl() == null) && file.isOptional()) {
                                try {
                                    RemoteMod mod = CurseForgeRemoteModRepository.MODS.getModById(Integer.toString(file.getProjectID()));
                                    RemoteMod.File remoteFile = CurseForgeRemoteModRepository.MODS.getModFile(Integer.toString(file.getProjectID()), Integer.toString(file.getFileID()));
                                    return file.withFileName(remoteFile.getFilename()).withURL(remoteFile.getUrl()).withMod(mod);
                                } catch (FileNotFoundException fof) {
                                    LOG.warning("Could not query api.curseforge.com for deleted mods: " + file.getProjectID() + ", " + file.getFileID(), fof);
                                    return file;
                                } catch (IOException | JsonParseException e) {
                                    LOG.warning("Unable to fetch the file name projectID=" + file.getProjectID() + ", fileID=" + file.getFileID(), e);
                                    return file;
                                }
                            } else {
                                return file;
                            }
                        })
                        .collect(Collectors.toList()));
    }
}
