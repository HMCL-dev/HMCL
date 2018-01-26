/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.mod;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

public final class MinecraftInstanceTask<T> extends Task {

    private final File zipFile;
    private final String subDirectory;
    private final File jsonFile;
    private final T manifest;

    public MinecraftInstanceTask(File zipFile, String subDirectory, T manifest, File jsonFile) {
        this.zipFile = zipFile;
        this.subDirectory = subDirectory;
        this.manifest = manifest;
        this.jsonFile = jsonFile;

        if (!zipFile.exists())
            throw new IllegalArgumentException("File " + zipFile + " does not exist. Cannot parse this modpack.");
    }

    @Override
    public void execute() throws Exception {
        Map<String, ModpackConfiguration.FileInformation> overrides = new HashMap<>();

        byte[] buf = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new FileInputStream(zipFile), null, true, true)) {
            ArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName();
                if (!path.startsWith(subDirectory) || entry.isDirectory())
                    continue;
                path = path.substring(subDirectory.length());
                if (path.startsWith("/") || path.startsWith("\\"))
                    path = path.substring(1);

                overrides.put(path, new ModpackConfiguration.FileInformation(
                        path, DigestUtils.sha1Hex(zip)
                ));
            }
        }

        FileUtils.writeText(jsonFile, Constants.GSON.toJson(new ModpackConfiguration<>(manifest, overrides)));
    }
}
