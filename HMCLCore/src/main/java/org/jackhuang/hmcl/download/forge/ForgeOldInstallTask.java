// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ForgeOldInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final Path installer;
    private final String selfVersion;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    ForgeOldInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, String selfVersion, Path installer) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.installer = installer;
        this.selfVersion = selfVersion;

        setSignificance(TaskSignificance.MAJOR);
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void execute() throws Exception {
        try (ZipFile zipFile = new ZipFile(installer.toFile())) {
            InputStream stream = zipFile.getInputStream(zipFile.getEntry("install_profile.json"));
            if (stream == null)
                throw new ArtifactMalformedException("Malformed forge installer file, install_profile.json does not exist.");
            ForgeInstallProfile installProfile = JsonUtils.fromNonNullJsonFully(stream, ForgeInstallProfile.class);

            // unpack the universal jar in the installer file.
            Library forgeLibrary = new Library(installProfile.install().getPath());
            GameRepository gameRepository = dependencyManager.getGameRepository();
            Path forgeFile = gameRepository.getLayout().getLibraryFile(manifest.id(), forgeLibrary);
            Files.createDirectories(forgeFile.getParent());

            ZipEntry forgeEntry = zipFile.getEntry(installProfile.install().getFilePath());
            try (InputStream is = zipFile.getInputStream(forgeEntry);
                 OutputStream os = Files.newOutputStream(forgeFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                is.transferTo(os);
            }

            setResult(GameInstancePatch.fromManifest(
                    installProfile.versionInfo(),
                    GameComponentType.FORGE.getPatchId(),
                    selfVersion,
                    GameInstancePatch.PRIORITY_LOADER));
            dependencies.add(dependencyManager.checkComponentCompletionAsync(installProfile.versionInfo(), true));
        } catch (ZipException ex) {
            throw new ArtifactMalformedException("Malformed forge installer file", ex);
        }
    }
}
