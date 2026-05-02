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
package org.jackhuang.hmcl.download.cleanroom;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public final class CleanroomInstallTask extends Task<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final CleanroomRemoteVersion remote;
    private Path installer;
    private FileDownloadTask dependent;
    private Task<Version> task;
    private String selfVersion;

    public CleanroomInstallTask(DefaultDependencyManager dependencyManager, Version version, CleanroomRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remote = remoteVersion;

        setSignificance(TaskSignificance.MODERATE);
    }

    public CleanroomInstallTask(DefaultDependencyManager dependencyManager, Version version, String selfVersion, Path installer) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.selfVersion = selfVersion;
        this.remote = null;
        this.installer = installer;

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        if (installer == null) {
            installer = Files.createTempFile("cleanroom-installer", ".jar");

            dependent = new FileDownloadTask(
                    dependencyManager.getDownloadProvider().injectURLsWithCandidates(remote.getUrls()),
                    installer, null);
            dependent.setCacheRepository(dependencyManager.getCacheRepository());
            dependent.setCaching(true);
            dependent.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
        }
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        if (remote != null) {
            Files.deleteIfExists(installer);
        }

        setResult(task.getResult());
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependent == null ? Collections.emptySet() : Collections.singleton(dependent);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return Collections.singleton(task);
    }

    @Override
    public void execute() throws IOException, VersionMismatchException, UnsupportedInstallationException {
        if (selfVersion == null) {
            task = new ForgeNewInstallTask(dependencyManager, version, remote.getSelfVersion(), installer).thenApplyAsync((version) -> version.setId(LibraryAnalyzer.LibraryType.CLEANROOM.getPatchId()));
        } else {
            task = new ForgeNewInstallTask(dependencyManager, version, selfVersion, installer).thenApplyAsync((version) -> version.setId(LibraryAnalyzer.LibraryType.CLEANROOM.getPatchId()));
        }
    }

    public static Task<Version> install(DefaultDependencyManager dependencyManager, Version version, Path installer) throws IOException, VersionMismatchException {
        Optional<String> gameVersion = dependencyManager.getGameRepository().getGameVersion(version);
        if (gameVersion.isEmpty()) throw new IOException();
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (LibraryAnalyzer.LibraryType.CLEANROOM.getPatchId().equals(installProfile.get("profile"))) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.get().equals(profile.getMinecraft()))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion.get());
                return new CleanroomInstallTask(dependencyManager, version, modifyVersion(profile.getVersion()), installer);
            } else {
                throw new IOException();
            }
        }
    }

    private static String modifyVersion(String version) {
        return version.replace("cleanroom-", "");
    }
}
