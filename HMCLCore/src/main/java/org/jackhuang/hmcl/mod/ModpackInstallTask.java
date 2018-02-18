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
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.IOUtils;

import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class ModpackInstallTask<T> extends Task {

    private final File modpackFile;
    private final File dest;
    private final String subDirectory;
    private final List<ModpackConfiguration.FileInformation> overrides;
    private final Predicate<String> callback;

    public ModpackInstallTask(File modpackFile, File dest, String subDirectory, Predicate<String> callback, ModpackConfiguration<T> oldConfiguration) {
        this.modpackFile = modpackFile;
        this.dest = dest;
        this.subDirectory = subDirectory;
        this.callback = callback;

        if (oldConfiguration == null)
            overrides = Collections.emptyList();
        else
            overrides = oldConfiguration.getOverrides();
    }

    @Override
    public void execute() throws Exception {
        Set<String> entries = new HashSet<>();
        byte[] buf = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
        if (!FileUtils.makeDirectory(dest))
            throw new IOException("Unable to make directory " + dest);

        HashSet<String> files = new HashSet<>();
        for (ModpackConfiguration.FileInformation file : overrides)
            files.add(file.getPath());

        try (ZipArchiveInputStream zipStream = new ZipArchiveInputStream(new FileInputStream(modpackFile), null, true, true)) {
            ArchiveEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                String path = entry.getName();

                if (!path.startsWith(subDirectory))
                    continue;
                path = path.substring(subDirectory.length());
                if (path.startsWith("/") || path.startsWith("\\"))
                    path = path.substring(1);
                File entryFile = new File(dest, path);

                if (callback != null)
                    if (!callback.test(path))
                        continue;

                if (entry.isDirectory()) {
                    if (!FileUtils.makeDirectory(entryFile))
                        throw new IOException("Unable to make directory: " + entryFile);
                } else {
                    if (!FileUtils.makeDirectory(entryFile.getAbsoluteFile().getParentFile()))
                        throw new IOException("Unable to make parent directory for file " + entryFile);

                    entries.add(path);

                    ByteArrayOutputStream os = new ByteArrayOutputStream(IOUtils.DEFAULT_BUFFER_SIZE);
                    IOUtils.copyTo(zipStream, os, buf);
                    byte[] data = os.toByteArray();

                    if (files.contains(path) && entryFile.exists()) {
                        String oldHash = DigestUtils.sha1Hex(new FileInputStream(entryFile));
                        String newHash = DigestUtils.sha1Hex(new ByteArrayInputStream(data));
                        if (!oldHash.equals(newHash)) {
                            try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                                IOUtils.copyTo(new ByteArrayInputStream(data), fos, buf);
                            }
                        }
                    } else if (!files.contains(path)) {
                        try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                            IOUtils.copyTo(new ByteArrayInputStream(data), fos, buf);
                        }
                    }

                }
            }
        }

        for (ModpackConfiguration.FileInformation file : overrides) {
            File original = new File(dest, file.getPath());
            if (original.exists() && !entries.contains(file.getPath()))
                original.delete();
        }
    }
}
