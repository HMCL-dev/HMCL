// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.multimc;

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.modpack.MismatchedModpackTypeException;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.modpack.ModpackUpdateTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

public final class MultiMCModpackProvider implements ModpackProvider {
    public static final MultiMCModpackProvider INSTANCE = new MultiMCModpackProvider();

    @Override
    public String getName() {
        return "MultiMC";
    }

    @Override
    public @Nullable Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        return null;
    }

    @Override
    public Task<?> createUpdateTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            Path zipFile,
            Modpack modpack,
            @Nullable Set<String> excludedFiles) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof MultiMCInstanceConfiguration multiMCInstanceConfiguration))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(instance, new MultiMCModpackInstallTask(dependencyManager, zipFile, modpack, multiMCInstanceConfiguration, instance));
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
    public Modpack readManifest(ZipArchiveReader modpackFile, Path modpackPath) throws IOException {
        String rootEntryName = getRootEntryName(modpackFile);
        MultiMCManifest manifest = MultiMCManifest.readMultiMCModpackManifest(modpackFile, rootEntryName);

        String name = rootEntryName.isEmpty() ? FileUtils.getNameWithoutExtension(modpackPath) : rootEntryName.substring(0, rootEntryName.length() - 1);
        ZipArchiveEntry instanceEntry = modpackFile.getEntry(rootEntryName + "instance.cfg");

        if (instanceEntry == null)
            throw new IOException("`instance.cfg` not found, " + modpackFile + " is not a valid MultiMC modpack.");
        try (InputStream instanceStream = modpackFile.getInputStream(instanceEntry)) {
            MultiMCInstanceConfiguration cfg = new MultiMCInstanceConfiguration(name, instanceStream, manifest);
            return new Modpack(cfg.getName(), "", "", cfg.getGameVersion(), cfg.getNotes(), cfg) {
                @Override
                public Task<?> getInstallTask(
                        DefaultDependencyManager dependencyManager,
                        Path zipFile,
                        GameInstanceID instanceId,
                        String iconUrl,
                        @Nullable Set<String> excludedFiles) {
                    return new MultiMCModpackInstallTask(dependencyManager, zipFile, this, cfg, instanceId);
                }
            };
        }
    }

}
