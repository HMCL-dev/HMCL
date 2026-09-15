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
package org.jackhuang.hmcl.modpack;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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

    /// Collects the override files under `dir`, in the order the sequential walk visited them.
    ///
    /// @param pending collects the files that still have to be hashed
    /// @param dir     the directory being walked
    /// @param names   the path components from the walked subdirectory down to `dir`, used as a stack
    private static void collectOverrides(List<PendingOverride> pending,
                                         ArchiveFileTree.Dir<ZipArchiveEntry> dir,
                                         List<String> names) {
        String prefix = String.join("/", names);
        if (!prefix.isEmpty())
            prefix = prefix + "/";

        for (Map.Entry<String, ZipArchiveEntry> entry : dir.getFiles().entrySet()) {
            pending.add(new PendingOverride(prefix + entry.getKey(), entry.getValue()));
        }

        for (ArchiveFileTree.Dir<ZipArchiveEntry> subDir : dir.getSubDirs().values()) {
            names.add(subDir.getName());
            collectOverrides(pending, subDir, names);
            names.remove(names.size() - 1);
        }
    }

    @Override
    public void execute() throws Exception {
        // Resolved once because every worker opens its own reader over the same archive.
        Charset charset = CompressingUtils.resolveZipEncoding(zipFile, encoding);

        List<PendingOverride> pending = new ArrayList<>();

        try (var tree = new ZipFileTree(CompressingUtils.openZipFile(zipFile, charset))) {
            for (String subDirectory : subDirectories) {
                ArchiveFileTree.Dir<ZipArchiveEntry> root = tree.getDirectory(subDirectory);
                if (root == null)
                    continue;
                collectOverrides(pending, root, new ArrayList<>());
            }
        }

        List<ModpackConfiguration.FileInformation> overrides = hashOverrides(pending, charset);

        ModpackConfiguration<T> configuration = new ModpackConfiguration<>(manifest, type, name, version, overrides);
        Files.createDirectories(jsonFile.getParent());
        JsonUtils.writeToJsonFile(jsonFile, configuration);
        setResult(configuration);
    }

    /// Digests every override, spreading the entries over several readers.
    ///
    /// Reading the entry bytes is the expensive half of this task and every entry is independent, so
    /// the hashing is split the same way the extraction is.
    ///
    /// @param pending the collected overrides, in traversal order
    /// @param charset the charset every reader decodes entry names with
    /// @return one record per collected override, in the same order
    /// @throws IOException if any entry cannot be read
    private List<ModpackConfiguration.FileInformation> hashOverrides(List<PendingOverride> pending, Charset charset) throws IOException {
        ModpackConfiguration.FileInformation[] overrides = new ModpackConfiguration.FileInformation[pending.size()];

        int threads = CompressingUtils.readerThreads(pending.size());
        CompressingUtils.forEachParallel(zipFile, charset, threads, pending.size(), "ModpackScan-",
                (reader, index) -> {
                    PendingOverride override = pending.get(index);
                    try (InputStream input = reader.getInputStream(override.entry())) {
                        overrides[index] = new ModpackConfiguration.FileInformation(
                                override.path(),
                                DigestUtils.digestToString("SHA-1", input));
                    }
                });

        return new ArrayList<>(Arrays.asList(overrides));
    }

    /// An override file that still has to be hashed.
    ///
    /// @param path  the path recorded in [ModpackConfiguration]
    /// @param entry the archive entry holding its bytes
    private record PendingOverride(String path, ZipArchiveEntry entry) {
    }
}
