// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.mcbbs;

import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

public final class McbbsModpackProvider implements ModpackProvider {
    public static final McbbsModpackProvider INSTANCE = new McbbsModpackProvider();

    @Override
    public String getName() {
        return "Mcbbs";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        return new McbbsModpackCompletionTask(dependencyManager, instance);
    }

    @Override
    public Task<?> createUpdateTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            Path zipFile,
            Modpack modpack,
            @Nullable Set<String> excludedFiles) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof McbbsModpackManifest mcbbsModpackManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(instance, new McbbsModpackLocalInstallTask(dependencyManager, zipFile, modpack, mcbbsModpackManifest, instance));
    }

    @Override
    public void injectLaunchOptions(String modpackConfigurationJson, LaunchOptions.Builder builder) {
        ModpackConfiguration<McbbsModpackManifest> config = JsonUtils.GSON.fromJson(modpackConfigurationJson, ModpackConfiguration.typeOf(McbbsModpackManifest.class));

        if (!getName().equals(config.getType())) {
            throw new IllegalArgumentException("Incorrect manifest type, actual=" + config.getType() + ", expected=" + getName());
        }

        config.getManifest().injectLaunchOptions(builder);
    }

    private static Modpack fromManifestFile(InputStream json) throws IOException, JsonParseException {
        McbbsModpackManifest manifest = JsonUtils.fromNonNullJsonFully(json, McbbsModpackManifest.class);
        return manifest.toModpack();
    }

    @Override
    public Modpack readManifest(ZipArchiveReader zip, Path file) throws IOException, JsonParseException {
        ZipArchiveEntry mcbbsPackMeta = zip.getEntry("mcbbs.packmeta");
        if (mcbbsPackMeta != null) {
            return fromManifestFile(zip.getInputStream(mcbbsPackMeta));
        }
        ZipArchiveEntry manifestJson = zip.getEntry("manifest.json");
        if (manifestJson != null) {
            return fromManifestFile(zip.getInputStream(manifestJson));
        }
        throw new IOException("`mcbbs.packmeta` or `manifest.json` cannot be found");
    }
}
