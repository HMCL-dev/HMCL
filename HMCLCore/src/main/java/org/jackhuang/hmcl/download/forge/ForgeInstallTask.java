/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

import static org.jackhuang.hmcl.download.UnsupportedInstallationException.UNSUPPORTED_LAUNCH_WRAPPER;
import static org.jackhuang.hmcl.download.forge.ForgeInstallation.detectForgeInstallerType;

/**
 *
 * @author huangyuhui
 */
public final class ForgeInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private Path installer;
    private final ForgeRemoteVersion remote;
    private FileDownloadTask dependent;
    private Task<GameInstancePatch> dependency;

    public ForgeInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, ForgeRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remote = remoteVersion;
        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        installer = Files.createTempFile("forge-installer", ".jar");

        dependent = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLsWithCandidates(remote.getUrls()),
                installer, null);
        dependent.setCacheRepository(dependencyManager.getCacheRepository());
        dependent.setCaching(true);
        dependent.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        Files.deleteIfExists(installer);
        setResult(dependency.getResult());
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(dependent);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return Collections.singleton(dependency);
    }

    @Override
    public void execute() throws IOException, VersionMismatchException, UnsupportedInstallationException {
        String originalMainClass = dependencyManager.getGameRepository().resolve(manifest).launchManifest().mainClass();
        if (GameVersionNumber.compare("1.13", remote.getGameVersion()) <= 0) {
            // Forge 1.13 is not compatible with fabric.
            if (!GameComponentAnalyzer.FORGE_OPTIFINE_MAIN.contains(originalMainClass))
                throw new UnsupportedInstallationException(UNSUPPORTED_LAUNCH_WRAPPER);
        }

        var type = detectForgeInstallerType(remote.getGameVersion(), installer);

        switch (type) {
            case LEGACY_MODLOADER, LEGACY_FML ->
                    dependency = new ForgeLegacyInstallTask(dependencyManager, manifest, remote.getSelfVersion(), installer, type);
            case OLD ->
                    dependency = new ForgeOldInstallTask(dependencyManager, manifest, remote.getSelfVersion(), installer);
            case NEW -> dependency = new GameDownloadTask(dependencyManager, manifest)
                    .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                            dependencyManager,
                            manifest,
                            minecraftJar,
                            remote.getSelfVersion(),
                            installer));
        }
    }
}
