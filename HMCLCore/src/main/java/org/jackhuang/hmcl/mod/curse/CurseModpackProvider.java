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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.mod.ModpackUpdateTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

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
    public Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, String name, File zipFile, Modpack modpack) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof CurseManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(dependencyManager.getGameRepository(), name, new CurseInstallTask(dependencyManager, zipFile, modpack, (CurseManifest) modpack.getManifest(), name));
    }

    @Override
    public Modpack readManifest(ZipFile zip, Path file, Charset encoding) throws IOException, JsonParseException {
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
            public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, File zipFile, String name) {
                return new CurseInstallTask(dependencyManager, zipFile, this, manifest, name);
            }
        };
    }

}
