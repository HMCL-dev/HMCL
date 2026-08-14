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
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class CleanroomInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    /// Minecraft version whose vanilla client JAR is required by the installer processors.
    private final String gameVersion;
    private final @Nullable CleanroomRemoteVersion remote;
    private @Nullable Path installer;
    private @Nullable FileDownloadTask dependent;
    private @Nullable Task<GameInstancePatch> task;
    private @Nullable String selfVersion;

    /// Creates a Cleanroom task that downloads the selected installer.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Cleanroom patch
    /// @param remoteVersion     selected Cleanroom version
    public CleanroomInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            CleanroomRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.gameVersion = remoteVersion.getGameVersion();
        this.remote = remoteVersion;

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Creates a Cleanroom task backed by an existing local installer.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Cleanroom patch
    /// @param gameVersion       Minecraft version expected by the installation
    /// @param selfVersion       Cleanroom version recorded in the returned patch
    /// @param installer         Cleanroom installer JAR
    public CleanroomInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            String selfVersion,
            Path installer) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.gameVersion = gameVersion;
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

            CleanroomRemoteVersion remoteVersion = Objects.requireNonNull(remote);
            dependent = new FileDownloadTask(
                    dependencyManager.getDownloadProvider().injectURLsWithCandidates(remoteVersion.getUrls()),
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
            Files.deleteIfExists(Objects.requireNonNull(installer));
        }

        setResult(Objects.requireNonNull(task).getResult());
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependent == null ? Collections.emptySet() : Collections.singleton(dependent);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return Collections.singleton(Objects.requireNonNull(task));
    }

    @Override
    public void execute() throws IOException, VersionMismatchException, UnsupportedInstallationException {
        String cleanroomVersion;
        if (selfVersion == null) {
            cleanroomVersion = Objects.requireNonNull(remote).getSelfVersion();
        } else {
            cleanroomVersion = selfVersion;
        }

        task = new GameDownloadTask(dependencyManager, gameVersion, manifest)
                .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                        dependencyManager,
                        manifest,
                        minecraftJar,
                        cleanroomVersion,
                        Objects.requireNonNull(installer)))
                .thenApplyAsync(patch -> patch.withId(GameComponentType.CLEANROOM));
    }

    /// Creates a task that installs Cleanroom from a local installer JAR.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Cleanroom patch
    /// @param gameVersion       Minecraft version expected by the installation
    /// @param installer         Cleanroom installer JAR
    /// @return the task producing the Cleanroom patch
    /// @throws IOException              if the installer profile is missing, malformed, or not a
    ///                                  Cleanroom profile
    /// @throws VersionMismatchException if the installer targets another Minecraft version
    public static Task<GameInstancePatch> install(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            Path installer) throws IOException, VersionMismatchException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (GameComponentType.CLEANROOM.getPatchId().equals(installProfile.get("profile"))) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.equals(profile.getMinecraft()))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion);
                return new CleanroomInstallTask(
                        dependencyManager,
                        manifest,
                        gameVersion,
                        modifyVersion(profile.getVersion()),
                        installer);
            } else {
                throw new IOException();
            }
        }
    }

    private static String modifyVersion(String version) {
        return version.replace("cleanroom-", "");
    }
}
