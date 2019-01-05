/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.util.DigestUtils.digest;
import static org.jackhuang.hmcl.util.Hex.encodeHex;

public class ModpackInstallTask<T> extends Task {

    private final File modpackFile;
    private final File dest;
    private final Charset charset;
    private final String subDirectory;
    private final List<ModpackConfiguration.FileInformation> overrides;
    private final Predicate<String> callback;

    public ModpackInstallTask(File modpackFile, File dest, Charset charset, String subDirectory, Predicate<String> callback, ModpackConfiguration<T> oldConfiguration) {
        this.modpackFile = modpackFile;
        this.dest = dest;
        this.charset = charset;
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
        if (!FileUtils.makeDirectory(dest))
            throw new IOException("Unable to make directory " + dest);

        HashMap<String, ModpackConfiguration.FileInformation> files = new HashMap<>();
        for (ModpackConfiguration.FileInformation file : overrides)
            files.put(file.getPath(), file);

        new Unzipper(modpackFile, dest)
                .setSubDirectory(subDirectory)
                .setTerminateIfSubDirectoryNotExists()
                .setReplaceExistentFile(true)
                .setEncoding(charset)
                .setFilter((destPath, isDirectory, zipEntry, entryPath) -> {
                    if (isDirectory) return true;
                    if (!callback.test(entryPath)) return false;
                    entries.add(entryPath);

                    if (!files.containsKey(entryPath)) {
                        // If old modpack does not have this entry, add this entry or override the file that user added.
                        return true;
                    } else if (!Files.exists(destPath)) {
                        // If both old and new modpacks have this entry, but the file is deleted by user, leave it missing.
                        return false;
                    } else {
                        // If user modified this entry file, we will not replace this file since this modified file is that user expects.
                        String fileHash = encodeHex(digest("SHA-1", Files.newInputStream(destPath)));
                        String oldHash = files.get(entryPath).getHash();
                        return Objects.equals(oldHash, fileHash);
                    }
                }).unzip();

        // If old modpack have this entry, and new modpack deleted it. Delete this file.
        for (ModpackConfiguration.FileInformation file : overrides) {
            File original = new File(dest, file.getPath());
            if (original.exists() && !entries.contains(file.getPath()))
                original.delete();
        }
    }
}
