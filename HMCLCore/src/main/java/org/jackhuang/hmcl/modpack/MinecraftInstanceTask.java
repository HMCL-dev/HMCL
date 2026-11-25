// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Reads an archive's override hashes for a pending modpack installation.
public final class MinecraftInstanceTask<T> extends Task<ModpackConfiguration<T>> {

    private final Path zipFile;
    /// Normalized paths of the archive's override directories.
    private final @Unmodifiable List<String> subDirectories;
    private final T manifest;
    private final String type;
    private final String name;
    /// Pack version, when supplied by the archive format.
    private final @Nullable String version;

    /// Scans the selected override directories for the new modpack configuration.
    public MinecraftInstanceTask(Path zipFile, List<String> subDirectories, T manifest, ModpackProvider modpackProvider, String name, @Nullable String version) {
        this.zipFile = zipFile;
        this.subDirectories = subDirectories.stream().map(FileUtils::normalizePath).toList();
        this.manifest = manifest;
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

    /// Returns the configuration without publishing it to the installed instance.
    @Override
    public ModpackConfiguration<T> execute(TaskExecutionContext context) throws Exception {
        List<ModpackConfiguration.FileInformation> overrides = new ArrayList<>();

        try (var tree = new ZipFileTree(CompressingUtils.openZipFile(zipFile))) {
            for (String subDirectory : subDirectories) {
                ArchiveFileTree.Dir<ZipArchiveEntry> root = tree.getDirectory(subDirectory);
                if (root == null)
                    continue;
                var names = new ArrayList<String>();
                getOverrides(overrides, tree, root, names);
            }
        }
        return new ModpackConfiguration<>(manifest, type, name, version, overrides);
    }
}
