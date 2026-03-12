/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MinecraftInstanceTask<T> extends Task<ModpackConfiguration<T>> {

    private final Path zipFile;
    private final Charset encoding;
    private final List<String> subDirectories;
    private final Path jsonFile;
    private final T manifest;
    private final String type;
    private final String name;
    private final String version;

    public MinecraftInstanceTask(Path zipFile, Charset encoding, List<String> subDirectories, T manifest, ModpackProvider modpackProvider, String name, String version, Path jsonFile) {
        this.zipFile = zipFile;
        this.encoding = encoding;
        this.subDirectories = subDirectories.stream().map(FileUtils::normalizePath).toList();
        this.manifest = manifest;
        this.jsonFile = jsonFile;
        this.type = modpackProvider.getName();
        this.name = name;
        this.version = version;
    }

    private static void getOverrides(List<ModpackConfiguration.FileInformation> overrides,
                                     ZipFileTree tree,
                                     ArchiveFileTree.Dir<ZipArchiveEntry> dir,
                                     List<String> names) throws IOException {
        String prefix = String.join("/", names);
        if (!prefix.isEmpty())
            prefix = prefix + "/";

        for (Map.Entry<String, ZipArchiveEntry> entry : dir.getFiles().entrySet()) {
            String hash;
            try (InputStream input = tree.getInputStream(entry.getValue())) {
                hash = DigestUtils.digestToString("SHA-1", input);
            }
            overrides.add(new ModpackConfiguration.FileInformation(prefix + entry.getKey(), hash));
        }

        for (ArchiveFileTree.Dir<ZipArchiveEntry> subDir : dir.getSubDirs().values()) {
            names.add(subDir.getName());
            getOverrides(overrides, tree, subDir, names);
            names.remove(names.size() - 1);
        }
    }

    @Override
    public void execute() throws Exception {
        List<ModpackConfiguration.FileInformation> overrides = new ArrayList<>();

        try (var tree = new ZipFileTree(CompressingUtils.openZipFileWithPossibleEncoding(zipFile, encoding))) {
            for (String subDirectory : subDirectories) {
                ArchiveFileTree.Dir<ZipArchiveEntry> root = tree.getDirectory(subDirectory);
                if (root == null)
                    continue;
                var names = new ArrayList<String>();
                getOverrides(overrides, tree, root, names);
            }
        }
        ModpackConfiguration<T> configuration = new ModpackConfiguration<>(manifest, type, name, version, overrides);
        Files.createDirectories(jsonFile.getParent());
        JsonUtils.writeToJsonFile(jsonFile, configuration);
        setResult(configuration);
    }
}
