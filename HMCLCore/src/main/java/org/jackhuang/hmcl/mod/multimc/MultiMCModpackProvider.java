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
package org.jackhuang.hmcl.mod.multimc;

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.mod.ModpackUpdateTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;

public final class MultiMCModpackProvider implements ModpackProvider {
    public static final MultiMCModpackProvider INSTANCE = new MultiMCModpackProvider();

    @Override
    public String getName() {
        return "MultiMC";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        return null;
    }

    @Override
    public Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, String name, Path zipFile, Modpack modpack) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof MultiMCInstanceConfiguration multiMCInstanceConfiguration))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(dependencyManager.getGameRepository(), name, new MultiMCModpackInstallTask(dependencyManager, zipFile, modpack, multiMCInstanceConfiguration, name));
    }

    private static String getRootEntryName(ZipArchiveReader file) throws IOException {
        final String instanceFileName = "instance.cfg";

        if (file.getEntry(instanceFileName) != null) return "";

        for (ZipArchiveEntry entry : file.getEntries()) {
            String entryName = entry.getName();

            int idx = entryName.indexOf('/');
            if (idx >= 0
                    && entryName.length() == idx + instanceFileName.length() + 1
                    && entryName.startsWith(instanceFileName, idx + 1))
                return entryName.substring(0, idx + 1);
        }

        throw new IOException("Not a valid MultiMC modpack");
    }

    @Override
    public Modpack readManifest(ZipArchiveReader modpackFile, Path modpackPath, Charset encoding) throws IOException {
        String rootEntryName = getRootEntryName(modpackFile);
        MultiMCManifest manifest = MultiMCManifest.readMultiMCModpackManifest(modpackFile, rootEntryName);

        String name = rootEntryName.isEmpty() ? FileUtils.getNameWithoutExtension(modpackPath) : rootEntryName.substring(0, rootEntryName.length() - 1);
        ZipArchiveEntry instanceEntry = modpackFile.getEntry(rootEntryName + "instance.cfg");

        if (instanceEntry == null)
            throw new IOException("`instance.cfg` not found, " + modpackFile + " is not a valid MultiMC modpack.");
        try (InputStream instanceStream = modpackFile.getInputStream(instanceEntry)) {
            MultiMCInstanceConfiguration cfg = new MultiMCInstanceConfiguration(name, instanceStream, manifest);
            return new Modpack(cfg.getName(), "", "", cfg.getGameVersion(), cfg.getNotes(), encoding, cfg) {
                @Override
                public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, Path zipFile, String name, String iconUrl) {
                    return new MultiMCModpackInstallTask(dependencyManager, zipFile, this, cfg, name);
                }
            };
        }
    }

}
