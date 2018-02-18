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

import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedList;
import java.util.List;

public final class MinecraftInstanceTask<T> extends Task {

    private final File zipFile;
    private final String subDirectory;
    private final File jsonFile;
    private final T manifest;
    private final String type;

    public MinecraftInstanceTask(File zipFile, String subDirectory, T manifest, String type, File jsonFile) {
        this.zipFile = zipFile;
        this.subDirectory = subDirectory;
        this.manifest = manifest;
        this.jsonFile = jsonFile;
        this.type = type;

        if (!zipFile.exists())
            throw new IllegalArgumentException("File " + zipFile + " does not exist. Cannot parse this modpack.");
    }

    @Override
    public void execute() throws Exception {
        List<ModpackConfiguration.FileInformation> overrides = new LinkedList<>();

        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new FileInputStream(zipFile), null, true, true)) {
            ArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName();
                if (!path.startsWith(subDirectory) || entry.isDirectory())
                    continue;
                path = path.substring(subDirectory.length());
                if (path.startsWith("/") || path.startsWith("\\"))
                    path = path.substring(1);

                overrides.add(new ModpackConfiguration.FileInformation(path, DigestUtils.sha1Hex(zip)));
            }
        }

        FileUtils.writeText(jsonFile, Constants.GSON.toJson(new ModpackConfiguration<>(manifest, type, overrides)));
    }
}
